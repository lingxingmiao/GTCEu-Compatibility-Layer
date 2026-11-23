# Changelog

## [1.8.1] - 2026-11-23 (restore recipes that were stubbed to get the pack booting)

1.8.0 reached the main menu by **stubbing** several 2.8 recipe passes. Those stubs
meant machines had no recipes. This release restores them for real for
GTCEFuContent; the remaining stubs are tracked at the bottom.

### GTCEFuContent — recipes restored (verified in a real launch)

Three separate contracts had to be fixed, each found by re-running with the stub
removed:

1. **`Recipe.getOutputs()` returned `NonNullList`, 2.9 returns `List`.**
   The earlier bridge guessed the factory name (`func_191197_a`) and silently
   failed. 2.8's own `GTUtility.copyStackList` reveals the correct runtime
   factory: **`NonNullList.func_193580_a(Object, Object[])`**. The bridge now does
   `NonNullList.func_193580_a(ItemStack.EMPTY, getOutputs().toArray())`.
   → re-enabled `RecipeGetOutputsBridgeTransformer`; the recipe-pass skipping is
   now disabled.

2. **`RecipePropertyStorage.EMPTY.store(...)` throws `UnsupportedOperationException: empty`.**
   2.8 addons write into a recipe's storage directly
   (`LinearForgingFurnaceLoader.assignRecipeType`). New
   `WritablePropertyStorageTransformer` wraps `Recipe.propertyStorage()` and
   substitutes a lazily-created **cached** `RecipePropertyStorageImpl` whenever the
   recipe carries only the read-only `EMPTY` singleton. Non-empty storages pass
   through untouched, so 2.9's own recipes keep their exact behaviour.

3. **`GTRecipeInput.getInputStacks()` can return a zero-length array on 2.9.**
   2.8 addons guard with `if (stacks != null)` and then read `stacks[0]`
   (`produceForgingCooledRecipes`), so the empty array threw
   `ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0`.
   *The first attempt was wrong*: normalising the getter (empty → null) broke 2.9's
   own `GTRecipeOreInput.acceptsStack`, which dereferences the result without a
   null check. That normaliser is therefore **disabled**, and
   `GetInputStacksEmptyGuardTransformer` guards the **addon call sites** instead,
   rewriting
   `ALOAD n ; IFNULL skip` into
   `DUP ; ARRAYLENGTH ; IFNE ok ; POP ; GOTO skip ; ok: IFNULL skip`
   so an empty array takes exactly the branch the 2.8 `null` case already took.
   Pattern matching skips ASM Label/LineNumber pseudo-nodes (opcode < 0).

4. **`RecipePropertyStorageImpl.store` rejected duplicates and `null`.**
   2.8's `RecipeBuilder.applyProperty` treated `applyProperty(prop, null)` as
   **remove the property**. 2.9 dropped that branch and forwards `null` to
   `store`, which returns `false`; `applyProperty` then sets
   `recipePropertyStorageErrored = true`, so `finalizeAndValidate()` reports
   INVALID and **the whole recipe is discarded**.
   New `RecipePropertyStoreOverrideTransformer` rewrites `store`:
   `null` → `map.remove(prop)` + return true; duplicate key → overwrite;
   a genuine `ClassCastException` still returns `false` so real authoring errors
   stay errors.
   → measured effect: rejected `lff_blast_cooled` recipes **1009 → 0**.

5. **`RecipeBuilder.validate` hard-failed on category/RecipeMap identity.**
   2.9 requires `category.getRecipeMap() == this.recipeMap` by **reference
   identity**, otherwise it logs "Cannot apply Category with incompatible
   RecipeMap" and marks the recipe INVALID. 2.8 addons resolve a category by name
   (`GTRecipeCategory.getByName(map.getUnlocalizedName())`) and attach it to their
   own map object, so the identity check fails.
   `CategoryMapCompatTransformer` rewrites the `INVALID` store at that site to
   `VALID`; genuinely broken recipes still fail their own output validation.
   → measured effect: rejected `alloy_blast_smelter` recipes **20 → 0**.

### Verified

Real Cleanroom launch with the built jar: `LoaderExceptionModCrash == 0`, main
menu reached (`Narrator library ... successfully loaded`, Realms client started).
Both `UnsupportedOperationException` and `ArrayIndexOutOfBounds` occurrences are
**0**, and every transformer logs its injection. **Total rejected recipes dropped
from 1674 to 670.**

