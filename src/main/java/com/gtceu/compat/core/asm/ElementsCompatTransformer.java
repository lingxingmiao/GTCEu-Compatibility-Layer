package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Injects compatibility overloads into GTCEu's {@code gregtech.api.unification.Elements}
 * that were added in later GTCEu builds but are missing in GTQT 2.9.0.
 *
 * <p>Currently adds:
 * <pre>{@code
 * public static Element add(long protons, long neutrons, long halfLifeNanos,
 *                           String decayTo, String name, String symbol, boolean isIsotope)
 * }
 * </pre>
 * which delegates to the existing
 * {@code add(long, long, double, String, String, String, boolean)} overload by
 * converting the third {@code long} argument to a {@code double}. This matches the
 * signature that GTCEFuContent 2.1.1-alpha calls.
 *
 * <p>The transform is a no-op if the overload already exists, so it is safe on
 * GTCEu versions that already ship it.
 */
public class ElementsCompatTransformer implements IClassTransformer {

    public static final String TARGET_INTERNAL = "gregtech/api/unification/Elements";

    private static final String ADD_NAME = "add";
    private static final String ADD_DESC_LONG =
            "(JJJLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)Lgregtech/api/unification/Element;";
    private static final String ADD_DESC_DOUBLE =
            "(JJDLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)Lgregtech/api/unification/Element;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET_INTERNAL.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return injectLongOverload(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Failed to transform Elements: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] injectLongOverload(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        // Already present on newer GTCEu builds -> nothing to do.
        List<MethodNode> methods = cn.methods;
        for (MethodNode mn : methods) {
            if (ADD_NAME.equals(mn.name) && ADD_DESC_LONG.equals(mn.desc)) {
                System.out.println("[GTCEuCompat] Elements.add(long,long,long,...) already present, skipping.");
                return basicClass;
            }
        }

        // The target overload we delegate to must exist (it does in GTQT 2.9.0).
        boolean targetFound = false;
        for (MethodNode mn : methods) {
            if (ADD_NAME.equals(mn.name) && ADD_DESC_DOUBLE.equals(mn.desc)
                    && (mn.access & Opcodes.ACC_STATIC) != 0) {
                targetFound = true;
                break;
            }
        }
        if (!targetFound) {
            System.err.println("[GTCEuCompat] Elements.add(long,long,double,...) not found; cannot add shim.");
            return basicClass;
        }

        MethodNode shim = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                ADD_NAME,
                ADD_DESC_LONG,
                null,
                null);
        InsnList il = shim.instructions;
        // return add(p0, p1, (double) p2, p3, p4, p5, p6);
        il.add(new VarInsnNode(Opcodes.LLOAD, 0));   // protons
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));   // neutrons
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));   // halfLifeNanos
        il.add(new InsnNode(Opcodes.L2D));           // widen to double halfLifeSeconds
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));   // decayTo
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));   // name
        il.add(new VarInsnNode(Opcodes.ALOAD, 8));   // symbol
        il.add(new VarInsnNode(Opcodes.ILOAD, 9));   // isIsotope
        il.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                TARGET_INTERNAL,
                ADD_NAME,
                ADD_DESC_DOUBLE,
                false));
        il.add(new InsnNode(Opcodes.ARETURN));
        shim.maxStack = 11;
        shim.maxLocals = 10;
        methods.add(shim);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] out = cw.toByteArray();
        System.out.println("[GTCEuCompat] Injected Elements.add(long,long,long,...) compatibility overload.");
        return out;
    }

    /**
     * Kept for clarity/debugging. The shim body never throws, so this is unused.
     */
    @SuppressWarnings("unused")
    private static AbstractInsnNode firstInsn(InsnList list) {
        return list.size() == 0 ? null : list.getFirst();
    }
}
