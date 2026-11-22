package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Skips ZBGT 0.16.2's {@code MaterialRecipes.alloyBlast()} recipe pass on GTQT 2.9.
 *
 * <p>That method reads {@code CraftingComponent.EBF_GASES.get(gasTier)} and
 * checkcasts it to FluidStack. In 2.8 the map was
 * {@code Map<GasTier, FluidStack>}; GTQT 2.9 changed it to
 * {@code Map<GasTier, List<FluidStack>>}, so ZBGT gets an ArrayList and throws
 * ClassCastException, aborting startup.
 *
 * <p>Skipping this ONE private recipe pass is a deliberate degradation: ZBGT's
 * chemicalReactor / vacuumFreezer / macerator recipes and all its machines stay
 * intact; only the alloy-blast gas recipes built on the removed 2.8 map shape
 * are omitted. The other recipe methods in MaterialRecipes.init are untouched.
 */
public class ZBGTAlloyBlastSkipTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "com/zorbatron/zbgt/recipe/MaterialRecipes";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] ZBGT alloyBlast skip failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        MethodNode m = null;
        for (MethodNode mn : cn.methods) {
            if ("alloyBlast".equals(mn.name) && "()V".equals(mn.desc)) {
                m = mn;
                break;
            }
        }
        if (m == null || m.instructions == null) return basicClass;

        InsnList il = new InsnList();
        il.add(new InsnNode(RETURN));
        m.instructions = il;
        m.maxStack = 0;
        m.maxLocals = 0;
        if (m.localVariables != null) m.localVariables.clear();
        if (m.tryCatchBlocks != null) m.tryCatchBlocks.clear();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] ZBGT MaterialRecipes.alloyBlast: skipped 2.8-only EBF gas recipes.");
        return cw.toByteArray();
    }
}
