# GTCEu Compatibility Layer

一个为 GregTech CE Unofficial (GTCEu) 2.8 和 2.9 版本提供双向兼容的模组。

> **1.8.0 现状**：在 GTQT 2.9 宿主上，本兼容层让整合包里 **13 个 2.8 编译的 GT 系附属 mod
> 全部通过加载**，整合包可正常启动进主菜单
> （`LoaderExceptionModCrash == 0`，156 mods loaded / 155 active）。
> 做法不是逐个改写附属 mod，而是**在兼容层里一次性补齐 GTCEu 2.8 的 API 契约**：
> vendored 源码 22 个类 + 47 个 ASM transformer/规则。详见 [CHANGELOG.md](CHANGELOG.md)。
>
> 编译需要 Java 21 工具链，且需要自备 3 个 compileOnly 参考 jar —— 见
> [libs/README.md](libs/README.md)（这些 jar 不入库）。

## 许可（重要）

本仓库**不是 MIT-only，而是双许可混合**：

| 范围 | 许可 |
|---|---|
| `src/main/java/gregtech/**`（vendored 的 GTCEu 2.8.10 源码，14 个文件） | **LGPL-3.0** |
| 其余原创代码（ASM transformer / 诊断 / `StructurePredicates` / 构建脚本等） | MIT |

LGPL-3.0 全文见 [LICENSE-GTCEu-LGPL-3.0.txt](LICENSE-GTCEu-LGPL-3.0.txt)，
逐文件的来源与**修改说明**见 [LICENSE-NOTICE.md](LICENSE-NOTICE.md)。

因为你把本兼容层和上游一起编译进同一个 jar，**分发构建产物即等于分发含
LGPL-3.0 代码的作品**，需要一并提供：许可声明 + 可获取完整对应源码（本仓库即为
Corresponding Source）。请不要把 LGPL 部分单独改声明为 MIT。

## 功能概述

这个兼容层模组解决了 GTCEu 2.8 和 2.9 之间的 API 不兼容问题：

- **运行在 GTCEu 2.8 上时**：提供 2.9 API 的兼容垫片，使得为 2.9 编写的插件可以在 2.8 上运行
- **运行在 GTCEu 2.9 上时**：提供 2.8 API 的兼容垫片，使得为 2.8 编写的插件可以在 2.9 上运行
- **ASM coremod 垫片**：向 `gregtech.api.unification.Elements` 注入 `add(long,long,long,String,String,String,boolean)` 重载（当宿主缺失时），修复 GTCEFuContent 2.1.1-alpha 在 GTQT 2.9.0 上的 `NoSuchMethodError` 启动崩溃
- **声明式方法桥**（1.7.0 起）：按真实 descriptor 把 2.8 的方法签名注入回 2.9 宿主（`getEUt()I`、`setSolidifyingFluid(Fluid)V`、`applyCoilEUtDiscount(III)I` 等），见「1.7.0 新增的 2.8→2.9 桥接」
- **结构系统还原**（1.8.0）：vendored 2.8 的 `gregtech.api.pattern.*`，并给 73 个 2.8 多方块注入
  2.9 的 `createStructureDefinition()`，让它们自己的 `BlockPattern` 继续判定成型


## 发现的API差异

通过分析GTCEu 2.8.10和GTQT (基于2.9)源码，发现以下关键差异：

| 特性 | 2.8.10 | GTQT (2.9) |
|------|--------|------------|
| MetaTileEntity接口 | `ISyncedTileEntity, CoverHolder, IVoidable` | `ISyncedTileEntity, CoverHolder, IVoidable, IPollution, IGuiHolder` |
| RecipeBuilder.EUt参数 | 只有 `EUt(int)` | `EUt(long)` **与** `EUt(int)` 两个重载（旧说法「2.9 只有 long」不准确） |
| GregTechAPI.mteManager | ❌ 不存在 | ✅ 存在 |
| GregTechAPI.coilManager | ❌ 不存在 | ✅ 存在 |
| MTERegistry/MTEManager类 | ❌ 不存在 | ✅ 存在 |
| IPollution接口 | ❌ 不存在 | ✅ 存在 |
| Overclocking方法 | `calculateOverclock(Recipe)` 返回 `int[]`；`runOverclockingLogic(IRecipePropertyStorage,int,long,int,int)` 返回 `int[]` | `calculateOverclock(Recipe)` 返回 `void`；`runOverclockingLogic(OCParams,OCResult,RecipePropertyStorage,long)` 返回 `void` |
| Overclocking电压方法 | 只有 `getMaximumOverclockVoltage()` 返回 `long` | `getOverclockVoltage()` 返回 `long` **与** `getMaximumOverclockVoltage()` 返回 `long`（均存在） |

