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
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Injects GTCEu 2.8.10's {@code RecipeMap} constructors into GTQT 2.9's
 * {@code gregtech.api.recipes.RecipeMap}.
 *
 * <p>2.8 constructors:
 * <pre>
 *   RecipeMap(String, int maxInputs, int maxOutputs, int maxFluidInputs,
 *             int maxFluidOutputs, RecipeBuilder, boolean)
 *   RecipeMap(String, int, boolean, int, boolean, int, boolean, int, boolean,
 *             RecipeBuilder, boolean)
 * </pre>
 * 2.9 replaced both with:
 * <pre>
 *   RecipeMap(String, RecipeBuilder, RecipeMapUIFunction,
 *             int maxInputs, int maxOutputs, int maxFluidInputs, int maxFluidOutputs)
 * </pre>
 * The 2.8 "boolean modify*" flags and trailing "hidden" boolean have no 2.9
 * equivalent, so the bridge discards them and supplies
 * {@link DefaultRecipeMapUIFunction} as the UI function. That is the same default
 * GTQT 2.9's own {@code RecipeMapBuilder} uses.
 */
public class RecipeMapCtorBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/RecipeMap";
    private static final String UI_FN = "com/gtceu/compat/core/asm/DefaultRecipeMapUIFunction";
    private static final String NEW_CTOR_DESC =
            "(Ljava/lang/String;Lgregtech/api/recipes/RecipeBuilder;"
                    + "Lgregtech/api/recipes/ui/RecipeMapUIFunction;IIII)V";

    /** Old ctor descriptor -> argument slot of (String, ints..., RecipeBuilder, boolean). */
    private static final String[][] OLD_CTORS = {
            // (String, maxInputs, maxOutputs, maxFluidInputs, maxFluidOutputs, RecipeBuilder, hidden)
            {"(Ljava/lang/String;IIIILgregtech/api/recipes/RecipeBuilder;Z)V", "simple"},
            // (String, maxInputs, bool, maxOutputs, bool, maxFluidInputs, bool,
            //  maxFluidOutputs, bool, RecipeBuilder, hidden)
            {"(Ljava/lang/String;IZIZIZIZLgregtech/api/recipes/RecipeBuilder;Z)V", "flags"}
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] RecipeMap ctor bridge failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        Set<String> present = new HashSet<>();
        for (MethodNode mn : cn.methods) {
            present.add(mn.name + mn.desc);
        }
        if (!present.contains("<init>" + NEW_CTOR_DESC)) {
            return basicClass; // not a 2.9 host
        }

        int added = 0;
        for (String[] c : OLD_CTORS) {
            String oldDesc = c[0];
            if (present.contains("<init>" + oldDesc)) continue;
            boolean simple = "simple".equals(c[1]);
            cn.methods.add(buildCtor(oldDesc, simple));
            present.add("<init>" + oldDesc);
            added++;
        }
        // 2.8: RecipeMap onRecipeBuild(Consumer<R>)
        // 2.9: RecipeMap onRecipeBuild(ResourceLocation, RecipeBuildAction<R>)
        String legacyBuild = "(Ljava/util/function/Consumer;)Lgregtech/api/recipes/RecipeMap;";
        if (!present.contains("onRecipeBuild" + legacyBuild)) {
            MethodNode mn = new MethodNode(ACC_PUBLIC, "onRecipeBuild", legacyBuild, null, null);
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(ALOAD, 0)); // this
            il.add(new VarInsnNode(ALOAD, 1)); // Consumer
            il.add(new MethodInsnNode(INVOKESTATIC,
                    "com/gtceu/compat/core/asm/RecipeBuildActionAdapter", "register",
                    "(Ljava/lang/Object;Ljava/util/function/Consumer;)Ljava/lang/Object;", false));
            il.add(new org.objectweb.asm.tree.TypeInsnNode(CHECKCAST, TARGET));
            il.add(new InsnNode(ARETURN));
            cn.methods.add(mn);
            present.add("onRecipeBuild" + legacyBuild);
            added++;
        }
        if (added == 0) return basicClass;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Injected " + added + " 2.8 RecipeMap constructor(s).");
        return cw.toByteArray();
    }

    private static MethodNode buildCtor(String desc, boolean simple) {
        MethodNode mn = new MethodNode(ACC_PUBLIC, "<init>", desc, null, null);
        InsnList il = mn.instructions;
        // this
        il.add(new VarInsnNode(ALOAD, 0));
        // String unlocalizedName
        il.add(new VarInsnNode(ALOAD, 1));
        // RecipeBuilder sample (slot 6 for simple ctor, slot 10 for flags ctor)
        il.add(new VarInsnNode(ALOAD, simple ? 6 : 10));
        // RecipeMapUIFunction: dynamic proxy created without a compile-time GTQT reference
        il.add(new MethodInsnNode(INVOKESTATIC, UI_FN, "asFunction",
                "()Ljava/lang/Object;", false));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(CHECKCAST,
                "gregtech/api/recipes/ui/RecipeMapUIFunction"));
        // maxInputs, maxOutputs, maxFluidInputs, maxFluidOutputs
        if (simple) {
            il.add(new VarInsnNode(ILOAD, 2));
            il.add(new VarInsnNode(ILOAD, 3));
            il.add(new VarInsnNode(ILOAD, 4));
            il.add(new VarInsnNode(ILOAD, 5));
        } else {
            il.add(new VarInsnNode(ILOAD, 2));  // maxInputs
            il.add(new VarInsnNode(ILOAD, 4));  // maxOutputs
            il.add(new VarInsnNode(ILOAD, 6));  // maxFluidInputs
            il.add(new VarInsnNode(ILOAD, 8));  // maxFluidOutputs
        }
        il.add(new MethodInsnNode(INVOKESPECIAL, TARGET, "<init>", NEW_CTOR_DESC, false));
        il.add(new InsnNode(RETURN));
        return mn;
    }
}
