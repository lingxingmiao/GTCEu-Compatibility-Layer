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

/**
 * Injects a 2.9 {@code createStructureDefinition()} into 2.8-compiled multiblock
 * controllers that don't have one.
 *
 * <p>GTQT 2.9 made this method abstract on {@code MultiblockControllerBase}. A 2.8
 * addon multiblock extends that class but only implements 2.8's
 * {@code createStructurePattern()}, so GTQT dies with
 * {@code AbstractMethodError: ... does not define or implement createStructureDefinition()}
 * while enumerating machines (JEI preview / structure checks).
 *
 * <p>The injected method returns {@link LegacyStructureCompat#legacyDefinition},
 * whose runtime detector evaluates the addon's own 2.8 {@code BlockPattern}; the
 * machine's real structure still governs formation.
 *
 * <p>Scoped to multiblock controller packages of the 2.8 addons in this pack, and
 * only applied when the class extends {@code MultiblockControllerBase}, has no
 * {@code createStructureDefinition()}, but does declare
 * {@code createStructurePattern()}.
 */
public class StructureDefinitionCompatTransformer implements IClassTransformer, Opcodes {

    private static final String MCB = "gregtech/api/metatileentity/multiblock/MultiblockControllerBase";
    private static final String SD = "Lgregtech/api/pattern/element/StructureDefinition;";
    private static final String DESC = "()" + SD;

    /** Addon packages whose multiblocks were compiled against GTCEu 2.8. */
    private static final String[] PREFIXES = {
            "com/m_w_k/gtcefucontent/",
            "com/github/gtexpert/",
            "gregicality/multiblocks/",
            "kono/ceu/gtconsolidate/",
            "kono/ceu/materialreplication/",
            "com/zorbatron/zbgt/",
            "keqing/gtqt/prismplan/",
            "com/iristhepianist/scalablestorageceu/",
            "gregtechfoodoption/",
            "rosatech/",
            "gtceugbh/"
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) return basicClass;
        String internal = transformedName.replace('.', '/');

        boolean addon = false;
        for (String p : PREFIXES) {
            if (internal.startsWith(p)) { addon = true; break; }
        }
        if (!addon) return basicClass;

        try {
            return inject(internal, basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] structure-definition compat failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(String internal, byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        // Only classes that actually derive from a 2.9 MultiblockControllerBase.
        boolean extendsController = MCB.equals(cn.superName);
        if (!extendsController) {
            // walk up a couple of levels within the same class file's hierarchy
            // is not possible without loading classes; rely on the direct-super
            // check plus the presence of createStructurePattern below.
            boolean hasPattern = false;
            for (MethodNode mn : cn.methods) {
                if ("createStructurePattern".equals(mn.name)) { hasPattern = true; break; }
            }
            if (!hasPattern) return basicClass;
        }

        boolean hasDefinition = false, hasLegacyPattern = false;
        for (MethodNode mn : cn.methods) {
            if ("createStructureDefinition".equals(mn.name) && DESC.equals(mn.desc)) hasDefinition = true;
            if ("createStructurePattern".equals(mn.name)) hasLegacyPattern = true;
        }
        if (hasDefinition) return basicClass;
        // Without a 2.8 pattern there is nothing to drive the detector.
        if (!hasLegacyPattern) return basicClass;

        MethodNode mn = new MethodNode(ACC_PROTECTED, "createStructureDefinition", DESC, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKESTATIC,
                "com/gtceu/compat/core/asm/LegacyStructureCompat", "legacyDefinition",
                "(L" + MCB + ";)" + SD, false));
        il.add(new InsnNode(ARETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] " + internal
                + ": injected 2.9 createStructureDefinition() from 2.8 createStructurePattern().");
        return cw.toByteArray();
    }
}
