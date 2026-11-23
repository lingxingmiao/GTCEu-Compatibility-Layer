package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Fixes HEI showing a recipe without its machine for 2.8 addons.
 *
 * <p>2.8 addons pass a category into {@code RecipeBuilder.category(...)} based on
 * the recipe map's unlocalised name. In GTQT 2.9 categories are bound to a
 * specific {@code RecipeMap} instance, and JEI/HEI resolves the "which machine"
 * icon through {@code category.getRecipeMap()}. When the addon's category belongs
 * to a different map instance than the builder's own map, HEI cannot match the
 * recipe to a machine and shows the recipe with no machine column.
 *
 * <p>2.9 itself rejects this mismatch in {@code RecipeBuilder.validate}
 * ("Cannot apply Category with incompatible RecipeMap"), which previously caused
 * the recipe to be discarded. Relaxing that check kept the recipe but left the
 * wrong category attached — hence recipes visible but without machines.
 *
 * <p>Fix: at the head of {@code category(GTRecipeCategory)}, if the incoming
 * category's recipe map is not the builder's recipe map (identity compare, same
 * rule 2.9 uses), replace the argument with
 * {@code this.recipeMap.getPrimaryRecipeCategory()}. The category then always
 * points at the correct machine, so HEI displays it.
 *
 * <pre>
 * public R category(GTRecipeCategory cat) {
 *     if (cat != null &amp;&amp; this.recipeMap != null &amp;&amp;
 *         cat.getRecipeMap() != this.recipeMap) {
 *         cat = this.recipeMap.getPrimaryRecipeCategory();
 *     }
 *     this.category = cat;   // original body
 *     return this;
 * }
 * </pre>
 */
