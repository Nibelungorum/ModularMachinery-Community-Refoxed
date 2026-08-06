# ModularMachinery: Community Edition (MMCE) — 项目分析与 API 变动

> 本文档是 MMCR（ModularMachinery-Community-Refoxed）项目的**参考基线**，由三部分组成：
>
> 1. **§1–§10 MMCE 1.12.2 功能全景**：原 `reference/mmce`（Version 2.3.2, MC 1.12.2, Forge 14.21+）的逐项拆解。编写人：KasumiNova、各类社区贡献者与原作者 HellFirePvP / wiiv / youyihj / ikexing。维护状态：原项目已停更，但内容代表 MMCE 的最终版本。
> 2. **§11 KasumiNova 扩展层可移植性分析**：MMCE 后期由 `github.kasuminova.mmce.*` 引入的高级扩展层。
> 3. **§12 MMCE 1.12.2 → MMCR 26.1.2 NeoForge API 变动**：跨大版本（1.12.2 → 1.21.1，Forge 14.21+ → NeoForge 26.1.2）的关键 API 差异。详细映射见 [`api-mapping.md`](./api-mapping.md)。
>
> 整体阅读建议：先看 §1.0 摘要决定是否需要深入；逐阶段实现时按需回查对应章节。

---

## 0. 目录

| 章节 | 内容 |
|------|------|
| §1   | 项目基本盘（版本、依赖、构建、模块坐标） |
| §2   | 包结构总览（顶级包与各包职责） |
| §3   | Mod 入口与生命周期（`ModularMachinery` / `CommonProxy` / `ClientProxy`） |
| §4   | 核心多方块机器系统（`DynamicMachine` / `AbstractMachine` / `MachineRegistry` / `MachineLoader` / `MachineComponent`） |
| §5   | 结构匹配（`BlockArray` / `TaggedPositionBlockArray` / `BlockArrayCache` / `DynamicPattern`） |
| §6   | 配方系统（`MachineRecipe` / `RecipeRegistry` / `RecipeLoader` / `RecipeAdapter` / `RecipeCraftingContext`） |
| §7   | 内置 ResourceType（需求类型） |
| §8   | 内置 ComponentType（组件类型） |
| §9   | 修饰符 / Modifier（`RecipeModifier` / `SingleBlockModifierReplacement` / `MultiBlockModifierReplacement` / `ModifierRegistry`） |
| §10  | 方块 / TileEntity 矩阵 |
| §11  | KasumiNova 扩展层可移植性分析 |
| §12  | API 变动（1.12.2 → 26.1.2 NeoForge） |

---

# 第 1–10 章：MMCE 1.12.2 功能全景

## 1. 项目基本盘

| 项 | 值 |
|---|---|
| 项目根 | `reference/mmce` |
| 命名空间 (MODID) | `modularmachinery` |
| 版本 | `2.3.2`（mod version 通过 `Tags.VERSION` 注入） |
| Minecraft | `1.12.2` |
| Forge | `>=14.21.0.2371` |
| 必需依赖 | `forge`, `crafttweaker@4.0.4+`, `zenutils@1.12.8+`, `jei@4.13.1.222+`, `gregtech@2.7.4-beta+` |
| 软依赖 | `appliedenergistics2(rv6-stable-7+)`, `fluxnetworks@4.1.0+`, `tconstruct@1.12.2-2.12.0.157+`, `thermalexpansion@5.5.0+` |
| 远程依赖版本 | `acceptableRemoteVersions = [2.1.0, 2.4.0)` |
| 编译 | `Kotlin 2.2.0` + `Java 17 toolchain` (target 8) + `RetroFuturaGradle 1.4.0` + `Jabel 0.4.2` |
| 渲染 | `Lumenized` Bloom Effect + `GeckoLib 3.0.31` |
| 贡献者 | HellFirePvP, wiiv, KasumiNova, youyihj, ikexing, kport 等 |

`build.gradle.kts` 同时挂载了一系列「撰写原始方块/机器」的工具集：AnvilCraft、Multiblocked、ModularMagic（kport 编译版）、Astral Sorcery、Blood Magic、Thaumcraft、Nature's Aura、Botania、Baubles、Guide-API、Patchouli、Thaumic Augmentation、Draconic Evolution、COFH Core / World / Thermal Foundation、Thermal Expansion、StellarCore、ConfigAnytime、Mekanism、Mekanism Energistics、AE2（关闭）、AE2 Extended Life、AE2 Fluid Crafting Rework、NAE2、GregTech CE Unofficial（compileOnly）、IC2、Nuclearcraft Overhauled、Flux Networks、Tinkers Construct。

---

## 2. 包结构总览

```
hellfirepvp.modularmachinery                ── 原 HellFirePvP 主干
   .client                                  ── 客户端 GUI / 渲染
   .common.base                              ── Mods 兼容判定
   .common.block[.prop]                      ── 全部方块
   .common.command                           ── /mm 子命令
   .common.container                         ── 全部 Container
   .common.crafting
       .adapter.{ic2,nco,tc6,tconstruct,te5} ── 各 mod 配方适配器
       .component                            ── 组件类型
       .helper                               ── 上下文 / 工艺检查
       .requirement[.jei][.type]             ── 需求类型 + JEI 集成
       .tooltip                              ── 蓝图提示
   .common.data                              ── Config / ModDataHolder
   .common.integration.{crafttweaker,
             fluxnetworks,ingredient,preview,
             recipe,theoneprobe}             ── 各种 mod 集成入口
   .common.item                              ── 物品
   .common.lib                               ── Resources / Keys
   .common.machine[.factory]                 ── 机器定义 / 工厂机械
   .common.modifier                          ── 修饰符
   .common.network                           ── 旧网络包
   .common.registry                          ── 全部 registry
   .common.selection                         ── 玩家方块选择（蓝图工具）
   .common.tiles[.base]                      ── 全部 TileEntity
   .common.util[.nbt]                        ── 工具类

github.kasuminova.mmce                      ── KasumiNova 全部新增（§11 单独分析）
   .client.{gui,model,renderer,resource,util,world,preivew}
   .common.{block.appeng, capability, concurrent, container[.handler],
             event.{client,machine,recipe}, handler, helper,
             integration.{gregtech,theoneprobe}, itemtype,
             machine.{component,pattern}, network, tile[.base],
             upgrade[.registry], util[.concurrent], world}
   .mixin.{ae2, ae2.nae2, jei, minecraft}

ink.ikx.mmce                                ── ikexing 自动组装
   .common.assembly                          ── MachineAssembly + Manager
   .common.utils                             ── StructureIngredient
   .core                                     ── 事件总线

kport.modularmagic                          ── 魔法合成模块
   .common.{block, container, crafting.{component,helper,requirement[.types]},
            event, integration.{crafttweaker,jei},
            item, network, tile.machinecomponent, utils}

youyihj.mmce                                ── youyihj 集装箱
   .common.item                              ── MachineProjector
   .common.preview                           ── StructurePreviewHelper

com.cleanroommc.client                      ── 移植 cleanroommc 的预览渲染
   .preview.renderer.scene                   ── WorldSceneRenderer / FBO
   .shader                                   ── ShaderManager
   .util[.world]                             ── 数学 / 假世界 / 缓冲区
```

---

## 3. Mod 入口与生命周期

### 3.1 `ModularMachinery`（根 Mod）

`reference/mmce/src/main/java/hellfirepvp/modularmachinery/ModularMachinery.java`

