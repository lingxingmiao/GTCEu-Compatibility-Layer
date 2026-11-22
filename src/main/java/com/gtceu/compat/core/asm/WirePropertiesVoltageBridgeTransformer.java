package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Restores GTCEu 2.8's {@code WireProperties.getVoltage(): int} on GTQT 2.9,
 * which widened the field/method to {@code long getVoltage()}.
 * 2.8 addons (ZBGT RecipeAssists.getMaterialsWithWireVoltage) still call the int
 * form -> NoSuchMethodError. Bridge delegates and narrows with L2I.
 */
public class WirePropertiesVoltageBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/unification/material/properties/WireProperties";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] WireProperties voltage bridge failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        boolean hasInt = false, hasLong = false;
        for (MethodNode mn : cn.methods) {
            if ("getVoltage".equals(mn.name)) {
                if ("()I".equals(mn.desc)) hasInt = true;
                if ("()J".equals(mn.desc)) hasLong = true;
            }
        }
        if (hasInt || !hasLong) return basicClass;

        MethodNode mn = new MethodNode(ACC_PUBLIC, "getVoltage", "()I", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET, "getVoltage", "()J", false));
        il.add(new InsnNode(L2I));
        il.add(new InsnNode(IRETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] WireProperties: injected 2.8 getVoltage():int bridge.");
        return cw.toByteArray();
    }
}
