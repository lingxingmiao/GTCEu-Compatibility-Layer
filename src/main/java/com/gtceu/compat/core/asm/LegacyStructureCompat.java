package com.gtceu.compat.core.asm;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureRuntimeDetectionContext;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;

/**
 * Bridges a 2.8 {@code createStructurePattern() : BlockPattern} into GTQT 2.9's
 * {@code StructureDefinition} API.
 *
 * <p>2.9 made {@code MultiblockControllerBase.createStructureDefinition()} abstract
 * and replaced the whole 2.8 pattern system. 2.8-compiled multiblocks therefore
 * fail with
 * {@code AbstractMethodError: ... does not define createStructureDefinition()}
 * as soon as GTQT enumerates them (JEI preview registration / structure checks).
 *
 * <p>{@code <addon>:</addon>StructureDefinitionCompatTransformer} injects a
 * forwarding {@code createStructureDefinition()} into each such controller; it
 * calls {@link #legacyDefinition(MultiblockControllerBase)} here. The returned
 * definition carries a {@code runtimeDetector} that evaluates the controller's own
 * 2.8 {@code BlockPattern}, so the machine's real structure still decides whether
 * it forms — 2.9 only supplies the container.
 */
@SuppressWarnings("unchecked")
public final class LegacyStructureCompat {

    private LegacyStructureCompat() {}

    private static final Method CREATE_PATTERN;
    private static final Method REINIT;

    static {
        Method cp = null, ri = null;
        try {
            cp = MultiblockControllerBase.class.getDeclaredMethod("createStructurePattern");
            cp.setAccessible(true);
        } catch (Throwable ignored) {}
        try {
            ri = MultiblockControllerBase.class.getDeclaredMethod("reinitializeStructurePattern");
            ri.setAccessible(true);
        } catch (Throwable ignored) {}
        CREATE_PATTERN = cp;
        REINIT = ri;
    }

    /** Build a 2.9 StructureDefinition backed by the controller's 2.8 BlockPattern. */
    public static StructureDefinition<?> legacyDefinition(MultiblockControllerBase controller) {
        gregtech.api.pattern.element.StructureDefinition.Builder<MultiblockControllerBase> b =
                StructureDefinition.builder(RelativeDirection.LEFT, RelativeDirection.UP,
                        RelativeDirection.FRONT);
        // A StructureDefinition must contain >= 1 piece. Register a tiny hidden
        // runtime-only piece (a single air cell): the real structure is judged by
        // the 2.8 BlockPattern in the runtimeDetector, so this piece only exists to
        // satisfy the builder and must not appear in JEI tooling.
        try {
            b.piece("compat_stub", "~")
                    .where('~', gregtech.api.pattern.element.Elements.air())
                    .runtimeOnly()
                    .hideFromTooling()
                    .end();
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] stub piece registration failed: " + t);
        }
        b.runtimeDetector(new gregtech.api.pattern.StructureRuntimeDetector<MultiblockControllerBase>() {
            @Override
            public boolean detect(StructureRuntimeDetectionContext<MultiblockControllerBase> ctx) {
                return LegacyStructureCompat.detect(ctx);
            }
        });
        try {
            return b.build();
        } catch (Throwable t) {
            // Never let a definition-construction failure abort JEI/registration.
            return null;
        }
    }

    private static boolean detect(StructureRuntimeDetectionContext<?> ctx) {
        try {
            Object raw = ctx.getController();
            if (!(raw instanceof MultiblockControllerBase)) return false;
            MultiblockControllerBase controller = (MultiblockControllerBase) raw;
            BlockPattern pattern = getPattern(controller);
            if (pattern == null) return false;

            // 2.9 has no getUpwardsFacing(); 2.8's pattern check uses the
            // controller's front facing plus an "up" reference. UP is the
            // conventional value for normally placed machines.
            PatternMatchContext match = pattern.checkPatternCompat(
                    controller.getWorld(), controller.getPos(),
                    controller.getFrontFacing(), EnumFacing.UP, false);
            return match != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static BlockPattern getPattern(MultiblockControllerBase controller) {
        try {
            if (CREATE_PATTERN != null) {
                Object p = CREATE_PATTERN.invoke(controller);
                if (p instanceof BlockPattern) return (BlockPattern) p;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
