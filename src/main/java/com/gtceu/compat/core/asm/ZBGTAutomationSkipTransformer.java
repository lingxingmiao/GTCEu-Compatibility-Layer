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
 * Skips ZBGT 0.16.2's {@code RecipeMapTrollery.resetAssemblerOnBuilder()} on GTQT 2.9.
 *
 * <p>That method registers a build action on ASSEMBLER_RECIPES whose lambda is
 * typed {@code AssemblerRecipeBuilder} and calls 2.8-only API
 * ({@code isWithRecycling()}, {@code clearFluidInputs()}, ...). In 2.9
 * ASSEMBLER_RECIPES uses {@code SimpleRecipeBuilder}, which has none of those
 * methods, so the action throws NoSuchMethodError the first time any assembler
 * recipe is built.
 *
 * <p>The skipped action is only a convenience tweak (replace a soldering-alloy
 * fluid input with tin and register recycling info). Deciding to skip keeps
 * every assembler recipe working; they just keep their original solder input and
 * no extra recycling entry. Other RecipeMapTrollery helpers are untouched.
 */
public class ZBGTAutomationSkipTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "com/zorbatron/zbgt/recipe/helpers/RecipeMapTrollery";
    private static final String METHOD = "resetAssemblerOnBuilder";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] ZBGT assembler-action skip failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        MethodNode m = null;
        for (MethodNode mn : cn.methods) {
            if (METHOD.equals(mn.name) && "()V".equals(mn.desc)) {
                m = mn;
                break;
            }
        }
        if (m == null || m.instructions == null) return basicClass;

        InsnList il = new InsnList();
        il.add(new InsnNode(RETURN));
        m.instructions = il;
        m.maxStack = 0;
        m.maxLocals = 0;
        if (m.localVariables != null) m.localVariables.clear();
        if (m.tryCatchBlocks != null) m.tryCatchBlocks.clear();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] ZBGT RecipeMapTrollery." + METHOD
                + ": skipped 2.8-only assembler build action.");
        return cw.toByteArray();
    }
}
