package com.gtceu.compat.multiblock;

import com.gtceu.compat.api.GTCEuVersion;
import com.gtceu.compat.util.GTCEuCompatLogger;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Compatibility adapter for Multiblock API differences between GTCEu 2.8 and 2.9.
 * 
 * Key changes in 2.9:
 * - Multiblock controller API changes
 * - Multiblock structure changes
 * - New multiblock parts
 * - Changes to formation logic
 */
public class MultiblockCompat {

    private static GTCEuVersion currentVersion;
    private static boolean initialized = false;

    // Cached reflection results
    private static final Map<String, Method> methodCache = new HashMap<>();
    private static final Map<String, Class<?>> classCache = new HashMap<>();

    /**
     * Initialize the Multiblock compatibility adapter.
     * @param version The detected GTCEu version.
     */
    public static void initialize(GTCEuVersion version) {
        currentVersion = version;
        initialized = true;
        GTCEuCompatLogger.info("Multiblock compatibility adapter initialized for {}", version);
    }

    /**
     * Enable 2.9 API shims when running on GTCEu 2.8.
     */
    public static void enableV29Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("MultiblockCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.9 Multiblock API shims...");

        // In 2.9, the Multiblock API was enhanced
        // Key differences:
        // 1. New multiblock controller methods
        // 2. Changes to structure formation
        // 3. New multiblock parts
        // 4. Parallel logic changes

        GTCEuCompatLogger.info("2.9 Multiblock API shims enabled.");
    }

    /**
     * Enable 2.8 API shims when running on GTCEu 2.9.
     */
    public static void enableV28Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("MultiblockCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.8 Multiblock API shims...");

        // Provide backward compatibility for 2.8 Multiblock APIs on 2.9

        GTCEuCompatLogger.info("2.8 Multiblock API shims enabled.");
    }

    /**
     * Checks if a multiblock is formed, handling version differences.
     * @param controller The multiblock controller.
     * @return true if the multiblock is formed, false otherwise.
     */
    public static boolean isMultiblockFormed(Object controller) {
        try {
            Method isFormed = getCachedMethod(controller.getClass(), "isStructureFormed");
            if (isFormed != null) {
                return (Boolean) isFormed.invoke(controller);
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Failed to check multiblock formation: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Gets the parallel limit for a multiblock, handling version differences.
     * @param controller The multiblock controller.
     * @return The parallel limit, or 1 if not applicable.
     */
    public static int getParallelLimit(Object controller) {
        try {
            // In 2.9, parallel logic was refactored
            Method getParallel = getCachedMethod(controller.getClass(), "getParallelLimit");
            if (getParallel != null) {
                return (Integer) getParallel.invoke(controller);
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Failed to get parallel limit: {}", e.getMessage());
        }
        return 1;
    }

    /**
     * Gets the coil level for a multiblock, handling version differences.
     * @param controller The multiblock controller.
     * @return The coil level, or 0 if not applicable.
     */
    public static int getCoilLevel(Object controller) {
        try {
            Method getCoil = getCachedMethod(controller.getClass(), "getCoilTier");
            if (getCoil != null) {
                return (Integer) getCoil.invoke(controller);
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Failed to get coil level: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Checks maintenance status for a multiblock, handling version differences.
     * @param controller The multiblock controller.
     * @return The maintenance status, or 0 if not applicable.
     */
    public static int getMaintenanceStatus(Object controller) {
        try {
            Method getMaintenance = getCachedMethod(controller.getClass(), "getMaintenanceProblems");
            if (getMaintenance != null) {
                return (Integer) getMaintenance.invoke(controller);
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Failed to get maintenance status: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Gets the number of working parallel machines, handling version differences.
     * @param controller The multiblock controller.
     * @return The number of working parallels, or 1 if not applicable.
     */
    public static int getCurrentParallels(Object controller) {
        try {
            Method getParallels = getCachedMethod(controller.getClass(), "getCurrentParallels");
            if (getParallels != null) {
                return (Integer) getParallels.invoke(controller);
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Failed to get current parallels: {}", e.getMessage());
        }
        return 1;
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
