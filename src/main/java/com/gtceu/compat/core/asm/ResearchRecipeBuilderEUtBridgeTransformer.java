package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Restores GTCEu 2.8's {@code ResearchRecipeBuilder.EUt(int)} on GTQT 2.9,
 * which widened it to {@code EUt(long)}.
 *
 * <p>2.8-compiled addons (GTCEFuContent's stationResearch lambdas, ...) still
 * emit {@code EUt(I)L.../ResearchRecipeBuilder;} and fail with
 * NoSuchMethodError. The bridge widens the int to long and delegates to the 2.9
 * method, preserving the erased return type (ResearchRecipeBuilder), so
 * StationRecipeBuilder subclasses resolve it through inheritance.
 */
public class ResearchRecipeBuilderEUtBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/builders/ResearchRecipeBuilder";
    private static final String RET = "Lgregtech/api/recipes/builders/ResearchRecipeBuilder;";
    private static final String OLD_DESC = "(I)" + RET;
    private static final String NEW_DESC = "(J)" + RET;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] ResearchRecipeBuilder EUt bridge failed: " + t);
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
            if ("EUt".equals(mn.name)) {
                if (OLD_DESC.equals(mn.desc)) hasOld = true;
                if (NEW_DESC.equals(mn.desc)) hasNew = true;
            }
        }
        if (hasOld || !hasNew) return basicClass;

        MethodNode mn = new MethodNode(ACC_PUBLIC, "EUt", OLD_DESC, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new VarInsnNode(ILOAD, 1));
        il.add(new InsnNode(I2L));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET, "EUt", NEW_DESC, false));
        il.add(new InsnNode(ARETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] ResearchRecipeBuilder: injected 2.8 EUt(int) bridge.");
        return cw.toByteArray();
    }
}