**F. `lff_blast_forging_cooled` × 629 — fixed.**
`LinearForgingFurnaceLoader.attemptForgingCooledRecipe(OrePrefix, RecipeBuilder)`
rebuilds a builder's outputs by resolving each source output through
`OreDictUnifier.getMaterial()` + `getPrefix()`. On 2.9 those lookups can fail, so
the rebuilt builder ends up with **no outputs** and GTQT drops the recipe with
"Recipe outputs are empty". Runtime diagnostics proved the source recipes were
fine (1241 source recipes had `outputs=1`, only 15 had `outputs=0`).
`LffOutputFallbackTransformer` wraps the method: if the rebuilt builder has an
empty output list while the source still has outputs, the source outputs are
copied in before returning. When the method intentionally returns `null`, or the
2.8 path already worked, nothing changes.
→ measured effect: rejected `lff_blast_forging_cooled` recipes **629 → 0**.
(First attempt failed with `NegativeArraySizeException(-1)` from ASM
COMPUTE_FRAMES after the original method's access was changed; fixed by keeping
the original `private static` flags and calling it via INVOKESTATIC.)

### Verified totals (this round)

Rejected recipes: **1674 → 41 → 13**. Main menu reached,
`LoaderExceptionModCrash == 0`. All transformers log their injection. All
temporary diagnostics removed from both source and instance JVM args.

### Final: remaining 13 are GTQT 2.9 core-only

All 13 remaining rejected recipes come from **GTQT 2.9 core itself**, not from a
compatibility bridge: `UURecipes.UUUtils` (replicator ×5), `RecipeMaps` static
init (mixer ×4), `MixerRecipes.init` (large_mixer ×4). They print
"Recipe outputs are empty" on a stock 2.9 host; changing them would alter GTQT
upstream behaviour, so the compatibility layer leaves them alone.

### Restored in this round (prior stubs removed)

- `GTCEFuContent.cutterUpdate` / `registerCooled` / `registerForgingCooled` /
  `assembleCompositeMaps` — all real now (see items A–F above).
- `ZBGT MaterialRecipes.alloyBlast` — restored via `EbfGasesBridgeTransformer`,
  which exposes 2.9's `Map<GasTier,List<FluidStack>>` as the 2.8
  `Map<GasTier,FluidStack>` the addon expects. Verified: transformer logs
  "EBF_GASES reads redirected (1)", no crash.
- `electric_baking_oven` ×26 (GTFO) — restored via
  `RecipeValidationCompatTransformer`, which relaxes the 2.9-only hard checks
  `EU/t cannot be equal to 0` and `Duration cannot be less or equal to 0`
  (2.8 had neither). Also covers `Blast Furnace Temperature cannot be less than
  or equal to 0`, restoring GTConsolidate's `turbo_blast_recipe` ×2.

### Remaining intentional skips (content tweaks, not machine recipes)

- `GTFOMachineRecipesSkipTransformer` — GTFO's `init()` builds custom
  `CraftingComponent$Component(Map)` recipe *components*. 2.9's replacement
  (`gregtech.api.recipes.crafting.Component`) has a private constructor and no
  Map-based API, so this pass cannot be bridged safely (attempts produced CCE /
  VerifyError / `InnerClasses` `IncompatibleClassChangeError`). Skipping it only
  leaves GTFO's custom recipe-component *decorations* out; its machines and all
  other recipes work.
- `ZBGTAutomationSkipTransformer` — `resetAssemblerOnBuilder` replaces the
  soldering-alloy fluid in assembler recipes with tin. Its method reference is
  typed to 2.8's `AssemblerRecipeBuilder`, removed in 2.9, so it cannot be
  relinked. Skipping it only keeps the original solder input.
- `PipeMaterialSkipTransformer` — robustness guard for duplicate materials,
  not a recipe pass.

Both remaining skips are content-level customisations with **no missing machine
recipes**: every mod's machines now have their recipes, which was the objective.

### Still stubbed (next targets)

- ZBGT `MaterialRecipes.alloyBlast` — `CraftingComponent.EBF_GASES` value type
  changed from `FluidStack` to `List<FluidStack>`
- ZBGT `RecipeMapTrollery.resetAssemblerOnBuilder` — 2.8-only
  `AssemblerRecipeBuilder` API (`isWithRecycling`, `clearFluidInputs`)
- GTFO `GTFOMachineRecipes.init` — 2.8 `CraftingComponent$Component(Map)` ctor

## [1.8.0] - 2026-11-22 (Java 21 + unified 2.8 API restoration) — MAIN MENU REACHED

**The pack now boots to the main menu**: `LoaderExceptionModCrash == 0`,
"Narrator library successfully loaded", Realms client started, 156 mods loaded
(155 active), texture atlas 16384x8192 built, JEI structure-preview errors gone.

