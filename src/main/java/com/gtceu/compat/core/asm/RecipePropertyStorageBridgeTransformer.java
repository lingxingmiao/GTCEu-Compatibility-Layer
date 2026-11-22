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

import java.util.HashSet;
import java.util.Set;

/**
 * Adds the GTCEu 2.8.10 method names of {@code IRecipePropertyStorage} back onto
 * GTQT 2.9's {@code gregtech.api.recipes.properties.RecipePropertyStorage} interface
 * as default methods that forward to the renamed 2.9 methods.
 *
 * <p>{@link AddonPackageRemapTransformer} rewrites the old interface name in addon
 * bytecode, but 2.9 also renamed most of its methods:
 * <pre>
 *   getSize()I                    -> size()I
 *   getRecipeProperties()Set       -> entrySet()Set
 *   getRecipePropertyValue(P,T)   -> get(P,T)
 *   hasRecipeProperty(P)Z         -> contains(P)Z
 *   getPropertyTypes()Set         -> values()Set
 *   copy()RecipePropertyStorage   -> copy()RecipePropertyStorage (unchanged)
 *   store(P,Object)Z              -> store(P,Object)Z (unchanged)
 * </pre>
 * Without these bridges, a 2.8 addon that calls {@code getSize()} on a 2.9 storage
 * object would fail with {@code NoSuchMethodError} even after the package rename.
 *
 * <p>The old methods with no 2.9 equivalent ({@code remove}, {@code freeze},
 * {@code getRawRecipePropertyValue}, {@code getRecipePropertyKeys}) are deliberately
 * NOT faked; addons using them must be recompiled.
 */
public class RecipePropertyStorageBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/recipes/properties/RecipePropertyStorage";
    private static final String PROP = "Lgregtech/api/recipes/properties/RecipeProperty;";

    private static final String[][] BRIDGES = {
            // old name, old desc, new name, new desc
            {"getSize", "()I", "size", "()I"},
            {"getRecipeProperties", "()Ljava/util/Set;", "entrySet", "()Ljava/util/Set;"},
            {"getRecipePropertyValue", "(" + PROP + "Ljava/lang/Object;)Ljava/lang/Object;",
                    "get", "(" + PROP + "Ljava/lang/Object;)Ljava/lang/Object;"},
            {"hasRecipeProperty", "(" + PROP + ")Z", "contains", "(" + PROP + ")Z"},
            {"getPropertyTypes", "()Ljava/util/Set;", "values", "()Ljava/util/Set;"}
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] RecipePropertyStorage bridge failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        Set<String> present = new HashSet<>();
        for (MethodNode mn : cn.methods) {
            present.add(mn.name + mn.desc);
        }

        int added = 0;
        for (String[] b : BRIDGES) {
            String oldName = b[0], oldDesc = b[1], newName = b[2], newDesc = b[3];
            if (present.contains(oldName + oldDesc) || !present.contains(newName + newDesc)) {
                continue;
            }
            MethodNode mn = new MethodNode(ACC_PUBLIC, oldName, oldDesc, null, null);
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(ALOAD, 0)); // this (interface receiver)
            org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(oldDesc);
            int slot = 1;
            for (org.objectweb.asm.Type t : args) {
                il.add(new VarInsnNode(t.getOpcode(ILOAD), slot));
                slot += t.getSize();
            }
            il.add(new MethodInsnNode(INVOKEINTERFACE, TARGET, newName, newDesc, true));
            char ret = oldDesc.charAt(oldDesc.indexOf(')') + 1);
            if (ret == 'Z' || ret == 'I') {
                il.add(new InsnNode(IRETURN));
            } else if (ret == 'J') {
                il.add(new InsnNode(LRETURN));
            } else if (ret == 'D') {
                il.add(new InsnNode(DRETURN));
            } else if (ret == 'F') {
                il.add(new InsnNode(FRETURN));
            } else if (ret == 'V') {
                il.add(new InsnNode(RETURN));
            } else {
                il.add(new InsnNode(ARETURN));
            }
            cn.methods.add(mn);
            present.add(oldName + oldDesc);
            added++;
        }

        // 2.8 methods with no 2.9 equivalent, added as harmless no-op/fallback.
        // freeze(boolean) only locked the storage against later modification; 2.9
        // dropped the guard and simply allows mutation, so a no-op is equivalent
        // for callers (GTCEFuContent LinearForgingFurnaceLoader.assignRecipeType).
        added += addNoOp(cn, present, "freeze", "(Z)V", RETURN);
        // remove(P)Z - no removal in 2.9; report "nothing removed".
        added += addNoOp(cn, present, "remove", "(" + PROP + ")Z", ICONST_0, IRETURN);
        // getRecipePropertyKeys()Set / getRawRecipePropertyValue(String)Object are
        // not shimmed: they have no safe 2.9 equivalent.

        if (added == 0) {
            return basicClass;
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Injected " + added
                + " IRecipePropertyStorage 2.8 method name(s) onto RecipePropertyStorage.");
        return cw.toByteArray();
    }

    /** Adds a no-op method pushing the given constant opcode(s) before the return. */
    private static int addNoOp(ClassNode cn, Set<String> present, String name, String desc, int... body) {
        if (present.contains(name + desc)) return 0;
        MethodNode mn = new MethodNode(ACC_PUBLIC, name, desc, null, null);
        InsnList il = mn.instructions;
        for (int op : body) {
            il.add(new InsnNode(op));
        }
        cn.methods.add(mn);
        present.add(name + desc);
        System.out.println("[GTCEuCompat] RecipePropertyStorage: added 2.8 no-op " + name + desc);
        return 1;
    }
}
