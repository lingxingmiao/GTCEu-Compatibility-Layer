package com.gtceu.compat.core;

import com.gtceu.compat.api.GTCEuVersion;
import com.gtceu.compat.util.GTCEuCompatLogger;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * Utility class to detect the installed GTCEu version at runtime.
 *
 * Detection is intentionally lenient: GTQT builds and re-branded packs keep the
 * {@code gregtech} mod id but report arbitrary version strings such as
 * {@code "2.9.0-190"}, {@code "GTCEu-2.9.0-190"}, {@code "v2.9.0"} or the legacy
 * GTQT {@code "1.9.0"}. Version strings are matched anywhere inside the reported
 * text, and class/member probes are used as a fallback (and as a confirmation for
 * the legacy {@code 1.9.x} builds).
 */
public class GTCEuVersionDetector {

    private static final String GTCEU_MOD_ID = "gregtech";

    /** Matches {@code 2.8} / {@code 2.8.10} / {@code 2.8.x} anywhere in a string. */
    private static final Pattern V28_PATTERN = Pattern.compile("(?<![0-9.])2[.]8(?:\\..*)?");

    /** Matches {@code 2.9} / {@code 2.9.0} / {@code 2.9.0-190} anywhere in a string. */
    private static final Pattern V29_PATTERN = Pattern.compile("(?<![0-9.])2[.]9(?:\\..*)?");

    /**
     * Legacy GTQT builds (the 2.9 code base re-released as GTQT) report {@code 1.9.x}.
     * Such a string is only treated as 2.9 when a 2.9 feature class is actually present.
     */
    private static final Pattern GTQT_LEGACY_19_PATTERN = Pattern.compile("(?<![0-9.])1[.]9(?:\\..*)?");

    /**
     * Classes that only exist in the GTCEu 2.9 API (verified with javap against the
     * 2.9 jar; see README "2.9 only" list). Any single hit means 2.9.
     */
    private static final String[] V29_FEATURE_CLASSES = {
            "gregtech.api.metatileentity.registry.MTEManager",
            "gregtech.api.metatileentity.registry.MTERegistry",
            "gregtech.api.metatileentity.interfaces.IPollution",
            "com.cleanroommc.modularui.api.IGuiHolder"
    };

    private static final String GREGTECH_API_CLASS = "gregtech.api.GregTechAPI";

    /** GregTechAPI fields that were added in 2.9 and do not exist in 2.8. */
    private static final String[] V29_FEATURE_FIELDS = { "mteManager", "coilManager" };

    /** Class that exists in the 2.8 API and was removed/relocated in 2.9. */
    private static final String V28_FEATURE_CLASS =
            "gregtech.api.unification.material.properties.ToolProperty$Builder";

    /**
     * Detects the installed GTCEu version.
     * @return The detected GTCEu version, or null if GTCEu is not installed.
     */
    public static GTCEuVersion detectVersion() {
        // First check if GTCEu is installed
        ModContainer gtceuMod = Loader.instance().getIndexedModList().get(GTCEU_MOD_ID);
        if (gtceuMod == null) {
            GTCEuCompatLogger.error("GregTech CEu is not installed!");
            return null;
        }

        String version = gtceuMod.getVersion();
        GTCEuCompatLogger.info("Found GregTech CEu version: {}", version);

        // Try to detect version from the mod metadata (lenient, matches anywhere)
        GTCEuVersion detected = parseVersionString(version);
        if (detected != GTCEuVersion.UNKNOWN) {
            GTCEuCompatLogger.info("Detected GTCEu {} from version string '{}'", detected, version);
            return detected;
        }

        // Legacy GTQT builds ship the 2.9 API but report 1.9.x: only trust 2.9 feature classes
        if (looksLikeLegacyGtqt(version)) {
            GTCEuCompatLogger.info("Version string '{}' looks like a legacy GTQT 1.9.x build, "
                    + "confirming against 2.9 feature classes...", version);
            GTCEuVersion classDetected = detectByVersionSpecificClasses();
            if (classDetected == GTCEuVersion.V2_9) {
                GTCEuCompatLogger.info("Confirmed GTCEu 2.9 API behind legacy GTQT version string '{}'", version);
                return GTCEuVersion.V2_9;
            }
            GTCEuCompatLogger.warn("Version string '{}' looks like legacy GTQT 1.9.x but no 2.9 feature "
                    + "class is present; leaving version as UNKNOWN.", version);
            return GTCEuVersion.UNKNOWN;
        }

        // If version detection failed, try to detect by checking for specific classes/members
        GTCEuCompatLogger.warn("Could not determine GTCEu version from '{}', attempting class-based detection...",
                version);
        detected = detectByVersionSpecificClasses();
        if (detected != GTCEuVersion.UNKNOWN) {
            GTCEuCompatLogger.info("Class-based detection resolved GTCEu version: {}", detected);
            return detected;
        }

        GTCEuCompatLogger.warn("Could not determine GTCEu version from '{}'; compatibility layer will run "
                + "in UNKNOWN mode.", version);
        return GTCEuVersion.UNKNOWN;
    }

