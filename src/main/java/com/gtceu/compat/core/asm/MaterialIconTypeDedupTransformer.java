package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Fixes the "MaterialIconType molten already registered!" startup crash.
 *
 * <p>GTQT 2.9 core already defines {@code MaterialIconType.molten}, but
 * Gregicality Multiblocks (GCYM) still re-registers its own
 * {@code new MaterialIconType("molten")}, which trips the duplicate-name check.
 *
 * <p>This transformer rewrites {@code GCYMMaterialIconTypes}' static initializer
 * from:
 * <pre>  new MaterialIconType("molten")</pre>
 * to:
 * <pre>  getstatic MaterialIconType.molten</pre>
 * so GCYM reuses the core's already-registered instance instead of creating a
 * duplicate. This is behaviourally identical for all consumers.
 */
public class MaterialIconTypeDedupTransformer implements IClassTransformer {

    private static final String TARGET = "gregicality/multiblocks/api/unification/material/GCYMMaterialIconTypes";

    private static final String MIT_OWNER = "gregtech/api/unification/material/info/MaterialIconType";
    private static final String MIT_DESC = "Lgregtech/api/unification/material/info/MaterialIconType;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return rewrite(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] MaterialIconType dedup failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] rewrite(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        int changed = 0;
        for (MethodNode mn : cn.methods) {
            if (!"<clinit>".equals(mn.name)) continue;
            InsnList il = mn.instructions;
            for (AbstractInsnNode insn = il.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                // Pattern: NEW MaterialIconType ; DUP ; LDC "molten" ; INVOKESPECIAL <init>(String)V
                if (insn instanceof TypeInsnNode && insn.getOpcode() == Opcodes.NEW
                        && MIT_OWNER.equals(((TypeInsnNode) insn).desc)
                        && next != null && next.getOpcode() == Opcodes.DUP) {
                    AbstractInsnNode ldc = next.getNext();
                    AbstractInsnNode init = ldc == null ? null : ldc.getNext();
                    if (ldc instanceof LdcInsnNode && "molten".equals(((LdcInsnNode) ldc).cst)
                            && init instanceof MethodInsnNode
                            && init.getOpcode() == Opcodes.INVOKESPECIAL
                            && "<init>".equals(((MethodInsnNode) init).name)) {
                        // Replace NEW+DUP+LDC+INVOKESPECIAL with a single GETSTATIC.
                        FieldInsnNode getStatic = new FieldInsnNode(
                                Opcodes.GETSTATIC, MIT_OWNER, "molten", MIT_DESC);
                        il.insertBefore(insn, getStatic);
                        il.remove(insn);          // NEW
                        il.remove(next);          // DUP
                        il.remove(ldc);           // LDC
                        il.remove(init);          // INVOKESPECIAL
                        changed++;
                    }
                }
                insn = next;
            }
        }

        if (changed == 0) {
            System.out.println("[GTCEuCompat] GCYM molten dedup: no matching pattern (skipped).");
            return basicClass;
        }

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] GCYM molten dedup: rewrote " + changed + " registration(s) to reuse core instance.");
        return cw.toByteArray();
    }
}
