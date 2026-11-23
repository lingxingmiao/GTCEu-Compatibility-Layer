package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Restores GTCEu 2.8's {@code RecipeMap.isHidden} field on GTQT 2.9.
 *
 * <p>2.8 declared {@code public final boolean isHidden}; 2.9 removed it.
 * GTFO's JEI plugin reads it while registering recipe categories:
 *
 * <pre>
 * NoSuchFieldError: RecipeMap does not have member field 'boolean isHidden'
 *   at gregtechfoodoption.integration.jei.JEIGTFOPlugin.register(JEIGTFOPlugin.java:46)
 * </pre>
 *
 * <p>That aborts the whole GTFO JEI plugin, so none of its recipe categories get
 * registered in HEI. Adding a default-false field back is faithful: 2.9's
 * {@code RecipeMap} has no hidden concept, i.e. every map is visible.
 */
public class RecipeMapIsHiddenFieldTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/RecipeMap";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] RecipeMap.isHidden field failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (FieldNode fn : cn.fields) {
            if ("isHidden".equals(fn.name) && "Z".equals(fn.desc)) return basicClass;
        }
        // A public non-final instance field is enough: a Java boolean field
        // defaults to false, which matches 2.9 ("nothing is hidden"). GTFO reads
        // it with GETFIELD, so no initialiser is needed.
        cn.fields.add(new FieldNode(ACC_PUBLIC, "isHidden", "Z", null, Boolean.FALSE));

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] RecipeMap: restored 2.8 'isHidden' field (false) for JEI plugins.");
        return cw.toByteArray();
    }
}
