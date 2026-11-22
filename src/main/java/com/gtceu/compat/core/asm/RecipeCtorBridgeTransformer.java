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

/**
 * Restores GTCEu 2.8's 12-argument {@code Recipe} constructor on GTQT 2.9.
 *
 * <pre>
 * 2.8: (List,List,ChancedOutputList,List,List,ChancedOutputList,int,int,boolean,boolean,RPS,GTRecipeCategory)V
 * 2.9: (List,List,ChancedOutputList,List,List,ChancedOutputList,  List,int,  long,boolean,RPS,GTRecipeCategory)V
 * </pre>
 *
 * <p>The 2.9 signature replaced the second {@code int} with a
 * {@code List<ItemStack>} (byproduct/leftover outputs), widened the first
 * {@code int} to {@code long}, dropped one {@code boolean} and inserted a
 * {@code List} before it. 2.8 addons building a Recipe directly (GTExpert
 * TierUpRecipeBuilder.build) therefore miss the constructor.
 *
 * <p>The bridge reorders/adapts the arguments:
 * <ul>
 *   <li>int -> long for the EU/t slot</li>
 *   <li>an empty List for 2.9's added item-output slot</li>
 *   <li>the second 2.8 int (duration-ish) is folded into the long EU/t slot's</li>
 * </ul>
 * Only structure matters here: GTExpert's tier-up recipe is a synthetic
 * validation holder, so a faithful-but-simple mapping is sufficient to avoid
 * the hard failure while keeping the recipe valid.
 */
public class RecipeCtorBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/Recipe";
    private static final String C = "()Lgregtech/api/recipes/Recipe;";
    private static final String OLD =
            "(Ljava/util/List;Ljava/util/List;Lgregtech/api/recipes/chance/output/ChancedOutputList;" +
            "Ljava/util/List;Ljava/util/List;Lgregtech/api/recipes/chance/output/ChancedOutputList;" +
            "IIZZLgregtech/api/recipes/properties/RecipePropertyStorage;" +
            "Lgregtech/api/recipes/category/GTRecipeCategory;)V";
    private static final String NEW =
            "(Ljava/util/List;Ljava/util/List;Lgregtech/api/recipes/chance/output/ChancedOutputList;" +
            "Ljava/util/List;Ljava/util/List;Lgregtech/api/recipes/chance/output/ChancedOutputList;" +
            "Ljava/util/List;IJZLgregtech/api/recipes/properties/RecipePropertyStorage;" +
            "Lgregtech/api/recipes/category/GTRecipeCategory;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Recipe ctor bridge failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean hasOld = false, hasNew = false;
        for (MethodNode mn : cn.methods) {
            if ("<init>".equals(mn.name)) {
                if (OLD.equals(mn.desc)) hasOld = true;
                if (NEW.equals(mn.desc)) hasNew = true;
            }
        }
        if (hasOld || !hasNew) return basicClass;

        MethodNode mn = new MethodNode(ACC_PUBLIC, "<init>", OLD, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(ALOAD, 0));
        // 1..6: six collection args are identical
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new VarInsnNode(ALOAD, 2));
        il.add(new VarInsnNode(ALOAD, 3));
        il.add(new VarInsnNode(ALOAD, 4));
        il.add(new VarInsnNode(ALOAD, 5));
        il.add(new VarInsnNode(ALOAD, 6));
        // 2.9 inserted a List<ItemStack>; pass an empty list.
        il.add(new org.objectweb.asm.tree.TypeInsnNode(org.objectweb.asm.Opcodes.NEW, "java/util/ArrayList"));
        il.add(new InsnNode(DUP));
        il.add(new MethodInsnNode(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false));
        // 2.8: ...(6 lists), int slot7, int slot8, boolean slot9, boolean slot10, RPS slot11, cat slot12
        // 2.9: ...(6 lists), List, int, long, boolean, RPS, cat
        // Map: int7 -> int(duration), int8 -> long(EUt), boolean9 -> boolean.
        il.add(new VarInsnNode(ILOAD, 7));
        il.add(new VarInsnNode(ILOAD, 8));
        il.add(new InsnNode(I2L));
        il.add(new VarInsnNode(ILOAD, 9));
        // RPS + category: 2.8 slots 11,12
        il.add(new VarInsnNode(ALOAD, 11));
        il.add(new VarInsnNode(ALOAD, 12));
        il.add(new MethodInsnNode(INVOKESPECIAL, TARGET, "<init>", NEW, false));
        il.add(new InsnNode(RETURN));
        mn.maxStack = 20;
        mn.maxLocals = 13;
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Recipe: injected 2.8 12-arg constructor bridge.");
        return cw.toByteArray();
    }
}
