package com.gtceu.compat.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.Name;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;

/**
 * FML Loading Plugin for GTCEu Compatibility Layer.
 * Registers the ASM {@code IClassTransformer}s that adapt GTCEu 2.8 addon bytecode
 * and inject 2.8 API shims into GTQT 2.9 classes at load time. This project does
 * NOT use Mixin ({@code use_mixins = false}); all byte code changes go through
 * the transformer list below.
 */
@Name("GTCEuCompatLoadingPlugin")
@MCVersion("1.12.2")
@SortingIndex(1001) // Load after most mods but before GTCEu addons
public class GTCEuCompatLoadingPlugin implements IFMLLoadingPlugin {

    public GTCEuCompatLoadingPlugin() {
        System.out.println("[GTCEuCompat] Loading plugin initialized");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
                // Rewrites 2.8-only class names in ALL addon packages (ToolProperty,
                // ToolProperty$Builder, recipeproperties.*) to their 2.9 equivalents.
                // Must run before the dedicated shims below.
                "com.gtceu.compat.core.asm.AddonPackageRemapTransformer",
                // Injects missing Elements.add(long,long,long,...) into GTQT 2.9.0
                "com.gtceu.compat.core.asm.ElementsCompatTransformer",
                // Re-adds the removed FluidProperty.getStorage() method
                "com.gtceu.compat.core.asm.FluidPropertyCompatTransformer",
                // Reuses core's MaterialIconType.molten instead of GCYM re-registering it
                "com.gtceu.compat.core.asm.MaterialIconTypeDedupTransformer",
                // Injects public Element(long,long,long,...) ctor for GTExpert's GTEElements
                "com.gtceu.compat.core.asm.ElementCtorCompatTransformer",
                // Declarative 2.8.10 API bridges for changed/retyped GTQT 2.9 methods
                "com.gtceu.compat.core.asm.GenericBridgeTransformer",
                // Restores Material$Builder.toolStats(ToolProperty) with a CHECKCAST
                "com.gtceu.compat.core.asm.ToolPropertyBridgeTransformer",
                // Restores selected 2.8 static fields / aliases on GTQT 2.9
                "com.gtceu.compat.core.asm.FieldCompatTransformer",
                // Restores selected 2.8 MetaTileEntity methods with safe fallbacks
                "com.gtceu.compat.core.asm.MetaTileEntityBridgeTransformer",
                // Restores 2.8 IRecipePropertyStorage method names on the 2.9 interface
                "com.gtceu.compat.core.asm.RecipePropertyStorageBridgeTransformer",
                // GCYM alloy-blast property: reuse GTQT 2.9 core PropertyKey.ALLOY_BLAST
                "com.gtceu.compat.core.asm.GcymPropertyKeyCompatTransformer",
                // Enum -> IStringSerializable erasure bridges on VariantBlock hierarchy
                "com.gtceu.compat.core.asm.VariantBlockBridgeTransformer",
                // NOTE: MTEManagerCompatTransformer is intentionally DISABLED.
                // Auto-creating per-mod MTE registries made GTQT 2.9's
                // CommonProxy.registerBlocks iterate registries whose getBlock()
                // is still null -> "Can't use a null-name for the registry" NPE.
                // 2.8 addons now register through the normal gregtech registry.
                // Relax only the namespace check on that registry (no new registries).
                "com.gtceu.compat.core.asm.MTERegistryNamespaceCompatTransformer",
                // Auto-resolve hardcoded 2.8 addon MTE ids colliding with 2.9
                "com.gtceu.compat.core.asm.MTERegistryIdCollisionTransformer",
                // Skip unregistered duplicate materials during pipe registration
                "com.gtceu.compat.core.asm.PipeMaterialSkipTransformer",
                // Re-register a material's fluid if the core walk missed it
                "com.gtceu.compat.core.asm.MaterialFluidFallbackTransformer",
                // Skip GTFO's 2.8-only crafting-component machine recipe pass
                "com.gtceu.compat.core.asm.GTFOMachineRecipesSkipTransformer",
                // Restore 2.8 ResearchRecipeBuilder.EUt(int) (2.9 widened to long)
                "com.gtceu.compat.core.asm.ResearchRecipeBuilderEUtBridgeTransformer",
                // Default CWU/t when a 2.8 stationResearch lambda leaves it zero
                "com.gtceu.compat.core.asm.StationRecipeBuilderCWUFallbackTransformer",
                // Restore 2.8 OreDictUnifier.registerOre(ItemStack, ItemMaterialInfo)
                "com.gtceu.compat.core.asm.OreDictUnifierMaterialInfoBridgeTransformer",
                // Restore 2.8 WireProperties.getVoltage():int (2.9 widened to long)
                "com.gtceu.compat.core.asm.WirePropertiesVoltageBridgeTransformer",
                // Skip ZBGT's alloyBlast pass (2.8 EBF_GASES map shape removed in 2.9)
                "com.gtceu.compat.core.asm.ZBGTAlloyBlastSkipTransformer",
                // Skip ZBGT's 2.8-only assembler build action
                "com.gtceu.compat.core.asm.ZBGTAutomationSkipTransformer",
                // Restore 2.8 Recipe.getOutputs():NonNullList. 2.8 itself builds
                // NonNullList via NonNullList.func_193580_a(Object,Object[]) (see
                // GTUtility.copyStackList), so that factory name is confirmed.
                "com.gtceu.compat.core.asm.RecipeGetOutputsBridgeTransformer",
                // NOTE: GTRecipeInputStacksNormalizer is DISABLED. Turning empty arrays
                // into null breaks 2.9's own callers (GTRecipeOreInput.acceptsStack
                // dereferences the result without a null check). The addon-side
                // call sites are guarded instead.
                // "com.gtceu.compat.core.asm.GTRecipeInputStacksNormalizer",
                // Guard addon getInputStacks()[0] sites against 2.9''s empty arrays
                "com.gtceu.compat.core.asm.GetInputStacksEmptyGuardTransformer",
                // Recipe.propertyStorage(): EMPTY -> writable impl
                "com.gtceu.compat.core.asm.WritablePropertyStorageTransformer",
                // GTCEFuContent''s late passes now run for real (were stubbed while
                // getOutputs() was unbridged); keep the class for regressions only.
                // "com.gtceu.compat.core.asm.AddonRecipePassSkipTransformer",
                // Inject 2.9 createStructureDefinition() into 2.8 multiblocks
                "com.gtceu.compat.core.asm.StructureDefinitionCompatTransformer",
                // Bridge SRG-named getters to MCP names (BlockWireCoil CoilType)
                "com.gtceu.compat.core.asm.SrgGetterNameBridgeTransformer",
                // Restore 2.8 Recipe 12-arg constructor
                "com.gtceu.compat.core.asm.RecipeCtorBridgeTransformer",
                // Restore 2.8 RecipeBuilder.isCTRecipe field/method
                "com.gtceu.compat.core.asm.RecipeBuilderFieldCompatTransformer",
                // Restore 2.8 RecipeBuilder.finalizeAndValidate()
                "com.gtceu.compat.core.asm.FinalizeAndValidateBridgeTransformer",
                // Restore 2.8 SimpleRecipeBuilder.withRecycling()
                "com.gtceu.compat.core.asm.SimpleRecipeBuilderRecyclingBridgeTransformer",
                // Restore 2.8 RecipeMap constructors (UI function supplied by compat layer)
                "com.gtceu.compat.core.asm.RecipeMapCtorBridgeTransformer",
                // Restore 2.8 MultiblockAbility(String) constructor
                "com.gtceu.compat.core.asm.MultiblockAbilityCtorBridgeTransformer",
                // Restore 2.8 RecipeMapSteamMultiblockController 3-arg constructor
                "com.gtceu.compat.core.asm.SteamMultiblockCtorBridgeTransformer",
                // Restore 2.8 MultiblockFuelRecipeLogic(RecipeMapMultiblockController) ctor
                "com.gtceu.compat.core.asm.FuelRecipeLogicCtorBridgeTransformer",
                // Table-driven bridges for ctors 2.9 extended with a trailing arg
                "com.gtceu.compat.core.asm.AppendedArgCtorBridgeTransformer"
        };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        System.out.println("[GTCEuCompat] Injecting data, environment: " + data.get("runtimeDeobfuscationEnabled"));
        // Run the incompatibility report at coremod time, BEFORE any mod's preInit.
        // Addons with a removed 2.8 parent class (e.g. Gregified Energistics) crash in
        // preInit, so a postInit diagnostic would never be reached.
        try {
            Object mcLocation = data.get("mcLocation"); // the game dir itself (contains mods/)
            File gameDir = mcLocation instanceof File ? (File) mcLocation : new File(".");
            GTCEuCompatDiagnostics.runEarly(gameDir);
        } catch (Throwable t) {
            System.err.println("[GTCEuCompat] Early diagnostics skipped: " + t);
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}

