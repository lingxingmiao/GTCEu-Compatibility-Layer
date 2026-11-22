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
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Re-adds GTCEu 2.8.10's {@code Material.Builder.toolStats(ToolProperty)} entry
 * point to GTQT 2.9's {@code gregtech.api.unification.material.Material$Builder}.
 *
 * <p>2.8.10 (verified with {@code javap -p -s} on
 * {@code gregtech-1.12.2-2.8.10-beta.jar}) declared:
 * <pre>
 * public Material$Builder toolStats(ToolProperty);
 *   (Lgregtech/api/unification/material/properties/ToolProperty;)Lgregtech/api/unification/material/Material$Builder;
 * </pre>
 * 2.9 renamed the parameter type to {@code MaterialToolProperty}:
 * <pre>
 * public Material$Builder toolStats(MaterialToolProperty);
 *   (Lgregtech/api/unification/material/properties/MaterialToolProperty;)Lgregtech/api/unification/material/Material$Builder;
 * </pre>
 * and {@code MaterialToolProperty extends ToolProperty implements IMaterialProperty},
 * so the old call site's argument is always a valid {@code MaterialToolProperty} at
 * run time (2.8 addons construct one via the remapped
 * {@code MaterialToolProperty$Builder}, see {@link ToolPropertyRemapTransformer}) -
 * only the compile-time descriptor no longer resolves, which surfaces as
 * {@code NoSuchMethodError} on {@code invirtual}.
 *
 * <p>The injected bridge is a pure adapter:
 * <pre>
 * public Material$Builder toolStats(ToolProperty property) {
 *     return this.toolStats((MaterialToolProperty) property);
 * }
 * </pre>
 * A {@code CHECKCAST} keeps it honest: if some addon really hands over a bare
 * {@code ToolProperty} that is not a {@code MaterialToolProperty}, the failure is a
 * {@code ClassCastException} at the call site instead of silently handing 2.9 a
 * broken property object.
 *
 * <p>Why a dedicated transformer instead of a {@link BridgeSets} entry:
 * <ul>
 *   <li>{@link BridgeSpec} only models numeric argument rewrites ({@code argOps} are
 *       {@code InsnNode}s such as {@code I2L}); it has no way to emit a
 *       {@code CHECKCAST} on a reference argument, and the bridge's parameter type
 *       ({@code ToolProperty}) deliberately differs from the delegate's
 *       ({@code MaterialToolProperty}).</li>
 *   <li>The delegate is a <em>virtual</em> call on the very class being transformed
 *       with a different descriptor, so the generic injector's
 *       {@code present.contains(delegateName + delegateDesc)} check alone is not
 *       enough to express the "cast then call" shape.</li>
 *   <li>Keeping it separate makes the no-op conditions explicit for a real 2.8 host
 *       and for a future GTQT that restores the 2.8 signature.</li>
 * </ul>
 *
 * <p>No-op when:
 * <ul>
 *   <li>the transformed class is not {@code Material$Builder};</li>
 *   <li>the 2.8 descriptor is already present (real GTCEu 2.8.10 host);</li>
 *   <li>the 2.9 {@code MaterialToolProperty} descriptor is missing (incompatible
 *       host) - a warning is printed and the class is returned unchanged.</li>
 * </ul>
 *
 * <p>Stack map frames are not recomputed ({@code ClassWriter.COMPUTE_MAXS}): the
 * injected body is straight-line code with no branches, so no new frame is needed
 * and only max stack/locals must be recalculated.
 */
public class ToolPropertyBridgeTransformer implements IClassTransformer, Opcodes {

    /** Internal name of the class that declares {@code toolStats}. */
    public static final String TARGET_INTERNAL = "gregtech/api/unification/material/Material$Builder";

    private static final String TOOL_PROPERTY = "gregtech/api/unification/material/properties/ToolProperty";
    private static final String MATERIAL_TOOL_PROPERTY =
            "gregtech/api/unification/material/properties/MaterialToolProperty";

    private static final String METHOD_NAME = "toolStats";

    /** 2.8.10 descriptor: what GTCEu 2.8 addons were compiled against. */
    private static final String OLD_DESC =
            "(L" + TOOL_PROPERTY + ";)Lgregtech/api/unification/material/Material$Builder;";

    /** 2.9 descriptor: what the host actually declares. */
    private static final String NEW_DESC =
            "(L" + MATERIAL_TOOL_PROPERTY + ";)Lgregtech/api/unification/material/Material$Builder;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) {
            return basicClass;
        }
        if (!TARGET_INTERNAL.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return injectBridge(basicClass, transformedName);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] ToolProperty bridge failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] injectBridge(byte[] basicClass, String transformedName) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        List<MethodNode> methods = cn.methods;
        boolean hasOld = false;
        boolean hasNew = false;
        for (MethodNode mn : methods) {
            if (!METHOD_NAME.equals(mn.name)) {
                continue;
            }
            if (OLD_DESC.equals(mn.desc)) {
                hasOld = true;
            } else if (NEW_DESC.equals(mn.desc)) {
                hasNew = true;
            }
        }

        if (hasOld) {
            // Real GTCEu 2.8.10 host (or a build that already restored the signature).
            return basicClass;
        }
        if (!hasNew) {
            System.err.println("[GTCEuCompat] ToolProperty bridge skipped: " + transformedName
                    + " has neither the 2.8 ToolProperty descriptor nor the 2.9 MaterialToolProperty delegate.");
            return basicClass;
        }

        // public Material$Builder toolStats(ToolProperty property) {
        //     return this.toolStats((MaterialToolProperty) property);
        // }
        MethodNode bridge = new MethodNode(ACC_PUBLIC, METHOD_NAME, OLD_DESC, null, null);
        InsnList il = bridge.instructions;
        il.add(new VarInsnNode(ALOAD, 0)); // this
        il.add(new VarInsnNode(ALOAD, 1)); // ToolProperty argument
        il.add(new TypeInsnNode(CHECKCAST, MATERIAL_TOOL_PROPERTY));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET_INTERNAL, METHOD_NAME, NEW_DESC, false));
        il.add(new InsnNode(ARETURN));
        bridge.maxStack = 2;
        bridge.maxLocals = 2;
        methods.add(bridge);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Injected " + transformedName + ".toolStats(ToolProperty) -> "
                + "toolStats(MaterialToolProperty) bridge.");
        return cw.toByteArray();
    }
}