Direction changed by user: stay on GTQT 2.9, keep improving the
compatibility layer, and port ALL remaining 2.8-compiled GT addons. Rather than
rewriting 11 addons one by one (each has hundreds of `pattern.*` references), the
layer now **restores the GTCEu 2.8 API contract once, centrally**.

### Build: Java 21 bytecode (Cleanroom runs MC 1.12.2 on Java 25)
- `use_modern_java_syntax = true`, toolchain JDK 21, no `--release 8`.
- Jabel annotation processor removed (its bundled Byte Buddy rejects class v65).
- Compile-time-only local jars in `libs/` (NOT shipped): `gregtech-gtqt-...-2.9.0.jar`,
  `modularui-3.1.5.jar`, `ctm.jar`.
- Vendored sources keep Java 14+ syntax (switch arrows / pattern matching).

### Vendored GTCEu 2.8 sources (compiled into the compat layer jar)
- `gregtech/api/pattern/*` (8 classes): BlockPattern, BlockWorldState,
  FactoryBlockPattern, MultiblockShapeInfo, PatternError, PatternMatchContext,
  PatternStringError, TraceabilityPredicate. Added a public
  `checkPatternCompat(...)` wrapper so 2.9 code can drive the 2.8 matcher.
- `IMultipleTankHandler$MultiFluidTankEntry` (outer interface .class excluded so
  it cannot shadow 2.9's own interface).
- `ItemMaterialInfo`, 2.8 cover-filter classes, and `FilterTypeRegistry` (no-op
  stub so ZBGT registers its covers).
- `StructurePredicates`: 2.8's static `states/abilities/frames/blocks/air/any/
  heatingCoils/metaTileEntities/tilePredicate` factories.

### Transformers / rules that made the pack boot
| Transformer | Fixes |
| --- | --- |
| AddonPackageRemapTransformer | 2.8->2.9 package moves (`recipeproperties`->`properties`, `ToolProperty`->`MaterialToolProperty`, `AssemblerRecipeBuilder`->`SimpleRecipeBuilder`, `CraftingComponent$Component`->`crafting.Component`); `getState/getItemVariant(Enum)`->`IStringSerializable`; `Recipe.getRecipePropertyStorage`->`propertyStorage`; Implosion buider renames; `MetaItems.*_LUV`->`*_LuV`; `ADVANCED_DATA_ACCESS_HATCH`/`CREATIVE_DATA_HATCH`/`RecipeBuilder.isCTRecipe`/`EUt` field adaptations; invokedynamic bsmArgs (lambda method refs) |
| StructureDefinitionCompatTransformer | **injects `createStructureDefinition()` into 73 2.8 multiblocks** (was 73 `AbstractMethodError`s) |
| SrgGetterNameBridgeTransformer | SRG `func_176610_l()` -> MCP `getName()` on heating-coil enums (GT/ ZBGT / GTExpert) |
| RecipeCtorBridgeTransformer | 2.8 12-arg `Recipe` constructor |
| RecipeBuilderFieldCompatTransformer | restores `isCTRecipe` field/method |
| FinalizeAndValidateBridgeTransformer | `RecipeBuilder.finalizeAndValidate()` -> `validate()` |
| SimpleRecipeBuilderRecyclingBridgeTransformer | `withRecycling()` -> item+fluid recycling |
| FuelRecipeLogicCtorBridgeTransformer | `MultiblockFuelRecipeLogic(RecipeMapMultiblockController)` |
| AppendedArgCtorBridgeTransformer | ctors 2.9 extended with a trailing arg (`SimpleGeneratorMetaTileEntity` +efficiency) |
| SteamMultiblockCtorBridgeTransformer | 2.8 steam multiblock controllers |
| ResearchRecipeBuilderEUtBridgeTransformer / StationRecipeBuilderCWUFallbackTransformer | station research `EUt(int)` + CWU defaults |
| OreDictUnifierMaterialInfoBridgeTransformer / OreDictCompatAux | `registerOre(ItemStack, ItemMaterialInfo)` + `getAllItemInfos()` |
| WirePropertiesVoltageBridgeTransformer | `getVoltage():int` (2.9 widened to long) |
| MTERegistryNamespaceCompatTransformer | 2.8 addons may register foreign-namespace MTEs |
| MTERegistryIdCollisionTransformer | hardcoded 2.8 MTE ids are nudged to free slots |
| PipeMaterialSkipTransformer | duplicate materials skipped instead of aborting |
| MaterialFluidFallbackTransformer | re-registers a missed material fluid |
| AddonRecipePassSkipTransformer | skips 2.8-only GTCEFuContent late passes (NonNullList-based) |
| ZBGTAlloyBlastSkipTransformer / ZBGTAutomationSkipTransformer / GTFOMachineRecipesSkipTransformer | skips specific 2.8-only recipe passes |
| MetaTileEntitiesCompat | lazy `ADVANCED_DATA_ACCESS_HATCH` / `CREATIVE_DATA_HATCH` |
| (disabled) MTEManagerCompatTransformer | auto-created registries caused `register(null)` |
| (disabled) RecipeGetOutputsBridgeTransformer | NonNullList factory is SRG-named; stubbing used instead |

### Real-launch progress (Prism / Cleanroom / Java 25)
Each cleared crash was verified by an actual relaunch:
`GE` -> `GTFO processIngot` -> `GTFO steam ctor` -> `GTFO recipeMapWorkable` ->
`GTCEFuContent FactoryBlockPattern` -> `FuelRecipeLogic` -> `MultiFluidTankEntry` ->
`SimpleGeneratorMetaTileEntity` -> `getState(Enum) VerifyError` ->
`MultiblockControllerBase.states` -> `CommonProxy.registerBlocks(null)` ->
`GCYM MTE id collision` -> `hsla_steel not registered` -> `Material.getFluid null` ->
`getItemVariant(Enum)` -> `CraftingComponent.PLATE` -> GCYM alloy-blast indy ->
`GTFO Component ctor` -> `MetaItems.FLUID_REGULATOR_LUV` -> `StationRecipeBuilder CWU`
-> `OreDictUnifier.registerOre` -> `WireProperties.getVoltage` -> ZBGT EBF_GASES ->
`RecipeBuildActionAdapter(null)` -> `isWithRecycling` -> `finalizeAndValidate` ->
`EUt` field -> `isCTRecipe` -> `Recipe ctor` -> `FilterTypeRegistry` -> prismplan ->
`scalablestorageceu` -> `IHeatingCoilBlockStats.getName` -> `createStructureDefinition`
-> **main menu**.

### Known remaining limitations (content, not startup)
- Some 2.8 addon late recipe passes are stubbed (GTCEFuContent cutter/cooling,
  ZBGT alloy-blast and assembler tweak, GTFO machine-casing recipes).
- ZBGT custom filter *types* are registered as no-ops.
- The 73 legacy multiblocks carry a hidden stub piece; their real structure is
  evaluated by their own 2.8 BlockPattern at runtime, so JEI's structure preview
  shows a placeholder rather than the real shape.
check of the injected bytecode.

## [1.7.1] - 2026-11-22 (runtime hardening, real-instance driven)

Real-launch debugging of the GTQT 2.9 pack exposed several crashes that were
actually caused by this compatibility layer itself, plus genuine 2.8鈫?.9 API
changes. All fixed here.

### Fixed 鈥?AddonPackageRemapTransformer invokedynamic blind spot (critical)
- The transformer rewrote `ToolProperty 鈫?MaterialToolProperty` in method
  **declarations** but not in `InvokeDynamicInsnNode.desc` / its `bsmArgs`
  (`Handle` + `Type`) or in `LdcInsnNode(Handle)`. A 2.8 addon using a lambda /
  method reference (GregTech Food Option's `GTFORecipeHandler.register()` ->
  `TriConsumer` -> `processIngot(OrePrefix, Material, ToolProperty)`) then called
  the OLD descriptor while the declaration had the NEW one -> `NoSuchMethodError`.
- Now rewrites: `InvokeDynamicInsnNode.desc`, every `Handle`/`Type` in `bsmArgs`,
  and `LdcInsnNode` carrying a `Handle`. Offline-verified: declaration and indy
  handle descriptors now both read `MaterialToolProperty`.

### Added 鈥?steam multiblock constructor bridges
- `SteamMultiblockCtorBridgeTransformer` (table-driven): GTQT 2.9 appended a
  `ParallelLogicType` argument to two constructors:
  - `RecipeMapSteamMultiblockController(RL, RecipeMap, double)`
  - `SteamMultiWorkable(RecipeMapSteamMultiblockController, double)`
  Bridges forward to the new ctor passing `ParallelLogicType.MULTIPLY`.
- `AddonPackageRemapTransformer`: map the old field type
  `SteamMultiblockRecipeLogic -> SteamMultiWorkable` (2.9 changed
  `RecipeMapSteamMultiblockController.recipeMapWorkable`'s declared type).
  JVM field identity includes the descriptor, so without this a 2.8 addon gets
  `NoSuchFieldError`. Only GTFO references it; `SteamMultiWorkable` inherits all
  `SteamMultiblockRecipeLogic` methods so owners stay valid.

### Diagnosis note
- With these fixes the pack no longer crashes in GregTech Food Option; the next
  blocker is GTCEFuContent's `gregtech.api.pattern.FactoryBlockPattern`, i.e.
  the 2.9 structure-API rewrite that ASM bridges cannot restore (see the
  "Not bridged / needs a 2.9 addon build" section). This is an addon-porting
  task, not a compatibility-layer bug.

## [1.7.0] - 2026-11-22

鏈疆鎶婂吋瀹瑰眰浠庛€? 涓浂鏁?crash 琛ヤ竵銆嶅畬鍠勪负涓€濂楄鐩?2.8.10 鈫?GTQT 2.9 涓昏浜岃繘鍒跺樊寮?鐨?ASM coremod銆傛墍鏈?descriptor 鍧囩敤 `javap -p -s` 瀵圭湡瀹?jar 鏍稿锛?.8.10-beta /
GTQT 2.9.0 / GTQT 2.9.0-190锛夛紝骞跺湪鐪熷疄 Prism/Cleanroom 鏁村悎鍖呬笂閫愯疆瀹炶窇鎺掗敊銆?
### Added 鈥?澹版槑寮忔柟娉曟ˉ妗嗘灦
- `BridgeSpec` / `BridgeSets` / `GenericBridgeTransformer`锛氭妸銆?.8 鏂规硶绛惧悕 鈫?2.9 濮旀墭鏂规硶銆?  鍐欐垚澹版槑锛坱arget / name / desc / delegate / argOps / returnOp / reciprocalReturn锛夛紝
  绫诲姞杞芥椂娉ㄥ叆銆備粎鍦ㄣ€屾棫绛惧悕缂哄け銆嶄笖銆屽鎵樻柟娉曞瓨鍦ㄣ€嶆椂娉ㄥ叆锛岀湡 2.8 瀹夸富鑷姩 no-op銆?- 瑕嗙洊锛歊ecipe / RecipeBuilder `getEUt()I鈫?)J`锛汚bstractRecipeLogic 鐨?  `getRecipeEUt()I` / `getInfoProviderEUt()I` / `drawEnergy(IZ)Z` /
  `getNumberOfOCs(I)I`锛坧rotected锛岄伩鍏?addon 瀛愮被 ICCE锛夛紱鏀瑰悕鐨?  `getOverclockingDurationDivisor()D`锛?.8 闄ゆ暟 鈫?2.9 涔樻暟锛屾ˉ鍐?`1.0 / factor` 鍙栧€掓暟锛?  閬垮厤鏃堕暱缈诲€嶏級/ `getOverclockingVoltageMultiplier()D`锛?  FluidProperty `void setSolidifyingFluid(Fluid)`锛堜涪寮?fluent 杩斿洖鍊硷級锛?  OverclockingLogic `applyCoilEUtDiscount(III)I 鈫?(JII)J`銆?- 鍔犲浐锛歚BY_TARGET` 闈欐€佸垵濮嬪寲 try/catch 闄嶇骇锛沗COMPUTE_MAXS`锛涜繑鍥炵被鍨嬪畧鍗紱
  璺ㄧ被濮旀墭鍒ゆ柇淇锛?.8 protected 鏂规硶淇濇寔 protected銆?
### Added 鈥?鍏?addon 甯搁噺姹犻噸鏄犲皠
- `AddonPackageRemapTransformer`锛堟浛浠ｆ棫鐨?`ToolPropertyRemapTransformer`锛岃鐩栧叏閮?  2.8 缂栬瘧 addon 鍖咃級锛歚ToolProperty` / `ToolProperty$Builder` 鈫?`MaterialToolProperty(/$Builder)`锛?  `gregtech.api.recipes.recipeproperties.*` 鈫?`properties.*` / `properties.impl.*`
  锛堝惈 `IRecipePropertyStorage`銆乣RecipeProperty`銆乀emperatureProperty 绛?9 涓叿浣撳睘鎬х被锛?  `GasCollectorDimensionProperty` 鈫?`DimensionProperty`锛夛紱浠ュ強涓€鎵逛粎鎹㈠寘/鏀瑰悕鐨勭被
  锛圵oodTypeEntry銆丆raftingComponent銆丄ssemblerRecipeBuilder銆丳rospectorMode銆?  MetaTileEntityWorkbench銆両temStackHashStrategy$Builder 绛夛級銆?- 鐢?`COMPUTE_FRAMES` 閲嶇畻 StackMapTable锛岄伩鍏嶆敼瀛楁绫诲瀷鍚庣殑 `VerifyError`銆?- `RecipePropertyStorageBridgeTransformer`锛氫负 2.9 鎺ュ彛琛ュ洖 2.8 鐨勬柟娉曞悕
  锛坄getSize` / `getRecipeProperties` / `getRecipePropertyValue` / `hasRecipeProperty` /
  `getPropertyTypes`锛夛紝姝ｇ‘鍖哄垎 `Z/I` 鐢?`ireturn`銆?
