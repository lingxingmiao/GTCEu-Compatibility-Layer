package com.gtceu.compat.core.asm;

import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityDataAccessHatch;

/**
 * Holds 2.8 {@code MetaTileEntities} fields that GTQT 2.9 restructured.
 *
 * <p>2.8 declared a single {@code ADVANCED_DATA_ACCESS_HATCH}; 2.9 replaced it
 * with a per-tier {@code DATA_ACCESS_HATCH[]} array. 2.8 addons (GTExpert
 * GTERecipe.end_contents) still read the old single static field, so
 * {@code AddonPackageRemapTransformer} retargets that GETSTATIC here.
 *
 * <p>{@link #init()} must be called once GTQT has finished registering its MTEs
 * (from the compat mod's postInit), before any addon recipe pass reads the field.
 */
public final class MetaTileEntitiesCompat {

    /**
     * 2.8 "advanced" data access hatch. Assigned in {@link #init()}.
     * Deliberately a public static field (not a getter) so a plain GETSTATIC
     * from 2.8 addon bytecode resolves it without instruction rewriting.
     */
    public static MetaTileEntityDataAccessHatch ADVANCED_DATA_ACCESS_HATCH;

    private MetaTileEntitiesCompat() {}

    /**
     * Returns the 2.8 "advanced" data access hatch, resolving lazily so it works
     * no matter when the addon reads it (GTQT may fill the array after our
     * postInit hook).
     */
    public static MetaTileEntityDataAccessHatch resolve() {
        MetaTileEntityDataAccessHatch current = ADVANCED_DATA_ACCESS_HATCH;
        if (current == null) {
            init();
            current = ADVANCED_DATA_ACCESS_HATCH;
        }
        return current;
    }

    /**
     * 2.8 {@code CREATIVE_DATA_HATCH} was removed in 2.9. Approximate it with the
     * highest-tier entry of {@code DATA_ACCESS_HATCH} (what creative data hatches
     * were superseded by); GTExpert only needs a valid MTE for recipe input.
     */
    public static MetaTileEntityDataAccessHatch resolveCreative() {
        try {
            MetaTileEntityDataAccessHatch[] all = MetaTileEntities.DATA_ACCESS_HATCH;
            if (all == null) return null;
            for (int i = all.length - 1; i >= 0; i--) {
                if (all[i] != null) return all[i];
            }
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] resolveCreative failed: " + t);
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // 2.8 ME hatches (ITEM_IMPORT_BUS_ME etc.)
    //
    // GTQT 2.9 deleted these fields: the ME hatches are now the GTQT "dual/ME
    // hatch" family and are not exposed as MetaTileEntities.* statics. prismplan's
    // creative tab calls MetaTileEntities.ITEM_IMPORT_BUS_ME.getStackForm() to
    // build its tab icon; the missing field throws NoSuchFieldError inside
    // GuiContainerCreative.drawTab, which breaks rendering of the creative tab
    // page (the "second page" of the creative inventory).
    //
    // Substitutes: the closest surviving 2.9 machine of the same kind. They are
    // only used as an icon / representative item here, never as a functional ME
    // hatch, so a normal item bus is an adequate stand-in.
    // ---------------------------------------------------------------------

    /** 2.8 ITEM_IMPORT_BUS_ME -> 2.9's ME (dual) import hatch. */
    public static gregtech.api.metatileentity.MetaTileEntity resolveItemImportBusMe() {
        gregtech.api.metatileentity.MetaTileEntity me = firstDualImportHatch();
        return me != null ? me : firstItemBus();
    }

    /** 2.8 ITEM_EXPORT_BUS_ME -> 2.9's ME (dual) export hatch. */
    public static gregtech.api.metatileentity.MetaTileEntity resolveItemExportBusMe() {
        gregtech.api.metatileentity.MetaTileEntity me = firstDualExportHatch();
        if (me != null) return me;
        try {
            gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus[] all =
                    MetaTileEntities.ITEM_EXPORT_BUS;
            if (all != null) {
                for (gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus b : all) {
                    if (b != null) return b;
                }
            }
        } catch (Throwable ignored) {}
        return firstItemBus();
    }

    /** 2.8 STOCKING_BUS_ME -> ME import hatch stand-in. */
    public static gregtech.api.metatileentity.MetaTileEntity resolveStockingBusMe() {
        return resolveItemImportBusMe();
    }

    /** 2.8 FLUID_IMPORT_HATCH_ME -> 2.9's ME import hatch, else fluid import hatch. */
    public static gregtech.api.metatileentity.MetaTileEntity resolveFluidImportHatchMe() {
        gregtech.api.metatileentity.MetaTileEntity me = firstDualImportHatch();
        return me != null ? me : firstFluidHatch(true);
    }

    /** 2.8 FLUID_EXPORT_HATCH_ME -> 2.9's ME export hatch, else fluid export hatch. */
    public static gregtech.api.metatileentity.MetaTileEntity resolveFluidExportHatchMe() {
        gregtech.api.metatileentity.MetaTileEntity me = firstDualExportHatch();
        return me != null ? me : firstFluidHatch(false);
    }

    /** 2.8 STOCKING_HATCH_ME -> ME import hatch stand-in. */
    public static gregtech.api.metatileentity.MetaTileEntity resolveStockingHatchMe() {
        return resolveFluidImportHatchMe();
    }

    private static gregtech.api.metatileentity.MetaTileEntity firstDualImportHatch() {
        try {
            gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityDualHatch[] all =
                    MetaTileEntities.DUAL_IMPORT_HATCH;
            if (all != null) {
                for (gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityDualHatch h : all) {
                    if (h != null) return h;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static gregtech.api.metatileentity.MetaTileEntity firstDualExportHatch() {
        try {
            gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityDualHatch[] all =
                    MetaTileEntities.DUAL_EXPORT_HATCH;
            if (all != null) {
                for (gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityDualHatch h : all) {
                    if (h != null) return h;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static gregtech.api.metatileentity.MetaTileEntity firstItemBus() {
        try {
            gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus[] all =
                    MetaTileEntities.ITEM_IMPORT_BUS;
            if (all != null) {
                for (gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus b : all) {
                    if (b != null) return b;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static gregtech.api.metatileentity.MetaTileEntity firstFluidHatch(boolean importSide) {
        try {
            gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityFluidHatch[] all =
                    importSide ? MetaTileEntities.FLUID_IMPORT_HATCH
                            : MetaTileEntities.FLUID_EXPORT_HATCH;
            if (all != null) {
                for (gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityFluidHatch h : all) {
                    if (h != null) return h;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Snapshot 2.9's per-tier array into the 2.8 single-field shape. */
    public static void init() {
        try {
            MetaTileEntityDataAccessHatch[] all = MetaTileEntities.DATA_ACCESS_HATCH;
            if (all == null || all.length == 0) return;
            // 2 = EV tier in the ULV-first array (matches 2.8's "advanced");
            // fall back to the first populated entry if that tier is absent.
            int idx = Math.min(2, all.length - 1);
            MetaTileEntityDataAccessHatch picked = all[idx];
            if (picked == null) {
                for (MetaTileEntityDataAccessHatch candidate : all) {
                    if (candidate != null) { picked = candidate; break; }
                }
            }
            ADVANCED_DATA_ACCESS_HATCH = picked;
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] MetaTileEntitiesCompat.init failed: " + t);
        }
    }
}
