package com.gtceu.compat.core.asm;

import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of the GTCEu 2.8.10 API signatures that GTQT 2.9 changed or removed.
 *
 * <p>Only signatures verified with {@code javap -p -s} against the real jars are
 * listed here:
 * <ul>
 *   <li>2.8.10: {@code GTCEu-Compatibility-Layer/build/libs/gregtech-1.12.2-2.8.10-beta.jar}</li>
 *   <li>GTQT 2.9: {@code GregTech-master-gtqt/build/libs/gregtech-gtqt-1.12.2-2.9.0.jar}</li>
 *   <li>GTQT 2.9-190: {@code PrismPlan-master/libs/gregtech-gtqt-1.12.2-2.9.0-190.jar}</li>
 * </ul>
 *
 * <p>Each bridge is injected only when the delegate exists on the class currently
 * being loaded, so the same transformer can be installed on both hosts.
 */
public final class BridgeSets implements Opcodes {

    private static final String ELEMENT = "gregtech/api/unification/Element";
    private static final String TOOL = "gregtech/api/unification/material/properties/ToolProperty";
    private static final String FLUID = "gregtech/api/unification/material/properties/FluidProperty";
    private static final String MATERIAL_BUILDER = "gregtech/api/unification/material/Material$Builder";
    private static final String RECIPE_BUILDER = "gregtech/api/recipes/RecipeBuilder";
    private static final String RECIPE = "gregtech/api/recipes/Recipe";
    private static final String RECIPE_MAP = "gregtech/api/recipes/RecipeMap";
    private static final String LOGIC = "gregtech/api/capability/impl/AbstractRecipeLogic";
    private static final String OC_LOGIC = "gregtech/api/recipes/logic/OverclockingLogic";
    private static final String MTE = "gregtech/api/metatileentity/MetaTileEntity";
    private static final String GT_API = "gregtech/api/GregTechAPI";
    private static final String ORE_PREFIX = "gregtech/api/unification/ore/OrePrefix";

    private BridgeSets() {}

