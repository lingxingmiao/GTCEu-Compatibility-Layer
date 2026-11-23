package com.gtceu.compat.core.asm;

import gregtech.api.unification.material.properties.BlastProperty.GasTier;
import gregtech.loaders.recipe.CraftingComponent;
import net.minecraftforge.fluids.FluidStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2.8-compatible view of {@code CraftingComponent.EBF_GASES}.
 *
 * <p>GTCEu 2.8: {@code Map<GasTier, FluidStack>}.
 * GTQT 2.9: {@code Map<GasTier, List<FluidStack>>}.
 *
 * <p>ZBGT's {@code MaterialRecipes.blastHelper} does
 * {@code (FluidStack) EBF_GASES.get(tier)}, so on 2.9 the checkcast receives a
 * List and throws ClassCastException, aborting its alloy-blast recipe pass.
 * {@link EbfGasesBridgeTransformer} redirects that GETSTATIC here, where the
 * first fluid of each tier is exposed as a plain FluidStack.
 */
public final class EbfGasesCompat {

    private EbfGasesCompat() {}

    /** Cached 2.8-shaped view; populated lazily so 2.9's map is fully initialised. */
    private static Map<GasTier, FluidStack> view;

    public static Map<GasTier, FluidStack> get() {
        if (view != null) return view;
        Map<GasTier, FluidStack> out = new LinkedHashMap<>();
        try {
            Map<GasTier, List<FluidStack>> source = CraftingComponent.EBF_GASES;
            if (source != null) {
                for (Map.Entry<GasTier, List<FluidStack>> e : source.entrySet()) {
                    List<FluidStack> list = e.getValue();
                    if (list != null && !list.isEmpty() && list.get(0) != null) {
                        out.put(e.getKey(), list.get(0));
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] EbfGasesCompat.get failed: " + t);
        }
        view = out;
        return out;
    }
}