    /**
     * Lenient version string parsing: matches {@code 2.8(...)} / {@code 2.9(...)} anywhere in the
     * reported string, so prefixed versions ({@code "GTCEu-2.9.0-190"}, {@code "v2.9.0"}) work.
     * The legacy GTQT {@code 1.9.x} form is deliberately NOT handled here (see {@link #detectVersion()}).
     *
     * @param versionString The raw mod metadata version string.
     * @return The parsed version, or {@link GTCEuVersion#UNKNOWN} when the string has no usable marker.
     */
    static GTCEuVersion parseVersionString(String versionString) {
        if (versionString == null) {
            return GTCEuVersion.UNKNOWN;
        }
        String trimmed = versionString.trim();
        if (trimmed.isEmpty()) {
            return GTCEuVersion.UNKNOWN;
        }

        // 2.9 is checked first: a 2.9 string also contains no 2.8 marker, but be explicit.
        if (V29_PATTERN.matcher(trimmed).find()) {
            return GTCEuVersion.V2_9;
        }
        if (V28_PATTERN.matcher(trimmed).find()) {
            return GTCEuVersion.V2_8;
        }

        // Keep the canonical enum parser as a last resort for any future upstream formats.
        return GTCEuVersion.fromString(trimmed);
    }

    /**
     * Checks whether a version string uses the legacy GTQT {@code 1.9.x} numbering.
     */
    private static boolean looksLikeLegacyGtqt(String versionString) {
        if (versionString == null) {
            return false;
        }
        return GTQT_LEGACY_19_PATTERN.matcher(versionString.trim()).find();
    }

    /**
     * Attempts to detect GTCEu version by checking for version-specific classes or members.
     * This is a fallback mechanism when version string parsing fails.
     *
     * Every probe is individually guarded: a single {@link ClassNotFoundException} or
     * {@link NoSuchFieldException} never aborts detection (that was the 2.8 bug: looking up
     * {@code GregTechAPI.coilManager} threw and the MTEManager / IPollution probes never ran).
     *
     * @return The detected GTCEu version, or UNKNOWN if detection fails.
     */
    private static GTCEuVersion detectByVersionSpecificClasses() {
        // 1) 2.9-only classes (strongest evidence)
        for (String className : V29_FEATURE_CLASSES) {
            if (safeClassExists(className)) {
                GTCEuCompatLogger.info("Detected GTCEu 2.9 (feature class present: {})", className);
                return GTCEuVersion.V2_9;
            }
            GTCEuCompatLogger.debug("2.9 feature class absent: {}", className);
        }

        // 2) 2.9-only static fields on GregTechAPI (auxiliary evidence)
        for (String fieldName : V29_FEATURE_FIELDS) {
            if (safeFieldExists(GREGTECH_API_CLASS, fieldName)) {
                GTCEuCompatLogger.info("Detected GTCEu 2.9 ({} exposes field '{}')",
                        GREGTECH_API_CLASS, fieldName);
                return GTCEuVersion.V2_9;
            }
            GTCEuCompatLogger.debug("2.9 feature field absent: {}#{}", GREGTECH_API_CLASS, fieldName);
        }

        // 3) 2.8-only class
        if (safeClassExists(V28_FEATURE_CLASS)) {
            GTCEuCompatLogger.info("Detected GTCEu 2.8 (feature class present: {})", V28_FEATURE_CLASS);
            return GTCEuVersion.V2_8;
        }
        GTCEuCompatLogger.debug("2.8 feature class absent: {}", V28_FEATURE_CLASS);

        GTCEuCompatLogger.warn("Class-based GTCEu version detection failed: none of the known "
                + "2.9/2.8 feature markers were found on the classpath.");
        return GTCEuVersion.UNKNOWN;
    }

