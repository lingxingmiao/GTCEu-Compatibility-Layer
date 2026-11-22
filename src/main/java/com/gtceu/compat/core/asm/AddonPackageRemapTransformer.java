package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rewrites GTCEu 2.8-only class names in the constant pool of <em>addon</em> classes
 * to their GTQT 2.9 equivalents.
 *
 * <p>The legacy {@link ToolPropertyRemapTransformer} only covered two addon packages
 * and one class ({@code ToolProperty -> MaterialToolProperty}). This transformer
 * generalises that idea to every 2.8 addon present in the target pack and to the
 * recipe-property package rename that 2.9 performed:
 * <pre>
 *   gregtech/api/recipes/recipeproperties/RecipeProperty
 *       -> gregtech/api/recipes/properties/RecipeProperty
 *   gregtech/api/recipes/recipeproperties/IRecipePropertyStorage
 *       -> gregtech/api/recipes/properties/RecipePropertyStorage
 *   gregtech/api/unification/material/properties/ToolProperty$Builder
 *       -> gregtech/api/unification/material/properties/MaterialToolProperty$Builder
 *   gregtech/api/unification/material/properties/ToolProperty
 *       -> gregtech/api/unification/material/properties/MaterialToolProperty
 * </pre>
 *
 * <p>Only classes under the known addon package prefixes are touched. GT core
 * classes are never rewritten (they already use the 2.9 names), and the class's
 * own name/superclass/interfaces, method descriptors, signatures, and all
 * instructions are rewritten consistently so the resulting class still verifies.
 *
 * <p>Limitations (documented in README): this restores <em>linkage</em>, not
 * behaviour. 2.9's {@code RecipeProperty} gained abstract {@code serialize} /
 * {@code deserialize} methods, so an addon that <em>subclasses</em> it still needs
 * to be recompiled; addons that merely reference/register one are fine. Likewise
 * {@code RecipePropertyStorage} renamed its methods ({@code getSize -> size},
 * {@code getRecipeProperties -> entrySet}, ...); those callers are handled by
 * {@link RecipePropertyStorageBridgeTransformer}.
 */
public class AddonPackageRemapTransformer implements IClassTransformer {

    /** Every 2.8-compiled addon package found in the target Prism instance. */
    private static final String[] ADDON_PREFIXES = {
            "com/m_w_k/gtcefucontent/",
            "com/github/gtexpert/",
            "gregicality/multiblocks/",
            "kono/ceu/gtconsolidate/",
            "kono/ceu/materialreplication/",
            "rosatech/",
            "gregtechfoodoption/",
            "gtceugbh/",
            "hellfall/visualores/",
            "com/zorbatron/zbgt/",
            "keqing/gtqt/prismplan/",
            "com/github/gtexpert/inb/",
            "com/iristhepianist/scalablestorageceu/",
            "hellfall/visualores/"
    };

