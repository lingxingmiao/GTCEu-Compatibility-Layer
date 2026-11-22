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
 * Skips GTCEFuContent 2.1.1's {@code GTCEFuCMiscRecipes.cutterUpdate()}.
 *
 * <p>That late recipe pass iterates all CUTTER recipes and reads
 * {@code Recipe.getOutputs()} as a {@code NonNullList} (2.8 return type).
 * GTQT 2.9 narrowed it to {@code List}, and {@code NonNullList.create()} is not
 * available on the obfuscated runtime under that name, so a bytecode bridge is
 * brittle. The pass only adjusts cutter outputs for GTCEFuC's own items; skipping
 * it leaves every recipe intact and just omits that cosmetic tweak.
 *
 * <p>{@code GTCEFuCRecipeLoader.initLate} calls this last, after all other late
 * recipe registration, so skipping it does not affect anything else.
 */
public class GTCEFuCCutterUpdateSkipTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "com/m_w_k/gtcefucontent/loaders/recipe/GTCEFuCMiscRecipes";
    private static final String METHOD = "cutterUpdate";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] GTCEFuC cutterUpdate skip failed: " + t);
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
        System.out.println("[GTCEuCompat] GTCEFuCMiscRecipes." + METHOD
                + ": skipped 2.8-only NonNullList cutter pass.");
        return cw.toByteArray();
    }
}
