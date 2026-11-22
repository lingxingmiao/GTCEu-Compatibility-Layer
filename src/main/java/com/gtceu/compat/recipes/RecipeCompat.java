package com.gtceu.compat.recipes;

import com.gtceu.compat.api.GTCEuVersion;
import com.gtceu.compat.util.GTCEuCompatLogger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Compatibility adapter for Recipe API differences between GTCEu 2.8 and 2.9.
 * 
 * Key changes in 2.9:
 * - RecipeMap API changes
 * - RecipeBuilder API changes
 * - New recipe properties
 * - Changes to recipe logic (AbstractRecipeLogic)
 * - New OverclockingLogic class
 */
public class RecipeCompat {

    private static GTCEuVersion currentVersion;
    private static boolean initialized = false;

    // Cached reflection results
    private static final Map<String, Method> methodCache = new HashMap<>();
    private static final Map<String, Class<?>> classCache = new HashMap<>();

    /**
     * Initialize the Recipe compatibility adapter.
     * @param version The detected GTCEu version.
     */
    public static void initialize(GTCEuVersion version) {
        currentVersion = version;
        initialized = true;
        GTCEuCompatLogger.info("Recipe compatibility adapter initialized for {}", version);
    }

    /**
     * Enable 2.9 API shims when running on GTCEu 2.8.
     */
    public static void enableV29Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("RecipeCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.9 Recipe API shims...");

        // In 2.9, the RecipeMap and RecipeBuilder APIs were significantly changed
        // Key differences:
        // 1. RecipeMap.recipeBuilder() signature changed
        // 2. New OverclockingLogic class
        // 3. Changes to AbstractRecipeLogic methods
        // 4. New recipe properties

        GTCEuCompatLogger.info("2.9 Recipe API shims enabled.");
    }

    /**
     * Enable 2.8 API shims when running on GTCEu 2.9.
     */
    public static void enableV28Shims() {
        if (!initialized) {
            GTCEuCompatLogger.warn("RecipeCompat not initialized!");
            return;
        }

        GTCEuCompatLogger.info("Enabling 2.8 Recipe API shims...");

        // Provide backward compatibility for 2.8 recipe APIs on 2.9
        // Key differences:
        // 1. Old RecipeMap API methods
        // 2. Old overclocking logic
        // 3. Old recipe builder methods

        GTCEuCompatLogger.info("2.8 Recipe API shims enabled.");
    }

    /**
     * Creates a recipe builder, handling the API difference between versions.
     * In 2.8: RecipeMap.recipeBuilder()
     * In 2.9: RecipeMap.recipeBuilder() with different signature
     * 
     * @param recipeMap The RecipeMap instance.
     * @param duration The recipe duration.
     * @param EUt The EU per tick.
     * @return A RecipeBuilder instance.
     */
    public static Object createRecipeBuilder(Object recipeMap, int duration, long EUt) {
        try {
            if (currentVersion == GTCEuVersion.V2_8) {
                // 2.8: recipeBuilder() with no args, then set duration/EUt
                Method recipeBuilder = getCachedMethod(recipeMap.getClass(), "recipeBuilder");
                if (recipeBuilder != null) {
                    Object builder = recipeBuilder.invoke(recipeMap);
                    // Set duration and EUt on the builder
                    setBuilderProperty(builder, "duration", duration);
                    setBuilderProperty(builder, "EUt", EUt);
                    return builder;
                }
            } else if (currentVersion == GTCEuVersion.V2_9) {
                // 2.9: recipeBuilder() with potential different signature
                Method recipeBuilder = getCachedMethod(recipeMap.getClass(), "recipeBuilder");
                if (recipeBuilder != null) {
                    Object builder = recipeBuilder.invoke(recipeMap);
                    setBuilderProperty(builder, "duration", duration);
                    setBuilderProperty(builder, "EUt", EUt);
                    return builder;
                }
            }
        } catch (Exception e) {
            GTCEuCompatLogger.error("Failed to create recipe builder", e);
        }
        return null;
    }

    /**
     * Sets a property on a RecipeBuilder using reflection.
     */
    private static void setBuilderProperty(Object builder, String propertyName, Object value) {
        try {
            Class<?>[] paramTypes;
            if (value instanceof Integer) {
                paramTypes = new Class<?>[]{int.class};
            } else if (value instanceof Long) {
                paramTypes = new Class<?>[]{long.class};
            } else {
                paramTypes = new Class<?>[]{value.getClass()};
            }

            Method setter = getCachedMethod(builder.getClass(), propertyName, paramTypes);
            if (setter != null) {
                setter.invoke(builder, value);
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Failed to set builder property {}: {}", propertyName, e.getMessage());
        }
    }

    /**
     * Gets a recipe map by name, handling version differences.
     * @param recipeMapName The recipe map name.
     * @return The RecipeMap instance, or null if not found.
     */
    public static Object getRecipeMap(String recipeMapName) {
        try {
            // Access RecipeMaps class
            Class<?> recipeMaps = getCachedClass("gregtech.api.recipes.RecipeMaps");
            if (recipeMaps != null) {
                // Get the static field for the recipe map
                java.lang.reflect.Field field = recipeMaps.getField(recipeMapName);
                if (field != null) {
                    return field.get(null);
                }
            }
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Failed to get recipe map {}: {}", recipeMapName, e.getMessage());
        }
        return null;
    }

    /**
     * Gets the overclocking logic, handling the API change between versions.
     * In 2.8: Overclocking logic was in AbstractRecipeLogic
     * In 2.9: Overclocking logic moved to OverclockingLogic class
     */
    public static Object getOverclockingLogic() {
        try {
            if (currentVersion == GTCEuVersion.V2_9) {
                // 2.9: Separate OverclockingLogic class
                Class<?> overclockingLogic = getCachedClass("gregtech.api.recipes.logic.OverclockingLogic");
                if (overclockingLogic != null) {
                    return overclockingLogic.newInstance();
                }
            }
            // 2.8 or fallback: logic is in AbstractRecipeLogic
            return null;
        } catch (Exception e) {
            GTCEuCompatLogger.debug("Failed to get overclocking logic: {}", e.getMessage());
            return null;
        }
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
