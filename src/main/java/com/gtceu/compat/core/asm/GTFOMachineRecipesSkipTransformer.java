package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Disables GregTech Food Option 1.12.8's {@code GTFOMachineRecipes.init()} recipe
 * pass on GTQT 2.9.
 *
 * <p>That method builds its crafting components with GTCEu 2.8's
 * {@code CraftingComponent$Component(Map)} / {@code appendIngredients(Map)} API.
 * GTQT 2.9 moved the class to {@code gregtech.api.recipes.crafting.Component}
 * with a private constructor and no appendIngredients, so the whole pass throws
 * {@code NoSuchMethodError: Component.<init>(Map)} and aborts startup.
 *
 * <p>Skipping this ONE recipe-registration method is a deliberate degradation:
 * GTFO's machines, items and all other recipe chains (GTFORecipeAddition.init
 * calls CoreChain/FatChain/GreenhouseChain/... before this) stay intact; only
 * the machine-casing recipes built through the removed 2.8 Component API are
 * omitted. This is the same trade-off used elsewhere in this layer (ZBGT mixins,
 * the 2.8 AssemblyLineMixin). If GTFO is later ported to 2.9, remove this
 * transformer.
 */
public class GTFOMachineRecipesSkipTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtechfoodoption/recipe/GTFOMachineRecipes";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] GTFO machine-recipe skip failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        MethodNode init = null;
        for (MethodNode mn : cn.methods) {
            if ("init".equals(mn.name) && "()V".equals(mn.desc)) {
                init = mn;
                break;
            }
        }
        if (init == null || init.instructions == null) return basicClass;

        InsnList il = new InsnList();
        il.add(new InsnNode(RETURN));
        init.instructions = il;
        init.maxStack = 0;
        init.maxLocals = 0;
        if (init.localVariables != null) init.localVariables.clear();
        if (init.tryCatchBlocks != null) init.tryCatchBlocks.clear();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] GTFOMachineRecipes.init: skipped 2.8-only crafting-component recipes.");
        return cw.toByteArray();
    }
}