## 主要兼容模块

### 1. MetaTileEntity 兼容层（ASM coremod + 反射）
- `MetaTileEntityBridgeTransformer` 为 2.9 补回 2.8 移除的 comparator / AE2 / tick-acceleration
  辅助方法（安全回退值），避免类链接期 `NoSuchMethodError`
- 反射适配器提供 `getMetaTileEntityId()` 等跨版本查询
- 复杂成员（`getDrops` / `clearInventory` / `getProxy` 等）不伪造，需重编译，见下表

### 2. Recipe API 兼容层（ASM coremod + 反射）
- `EUt(int)` 在 2.9 仍有重载，旧调用点不会断
- 2.9 缺 2.8 的 `getMaximumOverclockVoltage()` 时无需处理：2.9 仍有此方法，新增的是
  `getOverclockVoltage()`；2.8 侧缺的是后者
- `int[]` 超频系列与旧构造器不桥接，见「已知不能自动桥接」一节

### 3. GregTechAPI 兼容层（ASM coremod + 反射）
- `FieldCompatTransformer` 在 2.9 补回 `MTE_REGISTRY` / `MACHINE` / `TAB_*` 等 2.8 静态字段
  （`public static final`，在 `<clinit>` 里赋 `null` 或就近别名）
- `OrePrefix.oreSmall` 别名到 2.9 仍存在的 `OrePrefix.ore`

### 4. Cover API 兼容层
- 覆盖放置/移除方法兼容

### 5. Material 系统兼容层
- Material API 访问兼容

## 1.7.0 新增的 2.8→2.9 桥接

1.7.0 引入声明式桥接框架：`BridgeSpec`（一条桥的描述）/ `BridgeSets`（桥的注册表）/
`GenericBridgeTransformer`（按描述在类加载期注入）。每条桥的 descriptor 都用
`javap -p -s` 对真实 jar 核过（2.8.10-beta、GTQT 2.9.0、GTQT 2.9.0-190）。

注入条件有两个，缺一不注入：旧 `name+desc` 尚未存在、且委托方法存在。因此同一套
transformer 在真正的 2.8 宿主上是 no-op。JVM 方法身份包含返回类型，所以
`getEUt()I` 与 2.9 的 `getEUt()J` 能并存。

| 目标类 | 2.8 签名 | 2.9 委托 | 处理 |
|--------|----------|----------|------|
| `RecipeBuilder` | `int getEUt()` | `long getEUt()` | 返回值 `L2I` |
| `Recipe` | `int getEUt()` | `long getEUt()` | 返回值 `L2I` |
| `AbstractRecipeLogic` | `int getRecipeEUt()` | `long getRecipeEUt()` | 返回值 `L2I` |
| `AbstractRecipeLogic` | `int getInfoProviderEUt()` | `long getInfoProviderEUt()` | 返回值 `L2I` |
| `AbstractRecipeLogic` | `boolean drawEnergy(int, boolean)` | `boolean drawEnergy(long, boolean)` | 参数 `I2L` |
| `AbstractRecipeLogic` | `int getNumberOfOCs(int)` | `int getNumberOfOCs(long)` | 参数 `I2L` |
| `AbstractRecipeLogic` | `double getOverclockingDurationDivisor()` | `double getOverclockingDurationFactor()` | 改名转发 |
| `AbstractRecipeLogic` | `double getOverclockingVoltageMultiplier()` | `double getOverclockingVoltageFactor()` | 改名转发 |
| `FluidProperty` | `void setSolidifyingFluid(Fluid)` | `FluidProperty setSolidifyingFluid(Fluid)` | 丢弃 fluent 返回值 |
| `OverclockingLogic` | `static int applyCoilEUtDiscount(int,int,int)` | `static long applyCoilEUtDiscount(long,int,int)` | 参数 `I2L` + 结果 `L2I` |

另有 `ToolPropertyBridgeTransformer`（单独实现，不走 `BridgeSpec`）：
`Material$Builder.toolStats(ToolProperty)` → `toolStats(MaterialToolProperty)`，桥体带
`CHECKCAST`，addon 若真的传了非 `MaterialToolProperty` 的裸 `ToolProperty`，会在调用点
抛 `ClassCastException` 而不是把坏对象悄悄塞给 2.9。之所以不能塞进 `BridgeSets`：
`BridgeSpec` 只能表达数值转换（`I2L`/`L2I`），无法对引用参数发 `CHECKCAST`。

