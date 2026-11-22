package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Prevents GTQT 2.9's {@code ResearchRecipeBuilder$StationRecipeBuilder.build()}
 * from aborting startup when a 2.8 addon's stationResearch lambda leaves
 * cwut / totalCWU at zero.
 *
 * <p>2.8-compiled addons (GTCEFuContent's GTCEFuCControllerLoader) chain
 * {@code researchStack(...).CWUt(16).EUt(...)}. Because 2.9 widened EUt to long
 * and changed the erased return types of the fluent builder, the CWUt call can
 * land on a different erased type than the object that build() is invoked on,
 * leaving cwut=0. 2.9 throws "CWU/t and total CWU must both be set" and aborts
 * the whole recipe pass.
 *
 * <p>Guard: at build() entry, if cwut<=0 or totalCWU<=0, set cwut=1 and
 * totalCWU=DEFAULT_STATION_TOTAL_CWUT. Recipes keep building (with a degraded
 * research time) instead of crashing the pack. Already-correct recipes are
 * untouched.
 */
public class StationRecipeBuilderCWUFallbackTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET =
            "gregtech/api/recipes/builders/ResearchRecipeBuilder$StationRecipeBuilder";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] StationRecipeBuilder CWU fallback failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        MethodNode build = null;
        for (MethodNode mn : cn.methods) {
            if ("build".equals(mn.name) && mn.desc.startsWith("()")) {
                build = mn;
                break;
            }
        }
        if (build == null || build.instructions == null || build.instructions.size() == 0) {
            return basicClass;
        }

        // if (cwut > 0 && totalCWU > 0) goto ok;
        // cwut = 1; totalCWU = DEFAULT_STATION_TOTAL_CWUT;
        // ok:
        InsnList pre = new InsnList();
        LabelNode needDefault = new LabelNode();
        LabelNode ok = new LabelNode();
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new FieldInsnNode(GETFIELD, TARGET, "cwut", "I"));
        pre.add(new JumpInsnNode(IFLE, needDefault));
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new FieldInsnNode(GETFIELD, TARGET, "totalCWU", "I"));
        pre.add(new JumpInsnNode(IFLE, needDefault));
        pre.add(new JumpInsnNode(GOTO, ok));
        pre.add(needDefault);
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new InsnNode(ICONST_1));
        pre.add(new FieldInsnNode(PUTFIELD, TARGET, "cwut", "I"));
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new FieldInsnNode(GETSTATIC, TARGET, "DEFAULT_STATION_TOTAL_CWUT", "I"));
        pre.add(new FieldInsnNode(PUTFIELD, TARGET, "totalCWU", "I"));
        pre.add(ok);

        build.instructions.insertBefore(build.instructions.getFirst(), pre);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] StationRecipeBuilder.build: added CWU default guard.");
        return cw.toByteArray();
    }
}
