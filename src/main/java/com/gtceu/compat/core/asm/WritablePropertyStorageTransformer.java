package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Makes {@code Recipe.propertyStorage()} return a writable storage instead of the
 * read-only {@code RecipePropertyStorage.EMPTY} singleton.
 *
 * <p>2.8 addons mutate a recipe's property storage directly. GTCEFuContent's
 * {@code LinearForgingFurnaceLoader.assignRecipeType} does:
 *
 * <pre>
 *   recipe.getRecipePropertyStorage().store(TemperatureProperty.getInstance(), value);
 * </pre>
 *
 * <p>GTQT 2.9 returns {@code RecipePropertyStorage.EMPTY} for recipes that carry no
 * properties, and {@code EMPTY.store(...)} throws
 * {@code UnsupportedOperationException: empty}, aborting the addon's late recipe
 * pass (hence missing machine recipes).
 *
 * <p>Fix: wrap the accessor so that the immutable EMPTY instance is replaced by a
 * fresh writable {@code RecipePropertyStorageImpl}. Non-empty storages are
 * returned untouched, so 2.9's own recipes are unaffected.
 */
public class WritablePropertyStorageTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/Recipe";
    private static final String RPS = "gregtech/api/recipes/properties/RecipePropertyStorage";
    private static final String IMPL = "gregtech/api/recipes/properties/RecipePropertyStorageImpl";
    private static final String DESC = "()L" + RPS + ";";
    private static final String RAW = "propertyStorage$compatRaw";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] writable property storage failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        MethodNode original = null;
        for (MethodNode mn : cn.methods) {
            if ("propertyStorage".equals(mn.name) && DESC.equals(mn.desc)) {
                original = mn;
                break;
            }
        }
        if (original == null || original.instructions == null) return basicClass;
        // Already patched (e.g. double transform) — leave alone.
        for (MethodNode mn : cn.methods) {
            if (RAW.equals(mn.name)) return basicClass;
        }

        original.name = RAW;
        original.access = original.access & ~(ACC_PUBLIC | ACC_PROTECTED) | ACC_PRIVATE;

        // Cache field: the recipe's own storage is final, so a lazily created
        // writable replacement must be remembered here or writes would be lost.
        FieldNode cache = new FieldNode(ACC_PRIVATE, "compatPropertyStorage", "L" + RPS + ";", null, null);
        cn.fields.add(cache);

        MethodNode wrapper = new MethodNode(ACC_PUBLIC, "propertyStorage", DESC, null, null);
        InsnList il = wrapper.instructions;
        LabelNode useRaw = new LabelNode();
        LabelNode haveCache = new LabelNode();
        // if (compatPropertyStorage != null) return compatPropertyStorage;
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new FieldInsnNode(GETFIELD, TARGET, "compatPropertyStorage", "L" + RPS + ";"));
        il.add(new JumpInsnNode(IFNONNULL, haveCache));
        // RecipePropertyStorage s = this.propertyStorage$compatRaw();
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKESPECIAL, TARGET, RAW, DESC, false));
        il.add(new VarInsnNode(ASTORE, 1));
        // if (s != null && s != EMPTY) return s;
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new JumpInsnNode(IFNULL, useRaw));
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new FieldInsnNode(GETSTATIC, RPS, "EMPTY", "L" + RPS + ";"));
        il.add(new JumpInsnNode(IF_ACMPNE, useRaw));
        // compatPropertyStorage = new RecipePropertyStorageImpl();
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new TypeInsnNode(NEW, IMPL));
        il.add(new InsnNode(DUP));
        il.add(new MethodInsnNode(INVOKESPECIAL, IMPL, "<init>", "()V", false));
        il.add(new FieldInsnNode(PUTFIELD, TARGET, "compatPropertyStorage", "L" + RPS + ";"));
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new FieldInsnNode(GETFIELD, TARGET, "compatPropertyStorage", "L" + RPS + ";"));
        il.add(new InsnNode(ARETURN));
        // return s;  (non-empty or null)
        il.add(useRaw);
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new InsnNode(ARETURN));
        // return compatPropertyStorage;
        il.add(haveCache);
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new FieldInsnNode(GETFIELD, TARGET, "compatPropertyStorage", "L" + RPS + ";"));
        il.add(new InsnNode(ARETURN));
        wrapper.maxStack = 3;
        wrapper.maxLocals = 2;
        cn.methods.add(wrapper);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Recipe.propertyStorage: EMPTY replaced by a writable storage.");
        return cw.toByteArray();
    }
}
