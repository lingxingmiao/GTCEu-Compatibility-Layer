package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;


/**
 * Relaxes GTQT 2.9's "Cannot apply Category with incompatible RecipeMap" hard
 * failure so 2.8 addons can register recipes into a map via a category that was
 * resolved from a different (but equivalent) recipe map instance.
 *
 * <h3>2.9 check (RecipeBuilder.validate)</h3>
 * <pre>
 * if (category != null &amp;&amp; category.getRecipeMap() != this.recipeMap) {
 *     GTLog.logger.error("Cannot apply Category with incompatible RecipeMap", new Throwable());
 *     recipeStatus = EnumValidationResult.INVALID;    // recipe is thrown away
 * }
 * </pre>
 * Note the comparison is reference identity ({@code if_acmpeq}).
 *
 * <h3>Why it breaks 2.8 addons</h3>
 * GCYM's {@code ManualABSRecipes.registerBinaryAlloy} and GTCEFuContent's
 * {@code LinearForgingFurnaceLoader} look a category up by name
 * ({@code GTRecipeCategory.getByName(map.getUnlocalizedName())}) and attach it to a
 * builder targeting their own map object. When that name resolves to a category
 * created for a different map instance, the identity check fails, the recipe is
 * marked INVALID and silently dropped — observed as 20 rejected
 * {@code alloy_blast_smelter} recipes and 629 rejected
 * {@code lff_blast_forging_cooled} recipes ("Recipe outputs are empty").
 *
 * <h3>Fix</h3>
 * Downgrade the hard failure to a warning: keep the category on the builder (so
 * sorting/grouping still works) but do not mark the recipe INVALID. A genuinely
 * mis-targeted recipe still fails its own output/input validation, so this does
 * not mask real authoring mistakes.
 */
public class CategoryMapCompatTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/RecipeBuilder";
    private static final String MSG = "Cannot apply Category with incompatible RecipeMap";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] category/map compat failed: " + t);
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
            // find the LDC of our message, then drop the INVALID assignment that follows
            for (AbstractInsnNode insn : mn.instructions) {
                if (!(insn instanceof LdcInsnNode)) continue;
                if (!MSG.equals(((LdcInsnNode) insn).cst)) continue;

                // Walk forward past the logger.error(...) call and neutralise the
                // "recipeStatus = INVALID" store that follows it.
                AbstractInsnNode cur = insn;
                for (int guard = 0; guard < 40 && cur != null; guard++, cur = cur.getNext()) {
                    if (!(cur instanceof FieldInsnNode)) continue;
                    FieldInsnNode f = (FieldInsnNode) cur;
                    if (f.getOpcode() != PUTFIELD) continue;
                    if (!"recipeStatus".equals(f.name)) continue;
                    if (!"Lgregtech/api/util/EnumValidationResult;".equals(f.desc)) continue;

                    // The store looks like:  ALOAD 0 ; GETSTATIC INVALID ; PUTFIELD recipeStatus
                    // Keep the field assignment but write VALID instead of INVALID, so the
                    // stack stays balanced (same instruction count, only the enum changes)
                    // and the recipe is not discarded.
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
        System.out.println("[GTCEuCompat] RecipeBuilder.validate: incompatible-category check"
                + " downgraded (" + patched + " site(s)); recipe no longer discarded.");
        return cw.toByteArray();
    }
}
