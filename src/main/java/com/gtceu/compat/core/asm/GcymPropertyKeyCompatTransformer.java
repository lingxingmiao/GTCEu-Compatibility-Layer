package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes Gregicality Multiblocks (GCYM) 1.2.11 use GTQT 2.9's built-in
 * {@code PropertyKey.ALLOY_BLAST} instead of registering a second, incompatible key.
 *
 * <p>GCYM 1.2.11 was compiled against GTCEu 2.8, where alloy-blast support lived
 * in the addon. GTQT 2.9 absorbed it into the core:
 * <ul>
 *   <li>{@code gregtech.api.unification.material.properties.PropertyKey.ALLOY_BLAST}</li>
 *   <li>{@code gregtech.api.unification.material.properties.AlloyBlastProperty}</li>
 *   <li>{@code gregtech.api.recipes.alloyblast.AlloyBlastRecipeProducer}</li>
 * </ul>
 * GCYM still creates {@code new PropertyKey<>("blast_alloy", gcym.AlloyBlastProperty.class)}
 * in {@code GCYMPropertyKey.<clinit>}. Because GTQT already registered a key with the
 * same name whose type is the core AlloyBlastProperty, reading a material's property
 * through the key performs {@code Class.cast} to the core type and throws
 * {@code ClassCastException: Cannot cast gregicality...AlloyBlastProperty to gregtech...AlloyBlastProperty}.
 *
 * <p>This transformer does two things on GCYM classes only:
 * <ol>
 *   <li>Rewrites {@code GCYMPropertyKey.<clinit>} to
 *       {@code GETSTATIC PropertyKey.ALLOY_BLAST; PUTSTATIC GCYMPropertyKey.ALLOY_BLAST}
 *       so both names refer to the core key instance.</li>
 *   <li>Rewrites every reference from GCYM's own {@code AlloyBlastProperty} /
 *       {@code AlloyBlastRecipeProducer} to the core equivalents in all GCYM classes,
 *       so the object stored under the core key is itself a core AlloyBlastProperty.</li>
 * </ol>
 */
public class GcymPropertyKeyCompatTransformer implements IClassTransformer, Opcodes {

    private static final String PREFIX = "gregicality/multiblocks/";
    private static final String GCYM_KEY = "gregicality/multiblocks/api/unification/properties/GCYMPropertyKey";
    private static final String CORE_KEY = "gregtech/api/unification/material/properties/PropertyKey";
    private static final String KEY_DESC = "Lgregtech/api/unification/material/properties/PropertyKey;";
    private static final String GCYM_PROP = "gregicality/multiblocks/api/unification/properties/AlloyBlastProperty";
    private static final String CORE_PROP = "gregtech/api/unification/material/properties/AlloyBlastProperty";
    private static final String GCYM_PRODUCER = "gregicality/multiblocks/api/recipes/alloyblast/AlloyBlastRecipeProducer";
    private static final String CORE_PRODUCER = "gregtech/api/recipes/alloyblast/AlloyBlastRecipeProducer";

