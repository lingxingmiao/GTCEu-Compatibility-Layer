package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Restores the small set of GTCEu 2.8 {@link gregtech.api.metatileentity.MetaTileEntity}
 * methods that GTQT 2.9 removed and that can be given a safe, deterministic fallback.
 *
 * <p>These are intentionally <em>not</em> behaviour-critical methods: they were
 * comparator / AE2-grid / tick-acceleration helpers that 2.9 either inlined or
 * dropped. Injecting a method that returns {@code 0}, {@code false} or does nothing
 * prevents the hard {@code NoSuchMethodError} at class verification/link time while
 * keeping 2.9 behaviour unchanged.
 *
 * <p>Complex removed members (getDrops, clearInventory, getProxy, etc.) are NOT
 * faked: their signatures reference AE2 / NonNullList types that 2.9 no longer
 * exposes the same way, and a stub would silently break item drops. Those addons
 * must be recompiled (documented in README).
 */
public class MetaTileEntityBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/metatileentity/MetaTileEntity";

    /** {@code name + desc -> return opcode payload}. */
    private static final String[][] VOID_ZERO_METHODS = {
            // 2.8 only; 2.9 removed. Comparator output is unused on 2.9.
            {"getComparatorValue", "()I"},
            {"getActualComparatorValue", "()I"},
            {"getCableConnectionType", "(Lappeng/api/util/AEPartLocation;)Lappeng/api/util/AECableType;"},
            {"getProxy", "()Lappeng/me/helpers/AENetworkProxy;"},
            {"gridChanged", "()V"},
            {"allowTickAcceleration", "()Z"}
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
            System.err.println("[GTCEuCompat] MetaTileEntity bridge failed: " + t);
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

        int added = 0;
        for (String[] m : VOID_ZERO_METHODS) {
            String name = m[0], desc = m[1];
            if (present.contains(name + desc)) {
                continue;
            }
            MethodNode mn = new MethodNode(ACC_PUBLIC, name, desc, null, null);
            InsnList il = mn.instructions;
            char ret = desc.charAt(desc.indexOf(')') + 1);
            switch (ret) {
                case 'V':
                    il.add(new InsnNode(RETURN));
                    break;
                case 'I':
                case 'Z':
                    il.add(new InsnNode(ICONST_0));
                    il.add(new InsnNode(IRETURN));
                    break;
                case 'L':
                    il.add(new InsnNode(ACONST_NULL));
                    il.add(new InsnNode(ARETURN));
                    break;
                default:
                    il.add(new InsnNode(ACONST_NULL));
                    il.add(new InsnNode(ARETURN));
                    break;
            }
            cn.methods.add(mn);
            present.add(name + desc);
            added++;
        }

        if (added == 0) {
            return basicClass;
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Restored " + added + " MetaTileEntity 2.8 fallback method(s).");
        return cw.toByteArray();
    }
}
