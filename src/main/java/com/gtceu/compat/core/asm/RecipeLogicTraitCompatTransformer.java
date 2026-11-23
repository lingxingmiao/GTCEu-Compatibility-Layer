package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Keeps GTQT 2.9's JEI/HEI plugin from aborting on 2.8 addon machines.
 *
 * <p>2.9's {@code MetaTileEntity.getRecipeLogic()} does:
 *
 * <pre>
 * MTETrait t = getMTETrait("RecipeMapWorkable");
 * if (t instanceof AbstractRecipeLogic) return (AbstractRecipeLogic) t;
 * if (t == null) goto returnNull;
 * throw new IllegalStateException("MTE Trait RecipeMapWorkable ... is not
 *     instanceof AbstractRecipeLogic");      // &lt;-- kills the whole JEI plugin
 * returnNull: return null;
 * </pre>
 *
 * <p>GTCEu 2.8 addons define their own {@code RecipeMapWorkable} MTETrait (the 2.8
 * recipe logic), which is not a 2.9 {@code AbstractRecipeLogic}. When HEI registers
 * the GT plugin it enumerates every MTE through {@code getRecipeMap()} →
 * {@code getRecipeLogic()}, hits a legacy trait, and the *whole plugin* fails:
 *
 * <pre>
 * Failed to register mod plugin: gregtech.integration.jei.JustEnoughItemsModule
 * IllegalStateException: MTE Trait RecipeMapWorkable ... is not instanceof AbstractRecipeLogic
 * </pre>
 *
 * <p>Visible symptom: recipes appear in HEI but with no machine column, because
 * GT's machine categories were never registered.
 *
 * <p>Fix: turn the {@code IFNULL returnNull} into {@code GOTO returnNull}, so any
 * incompatible legacy trait returns null (same as "no recipe logic") instead of
 * throwing. This is a single opcode change: no new locals, no stack-shape change,
 * no new frames — and it avoids the {@code ClassCircularityError} that
 * {@code COMPUTE_FRAMES} triggers while transforming this very class.
 */
public class RecipeLogicTraitCompatTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/metatileentity/MetaTileEntity";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] recipe-logic trait compat failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        int patched = 0;
        for (MethodNode mn : cn.methods) {
            if (!"getRecipeLogic".equals(mn.name)) continue;
            if (mn.instructions == null) continue;

            InsnList il = mn.instructions;
            // Find: INSTANCEOF AbstractRecipeLogic ; IFEQ <throwBranch>
            // then within the throw branch the IFNULL that jumps to the return-null
            // tail. Turn that IFNULL into GOTO.
            for (int i = 0; i < il.size(); i++) {
                AbstractInsnNode insn = il.get(i);
                if (insn.getOpcode() != INSTANCEOF) continue;
                if (!"gregtech/api/capability/impl/AbstractRecipeLogic".equals(((TypeInsnNode) insn).desc)) continue;
                AbstractInsnNode ifEq = nextOp(insn, IFEQ);
                if (ifEq == null || !(ifEq instanceof JumpInsnNode)) continue;
                LabelNode throwStart = ((JumpInsnNode) ifEq).label;

                // Walk from the throw branch start until the IFNULL to the same
                // label that the trailing ACONST_NULL/ARETURN uses.
                AbstractInsnNode cur = throwStart;
                for (int guard = 0; guard < 12 && cur != null; guard++, cur = cur.getNext()) {
                    if (cur.getOpcode() == IFNULL) {
                        ((JumpInsnNode) cur).setOpcode(GOTO);
                        patched++;
                        break;
                    }
                }
                break;
            }
        }
        if (patched == 0) return basicClass;

        // No instruction was added/removed, only IFNULL -> GOTO (same length),
        // so the existing frames remain valid: COMPUTE_MAXS is enough and no
        // class loading is needed (COMPUTE_FRAMES would load this very class).
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] MetaTileEntity.getRecipeLogic: legacy"
                + " RecipeMapWorkable trait returns null instead of aborting JEI/HEI.");
        return cw.toByteArray();
    }

    private static AbstractInsnNode nextOp(AbstractInsnNode from, int opcode) {
        AbstractInsnNode n = from.getNext();
        while (n != null && n.getOpcode() < 0) n = n.getNext();
        return n != null && n.getOpcode() == opcode ? n : null;
    }
}