    private static final String[][] REMAPS = {
            {GCYM_PROP, CORE_PROP},
            {GCYM_PRODUCER, CORE_PRODUCER}
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) {
            return basicClass;
        }
        String internal = transformedName.replace('.', '/');
        if (!internal.startsWith(PREFIX)) {
            return basicClass;
        }
        try {
            return rewrite(internal, basicClass, transformedName);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] GCYM property key compat failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] rewrite(String internal, byte[] basicClass, String transformedName) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        int changed = 0;
        if (GCYM_KEY.equals(internal)) {
            changed += rewriteClinit(cn);
        }
        changed += remapClass(cn);

        if (changed == 0) {
            return basicClass;
        }
        // COMPUTE_FRAMES is REQUIRED here: changing a local variable's type also
        // changes the StackMapTable frames at every branch target. COMPUTE_MAXS
        // alone leaves the old gregicality...AlloyBlastProperty type in the frames
        // and the JVM rejects the class with VerifyError.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] GCYM alloy-blast compat: " + transformedName
                + " (" + changed + " edits)");
        return cw.toByteArray();
    }

    /**
     * Replace the whole static initialiser of GCYMPropertyKey with a single alias
     * assignment: {@code ALLOY_BLAST = PropertyKey.ALLOY_BLAST}.
     */
    private static int rewriteClinit(ClassNode cn) {
        for (MethodNode mn : new ArrayList<>(cn.methods)) {
            if (!"<clinit>".equals(mn.name)) continue;
            InsnList il = new InsnList();
            il.add(new FieldInsnNode(GETSTATIC, CORE_KEY, "ALLOY_BLAST", KEY_DESC));
            il.add(new FieldInsnNode(PUTSTATIC, GCYM_KEY, "ALLOY_BLAST", KEY_DESC));
            il.add(new InsnNode(RETURN));
            mn.instructions = il;
            mn.maxStack = 1;
            mn.maxLocals = 0;
            return 1;
        }
        return 0;
    }

    /** Rewrite GCYM -> core property/producer references in descriptors and instructions. */
    private static int remapClass(ClassNode cn) {
        int changed = 0;
        int[] n = {0};
        cn.superName = remapInternal(cn.superName, n);
        cn.signature = remapText(cn.signature, n);
        for (int i = 0; i < cn.interfaces.size(); i++) {
            cn.interfaces.set(i, remapInternal(cn.interfaces.get(i), n));
        }
        for (org.objectweb.asm.tree.FieldNode fn : cn.fields) {
            fn.desc = remapText(fn.desc, n);
            fn.signature = remapText(fn.signature, n);
        }
        for (MethodNode mn : cn.methods) {
            mn.desc = remapText(mn.desc, n);
            mn.signature = remapText(mn.signature, n);
            if (mn.instructions == null) continue;
            for (AbstractInsnNode insn : mn.instructions) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) insn;
                    m.owner = remapInternal(m.owner, n);
                    m.desc = remapText(m.desc, n);
                } else if (insn instanceof FieldInsnNode) {
                    FieldInsnNode f = (FieldInsnNode) insn;
                    f.owner = remapInternal(f.owner, n);
                    f.desc = remapText(f.desc, n);
                } else if (insn instanceof TypeInsnNode) {
                    TypeInsnNode t = (TypeInsnNode) insn;
                    t.desc = remapInternal(t.desc, n);
                } else if (insn instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if (ldc.cst instanceof org.objectweb.asm.Type) {
                        String nd = remapText(((org.objectweb.asm.Type) ldc.cst).getDescriptor(), n);
                        ldc.cst = org.objectweb.asm.Type.getType(nd);
                    } else if (ldc.cst instanceof org.objectweb.asm.Handle) {
                        org.objectweb.asm.Handle h = (org.objectweb.asm.Handle) ldc.cst;
                        ldc.cst = new org.objectweb.asm.Handle(h.getTag(),
                                remapInternal(h.getOwner(), n), h.getName(),
                                remapText(h.getDesc(), n), h.isInterface());
                    } else if (ldc.cst instanceof String) {
                        ldc.cst = remapText((String) ldc.cst, n);
                    }
                } else if (insn instanceof InvokeDynamicInsnNode) {
                    // Method references to generateAlloyBlastRecipes are embedded in
                    // invokedynamic bsmArgs (Handle + Type). Without rewriting those,
                    // the declaration has the core AlloyBlastProperty descriptor while
                    // the method reference still calls the GCYM one -> NoSuchMethodError.
                    InvokeDynamicInsnNode idy = (InvokeDynamicInsnNode) insn;
                    idy.desc = remapText(idy.desc, n);
                    for (int a = 0; a < idy.bsmArgs.length; a++) {
                        Object arg = idy.bsmArgs[a];
                        if (arg instanceof org.objectweb.asm.Handle) {
                            org.objectweb.asm.Handle h = (org.objectweb.asm.Handle) arg;
                            idy.bsmArgs[a] = new org.objectweb.asm.Handle(h.getTag(),
                                    remapInternal(h.getOwner(), n), h.getName(),
                                    remapText(h.getDesc(), n), h.isInterface());
                        } else if (arg instanceof org.objectweb.asm.Type) {
                            org.objectweb.asm.Type t = (org.objectweb.asm.Type) arg;
                            idy.bsmArgs[a] = org.objectweb.asm.Type.getType(
                                    remapText(t.getDescriptor(), n));
                        }
                    }
                }
            }
        }
        changed += n[0];
        return changed;
    }

    private static String remapInternal(String s, int[] changed) {
        if (s == null) return null;
        String r = s;
        for (String[] m : REMAPS) {
            if (r.startsWith(m[0])) {
                r = m[1] + r.substring(m[0].length());
                changed[0]++;
            }
        }
        return r;
    }

    private static String remapText(String s, int[] changed) {
        if (s == null) return null;
        String r = s;
        for (String[] m : REMAPS) {
            if (r.contains(m[0])) {
                r = r.replace(m[0], m[1]);
                changed[0]++;
            }
        }
        return r;
    }
}
