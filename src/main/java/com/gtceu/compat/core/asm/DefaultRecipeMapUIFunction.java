package com.gtceu.compat.core.asm;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Runtime bridge used by {@link RecipeMapCtorBridgeTransformer}.
 *
 * <p>The compatibility layer is compiled WITHOUT a GTCEu dependency, so this
 * class cannot implement {@code RecipeMapUIFunction} directly. The transformer
 * emits an {@code INVOKESTATIC} call to {@link #apply(Object)} through a tiny
 * generated adapter instead (see below); keeping the logic here avoids any
 * compile-time reference to GTQT classes.
 */
public final class DefaultRecipeMapUIFunction {

    private DefaultRecipeMapUIFunction() {}

    /**
     * Creates the default {@code RecipeMapUI} for a GTQT 2.9 RecipeMap.
     * Mirrors {@code new RecipeMapUI<>(recipeMap, true, true, true, true)}.
     */
    public static Object apply(Object recipeMap) {
        try {
            Class<?> ui = Class.forName("gregtech.api.recipes.ui.RecipeMapUI");
            Constructor<?> ctor = ui.getConstructor(
                    Class.forName("gregtech.api.recipes.RecipeMap"),
                    boolean.class, boolean.class, boolean.class, boolean.class);
            return ctor.newInstance(recipeMap, true, true, true, true);
        } catch (Throwable t) {
            throw new IllegalStateException("[GTCEuCompat] Cannot build default RecipeMapUI", t);
        }
    }

    /**
     * Invokedynamic / method-handle friendly entry point: returns the
     * {@code RecipeMapUIFunction} instance as a raw Object, again without any
     * compile-time GTCEu reference.
     */
    public static Object asFunction() {
        try {
            Class<?> fn = Class.forName("gregtech.api.recipes.ui.RecipeMapUIFunction");
            final Method apply = DefaultRecipeMapUIFunction.class.getMethod("apply", Object.class);
            return java.lang.reflect.Proxy.newProxyInstance(
                    DefaultRecipeMapUIFunction.class.getClassLoader(),
                    new Class<?>[]{fn},
                    (proxy, method, args) -> {
                        if ("apply".equals(method.getName())) {
                            return apply.invoke(null, args[0]);
                        }
                        if ("toString".equals(method.getName())) {
                            return "DefaultRecipeMapUIFunction";
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())) {
                            return proxy == args[0];
                        }
                        return null;
                    });
        } catch (Throwable t) {
            throw new IllegalStateException("[GTCEuCompat] Cannot create RecipeMapUIFunction proxy", t);
        }
    }
}
