package com.gtceu.compat.core.asm;

import org.objectweb.asm.Opcodes;

/**
 * Declarative description of ONE "2.8 API bridge" that {@link GenericBridgeTransformer}
 * injects into a GTCEu 2.9 / GTQT class at load time.
 *
 * <p>A bridge is a synthetic method whose name+descriptor is the signature an addon
 * compiled against GTCEu 2.8.10 expects. Its body loads {@code this} and the arguments,
 * applies optional primitive conversions ({@link #argOps}), calls a delegate method
 * that actually exists on the 2.9 host, adapts the result ({@link #returnOp} /
 * {@link #castReturn} / automatic POP for void bridges) and returns.
 *
 * <p>Because JVM method identity includes the return type, bridges such as
 * {@code getEUt()I} can coexist with the 2.9 {@code getEUt()J} method.
 *
 * <p>The transformer only injects a bridge when the delegate is present on the
 * transformed class, so the very same transformer is a no-op on a real 2.8 host.
 */
public final class BridgeSpec implements Opcodes {

    /** Internal name of the class to transform, e.g. {@code gregtech/api/recipes/RecipeBuilder}. */
    public final String target;
    /** Name of the synthetic method to inject (the old 2.8 method name). */
    public final String name;
    /** Descriptor of the synthetic method (the old 2.8 JVM descriptor). */
    public final String desc;
    /** ASM access flags of the synthetic method, e.g. {@code ACC_PUBLIC}. */
    public final int access;
    /** Invoke opcode used to call the delegate: INVOKEVIRTUAL / INVOKESTATIC / INVOKESPECIAL / INVOKEINTERFACE. */
    public final int opcode;
    /** Internal name of the delegate owner; {@code null} means the transformed class itself. */
    public final String owner;
    /** Delegate method name. */
    public final String delegateName;
    /** Delegate method descriptor (must exist on the 2.9 host). */
    public final String delegateDesc;
    /**
     * Optional conversion opcode applied right after loading argument {@code i},
     * e.g. {@link Opcodes#I2L} when 2.8 passes an {@code int} but 2.9 takes a {@code long}.
     * May be {@code null} or shorter than the argument count; {@code 0} means "no conversion".
     */
    public final int[] argOps;
    /** Optional opcode applied to the delegate's return value, e.g. {@link Opcodes#L2I}. */
    public final int returnOp;
    /** Optional internal name for a CHECKCAST on the delegate's return value. */
    public final String castReturn;
    /**
     * When true, replace the delegate's double return value {@code x} with {@code 1.0 / x}.
     * Used for 2.8 "divisor" getters that 2.9 re-expressed as "factor" multipliers.
     */
    public final boolean reciprocalReturn;

    public BridgeSpec(String target, String name, String desc, int access,
                      int opcode, String owner, String delegateName, String delegateDesc,
                      int[] argOps, int returnOp, String castReturn, boolean reciprocalReturn) {
        this.target = target;
        this.name = name;
        this.desc = desc;
        this.access = access;
        this.opcode = opcode;
        this.owner = owner;
        this.delegateName = delegateName;
        this.delegateDesc = delegateDesc;
        this.argOps = argOps;
        this.returnOp = returnOp;
        this.castReturn = castReturn;
        this.reciprocalReturn = reciprocalReturn;
    }

    /** Full-control factory (used by bridges needing access/reciprocal tuning). */
    public static BridgeSpec of(String target, String name, String desc, int access,
                               int opcode, String owner, String delegateName, String delegateDesc,
                               int[] argOps, int returnOp, String castReturn, boolean reciprocalReturn) {
        return new BridgeSpec(target, name, desc, access, opcode, owner,
                delegateName, delegateDesc, argOps, returnOp, castReturn, reciprocalReturn);
    }

    /**
     * Instance bridge: same class, same method name, different descriptor.
     * The delegate's return value is returned unchanged (or POPed when the bridge is void).
     */
    public static BridgeSpec virtual(String target, String name, String desc, String delegateDesc) {
        return new BridgeSpec(target, name, desc, ACC_PUBLIC,
                INVOKEVIRTUAL, null, name, delegateDesc, null, 0, null, false);
    }

    /** Protected instance bridge: matches 2.8 methods that addon subclasses could override. */
    public static BridgeSpec virtualProtected(String target, String name, String desc, String delegateDesc) {
        return new BridgeSpec(target, name, desc, ACC_PROTECTED,
                INVOKEVIRTUAL, null, name, delegateDesc, null, 0, null, false);
    }

    /** Protected instance bridge with primitive argument conversions. */
    public static BridgeSpec virtualProtected(String target, String name, String desc,
                                              String delegateDesc, int[] argOps) {
        return new BridgeSpec(target, name, desc, ACC_PROTECTED,
                INVOKEVIRTUAL, null, name, delegateDesc, argOps, 0, null, false);
    }

    /** Protected bridge whose double result must be inverted (2.8 divisor -> 2.9 factor). */
    public static BridgeSpec protectedReciprocal(String target, String name, String desc,
                                                  String delegateName, String delegateDesc) {
        return new BridgeSpec(target, name, desc, ACC_PROTECTED,
                INVOKEVIRTUAL, null, delegateName, delegateDesc, null, 0, null, true);
    }

    /** Instance bridge returning void that delegates to a value-returning method (result is discarded). */
    public static BridgeSpec voidVia(String target, String name, String desc, String delegateDesc) {
        return virtual(target, name, desc, delegateDesc);
    }

    /** Instance bridge with primitive argument conversions. */
    public static BridgeSpec virtual(String target, String name, String desc, String delegateDesc, int[] argOps) {
        return new BridgeSpec(target, name, desc, ACC_PUBLIC,
                INVOKEVIRTUAL, null, name, delegateDesc, argOps, 0, null, false);
    }

    /** Instance bridge with a return-value conversion, e.g. long -> int. */
    public static BridgeSpec virtualReturn(String target, String name, String desc,
                                            String delegateDesc, int returnOp) {
        return new BridgeSpec(target, name, desc, ACC_PUBLIC,
                INVOKEVIRTUAL, null, name, delegateDesc, null, returnOp, null, false);
    }

    /** Bridge that delegates to a method on another class. */
    public static BridgeSpec to(String target, String name, String desc,
                                int opcode, String owner, String delegateName, String delegateDesc) {
        return new BridgeSpec(target, name, desc, ACC_PUBLIC,
                opcode, owner, delegateName, delegateDesc, null, 0, null, false);
    }
}
