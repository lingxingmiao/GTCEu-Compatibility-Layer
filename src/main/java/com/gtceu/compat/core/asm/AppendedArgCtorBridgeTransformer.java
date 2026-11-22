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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Table-driven bridges for GTCEu 2.8 constructors that GTQT 2.9 extended by
 * appending ONE trailing primitive/enum argument.
 *
 * <p>Every row: target internal class -> { old 2.8 ctor desc, new 2.9 ctor desc,
 * opcodes that push the appended default value }. The bridge loads all original
 * arguments, pushes the default, then INVOKESPECIAL the 2.9 constructor.
 *
 * <p>Only "append one trailing argument" shapes are supported; more complex
 * changes go into dedicated transformers. Each bridge is injected only when the
 * old ctor is absent AND the new one exists, so it is a no-op on a real 2.8 host.
 */
public class AppendedArgCtorBridgeTransformer implements IClassTransformer, Opcodes {

    /** target -> rows of {oldDesc, newDesc, appendedOpcodes} */
    private static final Map<String, String[][]> CTORS = new LinkedHashMap<>();

    /** Sentinel: push double 1.0 (LdcInsnNode, not a bare opcode). */
    private static final int LDC_ONE_D = -1001;

    private static void add(String target, String oldDesc, String newDesc, int... appendInsns) {
        // encode appendInsns as a comma-separated string in the last slot
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < appendInsns.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(appendInsns[i]);
        }
        CTORS.computeIfAbsent(target, k -> new String[0][]);
        String[][] rows = CTORS.get(target);
        String[][] grown = new String[rows.length + 1][];
        System.arraycopy(rows, 0, grown, 0, rows.length);
        grown[rows.length] = new String[]{oldDesc, newDesc, sb.toString()};
        CTORS.put(target, grown);
    }

    static {
        // SimpleGeneratorMetaTileEntity: 2.9 appended `double efficiency`.
        // 2.8 had no efficiency concept; 1.0 = 100% (matches the 2.9 default usage).
        String SGM = "gregtech/api/metatileentity/SimpleGeneratorMetaTileEntity";
        String RL = "Lnet/minecraft/util/ResourceLocation;";
        String RM = "Lgregtech/api/recipes/RecipeMap;";
        String IC = "Lgregtech/client/renderer/ICubeRenderer;";
        String FN = "Ljava/util/function/Function;";
        add(SGM,
                "(" + RL + RM + IC + "I" + FN + ")V",
                "(" + RL + RM + IC + "I" + FN + "D)V",
                LDC_ONE_D); // handled specially below (needs LdcInsnNode, not a plain opcode)
        add(SGM,
                "(" + RL + RM + IC + "I" + FN + "Z)V",
                "(" + RL + RM + IC + "I" + FN + "ZD)V",
                LDC_ONE_D);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) return basicClass;
        String internal = transformedName.replace('.', '/');
        String[][] rows = CTORS.get(internal);
        if (rows == null) return basicClass;
        try {
            return inject(internal, rows, basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Appended-arg ctor bridge failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(String internal, String[][] rows, byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        java.util.Set<String> present = new java.util.HashSet<>();
        for (MethodNode mn : cn.methods) present.add(mn.name + mn.desc);

        int added = 0;
        for (String[] row : rows) {
            String oldDesc = row[0], newDesc = row[1];
            if (present.contains("<init>" + oldDesc) || !present.contains("<init>" + newDesc)) continue;

            MethodNode mn = new MethodNode(ACC_PUBLIC, "<init>", oldDesc, null, null);
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(ALOAD, 0));
            org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(oldDesc);
            int slot = 1;
            for (org.objectweb.asm.Type t : args) {
                il.add(new VarInsnNode(t.getOpcode(ILOAD), slot));
                slot += t.getSize();
            }
            // appended default(s)
            String enc = row[2];
            if (enc != null && !enc.isEmpty()) {
                for (String token : enc.split(",")) {
                    int op = Integer.parseInt(token.trim());
                    if (op == LDC_ONE_D) {
                        il.add(new org.objectweb.asm.tree.LdcInsnNode(Double.valueOf(1.0D)));
                    } else {
                        il.add(new InsnNode(op));
                    }
                }
            }
            il.add(new MethodInsnNode(INVOKESPECIAL, internal, "<init>", newDesc, false));
            il.add(new InsnNode(RETURN));
            cn.methods.add(mn);
            present.add("<init>" + oldDesc);
            added++;
        }
        if (added == 0) return basicClass;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Injected " + added + " appended-arg ctor bridge(s) on " + internal);
        return cw.toByteArray();
    }
}