`getOverclockingDurationDivisor` 桥会在返回前算 `1.0 / factor`：2.8 的「除数」
（2.0 / 4.0）与 2.9 的「乘数因子」（0.5 / 0.25）互为倒数，直接转发会把配方时长翻倍。

`applyCoilEUtDiscount` 的 `long→int` 收窄是安全的：2.8 配方里的 EU/t 在整个电压区间内都
远小于 2^31。

### 1.7.0 其余 transformer（实跑驱动补齐）

| Transformer | 作用 |
|-------------|------|
| `AddonPackageRemapTransformer` | 全 addon 包常量池重映射：`ToolProperty(/$Builder)` → `MaterialToolProperty(/$Builder)`；`recipeproperties.*` → `properties.*` / `properties.impl.*`（9 个具体属性类）；一批仅换包/改名的类。`COMPUTE_FRAMES` 重算栈图 |
| `RecipePropertyStorageBridgeTransformer` | 2.9 接口补回 2.8 方法名 `getSize` / `getRecipeProperties` / `getRecipePropertyValue` / `hasRecipeProperty` / `getPropertyTypes` |
| `RecipeMapCtorBridgeTransformer` | 补回 2.8 两个 `RecipeMap` 构造器（`DefaultRecipeMapUIFunction` 提供 2.9 UI 函数）与 `onRecipeBuild(Consumer)`（`RecipeBuildActionAdapter` 转命名 action） |
| `MultiblockAbilityCtorBridgeTransformer` | 补回 `MultiblockAbility(String)`（委托到 `(String,Class)`，传 `Object.class`） |
| `MTEManagerCompatTransformer` | `getRegistry(modid)` 对 2.8 addon 自动 `createRegistry(modid)` |
| `VariantBlockBridgeTransformer` | 2.8 泛型擦除 `Enum` → 2.9 `IStringSerializable` 的 `getState` / `isBloomEnabled` / `getRenderLayer` |
| `GcymPropertyKeyCompatTransformer` | GCYM `ALLOY_BLAST` 复用 2.9 核心 `PropertyKey.ALLOY_BLAST`，修复 `ClassCastException` |
| `FieldCompatTransformer` | 补回 `GregTechAPI.MTE_REGISTRY` / `MACHINE` / `TAB_*`、`OrePrefix.oreSmall` |
| `MetaTileEntityBridgeTransformer` | 补回 comparator / AE2 / tick-acceleration 辅助方法安全回退 |
| 原有 5 个 | `ElementsCompat` / `ElementCtorCompat` / `FluidPropertyCompat` / `MaterialIconTypeDedup`，以及被 `AddonPackageRemapTransformer` 取代的 `ToolPropertyRemapTransformer`（保留类、不再注册） |

## 已知不能自动桥接 / 需重编译 addon

下列 2.8 API 无法用方法桥接「假装还在」，相关 addon 必须对 2.9 重编译：

| 2.8 API | 2.9 现状 | 不桥接的原因 |
|---------|----------|--------------|
| `AbstractRecipeLogic.calculateOverclock(Recipe)` 返回 `int[]` | 返回 `void` | 返回类型在 JVM 方法签名里，且 `int[]` 超频结果已被 `OCParams`/`OCResult` 取代 |
| `runOverclockingLogic(IRecipePropertyStorage,int,long,int,int)` 返回 `int[]` | `runOverclockingLogic(OCParams,OCResult,RecipePropertyStorage,long)` 返回 `void` | 同上；且 property 存储类换了包 |
| `performOverclocking(Recipe)` 返回 `int[]` | `performOverclocking(Recipe,OCParams,OCResult)` 返回 `void` | 同上 |
| `modifyOverclockPre/Post(int[],...)` | `modifyOverclockPre(OCParams,RecipePropertyStorage)` / `modifyOverclockPost(OCResult,RecipePropertyStorage)` | 方法还在，但首参与 property 存储类型都换了，中间结果不再是 `int[]` |
| `OverclockingLogic.standardOverclockingLogic(...)` 返回 `int[]` | `standardOC(OCParams,OCResult,long,double,double)` | 已改为 `OCParams`/`OCResult`，硬编组会静默改变超频语义 |
| `OverclockingLogic.heatingCoilOverclockingLogic(...)` 返回 `int[]` | `heatingCoilOC(OCParams,OCResult,long,int,int)` | 同上 |
| `Recipe` 旧构造器 | 参数表变化（`int` → `long` EUt、新增 `ItemStack` 列表参数、property 类型改 `RecipePropertyStorage`） | 构造器签名变化无法用转发桥补出等价的旧调用序列 |
| `Recipe.getProperty*` 家族 | `getProperty(properties.RecipeProperty,T)` 仍在，但 `RecipeProperty` 换了包；`getPropertyRaw/Values/Keys/Types/Count` 已删除 | 参数/返回类型同时变了，`RecipeProperty` 旧包名不再存在 |
| `ToolProperty.setX(...)` 返回 `void` | 返回 `ToolProperty`（fluent） | `void setter` 与 fluent setter 同名同参数、仅返回类型不同，注入 `void` 版本必须改返回类型；2.8 addon 一般走 `ToolProperty$Builder`，由重映射 transformer 处理 |
| `RecipeBuilder.EUt(int)` | **仍然存在** `EUt(int)` 重载 | 无需桥接（旧 README 说「2.9 只有 long」是错的） |
| `gregtech.api.pattern.BlockPattern` / `FactoryBlockPattern` / `PatternMatchContext` / `TraceabilityPredicate` 等整套旧结构 API | 2.9 删除，换成 `StructureRuntime` / `PieceTemplate` / `DeclarativePatternBuilder` 全新体系 | **语义级重写，不是改名**：11 个 2.8 addon 累计引用上千次，ASM 桥无法还原，必须用 addon 的 2.9 版本或重编译 |

