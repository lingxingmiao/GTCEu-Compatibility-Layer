package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Restores GTCEu 2.8's {@code MultiblockAbility(String)} constructor on GTQT 2.9.
 *
 * <p>2.8: {@code public MultiblockAbility(String name)}.
 * 2.9: {@code public MultiblockAbility(String name, Class<T> clazz)} — the class
 * token is used for runtime ability-part type checks. 2.8 addons (GCYM, ...) that
 * declare their own ability constants call the one-arg constructor, which fails
 * with {@code NoSuchMethodError} on 2.9.
 *
 * <p>The bridge delegates to the two-arg constructor passing {@code Object.class},
 * which makes the 2.9 type check permissive (any ability part is accepted) — the
 * closest behaviour-preserving substitute for 2.8, which did no type check at all.
 */
public class MultiblockAbilityCtorBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/metatileentity/multiblock/MultiblockAbility";
    private static final String OLD_DESC = "(Ljava/lang/String;)V";
    private static final String NEW_DESC = "(Ljava/lang/String;Ljava/lang/Class;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] MultiblockAbility ctor bridge failed: " + t);
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
        if (present.contains("<init>" + OLD_DESC) || !present.contains("<init>" + NEW_DESC)) {
            return basicClass; // real 2.8 host or incompatible version
        }

        MethodNode mn = new MethodNode(ACC_PUBLIC, "<init>", OLD_DESC, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(ALOAD, 0));  // this
        il.add(new VarInsnNode(ALOAD, 1));  // String name
        il.add(new LdcInsnNode(org.objectweb.asm.Type.getType("Ljava/lang/Object;")));
        il.add(new MethodInsnNode(INVOKESPECIAL, TARGET, "<init>", NEW_DESC, false));
        il.add(new InsnNode(RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Injected 2.8 MultiblockAbility(String) constructor.");
        return cw.toByteArray();
    }
}