    /**
     * Ordered by longest/most-specific first. Every concrete 2.8 recipe property
     * class moved to {@code properties/impl/}; the base class and interfaces moved
     * to {@code properties/}. {@code GasCollectorDimensionProperty} was renamed too.
     * Only classes actually referenced by the target pack are listed (scanned from
     * all mod jars); add new entries here when another 2.8 addon is added.
     */
    private static final Map<String, String> REMAP = new LinkedHashMap<>();
    static {
        REMAP.put("gregtech/api/recipes/recipeproperties/IRecipePropertyStorage",
                "gregtech/api/recipes/properties/RecipePropertyStorage");
        REMAP.put("gregtech/api/recipes/recipeproperties/RecipePropertyStorage",
                "gregtech/api/recipes/properties/RecipePropertyStorageImpl");
        REMAP.put("gregtech/api/recipes/recipeproperties/RecipeProperty",
                "gregtech/api/recipes/properties/RecipeProperty");
        REMAP.put("gregtech/api/recipes/recipeproperties/GasCollectorDimensionProperty",
                "gregtech/api/recipes/properties/impl/DimensionProperty");
        String[] impl = {
                "ComputationProperty", "FusionEUToStartProperty", "PrimitiveProperty",
                "ResearchProperty", "TemperatureProperty", "TotalComputationProperty"
        };
        for (String n : impl) {
            REMAP.put("gregtech/api/recipes/recipeproperties/" + n,
                    "gregtech/api/recipes/properties/impl/" + n);
        }
        REMAP.put("gregtech/api/unification/material/properties/ToolProperty$Builder",
                "gregtech/api/unification/material/properties/MaterialToolProperty$Builder");
        REMAP.put("gregtech/api/unification/material/properties/ToolProperty",
                "gregtech/api/unification/material/properties/MaterialToolProperty");
        // Classes that merely moved packages / were renamed in GTQT 2.9.
        REMAP.put("gregtech/loaders/WoodTypeEntry", "gregtech/loaders/recipe/WoodTypeEntry");
        // 2.8: CraftingComponent.PLATE etc. had type CraftingComponent$Component;
        // 2.9 moved that class to gregtech.api.recipes.crafting.Component.
        // Rewrite the field descriptor so getstatic CraftingComponent.PLATE resolves.
        REMAP.put("gregtech/loaders/recipe/CraftingComponent$Component",
                "gregtech/api/recipes/crafting/Component");
        // 2.8's AssemblerRecipeBuilder was the builder for ASSEMBLER_RECIPES.
        // In 2.9 ASSEMBLER_RECIPES uses SimpleRecipeBuilder (CircuitAssemblerRecipeBuilder
        // belongs to CIRCUIT_ASSEMBLER_RECIPES). Mapping to CircuitAssemblerRecipeBuilder
        // caused GCYM's ASSEMBLER_RECIPES.recipeBuilder() checkcast to fail with CCE.
        REMAP.put("gregtech/api/recipes/builders/AssemblerRecipeBuilder",
                "gregtech/api/recipes/builders/SimpleRecipeBuilder");
        REMAP.put("gregtech/common/terminal/app/prospector/ProspectorMode",
                "gregtech/common/gui/widget/prospector/ProspectorMode");
        REMAP.put("gregtech/common/terminal/app/prospector/widget/WidgetProspectingMap",
                "gregtech/common/gui/widget/prospector/widget/WidgetProspectingMap");
        REMAP.put("gregtech/common/metatileentities/storage/MetaTileEntityWorkbench",
                "gregtech/common/metatileentities/workbench/MetaTileEntityWorkbench");
        REMAP.put("gregtech/api/util/ItemStackHashStrategy$ItemStackHashStrategyBuilder",
                "gregtech/api/util/ItemStackHashStrategy$Builder");
        // GTQT 2.9 changed RecipeMapSteamMultiblockController.recipeMapWorkable's
        // declared type from SteamMultiblockRecipeLogic to its subclass
        // SteamMultiWorkable. A 2.8 addon accessing that field with the old
        // descriptor gets NoSuchFieldError (JVM field identity includes the
        // descriptor). Rewrite the old type name to the 2.9 one. Only GTFO uses
        // it (2 refs, both this field); SteamMultiWorkable still inherits all
        // SteamMultiblockRecipeLogic methods, so method-call owners stay valid.
        REMAP.put("gregtech/api/capability/impl/SteamMultiblockRecipeLogic",
                "gregtech/api/capability/impl/SteamMultiWorkable");
        // NOTE: gregtech.api.pattern.BlockPattern / FactoryBlockPattern /
        // PatternMatchContext / TraceabilityPredicate and the cover-filter classes
        // were DELETED (not renamed) by GTQT 2.9's structure-API rewrite; they
        // cannot be restored by class-name remapping. Addons using them need a
        // 2.9 build. See README.
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) {
            return basicClass;
        }
        String internal = transformedName.replace('.', '/');
        boolean addon = false;
        for (String prefix : ADDON_PREFIXES) {
            if (internal.startsWith(prefix)) {
                addon = true;
                break;
            }
        }
        if (!addon) {
            return basicClass;
        }
        try {
            return remap(basicClass, transformedName);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Addon package remap failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] remap(byte[] basicClass, String transformedName) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        int changed = 0;
        cn.superName = remapInternal(cn.superName, new int[]{0});
        cn.signature = remapText(cn.signature, new int[]{0});
        for (int i = 0; i < cn.interfaces.size(); i++) {
            int[] n = {0};
            cn.interfaces.set(i, remapInternal(cn.interfaces.get(i), n));
            changed += n[0];
        }

