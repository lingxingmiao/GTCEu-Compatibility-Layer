package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Auto-creates per-mod MTE registries in GTQT 2.9 for addons compiled against 2.8.
 *
 * <p>GTCEu 2.8 had ONE global {@code GregTechAPI.MTE_REGISTRY}; every addon
 * registered its machines into it with its own {@code modid:} ResourceLocation.
 * GTQT 2.9 replaced that with {@code MTEManager.getRegistry(modid)} and a
 * per-mod {@code MTERegistry} that rejects entries whose namespace differs from
 * the registry's modid ("Cannot register MTE to another mod's registry").
 *
 * <p>{@code MetaTileEntity.<init>} already calls
 * {@code MTEManager.getRegistry(metaTileEntityId.getNamespace())}. 2.9's
 * {@code getRegistry(String)} falls back to the gregtech registry when the modid
 * has no registry, which is exactly what breaks 2.8 addons such as GCYM.
 *
 * <p>This transformer changes that fallback: when the requested registry is
 * missing, {@code getRegistry(String)} calls {@code createRegistry(modid)} and
 * returns the new per-mod registry instead of the gregtech one. Addon machines
 * then register into a registry whose modid matches their namespace.
 */
public class MTEManagerCompatTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/metatileentity/registry/MTEManager";
    private static final String REG_DESC = "Lgregtech/api/metatileentity/registry/MTERegistry;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return rewrite(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] MTEManager compat failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    /**
     * Replace the whole {@code getRegistry(String)} method:
     * <pre>
     * MTERegistry r = registryMap.get(modid);
     * if (r == null) r = createRegistry(modid);
     * return r;
     * </pre>
     * (2.9's original returned the gregtech fallback instead of creating one.)
     */
    private static byte[] rewrite(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            if ("getRegistry".equals(mn.name) && ("(Ljava/lang/String;)" + REG_DESC).equals(mn.desc)) {
                target = mn;
                break;
            }
        }
        if (target == null) {
            return basicClass;
        }

        InsnList il = new InsnList();
        LabelNode notNull = new LabelNode();

        // MTERegistry r = registryMap.get(modid);
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new org.objectweb.asm.tree.FieldInsnNode(GETFIELD, TARGET, "registryMap", "Ljava/util/Map;"));
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Map", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(CHECKCAST,
                "gregtech/api/metatileentity/registry/MTERegistry"));
        il.add(new VarInsnNode(ASTORE, 2));
        // if (r == null) r = createRegistry(modid);
        il.add(new VarInsnNode(ALOAD, 2));
        il.add(new JumpInsnNode(IFNONNULL, notNull));
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET, "createRegistry",
                "(Ljava/lang/String;)" + REG_DESC, false));
        il.add(new VarInsnNode(ASTORE, 2));
        il.add(notNull);
        // return r;
        il.add(new VarInsnNode(ALOAD, 2));
        il.add(new InsnNode(ARETURN));

        target.instructions = il;
        target.maxStack = 2;
        target.maxLocals = 3;
        // Drop original local-variable debug info / stack map frames; COMPUTE_FRAMES rebuilds them.
        target.localVariables.clear();
        if (target.tryCatchBlocks != null) target.tryCatchBlocks.clear();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] MTEManager.getRegistry(String): auto-create per-mod registry for 2.8 addons.");
        return cw.toByteArray();
    }
}
