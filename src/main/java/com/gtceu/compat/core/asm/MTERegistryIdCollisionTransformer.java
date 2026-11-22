package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Auto-resolves GTCEu 2.8 addon MTE numeric-id collisions on GTQT 2.9.
 *
 * <p>2.8 addons hard-code MTE ids (e.g. GCYM starts at 2000). GTQT 2.9 uses far
 * more ids itself, so {@code GTControlledRegistry.func_177775_a(id, key, value)}
 * throws "Tried to reassign id %d ... already assigned". A 2.8-era addon cannot
 * know the new id map, so the compatibility layer nudges the id forward to the
 * next free slot when a collision is detected.
 *
 * <p>Only the requested id is rewritten, at method entry, before the original
 * body runs; the rest of the registration (namespace/name checks) is untouched.
 * Client and server both run this transformer, so the resolved ids agree on both
 * sides (MTE network ids are derived from the registry).
 */
public class MTERegistryIdCollisionTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/util/GTControlledRegistry";
    private static final String DESC = "(ILjava/lang/Object;Ljava/lang/Object;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] MTE id collision compat failed: " + t);
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
            if ("func_177775_a".equals(mn.name) && DESC.equals(mn.desc)) {
                target = mn;
                break;
            }
        }
        if (target == null || target.instructions == null || target.instructions.size() == 0) {
            return basicClass;
        }

        // Prepend: while (func_148754_a(id) != null && id < maxId) id++;
        InsnList pre = new InsnList();
        LabelNode loop = new LabelNode();
        LabelNode done = new LabelNode();
        pre.add(loop);
        pre.add(new VarInsnNode(ALOAD, 0));           // this
        pre.add(new VarInsnNode(ILOAD, 1));           // id
        pre.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET, "func_148754_a",
                "(I)Ljava/lang/Object;", false));
        pre.add(new JumpInsnNode(IFNULL, done));      // slot is free -> proceed
        pre.add(new VarInsnNode(ILOAD, 1));
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new FieldInsnNode(GETFIELD, TARGET, "maxId", "I"));
        pre.add(new JumpInsnNode(IF_ICMPGE, done));   // out of range -> let original throw
        pre.add(new IincInsnNode(1, 1));              // id++
        pre.add(new JumpInsnNode(GOTO, loop));
        pre.add(done);
        // then the original body
        AbstractInsnNode first = target.instructions.getFirst();
        target.instructions.insertBefore(first, pre);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] GTControlledRegistry: auto-resolve MTE id collisions for 2.8 addons.");
        return cw.toByteArray();
    }
}