    /**
     * Class existence probe that never throws: handles missing classes, linkage errors
     * (missing supertypes / wrong MC version) and any other reflective failure.
     */
    private static boolean safeClassExists(String className) {
        try {
            Class.forName(className, false, GTCEuVersionDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (LinkageError e) {
            GTCEuCompatLogger.debug("Class probe '{}' failed with linkage error: {}", className, e.toString());
            return false;
        } catch (Throwable t) {
            GTCEuCompatLogger.debug("Class probe '{}' failed: {}", className, t.toString());
            return false;
        }
    }

    /**
     * Field existence probe that never throws (public field first, then declared field).
     */
    private static boolean safeFieldExists(String className, String fieldName) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, GTCEuVersionDetector.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return false;
        } catch (LinkageError e) {
            GTCEuCompatLogger.debug("Field probe class '{}' failed with linkage error: {}",
                    className, e.toString());
            return false;
        } catch (Throwable t) {
            GTCEuCompatLogger.debug("Field probe class '{}' failed: {}", className, t.toString());
            return false;
        }

        try {
            clazz.getField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            GTCEuCompatLogger.debug("Public field {}#{} not found, trying declared fields", className, fieldName);
        } catch (Throwable t) {
            GTCEuCompatLogger.debug("Field lookup {}#{} failed: {}", className, fieldName, t.toString());
        }

        try {
            clazz.getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        } catch (Throwable t) {
            GTCEuCompatLogger.debug("Declared field lookup {}#{} failed: {}", className, fieldName, t.toString());
            return false;
        }
    }

    /**
     * Checks if a specific class exists in the classpath.
     * @param className The fully qualified class name.
     * @return true if the class exists, false otherwise.
     */
    public static boolean classExists(String className) {
        try {
            Class.forName(className, false, GTCEuVersionDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (LinkageError e) {
            GTCEuCompatLogger.debug("classExists({}) hit a linkage error: {}", className, e.toString());
            return false;
        }
    }

    /**
     * Checks if a specific method exists in a class.
     * @param className The fully qualified class name.
     * @param methodName The method name.
     * @param paramTypes The parameter types (optional).
     * @return true if the method exists, false otherwise.
     */
    public static boolean methodExists(String className, String methodName, Class<?>... paramTypes) {
        try {
            Class<?> clazz = Class.forName(className, false, GTCEuVersionDetector.class.getClassLoader());
            Method method = clazz.getMethod(methodName, paramTypes);
            return method != null;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        } catch (LinkageError e) {
            GTCEuCompatLogger.debug("methodExists({}#{}) hit a linkage error: {}",
                    className, methodName, e.toString());
            return false;
        }
    }

    /**
     * Checks if a specific field exists in a class.
     * @param className The fully qualified class name.
     * @param fieldName The field name.
     * @return true if the field exists, false otherwise.
     */
    public static boolean fieldExists(String className, String fieldName) {
        try {
            Class<?> clazz = Class.forName(className, false, GTCEuVersionDetector.class.getClassLoader());
            Field field = clazz.getField(fieldName);
            return field != null;
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            return false;
        } catch (LinkageError e) {
            GTCEuCompatLogger.debug("fieldExists({}#{}) hit a linkage error: {}", className, fieldName, e.toString());
            return false;
        }
    }
}