- `@Mod(modid = "modularmachinery", name = "Modular Machinery: Community Edition", version = Tags.VERSION, dependencies = "...", acceptedMinecraftVersions = "[1.12, 1.13)", acceptableRemoteVersions = "[2.1.0, 2.4.0)")`
- 公共常量：
  - `MODID = "modularmachinery"`
  - `NET_CHANNEL = SimpleNetworkWrapper`，**注册 15 个网络包**（详见 §10.4）。
  - `EXECUTE_MANAGER = new TaskExecutor()`。
  - `EVENT_BUS = new EventBus()` 私有总线。
- `static { FluidRegistry.enableUniversalBucket(); }`
- 生命周期：`preInit`（注册网络包 + 加载 ModData）→ `init` → `postInit`（注册机器 / RecipeAdapter / RecipeEvent）→ `loadComplete`（异步构建 `BlockArrayCache`）→ `onServerStart`（注册 5 个命令）。

### 3.2 `CommonProxy` / `ClientProxy`

- `CommonProxy` 持有 `ModDataHolder`（机器 / 配方 / 变量目录管理）、`CreativeTabs`、`InternalRegistryPrimer`。
- 阶段职责与 MMCR `MMCR.java` 主类一一对应（见 [`architecture.md` §3.1](./architecture.md#31-mod-入口)）。
- `ClientProxy` 额外注册：JEI、TOP 客户端、ModularMagic 客户端、GeckoLib 控制器模型、Bloom 渲染、`BLOCK_MODEL_HIDER`、`ClientScheduler`。

---

## 4. 核心多方块机器系统

### 4.1 `AbstractMachine` / `DynamicMachine` / `MachineRegistry`

```
AbstractMachine                                 抽象：registryName、local 化名、definedColor、
                                                maxParallelism、internalParallelism、maxThreads、
                                                requiresBlueprint、parallelizable、hasFactory、
                                                factoryOnly、failureAction
DynamicMachine extends AbstractMachine          modifiers / multiBlockModifiers / dynamicPatterns /
                                                coreThreadPreset / smartInterfaces /
                                                machineEventHandlers / pattern / hideComponentsWhenFormed
MachineRegistry                                 单例：WAIT_FOR_LOAD_MACHINERY + LOADED_MACHINERY
MachineLoader                                   GSON 两阶段反序列化
```

- 默认值：`maxParallelism = Config.maxMachineParallelism`、`definedColor = Config.machineColor`、`parallelizable = Config.machineParallelizeEnabledByDefault`、`hasFactory = Config.enableFactoryControllerByDefault`。
- `MachineRegistry.getAllRegisteredMachinery()` 在 ZenScript 中可访问。
- `DynamicMachine.MachineDeserializer` 是自定义 GSON 反序列化器，配合 `MachineLoader` 两阶段加载。

### 4.2 `MachineComponent` / `IOType` / `MachineCombinationComponent`

- `MachineComponent<T>`：三种内置子类型 `ItemBus` / `FluidHatch` / `EnergyHatch`，`isAsyncSupported()` 默认 true。
- `IOType { INPUT, OUTPUT }`：`getByString(String)`。
- 实现 `MachineCombinationComponent` 支持多种类组件。

---

## 5. 结构匹配

### 5.1 `BlockArray` / `TaggedPositionBlockArray` / `BlockArrayCache`

```
BlockArray                  Map<BlockPos, BlockInformation>，matches(World, BlockPos, ...)
BlockInformation            可序列化 / GSON / NBT / 旋转 / 镜像
BlockArrayCache             按 EnumFacing 缓存预旋转版本
BlockCompatHelper           检查 IC2 / GregTech 等 mod 方块兼容
IBlockStateDescriptor       变量替换
TaggedPositionBlockArray    BlockArray + ComponentSelectorTag（按标签查找组件）
```

### 5.2 `DynamicPattern`

`github.kasuminova.mmce.common.util.DynamicPattern`（MMCE 创新点：可伸缩结构）：

- 字段：`name, minSize, maxSize, faces`、`pattern` + `patternEnd`、`structureSizeOffsetStart`、`structureSizeOffset`。
- `matches(TileMultiblockMachineController, oldState, ctrlFace) → MatchResult(size, facing)`。
- `addPatternToBlockArray(BlockArray, maxSize, ...)`：把动态结构展开注入到 `BlockArray`。
- `Status`（record）：NBT 序列化（patternName、facing、size）。

### 5.3 `PlayerStructureSelectionHelper`

- 维护玩家用 `ItemConstructTool` 选择的方块集合。
- `toggleInSelection` / `purgeSelection` / `finalizeSelection` / `sendSelection`。
- 通过 `PktSyncSelection` 显示边框。

---

## 6. 配方系统

### 6.1 `MachineRecipe` / `PreparedRecipe` / `RecipeRegistry`

```
MachineRecipe                recipeFilePath / registryName / owningMachine / tickTime /
                             configuredPriority / voidPerTickFailure / parallelized /
                             List<ComponentRequirement> / recipeEventHandlers /
                             tooltipList / threadName / maxThreads / loadJEI
PreparedRecipe (CT 端)       getFilePath / getRecipeRegistryName / getAssociatedMachineName /
                             getParentMachineName / getTotalProcessingTickTime / getPriority /
                             voidPerTickFailure / getComponents / getRecipeEventHandlers /
                             getTooltipList / isParallelized / getMaxThreads / getThreadName /
                             loadNeedAfterInitActions / getLoadJEI
RecipeRegistry               单例：registerModifiedMachineRecipe /
                             registerRecipeAdapterEarly / registerDynamicMachineAdapter /
                             loadRecipeRegistry / getRecipesFor
```

- `MachineRecipe.mergeAdapter(RecipeAdapterBuilder)`、`copy(registryNameChange, newOwningMachineIdentifier, modifiers)`。
- `MachineRecipeContainer`：可把一个机器的所有配方按多个「sub machine name」展开。

### 6.2 `RecipeAdapter` / `RecipeAdapterRegistry`

- 抽象 `RecipeAdapter`：提供 `createRecipesFor(owningMachineName, modifiers, additionalRequirements, eventHandlers, recipeTooltips)` 与 `createRecipeShell(...)`。
- 内置 12 个 adapter：minecraft:furnace、ic2:compressor/macerator、nuclearcraft:*、tconstruct:smeltery_*、thaumcraft:infusion_matrix、thermalexpansion:insolator(_fluid)。
- `DynamicMachineRecipeAdapter`：把一台已有机器 `originalMachine` 的所有配方当作模板，复制到另一台机器上。

### 6.3 `RecipeCraftingContext` / `ActiveMachineRecipe` / `RecipeCraftingContextPool`

- `RecipeCraftingContext`：一次配方执行的完整状态——`Map<MachineComponentType, ProcessingComponent>`、`ModifierList`、`CraftingCheckResult`、IOInventory 取还记录。
- 提供 `checkStartResult` / `checkPreStartResult` / `finalizeStart` / `finalizeTick` / `finalizeFinish`。
- `ComponentOutputRestrictor`：限制输出。
- `RecipeCraftingContextPool` 对象池（与 `FactoryRecipeThread` 配合）。

---

## 7. 内置 ResourceType（需求类型）

注册位置：`hellfirepvp.modularmachinery.common.registry.RegistryRequirementTypes`

| KEY | 类 | 资源 | 备注 |
|---|---|---|---|
| `modularmachinery:item` | `RequirementTypeItem` | `ItemStack` | 支持 `item@meta`、`ore:`、`any:fuel`、`chance`、`nbt`、`nbt-display` |
| `modularmachinery:item_durability` | `RequirementTypeItemDurability` | `ItemStack` | 按耐久消耗 |
| `modularmachinery:ingredient_array_input` | `RequirementTypeIngredientArray` | 多选一 | 多物品候选 |
| `modularmachinery:fluid` | `RequirementTypeFluid` | `FluidStack` | 总消耗 |
| `modularmachinery:fluid_pertick` | `RequirementTypeFluidPerTick` | `FluidStack` | 每 Tick 消耗 |
| `modularmachinery:gas` | `RequirementTypeGas` | `GasStack` | Mekanism |
| `modularmachinery:gas_pertick` | `RequirementTypeGasPerTick` | `GasStack` | Mekanism |
| `modularmachinery:energy` | `RequirementTypeEnergy` | `long` | FE 总消耗 |
| `modularmachinery:duration` | `RequirementDuration` | n/a | 仅作为 RecipeModifier 目标 |
| `modularmachinery:interface_number_input` | `RequirementTypeInterfaceNumInput` | `float` | 智能接口数值 |

### 7.1 ModularMagic 资源类型（kport）

10 种魔法类型，对应 Astral Sorcery / Botania / Blood Magic / Nature's Aura / Thaumcraft。每种都有 `requiresModid()` 软依赖检查。

### 7.2 一些细节

- `RequirementItem` 支持 `chance` 和 `nbt` / `nbt-display`。
- `RequirementFluid` / `RequirementFluidPerTick` 支持 `chance`。
- `RequirementEnergy` 支持守恒型（perTotal）与消耗型（perTick）。
- `RequirementInterfaceNumInput` 允许配方通过 `internalInterfaceNumber` 匹配 `SmartInterface` 数值。

---

## 8. 内置 ComponentType（组件类型）

注册位置：`RegistryComponentTypes`

| KEY | 类 | 描述 |
|---|---|---|
| `modularmachinery:item` | `ComponentItem` | 物品总线槽 |
| `modularmachinery:fluid` | `ComponentFluid` | 流体仓 |
| `modularmachinery:item_fluid` | `ComponentItemFluid` | 同时支持物品和流体（kasumi nova） |
| `modularmachinery:gas` | `ComponentGas` | Mekanism 气体 |
| `modularmachinery:energy` | `ComponentEnergy` | FE 仓 |
| `modularmachinery:interface_number` | `ComponentSmartInterface` | 智能接口数值 |
| `modularmachinery:parallel_controller` | `ComponentParallelController` | 并行控制器 |
| `modularmachinery:upgrade` | `ComponentUpgradeBus` | 升级仓 |

每种都对应一个 `MachineComponent<XxxProvider>`、`TileXxxProvider`、`BlockXxxProvider[Input/Output]`。

### 8.1 ModularMagic 组件类型

`kport.modularmagic.common.crafting.component`：`ComponentAspect` / `ComponentAura` / `ComponentConstellation` / `ComponentGrid` / `ComponentImpetus` / `ComponentLifeEssence` / `ComponentMana` / `ComponentRainbow` / `ComponentStarlight` / `ComponentWill`。

### 8.2 容器与组件的 Flex 机制

- 同一 `ComponentType` 可由多个不同方块提供（普通 item bus、MEItemBus、SmartInterface 等）。
- `TileMultiblockMachineController` 内部用 `Map<Long, Map<TileEntity, ProcessingComponent<?>>>` 收纳同 `groupId` 的组件。
- `MachineGroupInput` 接口：实现该接口的 Tile 可将其内部 buffer 视作「组输入」。

---

## 9. 修饰符 / Modifier

### 9.1 `RecipeModifier`

```
RecipeModifier
  ├─ target            RequirementType<?, ?>（可为 null）
  ├─ ioTarget          INPUT / OUTPUT
  ├─ modifier          float
  ├─ operation         OPERATION_ADD=0 / OPERATION_MULTIPLY=1
  └─ chance            bool
```

- `applyValueToApplier(applier, mod)` / `applyModifiers(context, in, value, isChance)`。
- `applyModifiers(modifiers, ...)` 静态多重重载：把整组 modifier 应用到 value 上，先 add 后 mul。
- `serialize()` / `deserialize()`：NBT 持久化。
- `ModifierApplier` 内部结构：分别记录 `inputAdd/outputAdd/inputMul/outputMul`。
- `Deserializer`（Gson）：JSON 形态 `{io, target, multiplier, operation, affectChance}`。

### 9.2 `SingleBlockModifierReplacement` / `MultiBlockModifierReplacement` / `DynamicModifierReplacement`

- `SingleBlockModifierReplacement`：替换单个方块（位置 ↔ `BlockInformation` 列表）。
- `MultiBlockModifierReplacement`：替换整个多方块结构（按主 anchor）。
- `DynamicModifierReplacement`：机器层 `dynamicPattern` / `coreThread` 也能被 modifier 替换。
- `ModifierRegistry`：`AbstractModifierReplacement` 注册器。

---

## 10. 方块 / TileEntity / 网络 / 命令矩阵

### 10.1 方块矩阵

| 方块 | 别名 / 备注 |
|---|---|
| 控制器 / 工厂控制器 | `BlockController` / `BlockFactoryController`（继承 `BlockController`，天然 Geckolib 模型） |
| 外壳 | `BlockCasing` 6 种 `CasingType` |
| 物品输入 / 输出总线 | `BlockInputBus` / `BlockOutputBus`，9 等级 |
| 流体输入 / 输出仓 | `BlockFluidInputHatch` / `BlockFluidOutputHatch`，6 等级 |
| 能源输入 / 输出仓 | `BlockEnergyInputHatch` / `BlockEnergyOutputHatch`，4 等级 |
| 升级仓 | `BlockUpgradeBus`，4 等级 |
| 智能接口 | `BlockSmartInterface` |
| 并行控制器 | `BlockParallelController`，5 等级 4/16/64/256/512 |
| ME 系列 | `BlockMEItemInputBus` / `BlockMEItemOutputBus` / `BlockMEFluidInputBus` / `BlockMEFluidOutputBus` / `BlockMEGasInputBus` / `BlockMEGasOutputBus` / `BlockMEPatternProvider` / `BlockMEPatternMirrorImage` |
| ModularMagic | 10 种 provider / 输入输出组合 |

### 10.2 TileEntity 矩阵

| Tile | 父类 | 简介 |
|---|---|---|
| `TileMachineController` / `TileFactoryController` | `TileMultiblockMachineController` | 单线程 / 多线程 |
| `TileItemInputBus` / `TileItemOutputBus` | `TileItemBus` | 物品总线 |
| `TileFluidInputHatch` / `TileFluidOutputHatch` | `TileFluidTank` | 流体仓 |
| `TileEnergyInputHatch` / `TileEnergyOutputHatch` | `TileEnergyHatch` | FE 仓 |
| `TileUpgradeBus` | `TileColorableMachineComponent` | 升级仓 |
| `TileSmartInterface` | `TileMultiblockMachineController` 辅助 | 数值接口 |
| `TileParallelController` | `TileColorableMachineComponent` | 并行 |
| ME 系列 | `MEMachineComponent` | AE2 / Mekanism Energistics 集成 |
| ModularMagic | 各 provider | 魔法合成 |

### 10.3 关键基础设施

- `ComponentRestriction`（`machine`）：约束按位置 / 朝向选择组件。
- `ComponentSelectorTag`：被 `TaggedPositionBlockArray` 使用。
- `SelectiveUpdateTileEntity`：只同步少量字段。
- `ColorableMachineTile`：可染色。
- `MachineGroupInput`：多物品仓共享同一组 input。
- `GTEnergyContainer`：GTCeu 兼容的能量能力。

### 10.4 网络数据包

总共注册 15 个 `PktXxx`（在 `ModularMachinery.preInit` 注册）。客户端发：13 个（`PktCopyToClipboard` / `PktSyncSelection` / `PktSmartInterfaceUpdate` / `PktGroupInputConfig` / `PktInteractFluidTankGui` / `PktParallelControllerUpdate` / `PktAutoAssemblyRequest` / `PktMEPatternProviderAction` / `PktMEPatternProviderHandlerItems` / `PktMEInputBusInvAction` / `PktMEInputBusRecipeTransfer` / `PktMEOutputBusStackSizeChange` / `PktSwitchGuiMEOutputBus`）。服务端发：2 个（`PktPerformanceReport` / `PktAssemblyReport`），加 kport 的 `StarlightMessage`。

### 10.5 命令

`/mm syntax` / `/mm hand` / `/mm blueprint` / `/mm performance` / `/mm reload`（ZenUtils 在场时）。`kport.modularmagic` 与 `ink.ikx.mmce` 通过 Forge 事件而非独立命令。

### 10.6 蓝图 / 工具 / 投影

- `ItemBlueprint`：绑定特定机器的物品，记录机器 registryName + 几何缓存。
- `ItemConstructTool`：创造模式工具，选择多个方块 → 选中 → 点击控制器生成 Blueprint。
- `ItemDebugStruct`：调试用，配合 `ItemMachineProjector` 投影当前结构。
- `ItemModularium`：装饰物品，可染色。
- `MachineProjector`（youyihj）：在世界中按方向投出当前机器结构。

### 10.7 自动组装（ikx）

- `AssemblyConfig`：配置开关。
- `MachineAssembly`：玩家拿着 Blueprint + 大量材料 → 在控制器上右键 → `PktAutoAssemblyRequest` → 服务端构造 `MachineAssembly`，逐 tick 把方块放到对应位置。
- `AssemblyEventHandler`：监听 `PlayerInteractEvent.RightClickBlock` 触发组装。
- `MachineAssemblyManager`：管理「同时进行的多个组装」。
- `StructureIngredient`：拆分结构为 `ItemIngredient`（每方块 1 个选项链）和 `FluidIngredient`（流体方块）。

---

# 第 11 章：KasumiNova 扩展层可移植性分析

> 范围：原 `reference/mmce/src/main/java/github/kasuminova/mmce/**`，即 MMCE 中 KasumiNova 新增的扩展层。原 `hellfirepvp.modularmachinery.*` 主干、`ink.ikx.mmce.*` 自动组装、`youyihj.mmce.*` 投影器、`kport.modularmagic.*` 魔法模块不在本章主范围。
>
> 目标：列出哪些内容适合移植到当前 MMCR NeoForge 26.1.2，哪些需要重写，哪些应删除或延后。

## 11.1 结论摘要

`github.kasuminova.mmce.*` 不是一个单独的小兼容包，而是 MMCE 后期核心增强层，覆盖动态 GUI、结构预览、ME/AE2 总线、GTCEu 代理、升级系统、机器事件、配方搜索优化、网络包和 mixin 补丁。它包含大量 1.12.2 Forge、旧 AE2、旧 JEI、旧渲染管线、反射和 mixin 依赖，不能整体搬运。

MMCR 已有 NeoForge 版本的机器定义、结构匹配、controller、item/fluid/energy port、recipe context、modifier、KubeJS、Jade、基础菜单等模块。因此移植策略为：

- **优先移植语义，不搬 API**：保留 MMCE 的 feature 设计和数据流，落地到 `cn.howxu.mmcr.*` 的 NeoForge API。
- **先移植服务端核心**：事件、recipe search/context pool、upgrade、special block proxy 这类不强依赖旧客户端的内容优先级更高。
- **客户端体验分阶段做**：动态 GUI、结构预览、模型渲染能提升体验，但依赖新版渲染/JEI/菜单体系，必须重写。
- **第三方联动后置**：AE2/ME、GTCEu、Mekanism gas 类内容只在核心闭环稳定后作为独立 compat 阶段处理。
- **1.12.2 mixin 默认不移植**：只有新版 API 无法覆盖时，再为具体兼容点写新的 NeoForge/Mixin 补丁。

## 11.2 MMCR 当前基线对照

| 能力 | 当前实现位置 | 对 `github.kasuminova.mmce` 移植的意义 |
|---|---|---|
| 机器定义 | `api.machine.Machine` / `DynamicMachine` / `BlockArray` / `StructureMatcher` / `MachinePatternCompiler` | 可承接动态结构、modifier replacement、preview 数据源 |
| 控制器运行时 | `internal.tile.MachineControllerBlockEntity` | 可承接 recipe event、recipe search、running status、parallel/upgrade 后续接入 |
| Item/Fluid/Energy 端口 | `internal.tile.*BusBlockEntity` / `*HatchBlockEntity` / `IOPortBlockEntity` | 可承接 component 路由与 selector tag |
| Recipe 层 | `api.recipe.*` / `RecipeCraftingContext` / `RecipeSearchTask` / `RecipeCraftingContextPool` | 已经有 MMCE 同名语义，应继续对齐 |
| Modifier 层 | `api.recipe.modifier.*` | 可承接 upgrade 与结构替换类逻辑 |
| 网络 | `internal.network.*Payload`（`PktMachineStatePayload` / `PktMultiblockDetectorPickPayload`） | 旧 `IMessage` 网络包必须重写为 NeoForge payload |
| 菜单/屏幕 | `internal.menu.*` / `client/gui/*` | 动态 GUI 可重映射到新版 `AbstractContainerMenu` / Screen |
| Compat | `compat.kubejs` / `compat.jade` | MMCE 的 CraftTweaker/TOP 语义已映射到 KubeJS/Jade |

## 11.3 可移植性分级

### A 类：建议优先移植或继续对齐

这类内容主要是服务端逻辑、数据结构或公共语义，较少绑定 1.12.2 客户端 API。

| MMCE 模块 | 代表类/包 | 移植方式 | 说明 |
|---|---|---|---|
| 配方搜索任务 | `common.concurrent.RecipeSearchTask` / `FactoryRecipeSearchTask` | 重映射 | 当前已有 `api.recipe.RecipeSearchTask`，应比对 MMCE 的搜索失败原因、并行搜索、缓存边界，补齐语义 |
| 上下文池 | `common.concurrent.RecipeCraftingContextPool` | 直译简化 | 当前已有同名类；可移植对象复用策略，但需避免跨 tick 保存过期 BE/capability |
| 同步/计时工具 | `common.concurrent.Sync` / `common.util.TimeRecorder` | 按需重写 | `Sync` 意图可保留；`TimeRecorder` 可用于后续性能报告，不应先引入复杂 UI/网络 |
| 机器/配方事件 | `common.event.recipe.*` / `common.event.client.*` / `Phase` | 重映射 | RecipeStart/Tick/ResultChance 等事件适合映射到 NeoForge EventBus 或 MMCR 私有事件总线 |
| Helper/Checker | `common.helper.AdvancedBlockChecker` / `AdvancedItemChecker` / `IBlockStatePredicate` / `IDynamicPatternInfo` | 重映射 | 与结构匹配、KubeJS builder、modifier replacement 关系密切 |
| Pattern 特殊代理 | `common.machine.pattern.SpecialItemBlockProxy` / `SpecialItemBlockProxyRegistry` | 重映射 | 适合支持「物品代表方块」「虚拟结构匹配」「第三方 block proxy」 |
| 常用工具 | `common.util.HashedItemStack` / `PatternItemFilter` | 直译/重写 | `HashedItemStack` 需要适配 `DataComponentPatch`；`PatternItemFilter` 可用于 blueprint/preview/auto assembly |
| 升级数据模型 | `common.upgrade.*` / `common.upgrade.registry.*` | 重映射 | 升级系统是 MMCE 核心增强之一；方块、GUI、脚本 API 需分阶段 |

### B 类：可移植，但必须重写适配层

这类内容有明确功能价值，但直接绑定旧 Minecraft/Forge/AE2/JEI/渲染 API。

| MMCE 模块 | 代表类/包 | 移植方式 | 说明 |
|---|---|---|---|
| 动态 GUI 基础 | `client.gui.GuiContainerDynamic` / `GuiScreenDynamic` / `client.gui.widget.base.*` | 重写 | 旧 `GuiContainer`、LWJGL Mouse、Forge 1.12 tooltip API 不可用。可以保留 Widget tree、layout、event bubbling 思路 |
| 通用 Widget | `client.gui.widget.*` / `client.gui.widget.container.*` | 重写 | 按新版 `AbstractWidget`、PoseStack / GuiGraphics 实现 |
| 虚拟槽 / JEI 槽 | `client.gui.widget.slot.*` | 重写 | 新 JEI 29 slot API 差异大，只保留 item/fluid/gas virtual slot 的概念 |
| 结构预览 GUI | `client.gui.widget.impl.preview.*` / `client.preivew.PreviewPanels` | 重写 | 价值高，但依赖世界渲染、假世界、层切换、ingredient list。建议先做 2D/文本预览，再做 3D |
| 客户端模型/渲染 | `client.model.*` / `client.renderer.*` / `client.resource.GeoModelExternalLoader` | 重写/部分删除 | 旧 GeckoLib / Lumenized / Bloom 渲染不应直搬 |
| BlockModelHider | `client.world.BlockModelHider` | 重写 | 用于结构预览/投影时隐藏重叠模型；新版需要走客户端 render hooks |
| 网络包 | `common.network.Pkt*` | 重写 | 每个旧包都要映射到 NeoForge custom payload |
| TOP / Jade 信息 | `common.integration.theoneprobe.MachineryHatchInfoProvider` | 重映射 | 当前已有 Jade compat（`compat.jade.*`），可把 MMCE hatch/controller 展示内容迁移为 Jade provider |
| GTCEu 代理 | `common.integration.gregtech.*` | 重写 compat | 概念可移植：GT energy/fluid/item hatch 作为 MMCR component proxy |
| AE2/ME 集成入口 | `common.integration.ModIntegrationAE2` | 重写 compat | 只保留「注册 AE2 相关升级/组件/菜单」的阶段入口 |

### C 类：只保留设计参考，暂不移植

这类内容价值存在，但依赖功能链太长，或当前项目已有更合适替代。

| MMCE 模块 | 处理方式 | 原因 |
|---|---|---|
| ME Item/Fluid/Gas Bus | 延后 | 依赖新版 AE2、菜单、网络、pattern provider、fluid/gas 生态；应作为独立 AE2 compat 里程碑 |
| ME Pattern Provider | 延后 | 功能复杂，且新版 AE2 pattern/container API 完全不同 |
| Gas 相关虚拟槽/总线 | 延后/视 Mekanism 而定 | 当前核心只有 item/fluid/energy；gas 应归入 Mekanism 阶段 |
| 性能报告 UI/命令 | 延后 | 先保留计时工具，不做完整报告链路 |
| 旧 AEBase GUI | 删除/参考 | AE2 客户端类版本差异过大 |
| 旧 mixin | 默认删除 | 1.12.2 目标、方法名、渲染管线、JEI/AE2 内部结构都不适用 |

## 11.4 分包详细分析

### 11.4.1 `common.concurrent`

**功能价值**：MMCE 用这一层解决 recipe 搜索、工厂配方搜索、上下文复用和任务执行。当前 MMCR 已经有 `RecipeSearchTask` 和 `RecipeCraftingContextPool`，说明这一块已经进入移植轨道。

**建议移植内容**：
- 搜索结果应包含失败原因，而不是只有成功/失败布尔值。
- 搜索过程应只做模拟，不提交 IO。
- context pool 可以复用临时列表、需求匹配状态、组件过滤结果。
- 工厂/并行相关搜索先不引入线程，只保留同步可测试版本。

**不要直搬**：
- 不要在异步线程读写 Level、BlockEntity、Capability。
- 不要照搬 1.12.2 的 `TaskExecutor` 生命周期到 NeoForge server tick。

### 11.4.2 `common.event`

**功能价值**：MMCE 的事件层让脚本/扩展能介入 recipe start、tick、result chance、controller GUI/model render。

**建议移植内容**：
- `RecipeEvent` 基类：携带 machine、controller pos、recipe、context、phase。
- `RecipeTickEvent`：允许观察进度，不建议首期允许取消或改 IO。
- `FactoryRecipeStartEvent` / `FactoryRecipeEvent`：等 factory controller 实现后再迁移。
- `ResultChanceCreateEvent`：等 output chance/modifier 完整后再迁移。
- `ControllerGUIRenderEvent` / `ControllerModelGetEvent` / `ControllerModelAnimationEvent`：客户端渲染阶段再做。

**落地建议**：先做私有 Java event API，再桥接 KubeJS；不要让 KubeJS 类型污染核心 API。

### 11.4.3 `common.helper`

**功能价值**：动态结构、复杂匹配和 controller 抽象的基础。

**建议移植内容**：
- `IBlockStatePredicate`：映射为新版 `BlockState` / `LevelReader` / `BlockPos` predicate。
- `AdvancedBlockChecker`：支持 tag、方块属性、方向、block entity 条件。
- `AdvancedItemChecker`：适配新版 `ItemStack` DataComponent，替代 1.12 NBT 判断。
- `IDynamicPatternInfo`：用于未来动态结构、upgrade replacement、preview。
- `MachineController` / `IMachineController`：不要新增平行控制器抽象；应合并到当前 `MachineControllerBlockEntity` 对外接口。

### 11.4.4 `common.machine.pattern`

**功能价值**：SpecialItemBlockProxy 解决「结构里某些方块/物品不是普通 block state」。

**建议移植内容**：
- 建立 `PatternProxyRegistry`，按 `ResourceLocation` 注册 proxy。
- proxy 输入应使用新版 `ItemStack` / `BlockState` / `HolderLookup.Provider`。
- 只让结构匹配和导出使用 proxy，不要让 runtime recipe IO 直接依赖 proxy。

**优先级**：中期。当前已有 pattern export 和 multiblock detector 时，这一块能增强脚本表达力。

### 11.4.5 `common.upgrade`

**功能价值**：升级系统是 MMCE 相比原 Modular Machinery 的重要扩展。

**建议拆分迁移**：

| 子阶段 | 内容 | 目标形态 |
|---|---|---|
| 1 | 数据模型 | `UpgradeType` / `MachineUpgrade` / `UpgradeInfo` 映射到 `api.upgrade` |
| 2 | 注册 | 用 NeoForge `DeferredRegister` / 普通 registry 管理 upgrade 定义 |
| 3 | 应用点 | controller 成型后扫描 upgrade bus 或 pattern replacement |
| 4 | Modifier 接入 | upgrade 产生 `RecipeModifier`，影响 duration / input / output / chance |
| 5 | GUI/Jade | 展示已安装升级、可用升级、冲突原因 |

### 11.4.6 `common.integration.theoneprobe`

**功能价值**：展示 controller/hatch 信息。当前项目已有 Jade compat，因此这块很适合早期吸收。

**建议移植内容**：
- controller：结构是否成型、机器 id、active recipe、进度、缺失原因。
- hatch/bus：IO 类型、方向、容量、当前存量。
- upgrade/parallel/smart interface 信息等到对应功能实现后再加入。

**处理方式**：不要移植 TOP API，直接映射到 `compat.jade`。

### 11.4.7 `common.integration.gregtech`

**功能价值**：通过 proxy 让 GTCEu 的 energy/fluid/item hatch 作为 MMCE 组件参与 recipe。

**建议移植内容**：
- `MachineComponentProxy` 思路：第三方 BE 不继承 MMCR 接口，也能被包装成 `ProcessingComponent`。
- `GTItemBusProxy` / `GTFluidHatchProxy` / `GTEnergyHatchProxy` 的职责划分。
- `GTBlockMachineProxy` 作为 pattern proxy 的思路。

**不要直搬**：所有 GTCEu 1.12 API、类名、capability 判断都需要换成当前目标版本 API。未确认 GTCEu 版本前不要写实现。

### 11.4.8 `common.integration.ModIntegrationAE2` 与 ME 系列

**可移植目标**：
- ME item input/output bus 映射为 AE2 storage/network 交互组件。
- ME fluid bus 视新版 AE2/附属是否支持 fluid API 决定。
- ME pattern provider 映射为自动把 pattern 输入/输出转换为 MMCR recipe 或 recipe transfer。
- ME bus GUI 使用新版 AE2/NeoForge menu/screen 重写。

**阶段建议**：后期单独开 `AE2 compat` spec，不与核心 runtime 混做。

### 11.4.9 `client.gui` 与 `client.gui.widget`

**功能价值**：MMCE 的动态 GUI 系统解决复杂 controller、结构预览、ME bus、pattern provider 的 UI 组合问题。

**建议保留的设计**：
- `WidgetController` 统一 update / render / input / tooltip 生命周期。
- `WidgetGui` 记录 GUI origin / size。
- container widget 支持 row / column / scrolling / selectable。
- preview widget 与 ingredient list 分离。
- virtual slot 只负责展示/交互，不直接持有真实 inventory。

**必须重写的部分**：
- 鼠标输入从 LWJGL / `Mouse.getEventX()` 改为新版 Screen 回调参数。
- 渲染从旧 GL state / `drawTexturedModalRect` 改为 `GuiGraphics`。
- tooltip 从旧 `drawHoveringText` 改为新版 tooltip API。
- slot / JEI 交互按 JEI 29 API 重写。

### 11.4.10 `client.model` / `client.renderer` / `client.resource` / `client.world`

**功能价值**：动态 controller 模型、GeckoLib 外部模型、Bloom 渲染、结构预览隐藏方块。

**当前建议**：
- 短期不移植 GeckoLib / Bloom 相关实现。
- 可保留「机器定义可指定 controller model/texture」的数据入口，但落地到 vanilla model JSON 或 datagen。
- 结构预览阶段再考虑假世界渲染和 block model hiding。
- 如果未来引入 GeckoLib，需要重新评估依赖和客户端/服务端边界。

### 11.4.11 `mixin`

**现状**：MMCE 有 early/late loader，并根据是否存在 JEI、AE2、NAE2 加载不同 mixin。具体 mixin 目标是 1.12.2 的 `RenderGlobal` / `TileEntityRendererDispatcher` / JEI `RecipesGui` / `RecipeLayout` / AE2 interface terminal / pattern multi tool 等。

**处理结论**：默认不移植。

**原因**：
- 目标类和方法签名基本全部过期。
- NeoForge 与 JEI / AE2 新版通常提供更正式的扩展点。
- 过早引入 mixin 会增加维护成本和启动风险。

**例外**：当新版 AE2 / JEI 没有公开 API 支持某个必要行为时，为该行为单独写新 mixin，并在文档里记录目标类、原因和替代方案。

## 11.5 按优先级排列的可移植清单

### P0：已经在当前项目中部分存在，应继续对齐 MMCE

| 功能 | MMCE 来源 | 当前目标 | 验收点 |
|---|---|---|---|
| Recipe search 语义 | `common.concurrent.RecipeSearchTask` | `api.recipe.RecipeSearchTask` | 成功/失败原因明确，模拟和提交分离 |
| Context pool | `common.concurrent.RecipeCraftingContextPool` | `api.recipe.RecipeCraftingContextPool` | 不复用失效 BE/capability，测试覆盖 roundtrip |
| Requirement/component 路由 | helper + machine component 相关语义 | `api.recipe.helper.*` / `ProcessingComponent` | pattern 内端口参与 IO，pattern 外端口不参与 |
| Modifier replacement | `client.util.UpgradeIngredient` / upgrade replacement 相关 | `api.recipe.modifier.*` | single/multi replacement 能转成统一 modifier 或 pattern metadata |
| Jade 展示 | TOP provider | `compat.jade.*` | 控制器和 hatch/bus 信息可读 |

### P1：核心闭环稳定后建议做

| 功能 | MMCE 来源 | 当前目标 | 验收点 |
|---|---|---|---|
| Recipe lifecycle events | `common.event.recipe.*` | `api.event` 或 internal event bus + KubeJS bridge | recipe start/tick/complete/fail 可观察 |
| Advanced checker | `common.helper.Advanced*Checker` | `api.machine` predicate/checker | block state、tag、item data component 匹配稳定 |
| Special block/item proxy | `common.machine.pattern.*` | pattern proxy registry | 导出/匹配能表达非普通方块条件 |
| Upgrade 数据模型 | `common.upgrade.*` | `api.upgrade.*` | upgrade 能声明类型、冲突、描述和 modifier 输出 |
| 性能计时基础 | `TimeRecorder` | internal profiling utility | 不带 UI，仅日志或 debug 命令可读 |

### P2：作为独立功能阶段

| 功能 | MMCE 来源 | 当前目标 | 验收点 |
|---|---|---|---|
| 动态 GUI / Widget | `client.gui.widget.*` | 轻量 NeoForge widget 层 | controller/upgrade/preview UI 能复用组件 |
| 结构预览 | `client.gui.widget.impl.preview.*` / `client.world.*` | 2D 后 3D preview | 可展示结构层、缺失块、材料清单 |
| JEI 虚拟槽和 recipe transfer | `client.gui.widget.slot.*` / `mixin.jei.*` | JEI 29 category/transfer | 不使用旧 mixin，slot 显示与转移正确 |
| Parallel/factory search | `FactoryRecipeSearchTask` / `TaskExecutor` | server-safe scheduler | 异步不触碰 world，commit 回主线程 |
| Upgrade bus GUI | upgrade + GUI | `UpgradeBusBlockEntity/Menu/Screen` | 安装、卸载、冲突提示、Jade 展示完整 |

### P3：第三方 compat 后期做

| 功能 | MMCE 来源 | 当前目标 | 验收点 |
|---|---|---|---|
| AE2 ME item bus | `MEItemInputBus` / `MEItemOutputBus` | `compat.ae2` | 能从 AE2 网络输入/输出 item |
| AE2 ME fluid bus | `MEFluidInputBus` / `MEFluidOutputBus` | `compat.ae2` | 取决于新版 AE2 fluid 支持 |
| ME pattern provider | `MEPatternProvider` | `compat.ae2.pattern` | pattern 与 MMCR recipe/transfer 对接 |
| GTCEu component proxy | `common.integration.gregtech.*` | `compat.gtceu` | GT hatch 能作为 MMCR component |
| Gas / Mekanism | `MEGas*` / `SlotGasVirtual*` | `compat.mekanism` | gas requirement/component 完整后再做 |

## 11.6 不建议移植清单

| 内容 | 原因 |
|---|---|
| `MMCEEarlyMixinLoader` / `MMCELateMixinLoader` 原样实现 | 1.12.2 Forge/MixinBooter 生命周期不适用于 NeoForge |
| `mixin.minecraft.*` 原目标 | 新版渲染管线已变，不能按旧 RenderGlobal/TESR patch 搬 |
| `mixin.jei.*` 原目标 | JEI 29 API 和内部结构不同，优先用公开 API |
| `mixin.ae2.*` / `mixin.ae2.nae2.*` 原目标 | 旧 AE2/NAE2 类和行为不适用 |
| `AEBaseGuiContainerDynamic` 原样 | 绑定旧 AE2 GUI 基类 |
| 旧 `GuiME*` 原样 | 绑定旧 AE2 fluid slot、reflection、buttonList/guiSlots |
| GeckoLib / Lumenized / Bloom 旧实现 | 当前首期不引入第三方渲染库；旧 API 不适用 |
| 旧网络包 `IMessage` / `SimpleNetworkWrapper` | 必须用 NeoForge custom payload |
| 旧 CraftTweaker / ZenScript 思路 | 当前项目以 KubeJS / Java API / datapack JSON 为入口 |

## 11.7 推荐落地顺序

### 阶段 1：核心语义补齐

1. 对照 MMCE `RecipeSearchTask`，补齐当前 recipe search 的失败原因、模拟边界和 context 生命周期。
2. 引入 recipe lifecycle event 的 Java API 雏形，不立刻暴露 KubeJS 可变能力。
3. 完善 advanced checker / predicate，用于结构匹配、导出和后续 pattern proxy。
4. 把 Jade provider 补成 controller + port 可诊断面板。

### 阶段 2：Upgrade 与 Pattern 扩展

1. 建立 `api.upgrade` 数据模型，不写 GUI。
2. 将 upgrade 输出统一映射为 `RecipeModifier` 或 pattern metadata。
3. 添加 pattern proxy registry，支持特殊 item/block 表达。
4. 在 controller 成型后计算 upgrade / effective modifier，并纳入 recipe context。

### 阶段 3：客户端可用性

1. 先做轻量动态 widget，不完整复制 MMCE widget 包。
2. 做 controller / port / upgrade 的统一 screen 组件。
3. 做结构材料清单和分层 2D preview。
4. 最后评估 3D preview、block model hiding 和自定义渲染。

### 阶段 4：第三方联动

1. JEI category/transfer，避免 mixin。
2. AE2 item bus，再评估 fluid/pattern provider。
3. GTCEu proxy。
4. Mekanism gas。

## 11.8 迁移时的命名建议

不要把 `github.kasuminova.mmce` 包名原样搬到主源码。建议按当前项目结构归档：

| MMCE 包 | MMCR 目标包建议 |
|---|---|
| `github.kasuminova.mmce.common.concurrent` | `cn.howxu.mmcr.api.recipe` 或 `cn.howxu.mmcr.internal.recipe` |
| `github.kasuminova.mmce.common.event` | `cn.howxu.mmcr.api.event` / `cn.howxu.mmcr.internal.event` |
| `github.kasuminova.mmce.common.helper` | `cn.howxu.mmcr.api.machine` / `cn.howxu.mmcr.internal.machine` |
| `github.kasuminova.mmce.common.upgrade` | `cn.howxu.mmcr.api.upgrade` / `cn.howxu.mmcr.internal.upgrade` |
| `github.kasuminova.mmce.common.integration.gregtech` | `cn.howxu.mmcr.compat.gtceu` |
| `github.kasuminova.mmce.common.integration.ModIntegrationAE2` | `cn.howxu.mmcr.compat.ae2` |
| `github.kasuminova.mmce.client.gui.widget` | `cn.howxu.mmcr.client.gui.widget` |
| `github.kasuminova.mmce.client.renderer/model/world` | `cn.howxu.mmcr.client.render` / `client.preview` |

## 11.9 验收标准

每个被迁移的功能都应满足：

- 不引入 1.12.2 Forge / 旧 Minecraft 类名、反射字段名、旧 JEI/AE2 API。
- 核心 API 不硬依赖 KubeJS、JEI、Jade、AE2、GTCEu、Mekanism。
- 服务端逻辑不引用客户端类。
- capability 访问遵循 NeoForge 当前 API。
- 网络同步使用 NeoForge payload，并有明确 client/server 方向。
- recipe IO 坚持 simulate → commit，不在搜索阶段修改库存、流体或能量。
- 可选 compat 未安装时，主 mod 可正常启动。

## 11.10 最小推荐 TODO

1. **MMCE recipe event/search 对齐**：补齐 `RecipeSearchTask`、失败原因、事件雏形。
2. **MMCE upgrade model port**：只做数据模型和 modifier 接入，不做 GUI/方块。
3. **Pattern proxy + advanced checker**：让结构匹配表达力接近 MMCE，为后续 preview/upgrade/third-party proxy 铺路。

这三个都完成后，再进入动态 GUI、结构预览、AE2/GTCEu 兼容会更稳。

---

# 第 12 章：API 变动（1.12.2 → 26.1.2 NeoForge）

> **详细映射见 [`api-mapping.md`](./api-mapping.md)**（Item / Energy / Fluid / Capability / Recipe 完整对照表）。本章只列关键差异与影响范围。

## 12.1 跨大版本核心变化

| 维度 | MMCE 1.12.2 | MMCR 26.1.2 |
|---|---|---|
| Minecraft | 1.12.2 | 1.21.1 |
| Mod 平台 | Forge 14.21+ | NeoForge 26.1.2 |
| 渲染 | LWJGL2 / `drawTexturedModalRect` / 旧 GL state | LWJGL3 / `GuiGraphics` / `PoseStack` |
| 数据序列化 | 自写 GSON 双阶段 + NBT | `MapCodec` / `Codec` + `DataComponentMap` / `CompoundTag` |
| 配方发现 | 自写 `RecipeRegistry` + JSON 扫盘 | `Recipe<?>` / `RecipeType<?>` / `RecipeManager` + `data/<ns>/recipe/*.json` |
| 注册 | `GameRegistry.register` + `IForgeRegistryEntry` | `DeferredRegister<T>` + `IEventBus.register()` |
| Tag | `OreDictionary` | `TagKey<T>` / `HolderSet<T>` / `Holder<T>` |
| Capability | `CapabilityManager.INSTANCE.register` + `CapabilityToken` | `RegisterCapabilitiesEvent` + `BlockCapability` + `Capabilities.X.BLOCK` |
| 能量 | `IEnergyStorage`（Forge Energy / FE） | `IEnergyStorage`（**API 与 1.12.2 完全一致**） |
| 流体 | `Fluid`（密度 / 粘度 / 亮度全在 Fluid 子类） | `Fluid` + `FluidType`（密度 / 粘度 / 亮度迁移到 FluidType） |
| 物品 | `ItemStack` + `NBTTagCompound` | `ItemStack` + `DataComponentMap`（NBT 大部分场景被替换） |
| 命名 | `World` / `Entity` / `IBlockState` | `Level` / `Entity` / `BlockState`（API 形态高度相似但命名不同） |
| `BlockPos` | `BlockPos` | `BlockPos`（**record**，API 兼容） |

## 12.2 关键 API 速查表（首期必背）

| API | 用途 | 出处 |
|---|---|---|
| `DeferredRegister.createBlocks(ns)` | Block 注册 | KubeJS `KubeJSMenus` |
| `DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, ns)` | RecipeType 注册 | KubeJS `KubeJSRecipeSerializers` |
| `RegisterCapabilitiesEvent.registerBlockEntity(cap, beType, fn)` | 给 BlockEntity 挂能力 | KubeJS `KubeJSModEventHandler:129` |
| `Capabilities.ItemHandler.BLOCK` / `FluidHandler.BLOCK` / `EnergyStorage.BLOCK` | 能力 token | NeoForge |
| `ItemStackHandler` | 物品容器实现 | NeoForge（同 1.12.2） |
| `EnergyStorage(capacity, maxReceive, maxExtract)` | 能量容器实现 | NeoForge（同 1.12.2） |
| `FluidStack(Fluid, int)` / `FluidStack(Fluid, int, DataComponentPatch)` | 流体实例 | NeoForge |
| `FluidType.Properties.create()...` | 流体属性构建 | NeoForge |
| `Ingredient.CODEC` / `FluidIngredient.CODEC` | 物品 / 流体 ingredient 序列化 | NeoForge |
| `Recipe<T>` / `RecipeSerializer<T>` | 配方接口 | NeoForge |
| `MapCodec<T>` / `Codec<T>` / `RecordCodecBuilder` | 数据序列化 | Mojang + NeoForge |
| `DataComponentType<T>` | 物品数据组件 | NeoForge |

## 12.3 受影响最大的子系统

### 12.3.1 注册（§4.1 / §10.1）

MMCE 的 `GameRegistry.register` + `IForgeRegistryEntry` 整套不能直搬。MMCR 用 `DeferredRegister`，所有方块、物品、BE、配方类型、菜单、DataComponent、Creative Tab 通过 `modBus` 注册。

MMCE 的 `InternalRegistryPrimer`（机器 / 配方 / 变量 / 修饰符的内部注册缓冲）**整包删**——这些对象在 MMCR 由 `MachineRegistry` / `RecipeRegistry` / `ModifierRegistry` 直接管理，不需要额外中间层。

### 12.3.2 配方（§6）

MMCE 的 `MachineRecipe` 是 22.2K 巨型类 + 自写 GSON 解析 + 自维护 `RecipeRegistry`。MMCR 的 `MachineRecipe` 改为 `record implements Recipe<RecipeInput>`，序列化用 `MapCodec` / `StreamCodec`，注册走 `DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, ns)`。

MMCE 17 个 `RecipeAdapter`（ic2 / nco / tc6 / tconstruct / te5）**整体 OUT**——首期零第三方 mod 深度依赖，配方完全由 datapack JSON / KubeJS / Java API 三入口注册。

### 12.3.3 流体（§7.1）

**最大认知差**。MMCE 自写 `BlockFluidBase` 写 `setDensity(...)` 的写法整个迁移。MMCR 不实现流体方块——只消费别人注册的流体（如 `minecraft:water`），流出 `FluidStack`。

### 12.3.4 物品数据（§7.0）

**第二大认知差**。MMCE 大量 `NBTTagCompound` 自定义 key 写法需要逐个映射。MMCR 策略：先用 `DataComponentMap` 当纯 NBT 用（`CompoundTag` 数据组件），后续再分拆。

### 12.3.5 网络（§10.4）

15 个 `PktXxx`（基于 `SimpleNetworkWrapper` / `IMessage`）**逐个 OUT**。MMCR 用 NeoForge `CustomPacketPayload`，首期只 2 个包：
- `PktMachineStatePayload`：机器状态同步。
- `PktMultiblockDetectorPickPayload`：multiblock detector 工具的客户端 → 服务端。

### 12.3.6 渲染 / 预览（§10.6 / §11.4.10）

MMCE 的 GeckoLib 控制器模型、Lumenized Bloom、`WorldSceneRenderer`、cleanroommc 移植预览代码**整包 OUT**。MMCR 用 vanilla 模型 JSON + NeoForge 原生渲染。

### 12.3.7 事件（§9 / §11.4.2）

MMCE 的私有 `EventBus` + 12 个 `RecipeEvent`（含 factory / chance）需要重映射到 NeoForge `IEventBus`。MMCR 首期只暴露 Recipe / Machine lifecycle event 的最小集，详细设计见 [`architecture.md` §6](./architecture.md#6-模块边界-package-layout) 和 §11.4.2。

## 12.4 不变的部分（好消息）

- **能量（FE）API 完全兼容**：`receiveEnergy` / `extractEnergy` / `getEnergyStored` / `getMaxEnergyStored` / `canReceive` / `canExtract` 在两个版本中签名一致。
- **`BlockPos` 基本兼容**：26.1.2 改为 record，但 `getX/getY/getZ` / `offset` / `immutable` 行为不变。
- **`ItemStackHandler` 同款**：`setSize` / `setStackInSlot` / `getSlots` / `getStackInSlot` API 一字未改。
- **`IItemHandler` / `IItemHandlerModifiable` / `IFluidHandler`**：能力接口形态一致，只是走 `BlockCapability` token。

## 12.5 永久删除（参考 §10 / §11.6）

| 内容 | 原因 |
|---|---|
| `CommonProxy` / `ClientProxy` | 1.12.2 Forge lifecycle 概念，NeoForge mod bus / client event 替代 |
| `GameRegistry` / `InternalRegistryPrimer` | 用 `DeferredRegister` |
| 双阶段 GSON loader / `MachineLoader.discoverDirectory` / 变量 JSON | 用 Codec / datapack / KubeJS / Java API |
| CraftTweaker / ZenScript 集成 | KubeJS 替代 |
| 旧 `SimpleNetworkWrapper` 15 个 packet | 按当前功能用 NeoForge `CustomPacketPayload` 重映射 |
| GeckoLib / Lumenized / Bloom controller renderer | 不引入第三方渲染库 |
| MMCE 针对 AE2 / JEI / GeckoLib 的旧 mixin | 不移植；遇到 NeoForge/API 限制时重新写最小 mixin |
| Recipe Adapter 旧外部机器桥接（IC2 / NCO / TC6 / TConstruct / TE5） | 不引入第三方 mod 深度依赖 |
| 蓝图 / 投影器 / 自动组装 | 首期不实现；蓝图用 `/mmcr reload` + 重进世界代替 |

---

> 本文档与 [`api-mapping.md`](./api-mapping.md)（逐项 NeoForge API 对照）、[`architecture.md`](./architecture.md)（MMCR 包结构与翻译策略）、[`kubejs-integration.md`](./kubejs-integration.md)（KubeJS 桥接层设计）共同组成项目文档体系。规划与进度见 [`MAIN.md`](./MAIN.md)。