    /**
     * Bridges that are safe, behaviour-preserving and worth injecting on a 2.9 host.
     *
     * <p>Deliberately NOT bridged here:
     * <ul>
     *   <li>{@code ToolProperty.setX(...)V} void setters: 2.8 addons normally used
     *       {@code ToolProperty$Builder}, which is rewritten by
     *       {@link ToolPropertyRemapTransformer}. Injecting {@code void} setters with
     *       the same name+descriptor as 2.9's fluent setters is impossible without
     *       changing their return type, so those are handled by rewriting addon
     *       references, not by touching GT core.</li>
     *   <li>{@code AbstractRecipeLogic.runOverclockingLogic(...)[I},
     *       {@code calculateOverclock(Recipe)[I}, {@code performOverclocking(Recipe)[I},
     *       {@code modifyOverclockPre/Post([I...)}: 2.9 replaced the {@code int[]}
     *       overclock result with {@code OCParams}/{@code OCResult} and migrated the
     *       recipe property storage package. A bridge would need to allocate and
     *       marshal those objects into an {@code int[]}; doing so silently would
     *       change overclock semantics. Those addons must be recompiled.</li>
     *   <li>{@code Recipe} old constructor / {@code getProperty*} family: 2.9 replaced
     *       the constructor and recipe-property storage class with
     *       {@code RecipePropertyStorage}; see README for the migration note.</li>
     *   <li>{@code RecipeMap} old constructors: 2.9 now requires a
     *       {@code RecipeMapUIFunction} parameter. A bridge would need to synthesise
     *       one; addons registering custom recipe maps should be recompiled.</li>
     *   <li>{@code AbstractRecipeLogic.hasEnoughPower([I)Z}: 2.9 changed the shape to
     *       {@code hasEnoughPower(JI)Z}; the argument count differs, so no bridge is possible.</li>
     * </ul>
     */
    public static List<BridgeSpec> all() {
        List<BridgeSpec> b = new ArrayList<>();

        // ---- Element / Elements -------------------------------------------------
        // 2.8 Element.halfLifeSeconds is a public long FIELD; 2.9 exposes
        // double getHalfLiveSeconds(). Field access cannot be delegated to a method,
        // so it is deliberately NOT bridged (addons reading the field must recompile).
        // The five existing dedicated transformers cover Elements.add(JJJ...),
        // Element(JJJ...) ctor, FluidProperty.getStorage(), ToolProperty remap and
        // GCYM molten dedup; keep them as-is rather than duplicating them here.

        // ---- FluidProperty ------------------------------------------------------
        // 2.8: void setSolidifyingFluid(Fluid); 2.9: FluidProperty setSolidifyingFluid(Fluid).
        // Bridge injects the void descriptor and discards the fluent return value.
        b.add(BridgeSpec.voidVia(FLUID, "setSolidifyingFluid",
                "(Lnet/minecraftforge/fluids/Fluid;)V",
                "(Lnet/minecraftforge/fluids/Fluid;)Lgregtech/api/unification/material/properties/FluidProperty;"));

        // ---- ToolProperty / Material$Builder ------------------------------------
        // 2.8: Material$Builder.toolStats(ToolProperty). 2.9 takes MaterialToolProperty.
        // The bridge needs a CHECKCAST on argument 0, so it is implemented by the
        // dedicated ToolPropertyBridgeTransformer instead of the generic injector.

        // ---- RecipeBuilder ------------------------------------------------------
        // 2.8 getEUt()I; 2.9 has getEUt()J. Append L2I to the delegate result.
        b.add(BridgeSpec.virtualReturn(RECIPE_BUILDER, "getEUt", "()I", "()J", L2I));
        // 2.8 field EUt:I; 2.9 field EUt:J. Field bridges are handled by
        // FieldCompatTransformer, not by method bridges.
        // EUt(int) still exists in 2.9 (verified), so no bridge needed.

        // ---- Recipe ----------------------------------------------------------
        b.add(BridgeSpec.virtualReturn(RECIPE, "getEUt", "()I", "()J", L2I));

        // ---- AbstractRecipeLogic: safe int/long widening bridges --------------
        b.add(BridgeSpec.virtualReturn(LOGIC, "getRecipeEUt", "()I", "()J", L2I));
        b.add(BridgeSpec.virtualReturn(LOGIC, "getInfoProviderEUt", "()I", "()J", L2I));
        // 2.8 protected drawEnergy(IZ)Z -> 2.9 protected drawEnergy(JZ)Z (int -> long widening).
        // Access must stay protected: 2.8 addon subclasses override this method, and injecting
        // a public method of the same signature would be an illegal access weakening (ICCE).
        b.add(BridgeSpec.virtualProtected(LOGIC, "drawEnergy", "(IZ)Z", "(JZ)Z", new int[]{I2L, 0}));
        // 2.8 protected getNumberOfOCs(I)I -> 2.9 protected getNumberOfOCs(J)I.
        b.add(BridgeSpec.virtualProtected(LOGIC, "getNumberOfOCs", "(I)I", "(J)I", new int[]{I2L}));
        // 2.8 divisor (2.0 / 4.0) and 2.9 factor (0.5 / 0.25) are RECIPROCALS:
        // 2.8 divides duration by the divisor, 2.9 multiplies duration by the factor.
        // Returning the factor unchanged would double/quadruple recipe duration. Invert it.
        b.add(BridgeSpec.protectedReciprocal(LOGIC, "getOverclockingDurationDivisor", "()D",
                "getOverclockingDurationFactor", "()D"));
        // 2.8 protected getOverclockingVoltageMultiplier()D -> 2.9 getOverclockingVoltageFactor()D.
        // Both return the same 4.0 multiplier constant (verified by javap -c), no inversion.
        b.add(BridgeSpec.of(LOGIC, "getOverclockingVoltageMultiplier", "()D",
                ACC_PROTECTED, INVOKEVIRTUAL, LOGIC,
                "getOverclockingVoltageFactor", "()D", null, 0, null, false));

        // ---- OverclockingLogic: static bridge -------------------------------
        // 2.8: applyCoilEUtDiscount(III)I; 2.9: applyCoilEUtDiscount(JII)J.
        // Widening the EUt argument and narrowing the result is safe for GT
        // voltage values (< 2^31 EU/t in every 2.8 recipe).
        b.add(new BridgeSpec(OC_LOGIC, "applyCoilEUtDiscount", "(III)I",
                ACC_PUBLIC | ACC_STATIC, INVOKESTATIC, OC_LOGIC,
                "applyCoilEUtDiscount", "(JII)J",
                new int[]{I2L, 0, 0}, L2I, null, false));
        // standardOverclockingLogic / heatingCoilOverclockingLogic were replaced
        // by OCParams/OCResult-based standardOC / heatingCoilOC. Building a faithful
        // int[] bridge requires OCParams/OCResult field layout knowledge and would
        // silently change overclock semantics, so addons calling those directly
        // must be recompiled against 2.9. Documented in README.

        // ---- MetaTileEntity ----------------------------------------------------
        // 2.9 removed both getComparatorValue()I and getActualComparatorValue()I.
        // MetaTileEntityBridgeTransformer restores them (and a few AE2 helpers) with
        // constant-0 / null fallbacks. Complex removed members are deliberately not faked.

        // ---- GregTechAPI static fields ------------------------------------------
        // MTE_REGISTRY / MACHINE / TAB_* are added by FieldCompatTransformer as
        // public static aliases pointing at the 2.9 equivalents where possible.

        // ---- OrePrefix ----------------------------------------------------------
        // 2.8 OrePrefix.oreSmall exists; 2.9 removed it. Aliased by
        // FieldCompatTransformer to OrePrefix.ore (nearest equivalent).

        return Collections.unmodifiableList(b);
    }

    /** Bridges grouped by target class internal name, preserving insertion order. */
    public static Map<String, List<BridgeSpec>> byTarget() {
        Map<String, List<BridgeSpec>> map = new LinkedHashMap<>();
        for (BridgeSpec s : all()) {
            List<BridgeSpec> list = map.get(s.target);
            if (list == null) {
                list = new ArrayList<>();
                map.put(s.target, list);
            }
            list.add(s);
        }
        return map;
    }
}