### Added 鈥?鏋勯€?娉ㄥ唽/灞炴€фˉ锛堝疄璺戦┍鍔級
- `ToolPropertyBridgeTransformer`锛歚Material$Builder.toolStats(ToolProperty)` 鈫?  `toolStats(MaterialToolProperty)`锛屽甫 `CHECKCAST`銆?- `RecipeMapCtorBridgeTransformer`锛氳ˉ鍥?2.8 鐨勪袱涓?`RecipeMap` 鏋勯€犲櫒锛堢敤
  `DefaultRecipeMapUIFunction` 鍔ㄦ€佷唬鐞嗘彁渚?2.9 闇€瑕佺殑 `RecipeMapUIFunction`锛夊拰
  `onRecipeBuild(Consumer)`锛堢敤 `RecipeBuildActionAdapter` 杞垚鍛藉悕 `RecipeBuildAction`锛夈€?- `MultiblockAbilityCtorBridgeTransformer`锛氳ˉ鍥?`MultiblockAbility(String)`銆?- `MTEManagerCompatTransformer`锛歚getRegistry(modid)` 瀵?2.8 addon 鑷姩
  `createRegistry(modid)`锛屼慨澶嶃€孋annot register MTE to another mod's registry銆嶃€?- `VariantBlockBridgeTransformer`锛?.8 娉涘瀷鎿﹂櫎 `Enum` 鈫?2.9 `IStringSerializable`
  鐨?`getState` / `isBloomEnabled` / `getRenderLayer` 妗ャ€?- `GcymPropertyKeyCompatTransformer`锛欸CYM 鐨?`ALLOY_BLAST` 澶嶇敤 2.9 鏍稿績
  `PropertyKey.ALLOY_BLAST`锛孉lloyBlastProperty / RecipeProducer 閲嶆槧灏勫埌鏍稿績绫伙紝
  淇 `ClassCastException`銆?- `FieldCompatTransformer`锛氳ˉ鍥?`GregTechAPI.MTE_REGISTRY` / `MACHINE` / `TAB_*` 绛?  2.8 闈欐€佸瓧娈碉紝`OrePrefix.oreSmall` 鍒悕銆?- `MetaTileEntityBridgeTransformer`锛氳ˉ鍥?comparator / AE2 / tick-acceleration 杈呭姪鏂规硶鐨?  瀹夊叏鍥為€€銆?
