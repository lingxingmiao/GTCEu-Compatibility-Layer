package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Restores GTCEu 2.8's {@code Recipe.getOutputs(): NonNullList} on GTQT 2.9,
 * which narrowed the return type to {@code List}.
 *
 * <p>2.8 addons (GTCEFuContent's GTCEFuCMiscRecipes.cutterUpdate) call the 2.8
 * descriptor and then use {@code NonNullList.toArray}; simply rewriting the
 * caller's descriptor to List would break that toArray call. The bridge wraps
 * the 2.9 List in a NonNullList so the caller's existing code keeps working.
 */
public class RecipeGetOutputsBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/Recipe";
    private static final String NNL = "net/minecraft/util/NonNullList";
    private static final String OLD_DESC = "()L" + NNL + ";";
    private static final String NEW_DESC = "()Ljava/util/List;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Recipe.getOutputs bridge failed: " + t);
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
            if ("getOutputs".equals(mn.name) && "()Ljava/util/List;".equals(mn.desc)) hasNew = true;
            if ("getOutputs".equals(mn.name) && OLD_DESC.equals(mn.desc)) hasOld = true;
        }
        if (hasOld || !hasNew) return basicClass;

        MethodNode mn = new MethodNode(ACC_PUBLIC, "getOutputs", OLD_DESC, null, null);
        InsnList il = mn.instructions;
        // NonNullList out = NonNullList.func_191197_a();   // SRG name for create()
        il.add(new MethodInsnNode(INVOKESTATIC, NNL, "func_191197_a", "()L" + NNL + ";", false));
        il.add(new VarInsnNode(ASTORE, 1));
        // out.addAll(this.getOutputs());
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET, "getOutputs", NEW_DESC, false));
        il.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/List", "addAll",
                "(Ljava/util/Collection;)Z", true));
        il.add(new InsnNode(POP));
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new InsnNode(ARETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Recipe: injected 2.8 getOutputs():NonNullList bridge.");
        return cw.toByteArray();
    }
}
