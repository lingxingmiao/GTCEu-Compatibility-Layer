package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Restores GTCEu 2.8's {@code RecipeBuilder.finalizeAndValidate()} on GTQT 2.9,
 * which dropped it in favour of {@code validate()}.
 *
 * <p>2.8 addons that subclass RecipeBuilder (GTExpert's TierUpRecipeBuilder)
 * call {@code finalizeAndValidate()} from their own {@code build()}. 2.8's
 * implementation checked the "recipe property storage errored" flag then called
 * {@code validate()}; 2.9 has no such flag, so delegating straight to
 * {@code validate()} preserves the meaningful part of the check.
 */
public class FinalizeAndValidateBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/RecipeBuilder";
    private static final String EVR = "Lgregtech/api/util/EnumValidationResult;";
    private static final String DESC = "()" + EVR;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] finalizeAndValidate bridge failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode mn : cn.methods) {
            if ("finalizeAndValidate".equals(mn.name)) return basicClass;
        }

        MethodNode mn = new MethodNode(ACC_PROTECTED, "finalizeAndValidate", DESC, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET, "validate", DESC, false));
        il.add(new InsnNode(ARETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] RecipeBuilder: injected 2.8 finalizeAndValidate() bridge.");
        return cw.toByteArray();
    }
}