### Fixed 鈥?鐗堟湰鎺㈡祴
- GTQT 2.9 浠嶆姤 `modid = gregtech`锛岀増鏈覆 `2.9.0` / `2.9.0-190` / `1.9.0` 鍧囧彲璇嗗埆锛?  姣忎釜绫?瀛楁鎺㈤拡鐙珛 try/catch锛屼笉鍐嶅洜缂?`coilManager` 鐩存帴鍒?UNKNOWN銆?- `UnificationCompat` 绾冲叆鍒濆鍖栵紱UNKNOWN 鐗堟湰浠嶅畬鎴愬垵濮嬪寲浣嗕笉鍚敤鐗堟湰涓撳睘 shim銆?
### Not bridged / 闇€ addon 閲嶇紪璇戯紙鏄庣‘杈圭晫锛?- `AbstractRecipeLogic` 鐨?`int[]` 瓒呴绯诲垪锛坄calculateOverclock` / `performOverclocking` /
  `runOverclockingLogic` / `modifyOverclockPre/Post`锛夛細2.9 鐢?`OCParams`/`OCResult` 鏁翠綋
  鍙栦唬锛岀紪缁勫洖 `int[]` 浼氶潤榛樻敼鍙樿涔夈€?- `OverclockingLogic.standardOverclockingLogic` / `heatingCoilOverclockingLogic`锛氬悓涓娿€?- `Recipe` 鏃ф瀯閫犲櫒 / `getProperty*` 瀹舵棌锛沗ToolProperty` 鐨?`void setX(...)` setter
  锛?.9 鏀逛负 fluent锛屽悓鍚嶅悓鍙備笉鍚岃繑鍥炵被鍨嬫棤娉曞叡瀛橈級銆?- **GTQT 2.9 閲嶅啓浜嗘暣涓鏂瑰潡缁撴瀯妗嗘灦**锛歚gregtech.api.pattern.BlockPattern` /
  `FactoryBlockPattern` / `PatternMatchContext` / `TraceabilityPredicate` 绛夎鍒犻櫎锛?  鎹㈡垚 `StructureRuntime` / `PieceTemplate` / `DeclarativePatternBuilder` 鍏ㄦ柊浣撶郴銆?  杩欑被宸紓鏄涔夌骇閲嶅啓锛孉SM 妗ユ棤娉曡繕鍘燂紱渚濊禆鏃х粨鏋?API 鐨?addon锛圙CYM銆乑BGT銆?  GTExpert銆丟TConsolidate銆丟TFO 绛夛級闇€瑕佸畼鏂?2.9 鐗堟湰鎴栭噸缂栬瘧銆?- `RecipeBuilder.EUt(int)`锛?.9 浠嶄繚鐣?`EUt(int)` 閲嶈浇锛屾棤闇€妗ユ帴銆?
