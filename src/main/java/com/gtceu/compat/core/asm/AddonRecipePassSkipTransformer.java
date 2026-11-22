package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic "make this void method a no-op" transformer for 2.8 addon recipe passes
 * that cannot work on GTQT 2.9.
 *
 * <p>Each entry is a class internal name plus the void methods to stub out. These
 * are always OPTIONAL recipe add-ons (late recipe tweaks for the addon's own
 * machines), never registration or registry code, so stubbing them degrades
 * content rather than breaking startup.
 *
 * <p>Used for GTCEFuContent 2.1.1, whose late loaders read
 * {@code Recipe.getOutputs()} as a {@code NonNullList} (2.8 return type).
 * GTQT 2.9 narrowed it to {@code List}; a bytecode bridge is unreliable because
 * {@code NonNullList}'s factory is SRG-renamed, and a blanket NonNullList->List
 * rewrite breaks unrelated addon code (ZBGT MTECircuitAssemblyLine) with
 * VerifyError. Stubbing these specific passes keeps everything else intact.
 */
public class AddonRecipePassSkipTransformer implements IClassTransformer, Opcodes {

    private static final Map<String, String[]> SKIPS = new LinkedHashMap<>();

    static {
        // GTCEFuContent: all use Recipe.getOutputs() as NonNullList.
        SKIPS.put("com/m_w_k/gtcefucontent/loaders/recipe/GTCEFuCMiscRecipes",
                new String[]{"cutterUpdate"});
        SKIPS.put("com/m_w_k/gtcefucontent/loaders/recipe/LinearForgingFurnaceLoader",
                new String[]{"registerCooled", "registerForgingCooled", "assembleCompositeMaps"});
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) return basicClass;
        String internal = transformedName.replace('.', '/');
        String[] methods = SKIPS.get(internal);
        if (methods == null) return basicClass;
        try {
            return inject(internal, methods, basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Addon recipe pass skip failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(String internal, String[] methods, byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        int stubbed = 0;
        for (MethodNode mn : cn.methods) {
            if (!"()V".equals(mn.desc) || mn.instructions == null) continue;
            boolean wanted = false;
            for (String want : methods) {
                if (want.equals(mn.name)) { wanted = true; break; }
            }
            if (!wanted) continue;

            InsnList il = new InsnList();
            il.add(new InsnNode(RETURN));
            mn.instructions = il;
            mn.maxStack = 0;
            mn.maxLocals = 0;
            if (mn.localVariables != null) mn.localVariables.clear();
            if (mn.tryCatchBlocks != null) mn.tryCatchBlocks.clear();
            stubbed++;
        }
        if (stubbed == 0) return basicClass;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] " + internal + ": stubbed " + stubbed
                + " 2.8-only recipe pass(es).");
        return cw.toByteArray();
    }
}