判据：**返回类型是 JVM 方法签名的一部分**，所以「同参数、不同返回类型」的旧方法理论上
可以靠注入旧 descriptor 补回来（`getEUt()I` 就是这么做的）；构造器也能用转发补
（`RecipeMap` / `MultiblockAbility` / `Element` 都已桥接）。但当返回类型承载语义
（`int[]` 超频结果 → `OCParams`/`OCResult` 对象）、或整套类被重写删除（`pattern.*`），
补出来的就是语义错误的壳，宁可让 addon 重编译。

> 注意：本兼容层能消除的是「2.8 编译 addon 在 2.9 上的 linkage error」
> （NoClassDefFoundError / NoSuchMethodError / NoSuchFieldError / ClassCastException）
> 与「2.9 核心类缺 2.8 方法」这两类问题。它不能把依赖旧 `pattern.*` 结构框架的
> addon 变成 2.9 原生实现，也不能修复 addon 自带 Mixin 与 2.9 的不兼容（例如
> ZBGT 0.16.2 的 `PSSMixin`，需在 addon 侧禁用）。
>
> **Gregified Energistics 0.1.4** 是另一个典型：GTQT 2.9 没有魔改 AE2，只是保留
> 联动内容（贴图 / 配置），却把 2.8 由 GTCEu 核心提供的
> `gregtech.common.metatileentities.multi.multiblockpart.appeng.*` 宿主类整个删除。
> GE 的 `MTEAbstractAssemblyLineBus` 父类 `MetaTileEntityAEHostablePart` 不存在，
> Cleanroom 会报 `Found ... in invalid classes`。这类「父类被删」同样无法靠 ASM 桥
> 还原，需要 GE 的 2.9 版本。兼容层的 `GTCEuCompatDiagnostics` 会在 **coremod 阶段
> （preInit 之前，GE 崩溃之前）** 就把这类 addon 明确列出来并写
> `gtceu_compat_report.txt`，不用再猜 Foundation 的含糊报错。

## 工作原理

### 版本检测
模组在启动时按以下顺序检测 GTCEu 版本：
1. 读 mod 容器版本串，宽松匹配（`2.8` / `2.9` 出现在任意位置即可，`2.9.0`、`2.9.0-190`、
   `GTCEu-2.9.0-190`、`v2.9.0` 都能识别）
2. 版本串是旧 GTQT 的 `1.9.x` 时，改用 2.9 专属类确认（`MTEManager` / `MTERegistry` /
   `IPollution` / `IGuiHolder`），确认到才判为 2.9
3. 版本串无法判断时，退化为类/字段探测；每个探针单独 try/catch，缺一个字段不会中断后续探测
   （早期版本就是被 `GregTechAPI.coilManager` 的 `NoSuchFieldException` 打断，直接判成 UNKNOWN）

注意 GTQT 2.9 的 `mcmod.info` 里 modid 仍是 `gregtech`，所以不能靠 modid 区分版本。
确实识别不出时版本记为 `UNKNOWN`：兼容层照常完成初始化、适配器可查询，但不启用任何
版本专属 shim —— 猜错版本会往错误方向搭桥，比不搭更糟。

