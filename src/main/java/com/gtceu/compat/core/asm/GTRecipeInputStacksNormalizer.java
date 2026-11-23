package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Normalises {@code GTRecipeInput.getInputStacks()} so it never returns an
 * <em>empty</em> array — only {@code null} — for ingredients with no concrete
 * ItemStack.
 *
 * <p>GTCEu 2.8 addons guard the result with a plain {@code if (stacks != null)}
 * check and then index {@code stacks[0]} unconditionally. See GTCEFuContent
 * {@code LinearForgingFurnaceLoader.produceForgingCooledRecipes}:
 *
 * <pre>
 *   ItemStack[] stacks = input.getInputStacks();
 *   if (stacks != null) {                                        // 2.8: null when absent
 *       MaterialStack ms = OreDictUnifier.getMaterial(stacks[0]); // AIOOBE on 2.9
 *   }
 * </pre>
 *
 * <p>GTQT 2.9 can return a zero-length array (e.g. {@code GTRecipeOreInput} when an
 * ore dictionary resolves to nothing), so the 2.8 null-check passes and the
 * {@code [0]} index throws {@code ArrayIndexOutOfBoundsException: Index 0 out of
 * bounds for length 0}, aborting the addon's whole recipe pass.
 *
 * <h3>Implementation</h3>
 * For every class that declares {@code getInputStacks()}, the ORIGINAL body is
 * left in place but renamed to {@code getInputStacks$compatRaw}; the public
 * {@code getInputStacks()} keeps its signature (so it still overrides the base
 * method and stays virtual) and simply returns {@code null} when the raw result
 * is empty. Non-empty and null results pass through untouched, so well-formed
 * ingredients behave exactly as before.
 */
public class GTRecipeInputStacksNormalizer implements IClassTransformer, Opcodes {

    private static final String BASE = "gregtech/api/recipes/ingredients/GTRecipeInput";
    private static final String DESC = "()[Lnet/minecraft/item/ItemStack;";
    private static final String RAW = "getInputStacks$compatRaw";

    private static final String[] TARGETS = {
            BASE,
            "gregtech/api/recipes/ingredients/GTRecipeItemInput",
            "gregtech/api/recipes/ingredients/GTRecipeOreInput",
            "gregtech/api/recipes/ingredients/GTRecipeFluidInput",
            "gregtech/api/recipes/ingredients/IntCircuitIngredient"
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) return basicClass;
        String internal = transformedName.replace('.', '/');

        boolean wanted = false;
        for (String s : TARGETS) {
            if (s.equals(internal)) { wanted = true; break; }
        }
        if (!wanted) return basicClass;

        try {
            return inject(internal, basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] GTRecipeInput normaliser failed for "
                    + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(String internal, byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        MethodNode original = null;
        for (MethodNode mn : cn.methods) {
            if ("getInputStacks".equals(mn.name) && DESC.equals(mn.desc)) {
                original = mn;
                break;
            }
        }
        // Only touch classes that actually DECLARE the method. Subclasses that
        // merely inherit it are already covered by the base-class wrapper.
        if (original == null || original.instructions == null) return basicClass;

        original.name = RAW;
        // Keep the renamed body but make sure it stays accessible to the wrapper
        // in the same class (private is fine, virtual dispatch is irrelevant here).
        original.access = original.access & ~(ACC_PUBLIC | ACC_PROTECTED) | ACC_PRIVATE;

        MethodNode wrapper = new MethodNode(ACC_PUBLIC, "getInputStacks", DESC, null, null);
        InsnList il = wrapper.instructions;
        LabelNode done = new LabelNode();
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKESPECIAL, internal, RAW, DESC, false));
        il.add(new VarInsnNode(ASTORE, 1));
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new JumpInsnNode(IFNULL, done));       // null  -> return null
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new InsnNode(ARRAYLENGTH));
        il.add(new JumpInsnNode(IFNE, done));         // non-empty -> return as-is
        il.add(new InsnNode(ACONST_NULL));            // empty -> null
        il.add(new InsnNode(ARETURN));
        il.add(done);
        il.add(new VarInsnNode(ALOAD, 1));
        il.add(new InsnNode(ARETURN));
        wrapper.maxStack = 2;
        wrapper.maxLocals = 2;
        cn.methods.add(wrapper);

        // The injected wrapper has branches, so StackMapTable frames must be
        // recomputed; COMPUTE_MAXS alone leaves stale/missing frames and the JVM
        // rejects the class with "Expecting a stackmap frame at branch target".
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] " + internal
                + ".getInputStacks: empty array normalised to null.");
        return cw.toByteArray();
    }
}
