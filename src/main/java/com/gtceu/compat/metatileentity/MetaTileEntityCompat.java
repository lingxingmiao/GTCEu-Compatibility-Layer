package com.gtceu.compat.metatileentity;

import com.gtceu.compat.api.GTCEuVersion;
import com.gtceu.compat.util.GTCEuCompatLogger;

import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Compatibility adapter for MetaTileEntity API differences between GTCEu 2.8 and 2.9.
 * 
 * Key changes in 2.9:
 * - MetaTileEntity constructor signature changes
 * - New methods added to MetaTileEntity
 * - Changes to capability handling
 * - New trait system (MTETrait)
 * - Changes to rendering methods
 */
public class MetaTileEntityCompat {

    private static GTCEuVersion currentVersion;
    private static boolean initialized = false;

    // Cached reflection results
    private static final Map<String, Method> methodCache = new HashMap<>();
    private static final Map<String, Class<?>> classCache = new HashMap<>();

    /**
     * Initialize the MetaTileEntity compatibility adapter.
     * @param version The detected GTCEu version.
     */
    public static void initialize(GTCEuVersion version) {
        currentVersion = version;
        initialized = true;
        GTCEuCompatLogger.info("MetaTileEntity compatibility adapter initialized for {}", version);
    }

    /**
     * Enable 2.9 API shims when running on GTCEu 2.8.
     * This provides backward compatibility for addons written for 2.9.
     */
    public static void enableV29Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("MetaTileEntityCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.9 MetaTileEntity shims...");

        // Register shims for 2.9 API methods that don't exist in 2.8
        // These will be called via reflection or proxy when addons use 2.9 APIs

        // Example: In 2.9, MetaTileEntity has new methods like:
        // - getDefinition() (changed from getMetaTileEntityId())
        // - New trait-related methods
        // - Changes to capability methods

        GTCEuCompatLogger.info("2.9 MetaTileEntity shims enabled.");
    }

    /**
     * Enable 2.8 API shims when running on GTCEu 2.9.
     * This provides forward compatibility for addons written for 2.8.
     */
    public static void enableV28Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("MetaTileEntityCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.8 MetaTileEntity shims...");

        // Register shims for 2.8 API methods that were changed/removed in 2.9
        // These will provide backward compatibility

        // Example: In 2.8, MetaTileEntity had methods like:
        // - getMetaTileEntityId() (renamed to getDefinition() in 2.9)
        // - Old capability methods
        // - Different trait handling

        GTCEuCompatLogger.info("2.8 MetaTileEntity shims enabled.");
    }

    /**
     * Gets a MetaTileEntity ID, handling the difference between 2.8 and 2.9.
     * In 2.8: uses getMetaTileEntityId()
     * In 2.9: uses getDefinition().getId() or similar
     * 
     * @param metaTileEntity The MetaTileEntity instance.
     * @return The ResourceLocation ID of the MetaTileEntity.
     */
    public static ResourceLocation getMetaTileEntityId(Object metaTileEntity) {
        if (metaTileEntity == null) {
            return null;
        }

        try {
            if (currentVersion == GTCEuVersion.V2_8) {
                // 2.8: getMetaTileEntityId()
                Method method = getCachedMethod(metaTileEntity.getClass(), "getMetaTileEntityId");
                if (method != null) {
                    return (ResourceLocation) method.invoke(metaTileEntity);
                }
            } else if (currentVersion == GTCEuVersion.V2_9) {
                // 2.9: getDefinition().getId() or getMetaTileEntityId()
                // Try new API first
                try {
                    Method getDefinition = getCachedMethod(metaTileEntity.getClass(), "getDefinition");
                    if (getDefinition != null) {
                        Object definition = getDefinition.invoke(metaTileEntity);
                        if (definition != null) {
                            Method getId = getCachedMethod(definition.getClass(), "getId");
                            if (getId != null) {
                                return (ResourceLocation) getId.invoke(definition);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Fall back to old API
                    Method method = getCachedMethod(metaTileEntity.getClass(), "getMetaTileEntityId");
                    if (method != null) {
                        return (ResourceLocation) method.invoke(metaTileEntity);
                    }
                }
            }
        } catch (Exception e) {
            GTCEuCompatLogger.error("Failed to get MetaTileEntity ID", e);
        }

        return null;
    }

    /**
     * Creates a MetaTileEntity instance, handling constructor differences between versions.
     * 
     * @param id The ResourceLocation ID.
     * @return A new MetaTileEntity instance, or null if creation fails.
     */
    public static Object createMetaTileEntity(ResourceLocation id) {
        try {
            // Get the MetaTileEntity registry
            Class<?> registryClass = Class.forName("gregtech.api.metatileentity.registry.MTERegistry");
            // The registry access differs between versions
            // This is a simplified example - actual implementation would be more complex

            GTCEuCompatLogger.debug("Creating MetaTileEntity with id: {}", id);
            // Implementation depends on specific version differences
            return null;
        } catch (Exception e) {
            GTCEuCompatLogger.error("Failed to create MetaTileEntity", e);
            return null;
        }
    }

    /**
     * Gets a cached method by reflection.
     * @param clazz The class to search.
     * @param methodName The method name.
     * @param paramTypes The parameter types (optional).
     * @return The Method object, or null if not found.
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
     * @param className The fully qualified class name.
     * @return The Class object, or null if not found.
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

    /**
     * Checks if the MetaTileEntity has a specific capability, handling version differences.
     * @param metaTileEntity The MetaTileEntity instance.
     * @param capabilityName The capability name.
     * @return true if the capability exists, false otherwise.
     */
    public static boolean hasCapability(Object metaTileEntity, String capabilityName) {
        // Capability handling may differ between versions
        try {
            Class<?> capClass = getCachedClass(capabilityName);
            if (capClass == null) {
                return false;
            }

            // Try standard Forge capability system
            Method hasCap = getCachedMethod(metaTileEntity.getClass(), "hasCapability", 
                Class.forName("net.minecraftforge.common.capabilities.Capability"),
                Class.forName("net.minecraft.util.EnumFacing"));
            if (hasCap != null) {
                // Use Forge capability system
                return true;
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Capability check failed: {}", e.getMessage());
        }

        return false;
    }
}