### 兼容层实现
兼容层使用以下技术实现：
1. **ASM coremod**：类加载期注入缺失的方法/字段（见「1.7.0 新增的 2.8→2.9 桥接」）；
   项目未启用 Mixin（`use_mixins = false`），全部改写由 `IFMLLoadingPlugin` 注册的
   `IClassTransformer` 完成
2. **反射**：动态访问不同版本的 API
3. **代理模式**：为新旧 API 创建统一的访问接口
4. **方法缓存**：优化反射性能
5. **版本特定适配器**：为每个版本维护独立的适配逻辑

## 使用方法

### 安装
1. 下载最新版本的 `GTCEu-Compatibility-Layer.jar`
2. 将文件放入 Minecraft 的 `mods` 文件夹
3. 确保已安装 GTCEu 2.8 或 2.9

### 依赖项
- Minecraft 1.12.2
- MinecraftForge 14.23.5.2850 或更高版本
- GregTech CE Unofficial 2.8.0 或更高版本

### 配置
模组不需要额外配置。它会自动检测 GTCEu 版本并启用相应的兼容层。

## 开发者指南

### 使用兼容层 API
```java
import com.gtceu.compat.api.GTCEuVersion;
import com.gtceu.compat.metatileentity.MetaTileEntityCompat;
import com.gtceu.compat.recipes.RecipeCompat;

// 检测版本
GTCEuVersion version = GTCEuCompatMod.getDetectedVersion();

// 使用兼容的 MetaTileEntity ID 获取方法
ResourceLocation id = MetaTileEntityCompat.getMetaTileEntityId(metaTileEntity);

// 使用兼容的配方构建器
Object builder = RecipeCompat.createRecipeBuilder(recipeMap, duration, EUt);
```

### 添加新的兼容层
1. 在 `com.gtceu.compat` 包下创建新的兼容类
2. 实现 `initialize()`, `enableV28Shims()`, 和 `enableV29Shims()` 方法
3. 在 `GTCEuCompatMod` 中注册新的兼容层

## 构建项目

```bash
# 克隆项目
git clone https://github.com/yourusername/GTCEu-Compatibility-Layer.git

# 进入项目目录
cd GTCEu-Compatibility-Layer

# 构建模组
./gradlew build
```

## 验证方法

### 1. 构建
跑 `gradlew build`（见上一节「构建项目」）。交付产物是 `build/libs/gtceu_compat-<version>.jar`，
同目录还有 `-sources.jar` 与 `-dev.jar`，只有不带后缀的那个是给玩家放进 `mods` 的。

### 2. 用 javap 核对桥接目标签名
桥不是照文档写的，是照 jar 里的真实 descriptor 写的。改桥之前先确认两端：

```bash
# 2.8 侧：旧签名到底长什么样
javap -p -s -classpath build/libs/gregtech-1.12.2-2.8.10-beta.jar \
      gregtech.api.recipes.RecipeBuilder | findstr /C:"getEUt" /C:"EUt"

# 2.9 侧：委托方法是否存在、descriptor 是否一致
javap -p -s -classpath /path/to/gregtech-gtqt-1.12.2-2.9.0.jar \
      gregtech.api.capability.impl.AbstractRecipeLogic
```

`-s` 必加：只输出 `javap -p` 会给人读的泛型形式，桥要用的是 `-s` 打印的那行
`descriptor:`。都核过之后再去改 `BridgeSets`，否则注入的桥指向一个不存在的方法，
运行期只会静默跳过。

### 3. 运行时确认注入结果
类加载时看日志：

```
[GTCEuCompat] Injected N 2.8 API bridge(s) into gregtech/api/...
[GTCEuCompat] Injected gregtech/api/unification/material/Material$Builder.toolStats(ToolProperty) -> ...
```

在真 2.8 宿主上应当**看不到**这些行（旧签名已存在 → 自动 no-op），这本身就是一次
「没写错版本」的验证。

## 许可证

本项目基于 MIT 许可证开源。

## 贡献

欢迎提交 Pull Request 和 Issue！

## 致谢

- GregTechCEu 团队
- GregTech 社区
- 所有贡献者

## 相关链接

- [GregTech CEu GitHub](https://github.com/GregTechCEu/GregTech)
- [GTCEu Wiki](https://github.com/GregTechCEu/GregTech/wiki)
- [GTCEu 2.9 MetaTileEntity 变更](https://github.com/GregTechCEu/GregTech/wiki/2.9-MetaTileEntity-Changes)
