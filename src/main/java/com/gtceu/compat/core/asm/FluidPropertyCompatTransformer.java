package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Injects the removed {@code FluidProperty.getStorage()} method back into
 * GTQT 2.9's {@code gregtech.api.unification.material.properties.FluidProperty}.
 *
 * <p>GTCEu 2.8.10 had a deprecated {@code public FluidStorage getStorage() { return this; }}
 * which GTCEFuContent still calls. GTQT 2.9 removed it (FluidProperty itself now
 * implements FluidStorage directly). Since the class still implements FluidStorage,
 * re-adding {@code getStorage()} as {@code return this} is binary- and
 * behaviour-compatible with 2.8.
 *
 * <p>No-op if the method already exists (e.g. on older GTCEu builds).
 */
public class FluidPropertyCompatTransformer implements IClassTransformer {

    public static final String TARGET_INTERNAL = "gregtech/api/unification/material/properties/FluidProperty";

    private static final String GET_STORAGE_NAME = "getStorage";
    private static final String GET_STORAGE_DESC = "()Lgregtech/api/fluids/store/FluidStorage;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET_INTERNAL.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return injectGetStorage(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Failed to transform FluidProperty: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] injectGetStorage(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        List<MethodNode> methods = cn.methods;
        for (MethodNode mn : methods) {
            if (GET_STORAGE_NAME.equals(mn.name) && GET_STORAGE_DESC.equals(mn.desc)) {
                System.out.println("[GTCEuCompat] FluidProperty.getStorage() already present, skipping.");
                return basicClass;
            }
        }

        // public FluidStorage getStorage() { return this; }
        MethodNode shim = new MethodNode(
                Opcodes.ACC_PUBLIC,
                GET_STORAGE_NAME,
                GET_STORAGE_DESC,
                null,
                null);
        InsnList il = shim.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
        il.add(new InsnNode(Opcodes.ARETURN));
        shim.maxStack = 1;
        shim.maxLocals = 1;
        methods.add(shim);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Injected FluidProperty.getStorage() compatibility method.");
        return cw.toByteArray();
    }
}
