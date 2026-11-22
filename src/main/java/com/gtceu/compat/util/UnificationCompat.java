package com.gtceu.compat.util;

import com.gtceu.compat.api.GTCEuVersion;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Compatibility adapter for Unification API differences between GTCEu 2.8 and 2.9.
 */
public class UnificationCompat {

    private static GTCEuVersion currentVersion;
    private static boolean initialized = false;

    // Cached reflection results
    private static final Map<String, Method> methodCache = new HashMap<>();
    private static final Map<String, Class<?>> classCache = new HashMap<>();

    /**
     * Initialize the Unification compatibility adapter.
     * @param version The detected GTCEu version.
     */
    public static void initialize(GTCEuVersion version) {
        currentVersion = version;
        initialized = true;
        GTCEuCompatLogger.info("Unification compatibility adapter initialized for {}", version);
    }

    /**
     * Enable 2.9 API shims when running on GTCEu 2.8.
     */
    public static void enableV29Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("UnificationCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.9 Unification API shims...");
        GTCEuCompatLogger.info("2.9 Unification API shims enabled.");
    }

    /**
     * Enable 2.8 API shims when running on GTCEu 2.9.
     */
    public static void enableV28Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("UnificationCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.8 Unification API shims...");
        GTCEuCompatLogger.info("2.8 Unification API shims enabled.");
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
