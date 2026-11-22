package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Injects the declarative 2.8 API bridges from {@link BridgeSets} into GTQT 2.9
 * classes at load time.
 *
 * <p>A bridge is only injected when:
 * <ul>
 *   <li>the class being transformed is a bridge target;</li>
 *   <li>the old {@code name+desc} is not already present (real 2.8 host, or a
 *       newer GTQT that already restored the signature); and</li>
 *   <li>the delegate {@code name+desc} exists on the transformed class, so a
 *       renamed/retyped 2.9 method is actually reachable.</li>
 * </ul>
 *
 * <p>Stack map frames are not recomputed ({@code ClassWriter.COMPUTE_MAXS}) because
 * every injected body is straight-line code without branches; only max stack/locals
 * need recomputing.
 */
public class GenericBridgeTransformer implements IClassTransformer, Opcodes {

    private static final Map<String, List<BridgeSpec>> BY_TARGET = loadBridgeSets();

    private static Map<String, List<BridgeSpec>> loadBridgeSets() {
        try {
            return BridgeSets.byTarget();
        } catch (Throwable t) {
            // A coremod is loaded extremely early; never let a bad declaration take
            // down the entire transformer chain with a NoClassDefFoundError.
            System.err.println("[GTCEuCompat] Failed to load BridgeSets; generic bridges disabled: " + t);
            t.printStackTrace();
            return java.util.Collections.emptyMap();
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) {
            return basicClass;
        }
        String internal = transformedName.replace('.', '/');
        List<BridgeSpec> specs = BY_TARGET.get(internal);
        if (specs == null || specs.isEmpty()) {
            return basicClass;
        }
        try {
            return inject(internal, specs, basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Generic bridge failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(String internal, List<BridgeSpec> specs, byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        Set<String> present = new HashSet<>();
        for (MethodNode mn : cn.methods) {
            present.add(mn.name + mn.desc);
        }

        int injected = 0;
        for (BridgeSpec s : specs) {
            if (present.contains(s.name + s.desc)) {
                continue; // old signature already exists -> real 2.8 host
            }
            String delegateOwner = s.owner == null ? internal : s.owner;
            if (delegateOwner.equals(internal) && !present.contains(s.delegateName + s.delegateDesc)) {
                continue; // same-class delegate missing -> incompatible host, skip silently
            }
            // Cross-class delegates cannot be verified against this class's method list;
            // the JVM will resolve them (and fail loudly) only if the bridge is ever called.
            MethodNode bridge = buildBridge(s, delegateOwner);
            cn.methods.add(bridge);
            present.add(s.name + s.desc);
            injected++;
        }

        if (injected == 0) {
            return basicClass;
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Injected " + injected + " 2.8 API bridge(s) into " + internal);
        return cw.toByteArray();
    }

    private static MethodNode buildBridge(BridgeSpec s, String delegateOwner) {
        MethodNode mn = new MethodNode(s.access, s.name, s.desc, null, null);
        InsnList il = mn.instructions;

        boolean isStatic = (s.access & ACC_STATIC) != 0;
        int slot = 0;
        if (!isStatic) {
            il.add(new VarInsnNode(ALOAD, slot++)); // this
        }

        Type[] args = Type.getArgumentTypes(s.desc);
        for (int i = 0; i < args.length; i++) {
            Type t = args[i];
            il.add(new VarInsnNode(t.getOpcode(ILOAD), slot));
            slot += t.getSize();
            if (s.argOps != null && i < s.argOps.length && s.argOps[i] != 0) {
                il.add(new InsnNode(s.argOps[i]));
            }
        }

        il.add(new MethodInsnNode(s.opcode, delegateOwner, s.delegateName, s.delegateDesc,
                s.opcode == INVOKEINTERFACE));

        Type bridgeReturn = Type.getReturnType(s.desc);
        Type delegateReturn = Type.getReturnType(s.delegateDesc);

        if (s.castReturn != null) {
            il.add(new TypeInsnNode(CHECKCAST, s.castReturn));
        }
        if (s.returnOp != 0) {
            il.add(new InsnNode(s.returnOp));
        }
        if (s.reciprocalReturn) {
            // 2.8 divisor x == 1 / 2.9 factor. Emit 1.0 / factor.
            il.add(new LdcInsnNode(Double.valueOf(1.0d)));
            il.add(new InsnNode(DDIV));
        }

        if (bridgeReturn.getSort() == Type.VOID) {
            if (delegateReturn.getSort() != Type.VOID) {
                il.add(new InsnNode(delegateReturn.getSize() == 2 ? POP2 : POP));
            }
            il.add(new InsnNode(RETURN));
        } else {
            il.add(new InsnNode(bridgeReturn.getOpcode(IRETURN)));
        }
        return mn;
    }
}
