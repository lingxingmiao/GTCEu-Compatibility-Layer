package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Makes GTQT 2.9's {@code RecipePropertyStorageImpl.store} overwrite duplicates and
 * accept a null value as "remove this property", matching GTCEu 2.8.
 *
 * <h3>Why</h3>
 * 2.8 addons legitimately re-apply a recipe property to replace an inherited value,
 * and 2.8 signalled "clear it" by applying {@code null}. GTCEu 2.8's
 * {@code RecipeBuilder.applyProperty} handled null by calling
 * {@code storage.remove(prop)}; 2.9 removed that branch and always calls
 * {@code store(prop, value)}.
 *
 * <p>2.9's {@code store} then returns {@code false} in two situations 2.8 callers
 * rely on being harmless:
 * <ul>
 *   <li>the key is already present (duplicate) — 2.9 logs and returns false;</li>
 *   <li>the value is {@code null} — {@code castValue(null)} throws and false is
 *       returned.</li>
 * </ul>
 *
 * <p>{@code RecipeBuilder.applyProperty} turns a {@code false} result into
 * {@code recipePropertyStorageErrored = true}, which makes
 * {@code finalizeAndValidate()} report INVALID and discards the entire recipe.
 * That is what removed GTCEfuContent's Linear Forging Furnace recipes (observed:
 * 1009 {@code lff_blast_cooled} + 629 {@code lff_blast_forging_cooled} rejected
 * with "Recipe outputs are empty").
 *
 * <h3>Fix</h3>
 * Replace {@code store} with:
 * <pre>
 * public boolean store(RecipeProperty&lt;?&gt; prop, Object value) {
 *     String key = prop.getKey();
 *     if (value == null) {
 *         map.remove(prop);          // 2.8's remove-on-null
 *         return true;
 *     }
 *     try { prop.castValue(value); }
 *     catch (ClassCastException e) { log; return false; }   // genuine type error stays an error
 *     map.put(prop, value);          // duplicates simply overwrite
 *     return true;
 * }
 * </pre>
 * Genuine type mismatches still fail, so real authoring bugs are not hidden.
 */
public class RecipePropertyStoreOverrideTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/properties/RecipePropertyStorageImpl";
    private static final String PROP = "Lgregtech/api/recipes/properties/RecipeProperty;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] RecipePropertyStorageImpl.store override failed: " + t);
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
            if ("store".equals(mn.name) && ("(" + PROP + "Ljava/lang/Object;)Z").equals(mn.desc)) {
                target = mn;
                break;
            }
        }
        if (target == null) return basicClass;

        MethodNode fresh = new MethodNode(ACC_PUBLIC, "store", target.desc, null, null);
        InsnList il = fresh.instructions;
        LabelNode afterCast = new LabelNode();
        LabelNode typeError = new LabelNode();
        LabelNode putIt = new LabelNode();

        // String key = prop.getKey();   // slot 3 (no need to keep, but mirrors 2.8 logs)
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new MethodInsnNode(INVOKEVIRTUAL,
                "gregtech/api/recipes/properties/RecipeProperty", "getKey",
                "()Ljava/lang/String;", false));
        il.add(new VarInsnNode(ASTORE, 3));

        // if (value == null) { map.remove(prop); return true; }
        il.add(new VarInsnNode(ALOAD, 2));
        il.add(new JumpInsnNode(IFNONNULL, afterCast));
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new FieldInsnNode(GETFIELD, TARGET, "map", "Ljava/util/Map;"));
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Map", "remove",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true));
        il.add(new InsnNode(POP));
        il.add(new InsnNode(ICONST_1));
        il.add(new InsnNode(IRETURN));

        // try { prop.castValue(value); } catch (ClassCastException e) { ... return false; }
        il.add(afterCast);
        LabelNode tryStart = new LabelNode();
        LabelNode tryEnd = new LabelNode();
        il.add(tryStart);
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new VarInsnNode(ALOAD, 2));
        il.add(new MethodInsnNode(INVOKEVIRTUAL,
                "gregtech/api/recipes/properties/RecipeProperty", "castValue",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false));
        il.add(new InsnNode(POP));
        il.add(tryEnd);
        il.add(new JumpInsnNode(GOTO, putIt));

        il.add(typeError);
        // Log the genuine type error and fail as before.
        il.add(new FieldInsnNode(GETSTATIC, "gregtech/api/util/GTLog", "logger",
                "Lorg/apache/logging/log4j/Logger;"));
        il.add(new LdcInsnNode("Provided incorrect value for RecipeProperty with key {}"));
        il.add(new VarInsnNode(ALOAD, 3));
        il.add(new MethodInsnNode(INVOKEINTERFACE, "org/apache/logging/log4j/Logger", "error",
                "(Ljava/lang/String;Ljava/lang/Object;)V", true));
        il.add(new InsnNode(ICONST_0));
        il.add(new InsnNode(IRETURN));

        // map.put(prop, value); return true;
        il.add(putIt);
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new FieldInsnNode(GETFIELD, TARGET, "map", "Ljava/util/Map;"));
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new VarInsnNode(ALOAD, 2));
        il.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Map", "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true));
        il.add(new InsnNode(POP));
        il.add(new InsnNode(ICONST_1));
        il.add(new InsnNode(IRETURN));

        fresh.maxStack = 4;
        fresh.maxLocals = 4;
        fresh.tryCatchBlocks = new java.util.ArrayList<>();
        fresh.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, typeError,
                "java/lang/ClassCastException"));

        int idx = cn.methods.indexOf(target);
        cn.methods.set(idx, fresh);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] RecipePropertyStorageImpl.store: duplicates now"
                + " overwrite and null clears (2.8 semantics).");
        return cw.toByteArray();
    }
}
