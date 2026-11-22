package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Restores selected GTCEu 2.8.10 static fields on GTQT 2.9 without changing any
 * existing 2.9 field or constructor.
 *
 * <p>JVM field identity is {@code name + ':' + descriptor}, so a 2.8 addon doing
 * {@code GETSTATIC GregTechAPI.MTE_REGISTRY:Lgregtech/api/util/GTControlledRegistry;}
 * would throw {@code NoSuchFieldError} on 2.9 even though the type is erased and the
 * field was simply removed. This transformer re-creates the missing field as
 * {@code public static final} and assigns it in {@code <clinit>}: from the 2.9
 * equivalent when one exists, otherwise {@code null} (so the linkage error is
 * replaced by a graceful null that the reflection adapter can detect).
 *
 * <p>Element.halfLifeSeconds is a 2.8 public instance long field. 2.9 exposes
 * {@code double getHalfLiveSeconds()} instead. A field cannot delegate to a method,
 * so addons reading it must be recompiled; this transformer deliberately does not
 * fake it (documented in README). The OrePrefix.oreSmall alias is a static field
 * of an enum-like registry, so aliasing it to {@code ore} in {@code <clinit>} is safe.
 */
public class FieldCompatTransformer implements IClassTransformer, Opcodes {

    private static final String GT_API = "gregtech/api/GregTechAPI";
    private static final String ORE_PREFIX = "gregtech/api/unification/ore/OrePrefix";
    private static final String GT_CONTROLLED = "Lgregtech/api/util/GTControlledRegistry;";
    private static final String ORE_DESC = "Lgregtech/api/unification/ore/OrePrefix;";
    private static final String BLOCK_MACHINE = "Lgregtech/api/block/machines/BlockMachine;";
    private static final String TAB_DESC = "Lgregtech/api/util/BaseCreativeTab;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) {
            return basicClass;
        }
        String internal = transformedName.replace('.', '/');
        try {
            if (GT_API.equals(internal)) {
                return injectGregTechApi(basicClass);
            }
            if (ORE_PREFIX.equals(internal)) {
                return injectOrePrefix(basicClass);
            }
            return basicClass;
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] FieldCompat failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] injectGregTechApi(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        // 2.8: public static final GTControlledRegistry<ResourceLocation, MetaTileEntity> MTE_REGISTRY
        // 2.9: removed. Assign null; addons that only read the registry for iteration
        //       are covered by the MTEManager reflection helpers instead.
        String[][] fields = {
                {"MTE_REGISTRY", GT_CONTROLLED},
                {"MACHINE", BLOCK_MACHINE},
                {"TAB_GREGTECH", TAB_DESC},
                {"TAB_GREGTECH_CABLES", TAB_DESC},
                {"TAB_GREGTECH_DECORATIONS", TAB_DESC},
                {"TAB_GREGTECH_MACHINES", TAB_DESC},
                {"TAB_GREGTECH_MATERIALS", TAB_DESC},
                {"TAB_GREGTECH_ORES", TAB_DESC},
                {"TAB_GREGTECH_PIPES", TAB_DESC},
                {"TAB_GREGTECH_TOOLS", TAB_DESC}
        };

        MethodNode clinit = findClinit(cn);
        if (clinit == null) {
            clinit = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
            cn.methods.add(clinit);
        }
        InsnList il = clinit.instructions;
        int added = 0;
        for (String[] f : fields) {
            if (hasField(cn, f[0], f[1])) {
                continue;
            }
            cn.fields.add(new org.objectweb.asm.tree.FieldNode(
                    ACC_PUBLIC | ACC_STATIC | ACC_FINAL, f[0], f[1], null, null));
            // public static final fields are assigned in <clinit> as null (ACONST_NULL + PUTSTATIC).
            // Insert at the beginning so any later 2.9 <clinit> logic sees them initialised.
            InsnList assign = new InsnList();
            assign.add(new InsnNode(ACONST_NULL));
            assign.add(new FieldInsnNode(PUTSTATIC, GT_API, f[0], f[1]));
            if (il.size() == 0) {
                il.add(assign);
            } else {
                il.insert(assign);
            }
            added++;
        }
        return write(cn, basicClass, added, "GregTechAPI 2.8 static field aliases");
    }

    private static byte[] injectOrePrefix(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        if (hasField(cn, "oreSmall", ORE_DESC)) {
            return basicClass;
        }
        // 2.8 OrePrefix.oreSmall is a static registry entry; 2.9 removed it.
        // Alias to OrePrefix.ore (nearest surviving equivalent) in <clinit>.
        cn.fields.add(new org.objectweb.asm.tree.FieldNode(
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "oreSmall", ORE_DESC, null, null));
        MethodNode clinit = findClinit(cn);
        if (clinit == null) {
            clinit = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
            cn.methods.add(clinit);
        }
        InsnList assign = new InsnList();
        assign.add(new FieldInsnNode(GETSTATIC, ORE_PREFIX, "ore", ORE_DESC));
        assign.add(new FieldInsnNode(PUTSTATIC, ORE_PREFIX, "oreSmall", ORE_DESC));
        // Must run AFTER the existing <clinit> has populated OrePrefix.ore,
        // so insert immediately before the trailing RETURN, not at the start.
        org.objectweb.asm.tree.AbstractInsnNode ret = null;
        for (org.objectweb.asm.tree.AbstractInsnNode insn = clinit.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == RETURN) {
                ret = insn;
            }
        }
        if (ret != null) {
            clinit.instructions.insertBefore(ret, assign);
        } else {
            clinit.instructions.add(assign);
        }
        return write(cn, basicClass, 1, "OrePrefix.oreSmall alias");
    }

    private static byte[] write(ClassNode cn, byte[] original, int added, String what) {
        if (added == 0) {
            return original;
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] " + what + ": restored " + added + " field(s).");
        return cw.toByteArray();
    }

    private static boolean hasField(ClassNode cn, String name, String desc) {
        for (org.objectweb.asm.tree.FieldNode fn : cn.fields) {
            if (name.equals(fn.name) && desc.equals(fn.desc)) {
                return true;
            }
        }
        return false;
    }

    private static MethodNode findClinit(ClassNode cn) {
        for (MethodNode mn : cn.methods) {
            if ("<clinit>".equals(mn.name)) {
                return mn;
            }
        }
        return null;
    }
}