public class CategoryMapCompatTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/RecipeBuilder";
    private static final String CAT = "Lgregtech/api/recipes/category/GTRecipeCategory;";
    private static final String DESC = "(" + CAT + ")Lgregtech/api/recipes/RecipeBuilder;";

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

        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            if ("category".equals(mn.name) && DESC.equals(mn.desc)) { target = mn; break; }
        }
        if (target == null || target.instructions == null) return basicClass;

        // Guard against double transform.
        for (AbstractInsnNode insn : target.instructions) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode m = (MethodInsnNode) insn;
                if ("getPrimaryRecipeCategory".equals(m.name)) return basicClass;
            }
        }

        // if (cat == null) -> keep; if (this.recipeMap == null) -> keep
        // if (cat.getRecipeMap() != this.recipeMap) {
        //     cat = this.recipeMap.getPrimaryRecipeCategory();
        // }
        InsnList pre = new InsnList();
        LabelNode keep = new LabelNode();
        pre.add(new VarInsnNode(ALOAD, 1));
        pre.add(new JumpInsnNode(IFNULL, keep));
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new FieldInsnNode(GETFIELD, TARGET, "recipeMap", "Lgregtech/api/recipes/RecipeMap;"));
        pre.add(new JumpInsnNode(IFNULL, keep));
        // cat.getRecipeMap()
        pre.add(new VarInsnNode(ALOAD, 1));
        pre.add(new MethodInsnNode(INVOKEVIRTUAL,
                "gregtech/api/recipes/category/GTRecipeCategory", "getRecipeMap",
                "()Lgregtech/api/recipes/RecipeMap;", false));
        // this.recipeMap
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new FieldInsnNode(GETFIELD, TARGET, "recipeMap", "Lgregtech/api/recipes/RecipeMap;"));
        pre.add(new JumpInsnNode(IF_ACMPEQ, keep));     // already compatible
        // cat = this.recipeMap.getPrimaryRecipeCategory();
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new FieldInsnNode(GETFIELD, TARGET, "recipeMap", "Lgregtech/api/recipes/RecipeMap;"));
        pre.add(new MethodInsnNode(INVOKEVIRTUAL, "gregtech/api/recipes/RecipeMap",
                "getPrimaryRecipeCategory", "()Lgregtech/api/recipes/category/GTRecipeCategory;", false));
        pre.add(new VarInsnNode(ASTORE, 1));
        pre.add(keep);

        target.instructions.insertBefore(target.instructions.getFirst(), pre);

        // Second path: RecipeBuilder(Recipe, RecipeMap) copies the source recipe's
        // category verbatim (bci ~193) without checking it against the new map.
        // GCYM ManualABSRecipes relies on this to move a recipe between maps, so
        // the copied category belongs to the old map -> HEI cannot match machines.
        // Insert, just before the ctor's RETURN:
        //   if (this.category != null && this.recipeMap != null &&
        //       this.category.getRecipeMap() != this.recipeMap)
        //       this.category = this.recipeMap.getPrimaryRecipeCategory();
        for (MethodNode mn : cn.methods) {
            if (!"<init>".equals(mn.name)) continue;
            if (!"(Lgregtech/api/recipes/Recipe;Lgregtech/api/recipes/RecipeMap;)V".equals(mn.desc)
                    && !("(Lgregtech/api/recipes/Recipe;Lgregtech/api/recipes/RecipeMap;" +
                    "Lgregtech/api/recipes/category/GTRecipeCategory;)V").equals(mn.desc)) {
                continue;
            }
            if (mn.instructions == null || mn.instructions.size() < 2) continue;
            boolean already = false;
            for (AbstractInsnNode i : mn.instructions) {
                if (i instanceof MethodInsnNode
                        && "getPrimaryRecipeCategory".equals(((MethodInsnNode) i).name)) already = true;
            }
            if (already) continue;

            InsnList tail = new InsnList();
            LabelNode ok = new LabelNode();
            tail.add(new VarInsnNode(ALOAD, 0));
            tail.add(new FieldInsnNode(GETFIELD, TARGET, "category", CAT));
            tail.add(new JumpInsnNode(IFNULL, ok));
            tail.add(new VarInsnNode(ALOAD, 0));
            tail.add(new FieldInsnNode(GETFIELD, TARGET, "recipeMap", "Lgregtech/api/recipes/RecipeMap;"));
            tail.add(new JumpInsnNode(IFNULL, ok));
            tail.add(new VarInsnNode(ALOAD, 0));
            tail.add(new FieldInsnNode(GETFIELD, TARGET, "category", CAT));
            tail.add(new MethodInsnNode(INVOKEVIRTUAL,
                    "gregtech/api/recipes/category/GTRecipeCategory", "getRecipeMap",
                    "()Lgregtech/api/recipes/RecipeMap;", false));
            tail.add(new VarInsnNode(ALOAD, 0));
            tail.add(new FieldInsnNode(GETFIELD, TARGET, "recipeMap", "Lgregtech/api/recipes/RecipeMap;"));
            tail.add(new JumpInsnNode(IF_ACMPEQ, ok));
            tail.add(new VarInsnNode(ALOAD, 0));
            tail.add(new VarInsnNode(ALOAD, 0));
            tail.add(new FieldInsnNode(GETFIELD, TARGET, "recipeMap", "Lgregtech/api/recipes/RecipeMap;"));
            tail.add(new MethodInsnNode(INVOKEVIRTUAL, "gregtech/api/recipes/RecipeMap",
                    "getPrimaryRecipeCategory", "()Lgregtech/api/recipes/category/GTRecipeCategory;", false));
            tail.add(new FieldInsnNode(PUTFIELD, TARGET, "category", CAT));
            tail.add(ok);

            // Insert before the final RETURN.
            AbstractInsnNode last = mn.instructions.getLast();
            while (last != null && last.getOpcode() < 0) last = last.getPrevious();
            if (last != null && last.getOpcode() == RETURN) {
                mn.instructions.insertBefore(last, tail);
                System.out.println("[GTCEuCompat] RecipeBuilder(Recipe,Map) ctor: copied category"
                        + " normalised to the target map's primary category.");
            }
        }

        // Also downgrade the hard check in validate(): some addons attach a
        // category by yet another route (e.g. a constructor taking the category
        // directly), which the two normalisations above cannot catch. Keeping the
        // recipe is essential even if HEI then falls back to the map's default.
        int relaxed = 0;
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn : mn.instructions) {
                if (!(insn instanceof LdcInsnNode)) continue;
                if (!"Cannot apply Category with incompatible RecipeMap".equals(((LdcInsnNode) insn).cst)) continue;
                AbstractInsnNode cur = insn;
                for (int guard = 0; guard < 24 && cur != null; guard++, cur = cur.getNext()) {
                    if (!(cur instanceof FieldInsnNode)) continue;
                    FieldInsnNode f = (FieldInsnNode) cur;
                    if (f.getOpcode() != PUTFIELD || !"recipeStatus".equals(f.name)) continue;
                    AbstractInsnNode prev = f.getPrevious();
                    while (prev != null && prev.getOpcode() < 0) prev = prev.getPrevious();
                    if (prev instanceof FieldInsnNode && prev.getOpcode() == GETSTATIC
                            && "INVALID".equals(((FieldInsnNode) prev).name)) {
                        mn.instructions.set(prev, new FieldInsnNode(GETSTATIC,
                                "gregtech/api/util/EnumValidationResult", "VALID",
                                "Lgregtech/api/util/EnumValidationResult;"));
                        relaxed++;
                    }
                    break;
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] RecipeBuilder.category: mismatched category"
                + " replaced by the recipe map's primary category (HEI machine fix);"
                + " validate() relaxed at " + relaxed + " site(s).");
        return cw.toByteArray();
    }
}
