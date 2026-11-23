package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Redirects 2.8 addons' reads of {@code CraftingComponent.EBF_GASES} to
 * {@link EbfGasesCompat#get()}, which presents 2.9's
 * {@code Map<GasTier, List<FluidStack>>} in the old
 * {@code Map<GasTier, FluidStack>} shape.
 */
public class EbfGasesBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String OWNER = "gregtech/loaders/recipe/CraftingComponent";
    private static final String FIELD = "EBF_GASES";
    private static final String FIELD_DESC = "Ljava/util/Map;";
    private static final String CALL_DESC = "()Ljava/util/Map;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) return basicClass;
        String internal = transformedName.replace('.', '/');
        // Only addons that use the 2.8 map shape.
        if (!internal.startsWith("com/zorbatron/zbgt/")) return basicClass;
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] EBF_GASES bridge failed for " + transformedName + ": " + t);
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        int patched = 0;
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn : mn.instructions) {
                if (!(insn instanceof FieldInsnNode)) continue;
                FieldInsnNode f = (FieldInsnNode) insn;
                if (f.getOpcode() != GETSTATIC) continue;
                if (!OWNER.equals(f.owner) || !FIELD.equals(f.name) || !FIELD_DESC.equals(f.desc)) continue;
                mn.instructions.set(insn, new MethodInsnNode(INVOKESTATIC,
                        "com/gtceu/compat/core/asm/EbfGasesCompat", "get", CALL_DESC, false));
                patched++;
            }
        }
        if (patched == 0) return basicClass;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] " + cn.name + ": EBF_GASES reads redirected (" + patched + ").");
        return cw.toByteArray();
    }
}
