package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;

/**
 * Restores GTCEu 2.8's {@code RecipeBuilder.isCTRecipe} field (and its fluent
 * setter) on GTQT 2.9, which removed both.
 *
 * <p>2.8 addons that subclass RecipeBuilder (GTExpert's TierUpRecipeBuilder)
 * both read and write the flag from their own {@code build()}. Rewriting the
 * GETFIELD/PUTFIELD in the addon proved stack-unsafe (VerifyError), so instead
 * the field itself is added back here: {@code protected boolean isCTRecipe},
 * defaulting to false, which is correct for every non-CraftTweaker recipe.
 */
public class RecipeBuilderFieldCompatTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/RecipeBuilder";
    private static final String RB = "Lgregtech/api/recipes/RecipeBuilder;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] RecipeBuilder field compat failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean hasField = false, hasGetter = false;
        for (FieldNode fn : cn.fields) {
            if ("isCTRecipe".equals(fn.name) && "Z".equals(fn.desc)) hasField = true;
        }
        for (MethodNode mn : cn.methods) {
            if ("isCTRecipe".equals(mn.name) && ("()" + RB).equals(mn.desc)) hasGetter = true;
        }

        int added = 0;
        if (!hasField) {
            cn.fields.add(new FieldNode(ACC_PROTECTED, "isCTRecipe", "Z", null, null));
            added++;
        }
        if (!hasGetter) {
            // public R isCTRecipe() { this.isCTRecipe = true; return this; }
            MethodNode mn = new MethodNode(ACC_PUBLIC, "isCTRecipe", "()" + RB, null, null);
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(ALOAD, 0));
            il.add(new InsnNode(ICONST_1));
            il.add(new FieldInsnNode(PUTFIELD, TARGET, "isCTRecipe", "Z"));
            il.add(new VarInsnNode(ALOAD, 0));
            il.add(new InsnNode(ARETURN));
            cn.methods.add(mn);
            added++;
        }
        if (added == 0) return basicClass;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] RecipeBuilder: restored 2.8 isCTRecipe field/method.");
        return cw.toByteArray();
    }
}
