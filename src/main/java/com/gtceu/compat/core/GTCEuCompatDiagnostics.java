package com.gtceu.compat.core;

import com.gtceu.compat.api.GTCEuVersion;
import com.gtceu.compat.util.GTCEuCompatLogger;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Startup diagnostics: tells the pack author WHICH 2.8-compiled addon still
 * references a GTQT 2.9 class that was removed (not renamed), so the failure
 * surfaces as an explicit report instead of Cleanroom's opaque
 * {@code "Found <class> in invalid classes"}.
 *
 * <p>Run once from {@link GTCEuCompatMod#onPostInit}. It never throws: a broken jar,
 * a missing source file, or a classpath problem is logged and skipped. It only
 * reads bytes; it does not transform classes or decide compatibility.
 *
 * <p>The point of this class is honesty: the compatibility layer restores the
 * 2.8 <em>linkage</em> it CAN restore (see the ASM transformers), but whole
 * subsystems that GTQT 2.9 deleted — the {@code pattern.*} structure framework
 * and the AE2 multiblock-part host — cannot be faked with bytecode bridges.
 * Those addons need their own 2.9 build, and this report names them.
 */
public final class GTCEuCompatDiagnostics {

    /**
     * Internal-name prefixes of GTCEu 2.8 subsystems that GTQT 2.9 DELETED
     * (not renamed). A reference to any of these from an addon jar means that
     * addon needs a 2.9 build; no ASM bridge can restore it.
     */
    private static final String[] REMOVED_PREFIXES = {
            // Entire 2.8 multiblock structure framework -> StructureRuntime / PieceTemplate
            "gregtech/api/pattern/BlockPattern",
            "gregtech/api/pattern/FactoryBlockPattern",
            "gregtech/api/pattern/PatternMatchContext",
            "gregtech/api/pattern/TraceabilityPredicate",
            "gregtech/api/pattern/MultiblockShapeInfo",
            // AE2 multiblock-part host hierarchy (used by Gregified Energistics)
            "gregtech/common/metatileentities/multi/multiblockpart/appeng/",
            // 2.8-only recipe property package (compat layer remaps this, so a hit
            // here means the addon was NOT covered by AddonPackageRemapTransformer)
            "gregtech/api/recipes/recipeproperties/",
            // Legacy ASM helper package used by 2.8 addon coremods
            "gregtech/asm/util/"
    };

    /** Matches descriptor/signature class references: {@code Lpkg/Name;}. */
    private static final Pattern CLASS_REF = Pattern.compile("L([A-Za-z0-9_/$]+);");
    /**
     * Some references (superclass / interfaces / generic signatures) appear as
     * BARE internal names with no {@code L...;} wrapper, e.g.
     * {@code gregtech/common/metatileentities/multi/multiblockpart/appeng/MetaTileEntityAEHostablePart<TT;>;}.
     * Match those too so an addon whose PARENT was removed is still reported.
     */
    private static final Pattern BARE_REF = Pattern.compile("((?:gregtech|appeng)/[A-Za-z0-9_/$]+)");

    private GTCEuCompatDiagnostics() {}

    /**
     * Early entry point used by {@code GTCEuCompatLoadingPlugin.injectData}, BEFORE
     * any mod's preInit runs. Scans the {@code mods/} directory directly (no
     * dependency on {@code Loader} / {@code ModContainer}, which are not populated
     * yet at coremod time). This matters: an addon like Gregified Energistics crashes
     * in preInit, so a postInit diagnostic would never be reached.
     *
     * @param gameDir the instance/game directory (its {@code mods/} child is scanned)
     */
    public static void runEarly(File gameDir) {
        Map<String, Set<String>> hits = new LinkedHashMap<>();
        int scanned = 0;
        int failed = 0;
        File modsDir = new File(gameDir, "mods");
        File[] jars = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                String name = jar.getName();
                if (name.startsWith("gregtech-gtqt") || name.startsWith("gtceu_compat")) {
                    continue; // host core + this layer
                }
                scanned++;
                try {
                    Set<String> found = scanJar(jar);
                    if (!found.isEmpty()) {
                        hits.put(name, found);
                    }
                } catch (Throwable t) {
                    failed++;
                    GTCEuCompatLogger.warn("[GTCEuCompat] Early scan failed for {}: {}", name, t.toString());
                }
            }
        }
        report(GTCEuVersion.UNKNOWN, gameDir, scanned, failed, hits);
    }

    /**
     * Scan every active mod's source jar and report references to removed 2.8 API.
     *
     * @param version the detected GTCEu version (diagnostics run even for UNKNOWN)
     * @param gameDir the instance directory (report file goes here)
     */
    public static void run(GTCEuVersion version, File gameDir) {
        Map<String, Set<String>> hits = new LinkedHashMap<>();
        int scanned = 0;
        int failed = 0;

        try {
            List<ModContainer> mods = Loader.instance().getActiveModList();
            for (ModContainer mc : mods) {
                String id = safeModId(mc);
                File src = safeSource(mc);
                if (src == null || !src.isFile() || !src.getName().endsWith(".jar")) {
                    continue;
                }
                // Never flag GTCEu itself or this compatibility layer.
                if ("gregtech".equals(id) || "gtceu_compat".equals(id)) {
                    continue;
                }
                scanned++;
                try {
                    Set<String> found = scanJar(src);
                    if (!found.isEmpty()) {
                        hits.put(id + "  (" + src.getName() + ")", found);
                    }
                } catch (Throwable t) {
                    failed++;
                    GTCEuCompatLogger.warn("[GTCEuCompat] Diagnostic scan failed for {}: {}", id, t.toString());
                }
            }
        } catch (Throwable t) {
            GTCEuCompatLogger.warn("[GTCEuCompat] Could not enumerate active mods for diagnostics: {}", t.toString());
        }

        report(version, gameDir, scanned, failed, hits);
    }

    /** Read a jar's class bytes and collect references to removed 2.8 prefixes. */
    private static Set<String> scanJar(File jar) throws IOException {
        Set<String> found = new TreeSet<>();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            byte[] buf = new byte[16384];
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
                byte[] bytes;
                try (InputStream is = zip.getInputStream(e)) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    int n;
                    while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                    bytes = bos.toByteArray();
                }
                // Constant-pool strings are length-prefixed UTF-8; scanning the raw
                // bytes for descriptor strings is enough for diagnostics (no need to
                // parse the whole class file structure).
                String text = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                Matcher m = CLASS_REF.matcher(text);
                while (m.find()) {
                    collectIfRemoved(m.group(1), found);
                }
                // Bare internal names (superclass / interface / signature refs).
                Matcher bm = BARE_REF.matcher(text);
                while (bm.find()) {
                    collectIfRemoved(bm.group(1), found);
                }
            }
        }
        return found;
    }

    private static void collectIfRemoved(String ref, Set<String> found) {
        for (String prefix : REMOVED_PREFIXES) {
            if (ref.startsWith(prefix)) {
                found.add(ref);
                return;
            }
        }
    }

    private static void report(GTCEuVersion version, File gameDir,
                               int scanned, int failed, Map<String, Set<String>> hits) {
        GTCEuCompatLogger.info("[GTCEuCompat] === 2.8 addon compatibility diagnostics (host: {}) ===", version);

        if (hits.isEmpty()) {
            GTCEuCompatLogger.info("[GTCEuCompat] Scanned {} mod jar(s), {} unreadable. "
                    + "No addon references a GTQT 2.9-removed subsystem. All clear.", scanned, failed);
            return;
        }

        GTCEuCompatLogger.warn("[GTCEuCompat] {} addon(s) still reference GTCEu 2.8 APIs that GTQT 2.9 "
                + "DELETED. These CANNOT be fixed by the compatibility layer - install a 2.9 build "
                + "of these mods (or remove them).", hits.size());
        for (Map.Entry<String, Set<String>> e : hits.entrySet()) {
            GTCEuCompatLogger.warn("[GTCEuCompat]   {} -> {} removed class(es):", e.getKey(), e.getValue().size());
            int shown = 0;
            for (String cls : e.getValue()) {
                if (shown++ >= 12) {
                    GTCEuCompatLogger.warn("[GTCEuCompat]     ... and {} more", e.getValue().size() - 12);
                    break;
                }
                GTCEuCompatLogger.warn("[GTCEuCompat]     {}", cls.replace('/', '.'));
            }
        }

        // Also write a readable report next to the instance's logs.
        try {
            File out = new File(gameDir, "gtceu_compat_report.txt");
            try (PrintWriter pw = new PrintWriter(new FileWriter(out, false))) {
                pw.println("GTCEu Compatibility Layer - 2.8 addon diagnostics");
                pw.println("Host version : " + version);
                pw.println("Mod jars scanned : " + scanned + " (unreadable: " + failed + ")");
                pw.println();
                pw.println("These addons reference GTCEu 2.8 classes that GTQT 2.9 DELETED.");
                pw.println("ASM bridges cannot restore deleted subsystems; install 2.9 builds.");
                pw.println();
                for (Map.Entry<String, Set<String>> e : hits.entrySet()) {
                    pw.println("== " + e.getKey() + " ==");
                    for (String cls : e.getValue()) {
                        pw.println("   " + cls.replace('/', '.'));
                    }
                    pw.println();
                }
            }
            GTCEuCompatLogger.info("[GTCEuCompat] Full report written to {}", out.getAbsolutePath());
        } catch (Throwable t) {
            GTCEuCompatLogger.warn("[GTCEuCompat] Could not write report file: {}", t.toString());
        }
    }

    private static String safeModId(ModContainer mc) {
        try { return mc.getModId(); } catch (Throwable t) { return "<unknown>"; }
    }

    private static File safeSource(ModContainer mc) {
        try { return mc.getSource(); } catch (Throwable t) { return null; }
    }
}
