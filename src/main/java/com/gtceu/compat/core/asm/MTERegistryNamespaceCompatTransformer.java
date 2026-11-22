package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Relaxes GTQT 2.9's per-mod MTE registry namespace check.
 *
 * <p>2.9's {@code MTERegistry.register} rejects a MetaTileEntity whose
 * ResourceLocation namespace differs from the registry's modid
 * ("Cannot register MTE to another mod's registry"). GTCEu 2.8 had ONE global
 * registry and every addon registered its own {@code <modid>:...} MTEs into it.
 *
 * <p>Unlike {@link MTEManagerCompatTransformer} (which is disabled), this does
 * NOT create any extra MTERegistry. It only makes the existing registry accept
 * foreign-namespace MTEs, so GTQT 2.9's {@code CommonProxy.registerBlocks} still
 * sees exactly the registries it created (with valid blocks) and never calls
 * {@code ForgeRegistry.register(null)}.
 */
public class MTERegistryNamespaceCompatTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/metatileentity/registry/MTERegistry";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] MTERegistry namespace compat failed: " + t);
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
            if ("canRegister".equals(mn.name) && "(Ljava/lang/String;)Z".equals(mn.desc)) {
                target = mn;
                break;
            }
        }
        if (target == null) return basicClass;

        // private boolean canRegister(String) { return true; }
        InsnList il = new InsnList();
        il.add(new InsnNode(ICONST_1));
        il.add(new InsnNode(IRETURN));
        target.instructions = il;
        target.maxStack = 1;
        target.maxLocals = 2;
        if (target.localVariables != null) target.localVariables.clear();
        if (target.tryCatchBlocks != null) target.tryCatchBlocks.clear();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] MTERegistry.canRegister: accept 2.8 addon MTE namespaces.");
        return cw.toByteArray();
    }
}
