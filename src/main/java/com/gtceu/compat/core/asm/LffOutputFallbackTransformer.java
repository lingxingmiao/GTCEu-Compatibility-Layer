package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Restores GTCEFuContent's forging-cooled recipes on GTQT 2.9.
 *
 * <p>{@code attemptForgingCooledRecipe(OrePrefix, RecipeBuilder)} rebuilds the
 * output list by resolving each source output through
 * {@code OreDictUnifier.getMaterial()} + {@code getPrefix()}. On 2.9 those lookups
 * can fail (the source item is not in {@code stackUnificationInfo}), so the
 * generated builder ends up with NO outputs and GTQT discards the recipe with
 * "Recipe outputs are empty" — observed on 629 {@code lff_blast_forging_cooled}
 * recipes even though diagnostic logging proved the source recipe had
 * {@code outputs=1}.
 *
 * <p>Fix: wrap the method. If the rebuilt builder has an empty output list but the
 * source builder has outputs, copy the source outputs into it before returning.
 * When the method returns {@code null} (an intentional skip) the wrapper leaves it
 * alone; when the rebuilt outputs are non-empty (the 2.8 path worked) nothing
 * changes.
 */
public class LffOutputFallbackTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "com/m_w_k/gtcefucontent/loaders/recipe/LinearForgingFurnaceLoader";
    private static final String METHOD = "attemptForgingCooledRecipe";
    private static final String DESC =
            "(Lgregtech/api/unification/ore/OrePrefix;Lgregtech/api/recipes/RecipeBuilder;)" +
            "Lgregtech/api/recipes/RecipeBuilder;";
    private static final String RAW = METHOD + "$compatRaw";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] LFF output fallback failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        MethodNode original = null;
        for (MethodNode mn : cn.methods) {
            if (METHOD.equals(mn.name) && DESC.equals(mn.desc)) { original = mn; break; }
        }
        if (original == null || original.instructions == null) return basicClass;
        for (MethodNode mn : cn.methods) {
            if (RAW.equals(mn.name)) return basicClass; // already patched
        }

        original.name = RAW;
        // Keep the original access flags exactly as they were; flipping a static
        // method to private makes COMPUTE_FRAMES try to load it as an instance
        // member and throws NegativeArraySizeException(-1) in ASM 7.2.

        MethodNode w = new MethodNode(ACC_PRIVATE | ACC_STATIC, METHOD, DESC, null, null);
        InsnList il = w.instructions;
        LabelNode notNull = new LabelNode();
        LabelNode hasOutputs = new LabelNode();
        LabelNode done = new LabelNode();

        // RecipeBuilder result = attemptForgingCooledRecipe$compatRaw(prefix, source);
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new MethodInsnNode(INVOKESTATIC, TARGET, RAW, DESC, false));
        il.add(new VarInsnNode(ASTORE, 2));
        il.add(new VarInsnNode(ALOAD, 2));
        il.add(new JumpInsnNode(IFNONNULL, notNull));
        il.add(new InsnNode(ACONST_NULL));
        il.add(new InsnNode(ARETURN));

        // if (!result.getOutputs().isEmpty()) return result;
        il.add(notNull);
        il.add(new VarInsnNode(ALOAD, 2));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, "gregtech/api/recipes/RecipeBuilder",
                "getOutputs", "()Ljava/util/List;", false));
        il.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true));
        il.add(new JumpInsnNode(IFEQ, hasOutputs));

        // result.outputs(source.getOutputs());  // restore the originals
        il.add(new VarInsnNode(ALOAD, 2));
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, "gregtech/api/recipes/RecipeBuilder",
                "getOutputs", "()Ljava/util/List;", false));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, "gregtech/api/recipes/RecipeBuilder",
                "outputs", "(Ljava/util/Collection;)Lgregtech/api/recipes/RecipeBuilder;", false));
        il.add(new InsnNode(POP));

        il.add(hasOutputs);
        il.add(new VarInsnNode(ALOAD, 2));
        il.add(done);
        il.add(new InsnNode(ARETURN));
        w.maxStack = 4;
        w.maxLocals = 3;
        cn.methods.add(w);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] LFF attemptForgingCooledRecipe: source-output fallback installed.");
        return cw.toByteArray();
    }
}
