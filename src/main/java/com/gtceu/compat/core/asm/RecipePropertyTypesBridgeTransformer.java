package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Restores GTCEu 2.8's {@code Recipe.getPropertyTypes()} on GTQT 2.9.
 *
 * <p>2.8: {@code Set<RecipeProperty<?>> getPropertyTypes()}.
 * 2.9 removed it; the equivalent is
 * {@code recipe.propertyStorage().values()} (a Set of property types).
 *
 * <p>GTExpert's JEI mixin ({@code localvar$beb000$gtexpert$fixYPosition}, injected
 * into {@code GTRecipeWrapper.drawInfo}) calls the old method while adjusting the
 * recipe info layout. On 2.9 it throws
 * {@code NoSuchMethodError: Recipe.getPropertyTypes()} on **every** recipe draw,
 * which is exactly the user-reported "HEI renders the recipe wrong / no output":
 *
 * <pre>
 * NoSuchMethodError: java.util.Set gregtech.api.recipes.Recipe.getPropertyTypes()
 *   at GTRecipeWrapper.drawInfo(GTRecipeWrapper.java:280)
 *   at RecipeLayout.drawRecipe / RecipesGui.drawScreen
 * </pre>
 *
 * <p>The bridge returns an empty set when the recipe has no property storage,
 * keeping 2.8's "no custom properties" semantics.
 */
public class RecipePropertyTypesBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/Recipe";
    private static final String DESC = "()Ljava/util/Set;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Recipe.getPropertyTypes bridge failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode mn : cn.methods) {
            if ("getPropertyTypes".equals(mn.name)) return basicClass;
        }

        MethodNode mn = new MethodNode(ACC_PUBLIC, "getPropertyTypes", DESC, null, null);
        InsnList il = mn.instructions;
        LabelNode have = new LabelNode();
        // RecipePropertyStorage s = this.propertyStorage();
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET, "propertyStorage",
                "()Lgregtech/api/recipes/properties/RecipePropertyStorage;", false));
        il.add(new VarInsnNode(ASTORE, 1));
        // if (s == null) return Collections.emptySet();
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new JumpInsnNode(IFNONNULL, have));
        il.add(new MethodInsnNode(INVOKESTATIC, "java/util/Collections", "emptySet",
                "()Ljava/util/Set;", false));
        il.add(new InsnNode(ARETURN));
        // return s.values();
        il.add(have);
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new MethodInsnNode(INVOKEINTERFACE,
                "gregtech/api/recipes/properties/RecipePropertyStorage", "values",
                "()Ljava/util/Set;", true));
        il.add(new InsnNode(ARETURN));
        mn.maxStack = 2;
        mn.maxLocals = 2;
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Recipe: injected 2.8 getPropertyTypes() bridge (HEI draw fix).");
        return cw.toByteArray();
    }
}
