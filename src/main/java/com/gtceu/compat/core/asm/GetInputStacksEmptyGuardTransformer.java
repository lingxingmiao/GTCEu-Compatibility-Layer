package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Guards 2.8 addon call sites that index {@code getInputStacks()[0]} after only a
 * null check.
 *
 * <p>GTCEu 2.8 contract: {@code GTRecipeInput.getInputStacks()} returned either a
 * non-empty array or {@code null}, so addons wrote
 *
 * <pre>
 *   ItemStack[] stacks = input.getInputStacks();
 *   if (stacks != null) {                     // 2.8 was sufficient
 *       ... stacks[0] ...
 *   }
 * </pre>
 *
 * <p>GTQT 2.9 can return a <b>zero-length</b> array instead of null (e.g.
 * {@code GTRecipeOreInput} whose ore dictionary resolves to nothing), so the null
 * check passes and {@code stacks[0]} throws
 * {@code ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0},
 * aborting the addon's whole recipe pass — which is why GTCEFuContent's machine
 * recipes went missing.
 *
 * <p>Note: normalising the getter itself (empty -> null) is NOT viable, because
 * 2.9's own {@code GTRecipeOreInput.acceptsStack} dereferences the array without a
 * null check. So the guard is installed at the addon call sites instead: right
 * after the {@code getInputStacks()} call, a zero length is redirected to the same
 * branch the 2.8 {@code null} case already took.
 *
 * <p>Scoped to addons known to use this 2.8 idiom.
 */
public class GetInputStacksEmptyGuardTransformer implements IClassTransformer, Opcodes {

    private static final String GET_DESC = "()[Lnet/minecraft/item/ItemStack;";

    /** Addon packages whose getInputStacks() call sites get the empty-array guard. */
    private static final String[] PREFIXES = {
            "com/m_w_k/gtcefucontent/",
            "com/github/gtexpert/",
            "com/zorbatron/zbgt/",
            "gregicality/multiblocks/",
            "kono/ceu/gtconsolidate/",
            "kono/ceu/materialreplication/",
            "gregtechfoodoption/",
            "keqing/gtqt/prismplan/"
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) return basicClass;
        String internal = transformedName.replace('.', '/');

        boolean wanted = false;
        for (String p : PREFIXES) {
            if (internal.startsWith(p)) { wanted = true; break; }
        }
        if (!wanted) return basicClass;

        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] getInputStacks guard failed for "
                    + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    /** Skips LabelNode / LineNumberNode / FrameNode (opcode -1) pseudo-instructions. */
    private static AbstractInsnNode nextReal(AbstractInsnNode n) {
        while (n != null && n.getOpcode() < 0) {
            n = n.getNext();
        }
        return n;
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        int patched = 0;
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null || mn.instructions.size() == 0) continue;
            List<InsnList> inserts = new ArrayList<>();
            List<AbstractInsnNode> calls = new ArrayList<>();
            for (AbstractInsnNode insn : mn.instructions) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode m = (MethodInsnNode) insn;
                if (!"getInputStacks".equals(m.name) || !GET_DESC.equals(m.desc)) continue;

                // Only patch the "store then null-check" idiom. Real bytecode has
                // Label/LineNumber pseudo-nodes interleaved (opcode -1), so skip
                // those when matching.
                AbstractInsnNode a = nextReal(m.getNext());
                if (a == null || a.getOpcode() != ASTORE) continue;
                int slot = ((VarInsnNode) a).var;
                AbstractInsnNode b = nextReal(a.getNext());
                if (b == null || b.getOpcode() != ALOAD || ((VarInsnNode) b).var != slot) continue;
                AbstractInsnNode c = nextReal(b.getNext());
                if (c == null || c.getOpcode() != IFNULL) continue;

                // Original:  ALOAD n ; IFNULL skip      (skip expects an empty stack)
                // Becomes:   ALOAD n ; DUP ; ARRAYLENGTH ; IFNE ok ; POP ; GOTO skip ; ok:
                //            IFNULL skip
                // The empty case pops the duplicate then jumps, so the stack at
                // `skip` matches the null case exactly (nothing pushed).
                InsnList seq = new InsnList();
                LabelNode skip = ((JumpInsnNode) c).label;
                LabelNode ok = new LabelNode();
                seq.add(new InsnNode(DUP));
                seq.add(new InsnNode(ARRAYLENGTH));
                seq.add(new JumpInsnNode(IFNE, ok));
                seq.add(new InsnNode(POP));
                seq.add(new JumpInsnNode(GOTO, skip));
                seq.add(ok);
                inserts.add(seq);
                calls.add(c);
                patched++;
            }
            for (int i = 0; i < calls.size() && i < inserts.size(); i++) {
                mn.instructions.insertBefore(calls.get(i), inserts.get(i));
            }
        }
        if (patched == 0) return basicClass;

        // Branching instructions were added, so frames must be recomputed.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] " + cn.name
                + ": guarded " + patched + " getInputStacks()[0] site(s) against empty arrays.");
        return cw.toByteArray();
    }
}
