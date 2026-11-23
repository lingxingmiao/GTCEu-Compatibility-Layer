package com.gtceu.compat.core;

import com.gtceu.compat.api.GTCEuVersion;
import com.gtceu.compat.util.GTCEuCompatLogger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * GTCEu Compatibility Layer Main Mod Class
 * Provides backward and forward compatibility between GTCEu 2.8 and 2.9 APIs.
 * 
 * When running on GTCEu 2.8, provides utility methods for addons written for 2.9.
 * When running on GTCEu 2.9, provides utility methods for addons written for 2.8.
 * 
 * Uses pure reflection to access GTCEu APIs, no compile-time dependency required.
 */
@Mod(modid = GTCEuCompatMod.MODID,
     version = GTCEuCompatMod.VERSION,
     name = GTCEuCompatMod.NAME,
     acceptedMinecraftVersions = "[1.12.2]",
     dependencies = "required-after:forge@[14.23.5.2850,);required-after:gregtech@[2.8.0,);after:*")
public class GTCEuCompatMod {

    public static final String MODID = "gtceu_compat";
    public static final String VERSION = "1.8.1";
    public static final String NAME = "GTCEu Compatibility Layer";

    private static GTCEuVersion detectedVersion;
    private static boolean initialized = false;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        GTCEuCompatLogger.info("=== GTCEu Compatibility Layer ===");
        GTCEuCompatLogger.info("Initializing reflection-based compatibility...");

        // Detect GTCEu version
        detectedVersion = GTCEuVersionDetector.detectVersion();

        if (detectedVersion == null) {
            // Detection failed (no GTCEu container / no usable marker). Do NOT bail out:
            // fall back to UNKNOWN so the layer still initializes, but enables no shims (see onInit).
            GTCEuCompatLogger.warn("Failed to detect GTCEu version (GregTech CEu not found or unreadable); "
                    + "falling back to UNKNOWN and continuing initialization.");
            detectedVersion = GTCEuVersion.UNKNOWN;
        }

        if (detectedVersion == GTCEuVersion.UNKNOWN) {
            // Unknown build (e.g. a re-branded GTQT): keep the layer alive so adapters are
            // queryable, but do not enable any shim (see onInit) - guessing would mis-bridge.
            GTCEuCompatLogger.warn("GTCEu version is UNKNOWN; continuing with no version-specific shims enabled.");
        } else {
            GTCEuCompatLogger.info("Detected GTCEu version: {}", detectedVersion);
        }

        // Initialize version-specific adapters
        initializeAdapters(event);

        initialized = true;
        GTCEuCompatLogger.info("GTCEu Compatibility Layer initialized successfully (version: {}).",
                detectedVersion);
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        if (!initialized) {
            GTCEuCompatLogger.warn("Compatibility layer not initialized, skipping init phase.");
            return;
        }

        // Post-detection initialization for adapters
        if (detectedVersion == GTCEuVersion.V2_8) {
            GTCEuCompatLogger.info("Running on GTCEu 2.8 - 2.9 API utilities available");
            com.gtceu.compat.metatileentity.MetaTileEntityCompat.enableV29Shims();
            com.gtceu.compat.recipes.RecipeCompat.enableV29Shims();
            com.gtceu.compat.cover.CoverCompat.enableV29Shims();
            com.gtceu.compat.material.MaterialCompat.enableV29Shims();
            com.gtceu.compat.multiblock.MultiblockCompat.enableV29Shims();
            com.gtceu.compat.util.UnificationCompat.enableV29Shims();
        } else if (detectedVersion == GTCEuVersion.V2_9) {
            GTCEuCompatLogger.info("Running on GTCEu 2.9 - 2.8 API utilities available");
            com.gtceu.compat.metatileentity.MetaTileEntityCompat.enableV28Shims();
            com.gtceu.compat.recipes.RecipeCompat.enableV28Shims();
            com.gtceu.compat.cover.CoverCompat.enableV28Shims();
            com.gtceu.compat.material.MaterialCompat.enableV28Shims();
            com.gtceu.compat.multiblock.MultiblockCompat.enableV28Shims();
            com.gtceu.compat.util.UnificationCompat.enableV28Shims();
            // GTQT 2.9 restructured MetaTileEntities fields; snapshot the 2.8
            // single-object shape before any addon recipe pass reads it.
            try {
                com.gtceu.compat.core.asm.MetaTileEntitiesCompat.init();
            } catch (Throwable t) {
                GTCEuCompatLogger.warn("MetaTileEntitiesCompat.init failed: " + t);
            }
        } else {
            // UNKNOWN: do not enable either shim set, that would bridge the wrong API.
            GTCEuCompatLogger.warn("GTCEu version is UNKNOWN - no version-specific shims enabled. "
                    + "Compatibility utilities answer as UNKNOWN; set the GTCEu build explicitly if this persists.");
        }
    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
        if (!initialized) {
            return;
        }

        GTCEuCompatLogger.info("=== Compatibility Layer Ready ===");
        GTCEuCompatLogger.info("Use GTCEuCompat API methods in your addon code for cross-version support.");

        // Report any addon that still references a GTQT 2.9-removed 2.8 subsystem
        // (pattern.* structure framework, AE2 multiblock-part host, ...). These
        // cannot be restored by ASM bridges and need their own 2.9 build.
        try {
            // new File(".") is the instance/game working directory; no reliance on
            // FMLPostInitializationEvent (which has no config-dir accessor).
            GTCEuCompatDiagnostics.run(detectedVersion, new java.io.File("."));
        } catch (Throwable t) {
            GTCEuCompatLogger.warn("Diagnostics skipped: {}", t.toString());
        }
    }

    /**
     * Initialize version-specific adapters based on detected version.
     */
    private void initializeAdapters(FMLPreInitializationEvent event) {
        com.gtceu.compat.metatileentity.MetaTileEntityCompat.initialize(detectedVersion);
        com.gtceu.compat.recipes.RecipeCompat.initialize(detectedVersion);
        com.gtceu.compat.cover.CoverCompat.initialize(detectedVersion);
        com.gtceu.compat.material.MaterialCompat.initialize(detectedVersion);
        com.gtceu.compat.multiblock.MultiblockCompat.initialize(detectedVersion);
        com.gtceu.compat.util.UnificationCompat.initialize(detectedVersion);
    }

    /**
     * Gets the detected GTCEu version.
     */
    public static GTCEuVersion getDetectedVersion() {
        return detectedVersion;
    }

    /**
     * Checks if the compatibility layer has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
