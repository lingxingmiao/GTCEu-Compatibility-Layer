package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Restores GTCEu 2.8's {@code OreDictUnifier.registerOre(ItemStack, ItemMaterialInfo)}
 * on GTQT 2.9, which removed that overload (material recycling moved to
 * {@code GregTechAPI.RECYCLING_MANAGER.registerRecyclingData(stack, RecyclingData)}).
 *
 * <p>2.8 addons (GTConsolidate's GTConsolidateMiscLoader.materialInfo, ...) still
 * call the 2.8 method -> NoSuchMethodError. The compatibility layer vendors the
 * 2.8 {@code ItemMaterialInfo} class; this bridge converts it to 2.9's
 * {@code RecyclingData} and forwards to the new manager.
 */
public class OreDictUnifierMaterialInfoBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/unification/OreDictUnifier";
    private static final String IMI = "gregtech/api/unification/stack/ItemMaterialInfo";
    private static final String RD = "gregtech/api/unification/stack/RecyclingData";
    private static final String RM = "gregtech/api/unification/RecyclingManager";
    private static final String OLD_DESC =
            "(Lnet/minecraft/item/ItemStack;L" + IMI + ";)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] OreDictUnifier material-info bridge failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode mn : cn.methods) {
            if ("registerOre".equals(mn.name) && OLD_DESC.equals(mn.desc)) {
                return basicClass; // already present (real 2.8 host)
            }
        }

        MethodNode mn = new MethodNode(ACC_PUBLIC | ACC_STATIC, "registerOre", OLD_DESC, null, null);
        InsnList il = mn.instructions;
        // GregTechAPI.RECYCLING_MANAGER
        il.add(new FieldInsnNode(GETSTATIC, "gregtech/api/GregTechAPI", "RECYCLING_MANAGER",
                "L" + RM + ";"));
        // stack
        il.add(new VarInsnNode(ALOAD, 0));
        // new RecyclingData(info.getMaterials())
        il.add(new TypeInsnNode(NEW, RD));
        il.add(new InsnNode(DUP));
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, IMI, "getMaterials",
                "()Lcom/google/common/collect/ImmutableList;", false));
        // ImmutableList is a List; RecyclingData(List<MaterialStack>) ctor
        il.add(new MethodInsnNode(INVOKESPECIAL, RD, "<init>",
                "(Ljava/util/List;)V", false));
        il.add(new MethodInsnNode(INVOKEVIRTUAL, RM, "registerRecyclingData",
                "(Lnet/minecraft/item/ItemStack;L" + RD + ";)V", false));
        il.add(new InsnNode(RETURN));
        cn.methods.add(mn);

        // 2.8 getAllItemInfos(): rebuild from 2.9 RecyclingManager.
        boolean hasAllInfos = false;
        for (MethodNode m2 : cn.methods) {
            if ("getAllItemInfos".equals(m2.name)) { hasAllInfos = true; break; }
        }
        if (!hasAllInfos) {
            MethodNode all = new MethodNode(ACC_PUBLIC | ACC_STATIC, "getAllItemInfos",
                    "()Ljava/util/List;", null, null);
            InsnList a = all.instructions;
            a.add(new MethodInsnNode(INVOKESTATIC,
                    "com/gtceu/compat/core/asm/OreDictCompatAux", "getAllItemInfos",
                    "()Ljava/util/List;", false));
            a.add(new InsnNode(ARETURN));
            cn.methods.add(all);
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] OreDictUnifier: injected 2.8 registerOre/getAllItemInfos bridges.");
        return cw.toByteArray();
    }
}
