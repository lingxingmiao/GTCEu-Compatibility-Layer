package com.gtceu.compat.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Remaps GTCEu 2.8's {@code ToolProperty} API onto GTQT 2.9's
 * {@code MaterialToolProperty} API inside GTCEFuContent.
 *
 * <p>GTCEFuContent 2.1.1-alpha was compiled against GTCEu 2.8.10, where the tool
 * builder was {@code ToolProperty.Builder} and
 * {@code Material.Builder.toolStats(ToolProperty)}. GTQT 2.9 renamed that class
 * to {@code MaterialToolProperty} (its nested {@code Builder} has identical
 * method signatures), and {@code toolStats} now takes {@code MaterialToolProperty}.
 *
 * <p>This transformer rewrites the constant pool of addon classes (GTCEFuContent
 * and GTExpert) so that every reference to
 * {@code gregtech/api/unification/material/properties/ToolProperty} becomes
 * {@code gregtech/api/unification/material/properties/MaterialToolProperty}.
 * Because {@code MaterialToolProperty extends ToolProperty}, this is source- and
 * binary-safe for all the builder calls these addons make.
 *
 * <p>Only known addon packages are transformed; GT core classes are untouched.
 */
public class ToolPropertyRemapTransformer implements IClassTransformer {

    private static final String[] CLASS_PREFIXES = {
            "com/m_w_k/gtcefucontent/",
            "com/github/gtexpert/"
    };

    private static final String OLD = "gregtech/api/unification/material/properties/ToolProperty";
    private static final String NEW = "gregtech/api/unification/material/properties/MaterialToolProperty";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        String internal = transformedName.replace('.', '/');
        boolean target = false;
        for (String prefix : CLASS_PREFIXES) {
            if (internal.startsWith(prefix)) {
                target = true;
                break;
            }
        }
        if (!target) {
            return basicClass;
        }
        try {
            return remap(basicClass, transformedName);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] ToolProperty remap failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] remap(byte[] basicClass, String transformedName) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        int changed = 0;
        changed += remapInterfaces(cn.interfaces);
        String newSig = remapSignature(cn.signature);
        if (newSig != null && !newSig.equals(cn.signature)) {
            cn.signature = newSig;
            changed++;
        }

        List<MethodNode> methods = new ArrayList<>(cn.methods);
        for (MethodNode mn : methods) {
            mn.desc = remapDesc(mn.desc);
            mn.signature = remapSignature(mn.signature);
            if (mn.instructions != null) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode m = (MethodInsnNode) insn;
                        String oldOwner = m.owner;
                        String oldDesc = m.desc;
                        m.owner = remapInternal(oldOwner);
                        m.desc = remapDesc(oldDesc);
                        if (!oldOwner.equals(m.owner) || !oldDesc.equals(m.desc)) changed++;
                    } else if (insn instanceof FieldInsnNode) {
                        FieldInsnNode f = (FieldInsnNode) insn;
                        String oldOwner = f.owner;
                        String oldDesc = f.desc;
                        f.owner = remapInternal(oldOwner);
                        f.desc = remapDesc(oldDesc);
                        if (!oldOwner.equals(f.owner) || !oldDesc.equals(f.desc)) changed++;
                    } else if (insn instanceof TypeInsnNode) {
                        TypeInsnNode ti = (TypeInsnNode) insn;
                        String oldDesc = ti.desc;
                        ti.desc = remapInternal(oldDesc);
                        if (!oldDesc.equals(ti.desc)) changed++;
                    } else if (insn instanceof LdcInsnNode) {
                        LdcInsnNode ldc = (LdcInsnNode) insn;
                        if (ldc.cst instanceof org.objectweb.asm.Type) {
                            org.objectweb.asm.Type t = (org.objectweb.asm.Type) ldc.cst;
                            String d = t.getDescriptor();
                            String nd = remapDesc(d);
                            if (!d.equals(nd)) {
                                ldc.cst = org.objectweb.asm.Type.getType(nd);
                                changed++;
                            }
                        } else if (ldc.cst instanceof String) {
                            String s = (String) ldc.cst;
                            String ns = remapDesc(s);
                            if (!s.equals(ns)) {
                                ldc.cst = ns;
                                changed++;
                            }
                        }
                    }
                }
            }
        }

        if (changed == 0) {
            return basicClass;
        }

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] ToolProperty -> MaterialToolProperty remap: " + transformedName
                + " (" + changed + " refs)");
        return cw.toByteArray();
    }

    private static int remapInterfaces(List<String> interfaces) {
        int n = 0;
        for (int i = 0; i < interfaces.size(); i++) {
            String r = remapInternal(interfaces.get(i));
            if (!r.equals(interfaces.get(i))) {
                interfaces.set(i, r);
                n++;
            }
        }
        return n;
    }

    private static String remapSignature(String sig) {
        return sig == null ? null : remapDesc(sig);
    }

    private static String remapInternal(String internalName) {
        if (internalName == null) return null;
        if (internalName.equals(OLD) || internalName.equals(OLD + "$Builder")) {
            return NEW + internalName.substring(OLD.length());
        }
        return internalName;
    }

    /**
     * Remap the old internal name inside arbitrary descriptors / signatures.
     * Uses a plain textual replacement because {@code ToolProperty$Builder}
     * contains a {@code $} which is legal inside a descriptor's class name.
     */
    private static String remapDesc(String desc) {
        if (desc == null) return null;
        if (!desc.contains(OLD)) return desc;
        return desc.replace(OLD + "$Builder", NEW + "$Builder").replace(OLD, NEW);
    }
}