        for (org.objectweb.asm.tree.FieldNode fn : cn.fields) {
            int[] n = {0};
            fn.desc = remapText(fn.desc, n);
            fn.signature = remapText(fn.signature, n);
            changed += n[0];
        }

        List<MethodNode> methods = new ArrayList<>(cn.methods);
        for (MethodNode mn : methods) {
            int[] n = {0};
            mn.desc = remapText(mn.desc, n);
            mn.signature = remapText(mn.signature, n);
            changed += n[0];
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn : mn.instructions) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) insn;
                    int[] k = {0};
                    m.owner = remapInternal(m.owner, k);
                    m.desc = remapText(m.desc, k);
                    // GTQT 2.9 removed MultiblockControllerBase's static structure
                    // predicate factories (states/abilities/frames/blocks/air/any/
                    // heatingCoils/metaTileEntities/tilePredicate). Redirect those
                    // invokestatic calls to the compatibility layer's equivalent.
                    if (m.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC &&
                            "gregtech/api/metatileentity/multiblock/MultiblockControllerBase".equals(m.owner) &&
                            isStructureFactory(m.name)) {
                        m.owner = "gregtech/api/metatileentity/multiblock/StructurePredicates";
                        k[0]++;
                    }
                    // 2.9 renamed ImplosionRecipeBuilder fluent setters:
                    //   explosivesAmount(int)      -> explosives(int)
                    //   explosivesType(ItemStack)  -> explosives(ItemStack)
                    //   getExplosivesType()        -> getExplosives()
                    if ("gregtech/api/recipes/builders/ImplosionRecipeBuilder".equals(m.owner)) {
                        if ("explosivesAmount".equals(m.name) && "(I)Lgregtech/api/recipes/builders/ImplosionRecipeBuilder;".equals(m.desc)) {
                            m.name = "explosives";
                            k[0]++;
                        } else if ("explosivesType".equals(m.name) &&
                                "(Lnet/minecraft/item/ItemStack;)Lgregtech/api/recipes/builders/ImplosionRecipeBuilder;".equals(m.desc)) {
                            m.name = "explosives";
                            k[0]++;
                        } else if ("getExplosivesType".equals(m.name)) {
                            m.name = "getExplosives";
                            k[0]++;
                        }
                    }
                    // 2.9 renamed Recipe.getRecipePropertyStorage() to
                    // propertyStorage(). GTCEFuContent (LinearForgingFurnaceLoader)
                    // still calls the old name -> NoSuchMethodError.
                    if ("getRecipePropertyStorage".equals(m.name) &&
                            "gregtech/api/recipes/Recipe".equals(m.owner)) {
                        m.name = "propertyStorage";
                        m.desc = "()Lgregtech/api/recipes/properties/RecipePropertyStorage;";
                        k[0]++;
                    }
                    // Recipe.getOutputs() was narrowed from NonNullList to List in
                    // 2.9. Rewriting call sites is NOT safe: addons also use
                    // NonNullList independently (e.g. ZBGT
                    // MTECircuitAssemblyLine.checkRecipe), and a blanket
                    // NonNullList->List rewrite breaks their stack types with
                    // VerifyError. Those addon passes are skipped individually by
                    // the dedicated skip transformers instead.
                    // GTQT 2.9 converted GTCEu's casing enums (MetalCasingType,
                    // CoilType, FusionCasingType, ...) from java.lang.Enum to plain
                    // classes implementing IStringSerializable. VariantBlock<T>'s
                    // erased getState(T) descriptor therefore changed from
                    // (Enum)IBlockState to (IStringSerializable)IBlockState.
                    // 2.8-compiled addons (GTCEFuContent FusionStackPatterns, etc.)
                    // still emit the Enum form -> VerifyError "Bad type on operand
                    // stack". Rewrite that exact method descriptor globally.
                    if ("getState".equals(m.name) &&
                            "(Ljava/lang/Enum;)Lnet/minecraft/block/state/IBlockState;".equals(m.desc)) {
                        m.desc = "(Lnet/minecraft/util/IStringSerializable;)Lnet/minecraft/block/state/IBlockState;";
                        k[0]++;
                    }
                    // Same enum->IStringSerializable change hits VariantBlock.getItemVariant.
                    // GCYM's GCYMMetaTileEntityLoader calls BlockGlassCasing.getItemVariant(Enum).
                    if ("getItemVariant".equals(m.name)) {
                        if ("(Ljava/lang/Enum;)Lnet/minecraft/item/ItemStack;".equals(m.desc)) {
                            m.desc = "(Lnet/minecraft/util/IStringSerializable;)Lnet/minecraft/item/ItemStack;";
                            k[0]++;
                        } else if ("(Ljava/lang/Enum;I)Lnet/minecraft/item/ItemStack;".equals(m.desc)) {
                            m.desc = "(Lnet/minecraft/util/IStringSerializable;I)Lnet/minecraft/item/ItemStack;";
                            k[0]++;
                        }
                    }
                    changed += k[0];
                } else if (insn instanceof TypeInsnNode) {
                    TypeInsnNode t = (TypeInsnNode) insn;
                    int[] k = {0};
                    t.desc = remapInternal(t.desc, k);
                    changed += k[0];
                } else if (insn instanceof FieldInsnNode) {
                    FieldInsnNode f = (FieldInsnNode) insn;
                    int[] k = {0};
                    f.owner = remapInternal(f.owner, k);
                    f.desc = remapText(f.desc, k);
                    // GTQT 2.9 renamed two MetaItems fields from LUV to LuV
                    // (case-only). 2.8 addons (GTCEFuContent) still use the old
                    // spelling -> NoSuchFieldError.
                    if ("gregtech/common/items/MetaItems".equals(f.owner)) {
                        if ("ELECTRIC_PISTON_LUV".equals(f.name)) {
                            f.name = "ELECTRIC_PISTON_LuV";
                            k[0]++;
                        } else if ("FLUID_REGULATOR_LUV".equals(f.name)) {
                            f.name = "FLUID_REGULATOR_LuV";
                            k[0]++;
                        }
                    }
                    // 2.9 dropped RecipeBuilder.isCTRecipe (CraftTweaker marker).
                    // Handled by RecipeBuilderFieldCompatTransformer, which adds the
                    // field back to RecipeBuilder (stays false), so addon GETFIELD/
                    // PUTFIELD resolve without any instruction rewriting here.
                    // 2.9 widened RecipeBuilder.EUt from int to long. 2.8 addons
                    // (GTExpert TierUpRecipeBuilder) still read it as int:
                    // GETFIELD EUt:J + L2I replaces GETFIELD EUt:I.
                    if ("EUt".equals(f.name) && "I".equals(f.desc)
                            && f.owner.endsWith("RecipeBuilder")) {
                        InsnList seq = new InsnList();
                        seq.add(new FieldInsnNode(org.objectweb.asm.Opcodes.GETFIELD,
                                "gregtech/api/recipes/RecipeBuilder", "EUt", "J"));
                        seq.add(new InsnNode(org.objectweb.asm.Opcodes.L2I));
                        mn.instructions.insertBefore(f, seq);
                        mn.instructions.remove(f);
                        changed++;
                        continue;
                    }
                    // 2.9 replaced the single ADVANCED_DATA_ACCESS_HATCH with a
                    // per-tier DATA_ACCESS_HATCH[] array. Replace the GETSTATIC with
                    // a lazy INVOKESTATIC on the compat holder (the array may not be
                    // populated yet when the addon reads the field).
                    if ("ADVANCED_DATA_ACCESS_HATCH".equals(f.name) &&
                            "gregtech/common/metatileentities/MetaTileEntities".equals(f.owner)) {
                        mn.instructions.set(f, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                                "com/gtceu/compat/core/asm/MetaTileEntitiesCompat", "resolve",
                                "()Lgregtech/common/metatileentities/multi/multiblockpart/MetaTileEntityDataAccessHatch;",
                                false));
                        changed++;
                        continue;
                    }
                    if ("CREATIVE_DATA_HATCH".equals(f.name) &&
                            "gregtech/common/metatileentities/MetaTileEntities".equals(f.owner)) {
                        // Removed in 2.9; approximate with the highest data access tier.
                        mn.instructions.set(f, new MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                                "com/gtceu/compat/core/asm/MetaTileEntitiesCompat", "resolveCreative",
                                "()Lgregtech/common/metatileentities/multi/multiblockpart/MetaTileEntityDataAccessHatch;",
                                false));
                        changed++;
                        continue;
                    }
                    // 2.9 deleted the 2.8 ME hatch fields (the ME hatches became the
                    // GTQT dual/ME-hatch family). prismplan's creative tab reads
                    // ITEM_IMPORT_BUS_ME to build its tab icon; the missing field
                    // threw NoSuchFieldError inside GuiContainerCreative.drawTab and
                    // broke creative-inventory tab rendering. Redirect each to a lazy
                    // compat resolver that returns the closest surviving 2.9 machine.
                    if ("gregtech/common/metatileentities/MetaTileEntities".equals(f.owner)) {
                        String resolver = ME_HATCH_RESOLVERS.get(f.name);
                        if (resolver != null) {
                            mn.instructions.set(f, new MethodInsnNode(
                                    org.objectweb.asm.Opcodes.INVOKESTATIC,
                                    "com/gtceu/compat/core/asm/MetaTileEntitiesCompat", resolver,
                                    "()Lgregtech/api/metatileentity/MetaTileEntity;", false));
                            changed++;
                            continue;
                        }
                    }
                    changed += k[0];
                } else if (insn instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if (ldc.cst instanceof org.objectweb.asm.Type) {
                        int[] k = {0};
                        String nd = remapText(((org.objectweb.asm.Type) ldc.cst).getDescriptor(), k);
                        if (k[0] > 0) {
                            ldc.cst = org.objectweb.asm.Type.getType(nd);
                            changed += k[0];
                        }
                    } else if (ldc.cst instanceof org.objectweb.asm.Handle) {
                        int[] k = {0};
                        org.objectweb.asm.Handle h = (org.objectweb.asm.Handle) ldc.cst;
                        String owner = remapInternal(h.getOwner(), k);
                        String desc = remapText(h.getDesc(), k);
                        if (k[0] > 0) {
                            ldc.cst = new org.objectweb.asm.Handle(h.getTag(), owner,
                                    h.getName(), desc, h.isInterface());
                            changed += k[0];
                        }
                    } else if (ldc.cst instanceof String) {
                        int[] k = {0};
                        String ns = remapText((String) ldc.cst, k);
                        if (k[0] > 0) {
                            ldc.cst = ns;
                            changed += k[0];
                        }
                    }
                } else if (insn instanceof InvokeDynamicInsnNode) {
                    // Method references / lambdas embed the target descriptor in
                    // the indy desc AND in bsmArgs (Handle + Type). If we only
                    // rewrite the method DECLARATION but leave these untouched,
                    // the method reference keeps calling the OLD descriptor and
                    // fails at runtime with NoSuchMethodError. This was exactly
                    // the GregTech Food Option GTFORecipeHandler.register() case.
                    InvokeDynamicInsnNode idy = (InvokeDynamicInsnNode) insn;
                    int[] k = {0};
                    idy.desc = remapText(idy.desc, k);
                    for (int a = 0; a < idy.bsmArgs.length; a++) {
                        Object arg = idy.bsmArgs[a];
                        if (arg instanceof org.objectweb.asm.Handle) {
                            org.objectweb.asm.Handle h = (org.objectweb.asm.Handle) arg;
                            String owner = remapInternal(h.getOwner(), k);
                            String desc = remapText(h.getDesc(), k);
                            if (!owner.equals(h.getOwner()) || !desc.equals(h.getDesc())) {
                                idy.bsmArgs[a] = new org.objectweb.asm.Handle(h.getTag(), owner,
                                        h.getName(), desc, h.isInterface());
                                k[0]++;
                            }
                        } else if (arg instanceof org.objectweb.asm.Type) {
                            org.objectweb.asm.Type t = (org.objectweb.asm.Type) arg;
                            String nd = remapText(t.getDescriptor(), k);
                            if (!nd.equals(t.getDescriptor())) {
                                idy.bsmArgs[a] = org.objectweb.asm.Type.getType(nd);
                            }
                        }
                    }
                    changed += k[0];
                }
            }
        }

        if (changed == 0) {
            return basicClass;
        }
        // Descriptor / field type changes affect StackMapTable frames, so let ASM
        // recompute them instead of keeping stale old-class frames (VerifyError risk).
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Addon remap: " + transformedName + " (" + changed + " refs)");
        return cw.toByteArray();
    }

    private static final java.util.Set<String> STRUCTURE_FACTORIES = new java.util.HashSet<>(java.util.Arrays.asList(
            "states", "abilities", "frames", "blocks", "air", "any", "heatingCoils",
            "metaTileEntities", "tilePredicate", "selfPredicate"));

    /**
     * 2.8 {@code MetaTileEntities} ME-hatch fields deleted in 2.9, mapped to the
     * compat resolver that substitutes a surviving 2.9 machine. Used for icons /
     * representative stacks by addons such as prismplan.
     */
    private static final Map<String, String> ME_HATCH_RESOLVERS = new LinkedHashMap<>();

    static {
        ME_HATCH_RESOLVERS.put("ITEM_IMPORT_BUS_ME", "resolveItemImportBusMe");
        ME_HATCH_RESOLVERS.put("ITEM_EXPORT_BUS_ME", "resolveItemExportBusMe");
        ME_HATCH_RESOLVERS.put("STOCKING_BUS_ME", "resolveStockingBusMe");
        ME_HATCH_RESOLVERS.put("FLUID_IMPORT_HATCH_ME", "resolveFluidImportHatchMe");
        ME_HATCH_RESOLVERS.put("FLUID_EXPORT_HATCH_ME", "resolveFluidExportHatchMe");
        ME_HATCH_RESOLVERS.put("STOCKING_HATCH_ME", "resolveStockingHatchMe");
    }

    private static boolean isStructureFactory(String name) {
        return STRUCTURE_FACTORIES.contains(name);
    }

    private static String remapInternal(String s, int[] changed) {
        if (s == null) return null;
        String r = s;
        for (Map.Entry<String, String> e : REMAP.entrySet()) {
            if (r.equals(e.getKey()) || r.startsWith(e.getKey() + "$")) {
                r = e.getValue() + r.substring(e.getKey().length());
                changed[0]++;
            }
        }
        return r;
    }

    private static String remapText(String s, int[] changed) {
        if (s == null) return null;
        String r = s;
        for (Map.Entry<String, String> e : REMAP.entrySet()) {
            if (r.contains(e.getKey())) {
                r = r.replace(e.getKey(), e.getValue());
                changed[0]++;
            }
        }
        return r;
    }
}
