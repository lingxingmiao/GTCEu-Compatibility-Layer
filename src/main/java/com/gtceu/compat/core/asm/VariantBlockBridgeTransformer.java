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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Restores GTCEu 2.8.10's erased {@code Enum}-based descriptors on GTQT 2.9's
 * {@code VariantBlock} / {@code VariantActiveBlock} hierarchy.
 *
 * <p>2.8 declared
 * {@code VariantBlock<T extends Enum<T> & IStringSerializable>}, so javac erased
 * {@code getState(T)} to {@code getState(Enum)}. 2.9 changed the bound to
 * {@code IStringSerializable & Comparable<T>}, erasing it to
 * {@code getState(IStringSerializable)}.
 *
 * <p>Addon jars compiled against 2.8 (GCYM, ZBGT, ...) therefore contain
 * {@code invokevirtual BlockUniqueCasing.getState(Enum)} and fail with
 * {@code NoSuchMethodError}. This transformer injects public bridges with the old
 * descriptors that delegate to the 2.9 methods; because every 2.8 enum variant
 * also implements {@code IStringSerializable}, the CHECKCAST is always valid.
 */
public class VariantBlockBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String[] TARGETS = {
            "gregtech/api/block/VariantBlock",
            "gregtech/api/block/VariantActiveBlock"
    };

    private static final String ENUM = "java/lang/Enum";
    private static final String ISS = "net/minecraft/util/IStringSerializable";
    private static final String STATE = "Lnet/minecraft/block/state/IBlockState;";
    private static final String RENDER = "Lnet/minecraft/util/BlockRenderLayer;";

    /** name, old desc, new desc, return opcode, access flags */
    private static final Object[][] BRIDGES = {
            {"getState", "(L" + ENUM + ";)" + STATE, "(L" + ISS + ";)" + STATE, ARETURN, ACC_PUBLIC},
            {"isBloomEnabled", "(L" + ENUM + ";)Z", "(L" + ISS + ";)Z", IRETURN, ACC_PROTECTED},
            {"getRenderLayer", "(L" + ENUM + ";)" + RENDER, "(L" + ISS + ";)" + RENDER, ARETURN, ACC_PUBLIC}
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) {
            return basicClass;
        }
        String internal = transformedName.replace('.', '/');
        if (!Arrays.asList(TARGETS).contains(internal)) {
            return basicClass;
        }
        try {
            return inject(internal, basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] VariantBlock bridge failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(String internal, byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        Set<String> present = new HashSet<>();
        for (MethodNode mn : cn.methods) {
            present.add(mn.name + mn.desc);
        }

        int added = 0;
        for (Object[] b : BRIDGES) {
            String name = (String) b[0];
            String oldDesc = (String) b[1];
            String newDesc = (String) b[2];
            int ret = (Integer) b[3];
            int access = (Integer) b[4];
            if (present.contains(name + oldDesc) || !present.contains(name + newDesc)) {
                continue;
            }
            MethodNode mn = new MethodNode(access, name, oldDesc, null, null);
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(ALOAD, 0)); // this
            il.add(new VarInsnNode(ALOAD, 1)); // Enum variant
            il.add(new org.objectweb.asm.tree.TypeInsnNode(CHECKCAST, ISS));
            il.add(new MethodInsnNode(INVOKEVIRTUAL, internal, name, newDesc, false));
            il.add(new InsnNode(ret));
            cn.methods.add(mn);
            present.add(name + oldDesc);
            added++;
        }

        if (added == 0) {
            return basicClass;
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Restored " + added + " Enum-erasure bridge(s) on " + internal);
        return cw.toByteArray();
    }
}
