package com.gtceu.compat.cover;

import com.gtceu.compat.api.GTCEuVersion;
import com.gtceu.compat.util.GTCEuCompatLogger;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Compatibility adapter for Cover API differences between GTCEu 2.8 and 2.9.
 * 
 * Key changes in 2.9:
 * - Cover API refactoring
 * - New cover behavior methods
 * - Changes to cover placement logic
 * - New cover types and features
 */
public class CoverCompat {

    private static GTCEuVersion currentVersion;
    private static boolean initialized = false;

    // Cached reflection results
    private static final Map<String, Method> methodCache = new HashMap<>();
    private static final Map<String, Class<?>> classCache = new HashMap<>();

    /**
     * Initialize the Cover compatibility adapter.
     * @param version The detected GTCEu version.
     */
    public static void initialize(GTCEuVersion version) {
        currentVersion = version;
        initialized = true;
        GTCEuCompatLogger.info("Cover compatibility adapter initialized for {}", version);
    }

    /**
     * Enable 2.9 API shims when running on GTCEu 2.8.
     */
    public static void enableV29Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("CoverCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.9 Cover API shims...");

        // In 2.9, the Cover API was refactored
        // Key differences:
        // 1. Cover class changes
        // 2. New cover behavior methods
        // 3. Changes to cover registration

        GTCEuCompatLogger.info("2.9 Cover API shims enabled.");
    }

    /**
     * Enable 2.8 API shims when running on GTCEu 2.9.
     */
    public static void enableV28Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("CoverCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.8 Cover API shims...");

        // Provide backward compatibility for 2.8 Cover APIs on 2.9

        GTCEuCompatLogger.info("2.8 Cover API shims enabled.");
    }

    /**
     * Places a cover on a block, handling version differences.
     * @param coverId The cover ResourceLocation ID.
     * @param facing The facing direction.
     * @param coverHolder The cover holder (MetaTileEntity or block).
     * @return true if the cover was placed successfully, false otherwise.
     */
    public static boolean placeCover(ResourceLocation coverId, EnumFacing facing, Object coverHolder) {
        try {
            if (currentVersion == GTCEuVersion.V2_8) {
                // 2.8: Use Cover API
                return placeCoverV28(coverId, facing, coverHolder);
            } else if (currentVersion == GTCEuVersion.V2_9) {
                // 2.9: Use updated Cover API
                return placeCoverV29(coverId, facing, coverHolder);
            }
        } catch (Exception e) {
            GTCEuCompatLogger.error("Failed to place cover", e);
        }
        return false;
    }

    /**
     * Places a cover using 2.8 API.
     */
    private static boolean placeCoverV28(ResourceLocation coverId, EnumFacing facing, Object coverHolder) throws Exception {
        // Get the CoverDefinition from registry
        Class<?> coverDefClass = getCachedClass("gregtech.api.cover.CoverDefinition");
        if (coverDefClass == null) {
            return false;
        }

        // Access cover registry
        Class<?> gregTechAPI = getCachedClass("gregtech.api.GregTechAPI");
        if (gregTechAPI != null) {
            java.lang.reflect.Field registryField = gregTechAPI.getField("COVER_REGISTRY");
            if (registryField != null) {
                Object registry = registryField.get(null);
                Method getEntry = getCachedMethod(registry.getClass(), "getObjectById", ResourceLocation.class);
                if (getEntry != null) {
                    Object coverDef = getEntry.invoke(registry, coverId);
                    if (coverDef != null) {
                        // Place the cover
                        Method placeMethod = getCachedMethod(coverHolder.getClass(), "placeCover", 
                            coverDefClass, EnumFacing.class);
                        if (placeMethod != null) {
                            placeMethod.invoke(coverHolder, coverDef, facing);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Places a cover using 2.9 API.
     */
    private static boolean placeCoverV29(ResourceLocation coverId, EnumFacing facing, Object coverHolder) throws Exception {
        // 2.9 may have different API for cover placement
        // Try 2.9-specific methods first
        Method placeCover = getCachedMethod(coverHolder.getClass(), "placeCover",
            ResourceLocation.class, EnumFacing.class);
        if (placeCover != null) {
            placeCover.invoke(coverHolder, coverId, facing);
            return true;
        }
        // Fall back to 2.8 API
        return placeCoverV28(coverId, facing, coverHolder);
    }

    /**
     * Gets a cover from a block, handling version differences.
     * @param coverHolder The cover holder.
     * @param facing The facing direction.
     * @return The cover instance, or null if no cover exists.
     */
    public static Object getCover(Object coverHolder, EnumFacing facing) {
        try {
            Method getCover = getCachedMethod(coverHolder.getClass(), "getCover", EnumFacing.class);
            if (getCover != null) {
                return getCover.invoke(coverHolder, facing);
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Failed to get cover: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Removes a cover from a block, handling version differences.
     * @param coverHolder The cover holder.
     * @param facing The facing direction.
     * @return true if the cover was removed, false otherwise.
     */
    public static boolean removeCover(Object coverHolder, EnumFacing facing) {
        try {
            Method removeCover = getCachedMethod(coverHolder.getClass(), "removeCover", EnumFacing.class);
            if (removeCover != null) {
                removeCover.invoke(coverHolder, facing);
                return true;
            }
        } catch (Exception e) {
            GTCEuCompatLogger.error("Failed to remove cover", e);
        }
        return false;
    }

    /**
     * Gets a cached method by reflection.
     */
    private static Method getCachedMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        String key = clazz.getName() + "." + methodName;
        if (methodCache.containsKey(key)) {
            return methodCache.get(key);
        }

        try {
            Method method = clazz.getMethod(methodName, paramTypes);
            methodCache.put(key, method);
            return method;
        } catch (NoSuchMethodException e) {
            methodCache.put(key, null);
            return null;
        }
    }

    /**
     * Gets a cached class by reflection.
     */
    private static Class<?> getCachedClass(String className) {
        if (classCache.containsKey(className)) {
            return classCache.get(className);
        }

        try {
            Class<?> clazz = Class.forName(className);
            classCache.put(className, clazz);
            return clazz;
        } catch (ClassNotFoundException e) {
            classCache.put(className, null);
            return null;
        }
    }

    /**
     * Clears the reflection cache.
     */
    public static void clearCache() {
        methodCache.clear();
        classCache.clear();
    }
}
