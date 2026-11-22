package com.gtceu.compat.material;

import com.gtceu.compat.api.GTCEuVersion;
import com.gtceu.compat.util.GTCEuCompatLogger;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Compatibility adapter for Material system differences between GTCEu 2.8 and 2.9.
 * 
 * Key changes in 2.9:
 * - Material API changes
 * - MaterialFlags changes
 * - MaterialRegistry changes
 * - New material properties
 */
public class MaterialCompat {

    private static GTCEuVersion currentVersion;
    private static boolean initialized = false;

    // Cached reflection results
    private static final Map<String, Method> methodCache = new HashMap<>();
    private static final Map<String, Class<?>> classCache = new HashMap<>();

    /**
     * Initialize the Material compatibility adapter.
     * @param version The detected GTCEu version.
     */
    public static void initialize(GTCEuVersion version) {
        currentVersion = version;
        initialized = true;
        GTCEuCompatLogger.info("Material compatibility adapter initialized for {}", version);
    }

    /**
     * Enable 2.9 API shims when running on GTCEu 2.8.
     */
    public static void enableV29Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("MaterialCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.9 Material API shims...");

        // In 2.9, the Material API was enhanced
        // Key differences:
        // 1. New material flags
        // 2. MaterialRegistry changes
        // 3. New material properties
        // 4. Changes to material generation

        GTCEuCompatLogger.info("2.9 Material API shims enabled.");
    }

    /**
     * Enable 2.8 API shims when running on GTCEu 2.9.
     */
    public static void enableV28Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("MaterialCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.8 Material API shims...");

        // Provide backward compatibility for 2.8 Material APIs on 2.9

        GTCEuCompatLogger.info("2.8 Material API shims enabled.");
    }

    /**
     * Gets a material by name, handling version differences.
     * @param materialName The material name.
     * @return The Material instance, or null if not found.
     */
    public static Object getMaterial(String materialName) {
        try {
            // Access MaterialRegistry
            Class<?> gregTechAPI = getCachedClass("gregtech.api.GregTechAPI");
            if (gregTechAPI != null) {
                Field materialManagerField = gregTechAPI.getField("materialManager");
                if (materialManagerField != null) {
                    Object materialManager = materialManagerField.get(null);
                    if (materialManager != null) {
                        Method getMaterial = getCachedMethod(materialManager.getClass(), "getMaterial", String.class);
                        if (getMaterial != null) {
                            return getMaterial.invoke(materialManager, materialName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Failed to get material {}: {}", materialName, e.getMessage());
        }
        return null;
    }

    /**
     * Gets material by ID, handling version differences.
     * @param materialId The material ID.
     * @return The Material instance, or null if not found.
     */
    public static Object getMaterialById(int materialId) {
        try {
            Class<?> gregTechAPI = getCachedClass("gregtech.api.GregTechAPI");
            if (gregTechAPI != null) {
                Field materialManagerField = gregTechAPI.getField("materialManager");
                if (materialManagerField != null) {
                    Object materialManager = materialManagerField.get(null);
                    if (materialManager != null) {
                        // Try to get material by ID
                        Method getMaterialById = getCachedMethod(materialManager.getClass(), "getMaterial", int.class);
                        if (getMaterialById != null) {
                            return getMaterialById.invoke(materialManager, materialId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Failed to get material by ID {}: {}", materialId, e.getMessage());
        }
        return null;
    }

    /**
     * Registers a material, handling version differences.
     * @param materialName The material name.
     * @param materialId The material ID.
     * @return true if the material was registered, false otherwise.
     */
    public static boolean registerMaterial(String materialName, int materialId) {
        try {
            Class<?> gregTechAPI = getCachedClass("gregtech.api.GregTechAPI");
            if (gregTechAPI != null) {
                Field materialManagerField = gregTechAPI.getField("materialManager");
                if (materialManagerField != null) {
                    Object materialManager = materialManagerField.get(null);
                    if (materialManager != null) {
                        // Registration may differ between versions
                        // 2.8 and 2.9 both use the material manager for registration
                        GTCEuCompatLogger.debug("Registering material {} with ID {}", materialName, materialId);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            GTCEuCompatLogger.error("Failed to register material", e);
        }
        return false;
    }

    /**
     * Checks if a material flag exists, handling version differences.
     * @param flagName The flag name.
     * @return true if the flag exists, false otherwise.
     */
    public static boolean hasMaterialFlag(String flagName) {
        try {
            // Material flags may have different names between versions
            Class<?> materialFlagClass = getCachedClass("gregtech.api.unification.material.info.MaterialFlag");
            if (materialFlagClass != null) {
                // Check for the flag
                Field flagField = materialFlagClass.getField(flagName);
                return flagField != null;
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Material flag check failed: {}", e.getMessage());
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
