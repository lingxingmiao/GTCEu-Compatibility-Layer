package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Ensures a Material's fluid is registered before {@code getFluid(int)} builds a
 * FluidStack.
 *
 * <p>GTQT 2.9's {@code GTFluidRegistration.register()} walks
 * {@code materialManager.getRegisteredMaterials()} and calls
 * {@code FluidProperty.registerFluids(material)} for each. When 2.8 addons
 * (GCYM / GTExpert) register duplicate-named materials across registries, some
 * core materials (observed: {@code gregtech:hsla_steel}) can be missed by that
 * walk, so {@code Material.getFluid()} returns null and
 * {@code MultiblockLoader.CasingLoader} dies with
 * "Cannot create a fluidstack from a null fluid".
 *
 * <p>This transformer prepends a guard to {@code getFluid(int)}: if the primary
 * fluid is null but the material has a FluidProperty, call
 * {@code FluidProperty.registerFluids(this)} once (idempotent), so the normal
 * body then finds the fluid. Behaviour for already-correct materials is
 * unchanged; genuinely fluid-less materials still throw as before.
 */
public class MaterialFluidFallbackTransformer implements IClassTransformer, Opcodes {

    private static final String TARGET = "gregtech/api/unification/material/Material";
    private static final String FLUID_PROP = "gregtech/api/unification/material/properties/FluidProperty";
    private static final String GET_FLUID_DESC = "()Lnet/minecraftforge/fluids/Fluid;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName.replace('.', '/'))) {
            return basicClass;
        }
        try {
            return inject(basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Material fluid fallback failed: " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            if ("getFluid".equals(mn.name) && "(I)Lnet/minecraftforge/fluids/FluidStack;".equals(mn.desc)) {
                target = mn;
                break;
            }
        }
        if (target == null || target.instructions == null || target.instructions.size() == 0) {
            return basicClass;
        }

        // Prepend:
        //   if (getFluid() != null) goto proceed;
        //   FluidProperty p = getProperty(PropertyKey.FLUID);
        //   if (p == null) goto proceed;
        //   p.registerFluids(this);
        // proceed:
        InsnList pre = new InsnList();
        LabelNode proceed = new LabelNode();
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET, "getFluid", GET_FLUID_DESC, false));
        pre.add(new JumpInsnNode(IFNONNULL, proceed));       // fluid present -> skip
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new FieldInsnNode(GETSTATIC,
                "gregtech/api/unification/material/properties/PropertyKey", "FLUID",
                "Lgregtech/api/unification/material/properties/PropertyKey;"));
        pre.add(new MethodInsnNode(INVOKEVIRTUAL, TARGET, "getProperty",
                "(Lgregtech/api/unification/material/properties/PropertyKey;)" +
                        "Lgregtech/api/unification/material/properties/IMaterialProperty;", false));
        pre.add(new TypeInsnNode(CHECKCAST, FLUID_PROP));
        pre.add(new VarInsnNode(ASTORE, 2));
        pre.add(new VarInsnNode(ALOAD, 2));
        pre.add(new JumpInsnNode(IFNULL, proceed));           // no fluid property -> let original throw
        pre.add(new VarInsnNode(ALOAD, 2));
        pre.add(new VarInsnNode(ALOAD, 0));
        pre.add(new MethodInsnNode(INVOKEVIRTUAL, FLUID_PROP, "registerFluids",
                "(L" + TARGET + ";)V", false));
        pre.add(proceed);

        target.instructions.insertBefore(target.instructions.getFirst(), pre);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] Material.getFluid(int): added missing-fluid re-registration guard.");
        return cw.toByteArray();
    }
}