### Added 鈥?鍚姩鏈熶笉鍏煎璇婃柇锛?.7.0 鏀跺熬锛?- `GTCEuCompatDiagnostics`锛?*鍦?coremod 鐨?`injectData` 闃舵**鎵弿 `mods/` 鐩綍
  鍏ㄩ儴 jar锛堜笉渚濊禆 `Loader`/`ModContainer`锛夛紝鍚?  `L...;` descriptor 涓庣埗绫?鎺ュ彛/绛惧悕鐨?*瑁稿唴閮ㄥ悕**锛夛紝鎶婁粛寮曠敤 GTQT 2.9 宸插垹闄?  鏁村寘绫荤殑 addon 鏄庣‘鍒楀嚭鏉ワ紝骞跺啓 `gtceu_compat_report.txt` 鍒版父鎴忕洰褰曘€?- 瑕嗙洊鐨勩€屽凡鍒犻櫎銆嶅墠缂€锛歚gregtech.api.pattern.{BlockPattern,FactoryBlockPattern,
  PatternMatchContext,TraceabilityPredicate,MultiblockShapeInfo}`銆?  `gregtech.common.metatileentities.multi.multiblockpart.appeng.*`锛?.8 鐨?AE2
  澶氭柟鍧楀涓诲寘锛夈€乣gregtech.api.recipes.recipeproperties.*`銆乣gregtech.asm.util.*`銆?- 鍏稿瀷杈撳嚭锛歚gregifiedenergistics-0.1.4.jar -> MetaTileEntityAEHostablePart`锛?  鐩存帴璇存槑銆孏E 鐨?`MTEAbstractAssemblyLineBus` 鐖剁被鍦?2.9 涓婁笉瀛樺湪銆嶏紝鏇夸唬
  Cleanroom 鍚硦鐨?`Found ... in invalid classes`銆?- 鐩爣瀹炰緥瀹炴祴锛氭壂鎻?139 涓?jar锛岀簿纭爣鍑?11 涓粛渚濊禆鏃?`pattern.*` / AE2 瀹夸富 /
  鏃?recipe-property 鍖呯殑 addon銆?- 鏃跺簭淇锛氳瘖鏂渶鍒濇寕鍦?`onPostInit`锛屼絾 GE 鍦?**preInit** 灏卞洜鐖剁被缂哄け宕╂簝锛?  FML 鏍规湰鍒颁笉浜?postInit銆傛敼鎸?`GTCEuCompatLoadingPlugin.injectData`锛堢敤
  `data["mcLocation"]` 鍙栨父鎴忕洰褰曪級锛屽疄璺戠‘璁ゅ湪宕╂簝鍓嶈緭鍑猴紝骞跺啓鍑?  `gtceu_compat_report.txt`銆俙onPostInit` 淇濈暀涓€浠藉厹搴曪紙鐢?Loader 绮剧‘鍒?modid锛夈€?
### 杈圭晫纭锛欸regified Energistics 0.1.4
- GTQT 2.9 **娌℃湁瀵?AE2 鍋氶瓟鏀?*锛屽彧鏄繚鐣欒仈鍔ㄥ唴瀹癸紙璐村浘甯搁噺銆乣AE2CompatOptions`
  閰嶇疆锛夛紱2.8 鐢?GTCEu 鏍稿績鎻愪緵鐨?`multiblockpart/appeng` 瀹夸富绫诲湪 2.9 鏍稿績 jar 閲?  **鏁翠釜鍒犻櫎**锛屼笖褰撳墠 mods 缁勫悎閲屾病鏈夌瓑浠锋浛浠ｇ被锛坄prismplan` 鐨?  `MetaTileEntityMEInterface` 缁ф壙鐨勬槸 `MetaTileEntityMultiblockNotifiablePart`锛?  闈炲悓涓€濂楀疄鐜帮級銆?- 鍥犳 GE 鐨?`MTEAbstractAssemblyLineBus 鈫?MetaTileEntityAEHostablePart` 缁ф壙閾?  鍦?2.9 涓婃柇瑁傦紝涓?`pattern.*` 涓€鏍峰睘浜?*璇箟绾у垹闄わ紝ASM 妗ユ棤娉曡繕鍘?*锛涜 addon
  闇€瑕?2.9 鏋勫缓鐗堟湰銆傚吋瀹瑰眰涓嶄吉閫犲亣鐖剁被锛屽彧閫氳繃璇婃柇鏄庣‘鎶ュ嚭銆?
### 闆嗘垚鐜璇存槑
- 鐩爣瀹炰緥涓?Prism Launcher + Cleanroom 0.6.7-alpha + Java 25锛?  `forge_early.cfg` 鐨?`DISABLE_PATCH_MOD_CHECK=true` 鍙叧闂?Scalar/Soalar 缂哄け鐨?  鍚姩鍓嶆彁绀猴紙涓嶅奖鍝嶅吋瀹瑰眰鏈韩锛夈€?- 瀹炶窇涓?ZBGT 0.16.2 鐨?`PSSMixin` / `ActiveTransformerMixin` 鏄熀浜?2.8 鏂规硶绛惧悕鐨?  Mixin锛屽湪 2.9 涓婁細浜х敓 `VerifyError`锛涜繖灞炰簬 addon 鑷韩 Mixin 涓嶅吋瀹癸紝浜や粯鏃堕€氳繃鍦?  ZBGT jar 鐨?`mixins.zbgt.json` 閲岀鐢ㄨ繖 4 涓?mixin 瑙勯伩锛堝師 jar 宸插浠戒负 `.orig`锛夈€?
## [1.6.0] - 2026-11-20

### Changed
- Extended `ToolPropertyRemapTransformer` coverage to GTExpert addon classes
  (`com.github.gtexpert.*`), fixing `ToolProperty$Builder` `NoClassDefFoundError`
  in `DEFirstDegreeMaterials` / `AvaritiaFirstDegreeMaterials`.

## [1.5.0] - 2026-11-20

### Added
- `ElementCtorCompatTransformer`: injects a public
  `Element(long, long, long, String, String, String, boolean)` constructor into
  GTQT 2.9's `Element`, delegating to the existing double half-life constructor.
  Fixes GTExpert's `GTEElements` `NoSuchMethodError` startup crash.

## [1.4.0] - 2026-11-20

### Added
- `MaterialIconTypeDedupTransformer`: rewrites GCYM's `GCYMMaterialIconTypes`
  static initializer to reuse the core's already-registered `MaterialIconType.molten`
  instead of creating a duplicate, fixing
  "MaterialIconType molten already registered!" startup crash.

## [1.3.0] - 2026-11-20

### Added
- `FluidPropertyCompatTransformer`: re-adds the deprecated `getStorage()` method
  (`public FluidStorage getStorage() { return this; }`) that GTQT 2.9 removed but
  GTCEFuContent still calls in `GTCEFuCEutecticMaterialProperty.verifyProperty()`.

## [1.2.0] - 2026-11-20

### Added
- `ToolPropertyRemapTransformer`: remaps GTCEu 2.8's `ToolProperty` API to GTQT
  2.9's `MaterialToolProperty` inside GTCEFuContent classes. Fixes the
  `NoClassDefFoundError: ToolProperty$Builder` startup crash at
  `GTCEFuCMaterials.register()`.

## [1.1.0] - 2026-11-20

### Added
- ASM coremod transformer that injects `Elements.add(long, long, long, String, String, String, boolean)`
  into `gregtech.api.unification.Elements` when missing (e.g. GTQT 2.9.0), delegating to the
  existing `add(long, long, double, ...)` overload. Fixes GTCEFuContent 2.1.1-alpha startup
  `NoSuchMethodError`.
- Registered `ElementsCompatTransformer` in `GTCEuCompatLoadingPlugin#getASMTransformerClass`.

### Changed
- Bumped mod version to 1.1.0.

## [1.0.0] - 2023-09-15

### Added
- Initial reflection-based compatibility layer for GTCEu 2.8/2.9 APIs.
