package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Restores GTCEu 2.8's {@code SimpleRecipeBuilder.withRecycling()} on GTQT 2.9,
 * where recycling was split into {@code withItemRecycling()} /
 * {@code withFluidRecycling()} and returns the erased {@code RecipeBuilder}.
 *
 * <p>2.8 addons (GTExpert AEItemsRecipe) chain
 * {@code .withRecycling()} expecting a {@code SimpleRecipeBuilder} back.
 * The bridge enables both item and fluid recycling (2.8's single flag) and
 * returns {@code this} typed as SimpleRecipeBuilder.
 */
public class SimpleRecipeBuilderRecyclingBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/builders/SimpleRecipeBuilder";
    private static final String RB = "gregtech/api/recipes/RecipeBuilder";
    private static final String OLD_DESC = "()L" + TARGET + ";";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] withRecycling bridge failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean hasOld = false, hasItem = false, hasFluid = false;
        for (MethodNode mn : cn.methods) {
            if ("withRecycling".equals(mn.name)) hasOld = true;
            if ("withItemRecycling".equals(mn.name)) hasItem = true;
            if ("withFluidRecycling".equals(mn.name)) hasFluid = true;
        }
        // withItemRecycling is inherited from RecipeBuilder, so look for it on the
        // superclass instead of this class.
        if (hasOld) return basicClass;

        MethodNode mn = new MethodNode(ACC_PUBLIC, "withRecycling", OLD_DESC, null, null);
        InsnList il = mn.instructions;
        // this.withItemRecycling();  (inherited, returns RecipeBuilder)
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET, "withItemRecycling",
                "()L" + RB + ";", false));
        il.add(new InsnNode(POP));
        // this.withFluidRecycling();
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET, "withFluidRecycling",
                "()L" + RB + ";", false));
        il.add(new InsnNode(POP));
        // return this;
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new InsnNode(ARETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] SimpleRecipeBuilder: injected 2.8 withRecycling() bridge.");
        return cw.toByteArray();
    }
}
