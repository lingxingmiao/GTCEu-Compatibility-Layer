package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Skips unregistered materials in GTQT 2.9's {@code BlockMaterialPipe.addPipeMaterial}
 * instead of throwing.
 *
 * <p>GTCEu 2.8 addons (Gregicality Multiblocks) register their own materials
 * (e.g. {@code gcym:hsla_steel}) with fluid-pipe properties. GTQT 2.9 already
 * ships {@code gregtech:hsla_steel}, so the addon's material object ends up not
 * bound in the active material registry. When 2.9's CommonProxy.registerBlocks
 * walks all materials with FLUID_PIPE/ITEM_PIPE properties it calls
 * addPipeMaterial, whose {@code Preconditions.checkArgument(... "material %s is
 * not registered")} then aborts startup.
 *
 * <p>The compatibility layer adds an early guard: if the material has no
 * registry name, return without adding it. Registered materials are unaffected;
 * the unregistered duplicate simply does not become a pipe material (it never
 * could anyway). This is a robustness relaxation, not a behaviour change.
 */
public class PipeMaterialSkipTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/pipenet/block/material/BlockMaterialPipe";
    private static final String DESC =
            "(Lgregtech/api/unification/material/Material;Ljava/lang/Object;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Pipe material skip failed: " + t);
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
            if ("addPipeMaterial".equals(mn.name) && DESC.equals(mn.desc)) {
                target = mn;
                break;
            }
        }
        if (target == null || target.instructions == null || target.instructions.size() == 0) {
            return basicClass;
        }

        // if (material == null) -> let the original null-check throw (skip our guard).
        // if (material.getRegistry().func_177774_c(material) == null) return;
        InsnList pre = new InsnList();
        LabelNode proceed = new LabelNode();
        LabelNode check = new LabelNode();
        pre.add(new VarInsnNode(ALOAD, 1));
        pre.add(new JumpInsnNode(IFNONNULL, check));
        pre.add(new JumpInsnNode(GOTO, proceed));
        pre.add(check);
        // Use our own registry field (non-null) rather than material.getRegistry()
        // which may be null for an unregistered duplicate material.
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new FieldInsnNode(GETFIELD, TARGET, "registry",
                "Lgregtech/api/unification/material/registry/MaterialRegistry;"));
        pre.add(new VarInsnNode(ALOAD, 1)); // Material
        // func_177774_c = RegistryNamespaced.getNameForObject(Object) (SRG name)
        pre.add(new MethodInsnNode(INVOKEVIRTUAL,
                "gregtech/api/unification/material/registry/MaterialRegistry", "func_177774_c",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false));
        pre.add(new JumpInsnNode(IFNONNULL, proceed));
        pre.add(new InsnNode(RETURN));
        pre.add(proceed);

        target.instructions.insertBefore(target.instructions.getFirst(), pre);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] BlockMaterialPipe.addPipeMaterial: skip unregistered materials.");
        return cw.toByteArray();
    }
}
