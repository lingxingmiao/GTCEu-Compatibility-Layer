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
import org.objectweb.asm.tree.VarInsnNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges SRG-named getters to their MCP names where GTQT 2.9 mixes the two.
 *
 * <p>Observed: {@code BlockWireCoil$Builder$1} (a 2.9 anonymous CoilType) only
 * implements {@code func_176610_l()}, while
 * {@code CasingDefinition.fromMap} invokes the interface method
 * {@code IHeatingCoilBlockStats.getName()} by MCP name. The mismatch surfaces at
 * loadComplete as
 * {@code AbstractMethodError: ... does not define or inherit 'abstract String getName()'}.
 *
 * <p>Each row maps a class to alias methods that simply forward to the
 * existing SRG-named implementation.
 */
public class SrgGetterNameBridgeTransformer implements IClassTransformer, Opcodes {

    /** class -> { MCP name, SRG name } pairs to alias. */
    private static final Map<String, String[][]> ALIASES = new LinkedHashMap<>();

    /**
     * Classes handled generically: any CoilType implementation whose name
     * contains one of these fragments gets a getName() alias when it only has
     * the SRG-named func_176610_l(). 2.8 addons register their own heating coils
     * (ZBGT CreativeHeatingCoil, GTExpert GTEBlockWireCoil, ...), so an explicit
     * per-class list would need updating for every addon.
     */
    private static final String[] COIL_TYPE_HINTS = {"CoilType", "HeatingCoil"};

    static {
        ALIASES.put("gregtech/common/blocks/BlockWireCoil$Builder$1", new String[][]{
                {"getName", "func_176610_l"}
        });
        ALIASES.put("gregtech/common/blocks/BlockWireCoil$CoilType",
                new String[][]{{"getName", "func_176610_l"}});
        ALIASES.put("com/zorbatron/zbgt/common/block/blocks/CreativeHeatingCoil$CoilType",
                new String[][]{{"getName", "func_176610_l"}});
        ALIASES.put("com/github/gtexpert/core/common/blocks/GTEBlockWireCoil$GTECoilType",
                new String[][]{{"getName", "func_176610_l"}});
    }

    private static boolean looksLikeCoilType(String internal) {
        for (String hint : COIL_TYPE_HINTS) {
            if (internal.contains(hint)) return true;
        }
        return false;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null) return basicClass;
        String internal = transformedName.replace('.', '/');
        String[][] rows = ALIASES.get(internal);
        if (rows == null) {
            // Fallback: any addon heating-coil enum class.
            if (!looksLikeCoilType(internal)) return basicClass;
            rows = new String[][]{{"getName", "func_176610_l"}};
        }
        try {
            return inject(internal, rows, basicClass);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] SRG getter alias failed for " + transformedName + ": " + t);
            t.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] inject(String internal, String[][] rows, byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        int added = 0;
        for (String[] row : rows) {
            String mcp = row[0], srg = row[1];
            boolean hasMcp = false, hasSrg = false;
            for (MethodNode mn : cn.methods) {
                if (mcp.equals(mn.name) && "()Ljava/lang/String;".equals(mn.desc)) hasMcp = true;
                if (srg.equals(mn.name) && "()Ljava/lang/String;".equals(mn.desc)) hasSrg = true;
            }
            if (hasMcp || !hasSrg) continue;

            MethodNode mn = new MethodNode(ACC_PUBLIC, mcp, "()Ljava/lang/String;", null, null);
            InsnList il = mn.instructions;
            il.add(new VarInsnNode(ALOAD, 0));
            il.add(new MethodInsnNode(INVOKEVIRTUAL, internal, srg, "()Ljava/lang/String;", false));
            il.add(new InsnNode(ARETURN));
            cn.methods.add(mn);
            added++;
        }
        if (added == 0) return basicClass;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[GTCEuCompat] " + internal + ": aliased " + added
                + " SRG getter(s) to MCP names.");
        return cw.toByteArray();
    }
}
