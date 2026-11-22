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

import java.util.HashSet;
import java.util.Set;

/**
 * Restores GTCEu 2.8's {@code MultiblockFuelRecipeLogic(RecipeMapMultiblockController)}
 * constructor on GTQT 2.9, which narrowed the parameter to
 * {@code FuelMultiblockController} (a subclass of RecipeMapMultiblockController).
 *
 * <p>2.8-compiled addons (GTCEFuContent's Mega Steam Engine, ...) still call the
 * 2.8 signature and fail with {@code NoSuchMethodError}. Since
 * {@code FuelMultiblockController extends RecipeMapMultiblockController}, the
 * bridge only needs a CHECKCAST before delegating to the 2.9 constructor.
 */
public class FuelRecipeLogicCtorBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/capability/impl/MultiblockFuelRecipeLogic";
    private static final String FUEL_CTRL =
            "gregtech/api/metatileentity/multiblock/FuelMultiblockController";
    private static final String RECIPE_CTRL =
            "gregtech/api/metatileentity/multiblock/RecipeMapMultiblockController";
    private static final String OLD_DESC = "(L" + RECIPE_CTRL + ";)V";
    private static final String NEW_DESC = "(L" + FUEL_CTRL + ";)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] FuelRecipeLogic ctor bridge failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        Set<String> present = new HashSet<>();
        for (MethodNode mn : cn.methods) present.add(mn.name + mn.desc);
        if (present.contains("<init>" + OLD_DESC) || !present.contains("<init>" + NEW_DESC)) {
            return basicClass; // real 2.8 host, or incompatible 2.9 build
        }

        MethodNode mn = new MethodNode(ACC_PUBLIC, "<init>", OLD_DESC, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(ALOAD, 0));  // this
        il.add(new VarInsnNode(ALOAD, 1));  // RecipeMapMultiblockController arg
        il.add(new TypeInsnNode(CHECKCAST, FUEL_CTRL));
        il.add(new MethodInsnNode(INVOKESPECIAL, TARGET, "<init>", NEW_DESC, false));
        il.add(new InsnNode(RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Injected 2.8 MultiblockFuelRecipeLogic(RecipeMapMultiblockController) ctor.");
        return cw.toByteArray();
    }
}
