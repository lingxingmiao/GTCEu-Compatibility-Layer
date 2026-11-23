package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.List;

/**
 * Relaxes GTQT 2.9-only hard validations in {@code RecipeBuilder.validate} that
 * GTCEu 2.8 did not enforce.
 *
 * <p>2.9 added these checks, each followed by
 * {@code recipeStatus = EnumValidationResult.INVALID} which throws the whole recipe
 * away:
 * <ul>
 *   <li>{@code EU/t cannot be equal to 0}</li>
 *   <li>{@code Duration cannot be less or equal to 0}</li>
 * </ul>
 *
 * <p>GTCEu 2.8 had neither check. 2.8 addons (GTFO's
 * {@code ElectricBakingOvenRecipeBuilder}, whose recipes carry no EU/t) therefore
 * work on 2.8 but lose every recipe on 2.9 — observed as 26 rejected
 * {@code electric_baking_oven} recipes.
 *
 * <p>Fix: for the listed messages only, rewrite the immediately following
 * {@code INVALID} store into {@code VALID}. Other validations (chance range,
 * category/map compatibility, …) are untouched, so genuine authoring errors are
 * still rejected.
 */
public class RecipeValidationCompatTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/RecipeBuilder";
    private static final String BLAST = "gregtech/api/recipes/builders/BlastRecipeBuilder";
    private static final List<String> RELAX = Arrays.asList(
            "EU/t cannot be equal to 0",
            "Duration cannot be less or equal to 0",
            "Blast Furnace Temperature cannot be less than or equal to 0"
    );
    private static final String[] TARGETS = { TARGET, BLAST };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) return basicClass;
        String internal = transformedName.replace('.', '/');
        boolean wanted = false;
        for (String s : TARGETS) {
            if (s.equals(internal)) { wanted = true; break; }
        }
        if (!wanted) return basicClass;
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] recipe validation compat failed: " + t);
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
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn : mn.instructions) {
                if (!(insn instanceof LdcInsnNode)) continue;
                Object cst = ((LdcInsnNode) insn).cst;
                if (!(cst instanceof String) || !RELAX.contains(cst)) continue;

                // Walk forward until the recipeStatus = INVALID store (bounded).
                AbstractInsnNode cur = insn;
                for (int guard = 0; guard < 24 && cur != null; guard++, cur = cur.getNext()) {
                    if (!(cur instanceof FieldInsnNode)) continue;
                    FieldInsnNode f = (FieldInsnNode) cur;
                    if (f.getOpcode() != PUTFIELD || !"recipeStatus".equals(f.name)) continue;

                    AbstractInsnNode prev = f.getPrevious();
                    while (prev != null && prev.getOpcode() < 0) prev = prev.getPrevious();
                    if (prev instanceof FieldInsnNode
                            && prev.getOpcode() == GETSTATIC
                            && "INVALID".equals(((FieldInsnNode) prev).name)
                            && "Lgregtech/api/util/EnumValidationResult;".equals(((FieldInsnNode) prev).desc)) {
                        mn.instructions.set(prev, new FieldInsnNode(GETSTATIC,
                                "gregtech/api/util/EnumValidationResult", "VALID",
                                "Lgregtech/api/util/EnumValidationResult;"));
                        patched++;
                    }
                    break;
                }
            }
        }
        if (patched == 0) return basicClass;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] RecipeBuilder.validate: relaxed " + patched
                + " 2.9-only check(s) (EUt=0 / Duration<=0).");
        return cw.toByteArray();
    }
}
