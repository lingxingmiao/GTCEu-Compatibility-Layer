package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Restores GTCEu 2.8 constructors that GTQT 2.9 extended with a trailing
 * {@code ParallelLogicType} parameter.
 *
 * <p>GTQT 2.9 introduced parallel logic types for steam multiblocks and appended
 * a {@link ParallelLogicType} argument to two constructors:
 * <ul>
 *   <li>{@code RecipeMapSteamMultiblockController(RL, RecipeMap, double)} →
 *       {@code (RL, RecipeMap, double, ParallelLogicType)}</li>
 *   <li>{@code SteamMultiWorkable(RecipeMapSteamMultiblockController, double)} →
 *       {@code (..., double, ParallelLogicType)}</li>
 * </ul>
 * 2.8-compiled addons (GregTech Food Option, ...) still call the shorter ctors
 * and fail with {@code NoSuchMethodError}. Each bridge forwards to the 4-arg /
 * 3-arg version passing {@link ParallelLogicType#MULTIPLY}, the standard parallel
 * mode and the closest match to 2.8 (which had no parallel logic type at all).
 *
 * <p>The table is data-driven: if a later 2.9 build appends the same parameter
 * to another ctor, add one row here.
 */
public class SteamMultiblockCtorBridgeTransformer implements IClassTransformer, Opcodes {

    private static final String PLT = "gregtech/api/metatileentity/multiblock/ParallelLogicType";
    private static final String PLT_DESC = "L" + PLT + ";";

    private static final String RSMC =
            "gregtech/api/metatileentity/multiblock/RecipeMapSteamMultiblockController";
    private static final String SMW = "gregtech/api/capability/impl/SteamMultiWorkable";

    /** target internal name -> (old ctor desc, new ctor desc) */
    private static final Map<String, String[]> CTORS = new LinkedHashMap<>();
    static {
        CTORS.put(RSMC, new String[]{
                "(Lnet/minecraft/util/ResourceLocation;Lgregtech/api/recipes/RecipeMap;D)V",
                "(Lnet/minecraft/util/ResourceLocation;Lgregtech/api/recipes/RecipeMap;D" + PLT_DESC + ")V"});
        CTORS.put(SMW, new String[]{
                "(L" + RSMC + ";D)V",
                "(L" + RSMC + ";D" + PLT_DESC + ")V"});
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) {
            return basicClass;
        }
        String internal = transformedName.replace('.', '/');
        String[] pair = CTORS.get(internal);
        if (pair == null) {
            return basicClass;
        }
        try {
            return inject(internal, pair[0], pair[1], basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Steam multiblock ctor bridge failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(String internal, String oldDesc, String newDesc, byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        Set<String> present = new HashSet<>();
        for (MethodNode mn : cn.methods) present.add(mn.name + mn.desc);
        if (present.contains("<init>" + oldDesc) || !present.contains("<init>" + newDesc)) {
            return basicClass; // real 2.8 host, or incompatible 2.9 build
        }

        MethodNode mn = new MethodNode(ACC_PUBLIC, "<init>", oldDesc, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(ALOAD, 0));   // this
        // Load all original arguments. Both target ctors take (ref..., double)
        // as their first N params and append one ParallelLogicType at the end.
        org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(oldDesc);
        int slot = 1;
        for (org.objectweb.asm.Type t : args) {
            il.add(new VarInsnNode(t.getOpcode(ILOAD), slot));
            slot += t.getSize();
        }
        il.add(new FieldInsnNode(GETSTATIC, PLT, "MULTIPLY", PLT_DESC));
        il.add(new MethodInsnNode(INVOKESPECIAL, internal, "<init>", newDesc, false));
        il.add(new InsnNode(RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Injected 2.8 steam-multiblock ctor " + oldDesc + " on " + internal);
        return cw.toByteArray();
    }
}
