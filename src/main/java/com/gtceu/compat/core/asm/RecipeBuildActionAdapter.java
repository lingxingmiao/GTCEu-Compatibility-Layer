package com.gtceu.compat.core.asm;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Adapts a GTCEu 2.8 {@code Consumer<RecipeBuilder>} to GTQT 2.9's
 * {@code RecipeBuildAction<R>} and registers it on a RecipeMap.
 *
 * <p>Used by {@link RecipeMapCtorBridgeTransformer}'s injected
 * {@code onRecipeBuild(Consumer)} bridge. Kept free of compile-time GTCEu
 * references because the compatibility layer has no GTCEu dependency.
 */
public final class RecipeBuildActionAdapter {

    private RecipeBuildActionAdapter() {}

    /**
     * Registers {@code consumer} as a named build action on {@code recipeMap}.
     * The generated ResourceLocation is unique per call so multiple 2.8 addons
     * can register their own consumers without colliding.
     */
    @SuppressWarnings("rawtypes")
    public static Object register(Object recipeMap, Consumer consumer) {
        // Defensive: 2.8 addons sometimes pass null (e.g. a recipe map created with
        // a null build action). Registering a no-op action keeps parity with 2.8
        // where a null consumer simply meant "nothing to do".
        if (consumer == null) {
            return recipeMap;
        }
        try {
            Class<?> fn = Class.forName("gregtech.api.recipes.RecipeBuildAction");
            Object action = Proxy.newProxyInstance(
                    RecipeBuildActionAdapter.class.getClassLoader(),
                    new Class<?>[]{fn},
                    (proxy, method, args) -> {
                        if ("accept".equals(method.getName())) {
                            consumer.accept(args[0]);
                            return null;
                        }
                        if ("toString".equals(method.getName())) return "RecipeBuildActionAdapter";
                        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                        if ("equals".equals(method.getName())) return proxy == args[0];
                        return null;
                    });
            Class<?> rl = Class.forName("net.minecraft.util.ResourceLocation");
            Constructor<?> ctor = rl.getConstructor(String.class, String.class);
            Object id = ctor.newInstance("gtceu_compat", "legacy_" + UUID.randomUUID().toString().replace("-", ""));
            recipeMap.getClass()
                    .getMethod("onRecipeBuild", rl, fn)
                    .invoke(recipeMap, id, action);
            return recipeMap;
        } catch (Throwable t) {
            throw new IllegalStateException("[GTCEuCompat] Cannot adapt legacy onRecipeBuild(Consumer)", t);
        }
    }
}
