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

import java.util.List;

/**
 * Injects a public {@code Element(long, long, long, String, String, String, boolean)}
 * constructor into GTQT 2.9's {@code gregtech.api.unification.Element}, which GTExpert's
 * {@code GTEElements} calls but which was removed (the remaining constructor takes a
 * {@code double} half-life and is {@code protected}).
 *
 * <p>The injected constructor converts the third {@code long} argument to a
 * {@code double} and delegates to the existing constructor:
 * {@code this(p0, p1, (double) p2, p3, p4, p5, p6)}.
 *
 * <p>No-op if the constructor already exists.
 */
public class ElementCtorCompatTransformer implements IClassTransformer {

    public static final String TARGET_INTERNAL = "gregtech/api/unification/Element";

    private static final String INIT = "<init>";
    private static final String DESC_LONG =
            "(JJJLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V";
    private static final String DESC_DOUBLE =
            "(JJDLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET_INTERNAL.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Failed to transform Element: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        List<MethodNode> methods = cn.methods;
        for (MethodNode mn : methods) {
            if (INIT.equals(mn.name) && DESC_LONG.equals(mn.desc)) {
                System.out.println("[GTCEuCompat] Element(long,long,long,...) ctor already present, skipping.");
                return basicClass;
            }
        }
        boolean delegateFound = false;
        for (MethodNode mn : methods) {
            if (INIT.equals(mn.name) && DESC_DOUBLE.equals(mn.desc)) {
                delegateFound = true;
                break;
            }
        }
        if (!delegateFound) {
            System.err.println("[GTCEuCompat] Element(long,long,double,...) ctor not found; cannot add shim.");
            return basicClass;
        }

        MethodNode shim = new MethodNode(
                Opcodes.ACC_PUBLIC, INIT, DESC_LONG, null, null);
        InsnList il = shim.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));  // this
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));  // protons
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));  // neutrons
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));  // halfLife (long)
        il.add(new InsnNode(Opcodes.L2D));          // -> double
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));  // decayTo
        il.add(new VarInsnNode(Opcodes.ALOAD, 8));  // name
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));  // symbol
        il.add(new VarInsnNode(Opcodes.ILOAD, 10)); // isIsotope
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET_INTERNAL, INIT, DESC_DOUBLE, false));
        il.add(new InsnNode(Opcodes.RETURN));
        shim.maxStack = 12;
        shim.maxLocals = 11;
        methods.add(shim);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Injected public Element(long,long,long,...) constructor.");
        return cw.toByteArray();
    }
}
