# ModularMachinery: Community Edition (MMCE) — 功能全景拆解

> 本文档对 `reference/mmce` 下的 **ModularMachinery: Community Edition (Version 2.3.2, MC 1.12.2, Forge 14.21+)** 进行系统性拆解，包括其每一项功能、模块、类、对外接口、扩展点与配方/机器/组件/事件机制。
>
> 编写人：KasumiNova、各类社区贡献者与原作者 HellFirePvP / wiiv / youyihj / ikexing。
>
> 维护状态：原项目已停更，但内容代表 MMCE 的最终版本。

---

## 0. 索引

| 章节 | 内容 |
|------|------|
| 1.  项目基本盘 | 版本、依赖、构建、模块坐标 |
| 2.  包结构总览 | 顶级包与各包职责 |
| 3.  Mod 入口与生命周期 | `ModularMachinery` / `CommonProxy` / `ClientProxy` |
| 4.  核心多方块机器系统 | `DynamicMachine` / `AbstractMachine` / `MachineRegistry` / `MachineLoader` / `MachineComponent` |
| 5.  结构匹配 | `BlockArray` / `TaggedPositionBlockArray` / `BlockArrayCache` / `DynamicPattern` |
| 6.  配方系统 | `MachineRecipe` / `RecipeRegistry` / `RecipeLoader` / `RecipeAdapter` / `RecipeCraftingContext` |
| 7.  内置 ResourceType（需求类型） | 10 种 requirement 类型 + ModularMagic 10 种魔法类型 |
| 8.  内置 ComponentType（组件类型） | 8 种 component 类型 + ModularMagic 10 种魔法组件 |
| 9.  修饰符 / Modifier | `RecipeModifier` / `SingleBlockModifierReplacement` / `MultiBlockModifierReplacement` / `ModifierRegistry` |
| 10. 方块 (Block) 矩阵 | 控制器 / 外壳 / 总线 / 仓 / 升级 / 智能接口 / 并行 / 工厂 / ME 系列 |
| 11. TileEntity 矩阵 | 控制器 / 总线 / 仓 / 接口 / 升级 / 智能接口 / 并行 / 工厂 / ME 系列 |
| 12. 智能接口（SmartInterface） | `SmartInterfaceType` / `SmartInterfaceData` / `TileSmartInterface` |
| 13. 并行 / 工厂控制器 | `TileParallelController` / `TileFactoryController` / `FactoryRecipeThread` |
| 14. 升级系统（Upgrade） | `MachineUpgrade` / `DynamicMachineUpgrade` / `UpgradeType` / `RegistryUpgrade` |
| 15. 并发与执行 | `TaskExecutor` / `RecipeSearchTask` / `FactoryRecipeSearchTask` / `RecipeCraftingContextPool` / `Sync` / `TimeRecorder` |
| 16. 事件系统 | `MachineEvent` / `RecipeEvent` / `FactoryRecipeEvent` / `Phase` / 客户端事件 |
| 17. 网络数据包 | 15 个 `PktXxx` |
| 18. 命令 | 5 个命令 |
| 19. GUI 与客户端 | 容器 / 屏幕 / 自定义 Widget / 滚动条 / 多线标签 / 按钮 / 纹理叠层 |
| 20. 结构预览渲染 | `WorldSceneRenderer` / `FBOWorldSceneRenderer` / `ImmediateWorldSceneRenderer` / `ShaderManager` |
| 21. 蓝图 / 工具 / 投影 | `ItemBlueprint` / `ItemConstructTool` / `MachineProjector`（youyihj） |
| 22. 自动组装（ikx） | `MachineAssembly` / `AssemblyEventHandler` / `AssemblyConfig` |
| 23. CraftTweaker 集成 | `MachineBuilder` / `MachineModifier` / `RecipePrimer` / `RecipeAdapterBuilder` / `RecipeBuilder` / `BlockArrayBuilder` / `MachineUpgradeBuilder` / `DynamicMachineUpgradeBuilder` / `MMEvents` / `MultiBlockModifierBuilder` |
| 24. JEI 集成 | `CategoryDynamicRecipe` / `DynamicRecipeWrapper` / `RecipeLayoutPart` / `RecipeLayoutHelper` / `CategoryStructurePreview` / `StructurePreviewWrapper` |
| 25. TheOneProbe 集成 | `MMInfoProvider` |
| 26. AE2 / ME 集成 | `MEItemInputBus` / `MEItemOutputBus` / `MEFluidInputBus` / `MEFluidOutputBus` / `MEGasInputBus` / `MEGasOutputBus` / `MEPatternProvider` / `MEPatternMirrorImage` |
| 27. ModularMagic（kport） | 魔法需求 / 提供器 / JEI 渲染 |
| 28. Flux Networks 集成 | `MMEnergyHandler` |
| 29. GT CEu 集成 | `ModIntegrationGTCEU` / `MachineComponentProxy` 机制 |
| 30. GeckoLib 模型 | `MachineControllerModel` / `BloomGeoModelRenderer` / `MachineControllerRenderer` |
| 31. Mixin 补丁 | 4 个 mixin 包 |
| 32. 配置（Config） | 所有配置项 |
| 33. 资源 / 默认机器 | `default_machinery` / `default_recipes` / `default_variables` / `lang` |
| 34. 公共工具类 | `ItemUtils` / `BlockArray` / `HybridFluidUtils` / `OredictCache` / `ResultChance` |
| 35. 其它易忽略点 | 性能报告 / 安全系统 / 自定义数据 / 翻译 |

---

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

github.kasuminova.mmce                      ── KasumiNova 全部新增
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

### `ModularMachinery`（根 Mod）

`reference/mmce/src/main/java/hellfirepvp/modularmachinery/ModularMachinery.java`

- `@Mod(modid = "modularmachinery", name = "Modular Machinery: Community Edition", version = Tags.VERSION, dependencies = "...", acceptedMinecraftVersions = "[1.12, 1.13)", acceptableRemoteVersions = "[2.1.0, 2.4.0)")`
- 公共常量：
  - `MODID = "modularmachinery"`
  - `NET_CHANNEL = SimpleNetworkWrapper`，**注册 15 个网络包**（详见 §17）。
  - `EXECUTE_MANAGER = new TaskExecutor()`（详见 §15）。
  - `EVENT_BUS = new EventBus()` 私有总线，机器事件系统使用。
  - `CLIENT_PROXY / COMMON_PROXY` 字符串。
- `static { FluidRegistry.enableUniversalBucket(); }`
- 注册构造器 `MinecraftForge.EVENT_BUS.register(RegistrationEvent.class)`（kport）。
- 生命周期：
  - `preInit`：注册网络包、加载 ModData、调用 `proxy.preInit()`。
  - `init`：调用 `proxy.init()`。
  - `postInit`：调用 `proxy.postInit()`（注册机器、RecipeAdapter、RecipeEvent）。
  - `loadComplete`：调用 `proxy.loadComplete()`（异步构建 `BlockArrayCache`）。
  - `onServerStart`：注册 5 个命令（`/mm syntax`、`/mm hand`、`/mm blueprint`、`/mm performance`，若安装 ZenUtils 还注册 `/mm reload`）。
- `isRunningInDevEnvironment()`：通过 `Launch.blackboard.get("fml.deobfuscatedEnvironment")` 判定。

### `CommonProxy`（服务端 + 客户端）

- `static ModDataHolder dataHolder`：机器 / 配方 / 变量目录管理。
- `static CreativeTabs creativeTabModularMachinery`：图标为 `BlocksMM.blockController`。
- `static InternalRegistryPrimer registryPrimer`：内部注册器缓冲。
- `preInit`：
  - 注册 GUI Handler（自身）。
  - `MachineRegistry.preloadMachines()`（JSON 预解析）。
  - 注册 `EXECUTE_MANAGER` / `AssemblyEventHandler` / `EventHandler` / `UpgradeEventHandler` / `MMWorldEventListener`。
  - 调用 `ModularMagicItems.initItems()` / `ModularMagicComponents.initComponents()` / `ModularMagicRequirements.initRequirements()`。
  - 若 Astral Sorcery 在，注册 `StarlightEventHandler`。
  - `TaskExecutor.init()`：开启并行线程池。
- `init`：
  - `FuelItemHelper.initialize()`：扫描所有燃料物品。
  - `IntegrationTypeHelper.filterModIdComponents()` / `filterModIdRequirementTypes()`：按 mod 依赖过滤。
  - 若有 TOP，注册 `ModIntegrationTOP.registerProviders()`。
  - 若有 GTCEu，初始化 `ModIntegrationGTCEU`。
- `postInit`：
  - AE2 在 → `ModIntegrationAE2.registerUpgrade()`。
  - `MachineRegistry.registerMachines(loadMachines(null))` + 注册 CraftTweaker 等待的机器。
  - `MachineModifier.loadAll()`、`MMEvents.registryAll()`。
  - `RecipeAdapterRegistry.registerDynamicMachineAdapters()`。
  - `RecipeRegistry.getRegistry().loadRecipeRegistry(null, true)`。
  - 处理 `FactoryRecipeThread.WAIT_FOR_ADD` 队列。
- `loadComplete`：`CompletableFuture.runAsync(() -> BlockArrayCache.buildCache(MachineRegistry.getLoadedMachines()))`，准备结构预编译缓存。
- 实现 `IGuiHandler.getServerGuiElement(...)` / `getClientGuiElement(...)`：通过 `GuiType` 枚举派发至容器 / GUI。

### `ClientProxy`

`reference/mmce/src/main/java/hellfirepvp/modularmachinery/client/ClientProxy.java`

- 额外注册：
  - `ModIntegrationJEI` 注册（同时是 Plugin）。
  - `ModIntegrationCrafttweaker` 的客户端钩子（`CommandCTReloadClient`）。
  - TOP 客户端支持。
  - ModularMagic 客户端：`kport.modularmagic.client.gui` GUI 屏幕注册。
  - `kport.modularmagic.client.renderer` 渲染器注册（虹 / 灵气 / 魔力 / 星座等）。
  - `youyihj.mmce.common.item.MachineProjector` 物品方块颜色。
  - `BLOCK_MODEL_HIDER`（隐藏结构匹配时的冗余方块）。
  - `BloomGeoModelRenderer`、`ControllerModelRenderManager`（GeckoLib 模型）。
  - `ClientScheduler`：自定义客户端调度（定时任务）。
- 注册 `RenderGlobal` 钩子，让 `MachineControllerRenderer` 渲染 GeckoLib 模型。
- 客户端 `ModularMachinery.log` 在 `preInit` 设置。

---

## 4. 核心多方块机器系统

### 4.1 `AbstractMachine`

`hellfirepvp.modularmachinery.common.machine.AbstractMachine`

- 字段：registryName、包内 local 化名 / prefix、definedColor、`maxParallelism` / `internalParallelism`、`maxThreads`、`requiresBlueprint`、`parallelizable`、`hasFactory`、`factoryOnly`、`failureAction`（`RecipeFailureActions`）。
- 字段值：默认 `maxParallelism = Config.maxMachineParallelism`、`definedColor = Config.machineColor`、`parallelizable = Config.machineParallelizeEnabledByDefault`、`hasFactory = Config.enableFactoryControllerByDefault`。
- 复制 / 合并由 `DynamicMachine#mergeFrom(another)` 实现。

### 4.2 `DynamicMachine`

`hellfirepvp.modularmachinery.common.machine.DynamicMachine`

- 内部字段：
  - `Map<BlockPos, List<SingleBlockModifierReplacement>> modifiers`（机器结构替换规则）。
  - `List<MultiBlockModifierReplacement> multiBlockModifiers`（多方块替换规则）。
  - `Map<String, DynamicPattern> dynamicPatterns`（动态可变结构，例如 mega 工厂）。
  - `Map<String, FactoryRecipeThread> coreThreadPreset`（核心线程预设）。
  - `Map<String, SmartInterfaceType> smartInterfaces`（智能接口类型）。
  - `Map<Class<?>, List<IEventHandler<MachineEvent>>> machineEventHandlers`（机器事件）。
  - `TaggedPositionBlockArray pattern`（结构数组）。
  - `boolean hideComponentsWhenFormed`、`AxisAlignedBB controllerBoundingBox`。
- `DynamicMachine.MachineDeserializer`：自定义 GSON 反序列化器，配合 `MachineLoader` 两阶段加载。
- `ModifierReplacementMap`（内部静态）：支持 `rotateYCCW()`。
- 工厂兜底：`createContext(ActiveMachineRecipe, TileMultiblockMachineController)`：从 `RecipeCraftingContextPool` 借出上下文。

### 4.3 `MachineRegistry`

`hellfirepvp.modularmachinery.common.machine.MachineRegistry`

- 单例 Singleton，`mods.modularmachinery.MachineRegistry`（ZenScript 中可访问）。
- 内部两张表：
  - `WAIT_FOR_LOAD_MACHINERY = Map<ResourceLocation, Tuple<DynamicMachine, JSONString>>`（预扫描后等待加载）。
  - `LOADED_MACHINERY = Map<ResourceLocation, DynamicMachine>`（已加载）。
- 阶段：
  - `preloadMachines()`：`MachineLoader.discoverDirectory(...)` → `MachineLoader.registerMachines(...)`（并发解析）。
  - `loadMachines(sender)`：先 `prepareContext(variables)`，再 `MachineLoader.loadMachines(...)`（并发）。
  - `registerMachines(machines)` / `reloadMachine(machines)`。
- 提供 `getAllRegisteredMachinery()`（ZenScript）。
- `getRegistry()` / `getMachine(name)` / `getLoadedMachines()`。

### 4.4 `MachineLoader`

`hellfirepvp.modularmachinery.common.machine.MachineLoader`

- 静态 GSON `GSON` + `PRELOAD_GSON`（两阶段）。
- `VARIABLE_CONTEXT`：当前变量上下文（物品 / 流体 / OEM 字典）。
- `FileType` 枚举：`VARIABLES`（`*.var.json`）/ `MACHINE`（`*.json`）。
- `discoverDirectory(File)`：递归扫描。
- `registerMachines(files)`：并行 `PRELOAD_GSON` 解析。
- `loadMachines(registeredMachineList)`：合并 `preloadMachine.mergeFrom(loadedMachine)`。
- `prepareContext(variables)`：解析 `*.var.json` 注入 `VARIABLE_CONTEXT`。

### 4.5 `AbstractMachinePreDeserializer` / `DynamicPattern` / `MachineComponent`

- `AbstractMachinePreDeserializer`（`MachineRegistry` 同包）：第一阶段反序列化，只解析最基础字段，确保第二阶段引用正确。
- `MachineComponent<T>`：
  - 三种内置子类型：`ItemBus` / `FluidHatch` / `EnergyHatch`。
  - `isAsyncSupported()`：默认 true。
  - 实现 `MachineCombinationComponent`（多种类组件）。
- `IOType { INPUT, OUTPUT }`：`getByString(String)`。

---

## 5. 结构匹配

### 5.1 `BlockArray`

`hellfirepvp.modularmachinery.common.util.BlockArray`

- 机器结构匹配的核心数据结构；`Map<BlockPos, BlockInformation>`。
- `BlockInformation`：可序列化、能被 GSON 解析、能被 NBT 解析、能被旋转 / 镜像。
- 支持 `matches(World, BlockPos, ...)`：对每个位置与 `IBlockState` 比较。
- `BlockArrayCache`：根据 `EnumFacing` 缓存预旋转版本（避免每 tick 重新旋转）。
- `BlockCompatHelper`：检查 IC2 / GregTech 等 mod 的方块兼容。
- `IBlockStateDescriptor` / `BlockInformationVariable`：变量替换。

### 5.2 `TaggedPositionBlockArray`

`hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray`

- 在 `BlockArray` 之上加上 `ComponentSelectorTag`（多方块 tag 标记），用于按标签查找组件。
- 配合 `DynamicPattern` 用于「动态结构」（如 mega 工厂可堆叠复制）。

### 5.3 `DynamicPattern`

`github.kasuminova.mmce.common.util.DynamicPattern`

- MMCE 创新点：可伸缩的结构（如巨型多方块沿某一方向堆叠 1..N 段）。
- 字段：
  - `name, minSize, maxSize, faces`（合法的堆叠方向）。
  - `pattern` + `patternEnd`（覆盖末段不同形态）。
  - `structureSizeOffsetStart`（起始偏移）、`structureSizeOffset`（每段步进）。
- `matches(TileMultiblockMachineController, oldState, ctrlFace)` → `MatchResult(size, facing)`。
- `addPatternToBlockArray(BlockArray, maxSize, ...)`：把动态结构展开注入到 `BlockArray` 中供结构检查。
- `Status`（record）：NBT 序列化（patternName、facing、size）。

### 5.4 `PlayerStructureSelectionHelper`

`hellfirepvp.modularmachinery.common.selection.PlayerStructureSelectionHelper`

- 维护玩家用 `ItemConstructTool` 选择的方块集合。
- `toggleInSelection(...)` / `purgeSelection(...)`。
- `finalizeSelection(...)`：在控制器处右键确认 → 发送 `PktSyncSelection` 同步到客户端。
- `sendSelection(...)`：通过 `PktSyncSelection` 显示边框。

### 5.5 `ModIntegrationJEI.getCategoryStringFor(machine)`：将机器映射到 JEI 类别字符串。

---

## 6. 配方系统

### 6.1 `MachineRecipe`

`hellfirepvp.modularmachinery.common.crafting.MachineRecipe`

- 字段：
  - `recipeFilePath` / `registryName` / `owningMachine` / `tickTime` / `configuredPriority` / `voidPerTickFailure` / `parallelized`。
  - `List<ComponentRequirement<?, ?>> recipeRequirements`。
  - `Map<Class<?>, List<IEventHandler<RecipeEvent>>> recipeEventHandlers`。
  - `List<String> tooltipList`。
  - `String threadName` / `int maxThreads`（绑定到 `FactoryRecipeThread`）。
  - `boolean loadJEI`（是否在 JEI 中显示）。
- 构造器三个：`基础 6 参数`、`带 eventHandlers + tooltipList`、`从 PreparedRecipe`。
- `mergeAdapter(RecipeAdapterBuilder)`：在 RecipeAdapter 注入时合并得到 `parallelized` 等。
- `copy(registryNameChange, newOwningMachineIdentifier, modifiers)`：被 `DynamicMachineRecipeAdapter` 使用。
- `addRequirement(requirement)` / `getCraftingRequirements()` / `compareTo(MachineRecipe)`。
- `MachineRecipeContainer`：可把一个机器的所有配方按多个「sub machine name」展开。

### 6.2 `PreparedRecipe`

`hellfirepvp.modularmachinery.common.crafting.PreparedRecipe`

- 接口：`getFilePath() / getRecipeRegistryName() / getAssociatedMachineName() / getParentMachineName() / getTotalProcessingTickTime() / getPriority() / voidPerTickFailure() / getComponents() / getRecipeEventHandlers() / getTooltipList() / isParallelized() / getMaxThreads() / getThreadName() / loadNeedAfterInitActions() / getLoadJEI()`。
- 由 `RecipePrimer` 实现（CraftTweaker 端）。

### 6.3 `RecipeRegistry`

`hellfirepvp.modularmachinery.common.crafting.RecipeRegistry`

- 单例：自身注册到 `InternalRegistryPrimer`。
- `registerModifiedMachineRecipe` / `registerRecipeAdapterEarly` / `registerDynamicMachineAdapter`。
- `loadRecipeRegistry(sender, ...)`：扫描磁盘 recipes 目录、加载 JSON + 调用 AdapterRegistry。
- `getRecipesFor(machine)`：返回该机器的全部 `MachineRecipe`。
- `getRegistry()`。

### 6.4 `RecipeAdapter`

`hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapter`

- 抽象类：`registryName` + `int incId`（自增 ID 用于一台机器复制多个配方）。
- `createRecipesFor(owningMachineName, modifiers, additionalRequirements, eventHandlers, recipeTooltips)`。
- `createRecipeShell(uniqueRecipeName, owningMachineName, tickTime, priority, voidPerTickFailure)`：生成 `MachineRecipe` 模板。
- `resetIncId()`。
- 实现类（详见 §6.6 与 §27）。

### 6.5 `RecipeAdapterRegistry`

`hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapterRegistry`

- 维护 `Map<ResourceLocation, RecipeAdapter>`，由 `RegistryRecipeAdapters.initialize()` 加载。
- `registerAdapter(adapter)` / `getAdapter(name)` / `getAdapterValue(name)` / `registerDynamicMachineAdapters()`。

### 6.6 内置 Adapter 列表（全部 `RegistryRecipeAdapters.initialize()` 注册）

| RegistryName | 类 | 依赖 mod |
|---|---|---|
| `minecraft:furnace` | `AdapterMinecraftFurnace` | vanilla |
| `ic2:compressor` | `AdapterIC2Compressor` | IC2 |
| `ic2:macerator` | `AdapterIC2Macerator` | IC2 |
| `nuclearcraft:alloy_furnace` | `AdapterNCOAlloyFurnace` | Nuclearcraft Overhauled |
| `nuclearcraft:infuser` | `AdapterNCOInfuser` | NCO |
| `nuclearcraft:chemical_reactor` | `AdapterNCOChemicalReactor` | NCO |
| `nuclearcraft:melter` | `AdapterNCOMelter` | NCO |
| `tconstruct:smeltery_melting` | `AdapterSmelteryMeltingRecipe` | Tinkers' Construct |
| `tconstruct:smeltery_alloy` | `AdapterSmelteryAlloyRecipe` | Tinkers' Construct |
| `thaumcraft:infusion_matrix` | `AdapterTC6InfusionMatrix` | Thaumcraft |
| `thermalexpansion:insolator` | `InsolatorRecipeAdapter(false)` | TE |
| `thermalexpansion:insolator_fluid` | `InsolatorRecipeAdapter(true)` | TE |

### 6.7 `DynamicMachineRecipeAdapter`

- 把一台已有机器 `originalMachine` 的所有配方当作模板，复制到另一台机器上，每个配方都附 `RecipeModifier`。
- 用于 `DynamicMachineRecipeAdapter` 的 `RecipeAdapterBuilder`。

### 6.8 `RecipeCraftingContext`

`hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext`

- 一次配方执行的完整状态：`Map<MachineComponentType, ProcessingComponent>`、`ModifierList`、`CraftingCheckResult`、`IOInventory` 取还记录。
- `checkStartResult(...)` / `checkPreStartResult(...)`。
- `finalizeStart()` / `finalizeTick()` / `finalizeFinish()`。
- `ComponentOutputRestrictor`：限制输出。
- `CraftCheck` / `CraftingStatus`。
- `ProcessingComponent` 包含 component + container + tag。
- `RecipeCraftingContextPool`：对象池（与 `FactoryRecipeThread` 配合）。

### 6.9 `ActiveMachineRecipe`

`hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe`

- 工厂执行中的配方上下文：`recipe`、`parallelism`、`remainingTick`、`restTime`。

---

## 7. 内置 ResourceType（需求类型）

注册位置：`hellfirepvp.modularmachinery.common.registry.RegistryRequirementTypes`

| KEY | 类 | 资源 | 备注 |
|---|---|---|---|
| `modularmachinery:item` | `RequirementTypeItem` | `ItemStack` | 支持 `item@meta`、`ore:`、特殊 `any:fuel`（按燃烧时间合计）、`chance`、`nbt`、`nbt-display` |
| `modularmachinery:item_durability` | `RequirementTypeItemDurability` | `ItemStack` | 按耐久消耗 |
| `modularmachinery:ingredient_array_input` | `RequirementTypeIngredientArray` | 多选一 | 多物品候选 |
| `modularmachinery:fluid` | `RequirementTypeFluid` | `FluidStack` | 总消耗 |
| `modularmachinery:fluid_pertick` | `RequirementTypeFluidPerTick` | `FluidStack` | 每 Tick 消耗 |
| `modularmachinery:gas` | `RequirementTypeGas` | `GasStack` | Mekanism 气体总消耗 |
| `modularmachinery:gas_pertick` | `RequirementTypeGasPerTick` | `GasStack` | Mekanism 气体每 Tick 消耗 |
| `modularmachinery:energy` | `RequirementTypeEnergy` | `long` | FE 总消耗 |
| `modularmachinery:duration` | `RequirementDuration` | n/a | 仅作为 RecipeModifier 目标 |
| `modularmachinery:interface_number_input` | `RequirementTypeInterfaceNumInput` | `float` | 智能接口数值 |

### 7.1 ModularMagic 资源类型（kport，`kport.modularmagic.common.crafting.requirement.types`）

| KEY | 对应资源 / mod | 备注 |
|---|---|---|
| `modularmagic:aspect` | Astral Sorcery Aspect | `RequirementAspect` |
| `modularmagic:constellation` | Astral Sorcery Constellation | `RequirementConstellation` |
| `modularmagic:starlight` | Astral Sorcery 星光 | `RequirementStarlight` |
| `modularmagic:aura` | Nature's Aura 灵气 | `RequirementAura` |
| `modularmagic:grid` | Botania 神秘源汇 | `RequirementGrid` |
| `modularmagic:mana` | Botania 魔力 | `RequirementMana` |
| `modularmagic:rainbow` | Botania 彩虹 | `RequirementRainbow` |
| `modularmagic:lifeessence` | Blood Magic 生命精华 | `RequirementLifeEssence` |
| `modularmagic:will` | Blood Magic 意志 | `RequirementWill` |
| `modularmagic:impetus` | Thaumcraft 阻抗 | `RequirementImpetus` |

每种魔法需求都有 `requiresModid()` 软依赖检查。

### 7.2 一些细节

- `RequirementItem` 支持 `chance`（0..1）和 `nbt` / `nbt-display`（分别匹配实际 NBT 和 JEI 显示 NBT）。
- `RequirementFluid` / `RequirementFluidPerTick` 支持 `chance`。
- `RequirementGas` / `RequirementGasPerTick` 仅在 Mekanism 加载时启用。
- `RequirementEnergy` 支持守恒型（perTotal）与消耗型（perTick）。
- `RequirementInterfaceNumInput` 允许配方通过 `internalInterfaceNumber` 匹配 `SmartInterface` 的实际数值。

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

### 8.1 ModularMagic 组件类型

`kport.modularmagic.common.crafting.component`：

`ComponentAspect`, `ComponentAura`, `ComponentConstellation`, `ComponentGrid`, `ComponentImpetus`, `ComponentLifeEssence`, `ComponentMana`, `ComponentRainbow`, `ComponentStarlight`, `ComponentWill`。

每种都对应一个 `MachineComponent<XxxProvider>`、`TileXxxProvider`、`BlockXxxProvider[Input/Output]`。

### 8.2 容器与组件的 Flex 机制

- 同一 `ComponentType` 可由多个不同方块提供：例如 `item` 可由普通 item bus、MEItemBus、SmartInterface 的某个挡板提供。
- `TileMultiblockMachineController` 内部用 `Map<Long, Map<TileEntity, ProcessingComponent<?>>>` 收纳同 `groupId` 的组件。
- `MachineGroupInput` 接口：实现该接口的 Tile，可将其内部 buffer 视作「组输入」。

---

## 9. 修饰符 / Modifier

### 9.1 `RecipeModifier`

`hellfirepvp.modularmachinery.common.modifier.RecipeModifier`

- 字段：`target`（`RequirementType<?, ?>`，可为 null）、`ioTarget` (`INPUT/OUTPUT`)、`modifier`（float）、`operation`（`OPERATION_ADD=0` / `OPERATION_MULTIPLY=1`）、`chance`（是否影响概率）。
- `IO_INPUT = "input"` / `IO_OUTPUT = "output"`。
- `applyValueToApplier(applier, mod)` / `applyModifiers(context, in, value, isChance)`。
- `applyModifiers(modifiers, ...)` 静态多重重载：把整组 modifier 应用到 `value` 上，先 `add` 后 `mul`。
- `serialize()` / `deserialize()`：NBT 持久化。
- `multiply(value)` / `add(value)`：链式组合。
- `ModifierApplier` 内部结构：分别记录 `inputAdd/outputAdd/inputMul/outputMul`。
- `Deserializer`（Gson）：JSON 形态 `{io, target, multiplier, operation, affectChance}`。

### 9.2 `SingleBlockModifierReplacement`

`hellfirepvp.modularmachinery.common.modifier.SingleBlockModifierReplacement`

- 替换单个方块（位置 ↔ `BlockInformation` 列表）。
- 用于机器结构中「可替换此位置的方块」。

### 9.3 `MultiBlockModifierReplacement`

`hellfirepvp.modularmachinery.common.modifier.MultiBlockModifierReplacement`

- 替换整个多方块结构（按主 anchor）。
- `MultiBlockModifierBuilder`（CraftTweaker 端）：`addModifier(blockInfo)`。
- `ModifierRegistry`：`AbstractModifierReplacement` 注册器。

### 9.4 `DynamicModifierReplacement`

- 机器层 `dynamicPattern` / `coreThread` 也能被 modifier 替换。

---

## 10. 方块 (Block) 矩阵

| 方块 | 类 | 别名 / 备注 |
|---|---|---|
| 控制器 | `BlockController` | 状态有 `FACING` / `ACTIVE` / `FORMED` |
| 工厂控制器 | `BlockFactoryController` | 继承 `BlockController`；天然 Geckolib 模型 |
| 外壳 | `BlockCasing` | 6 种 `CasingType`：PLAIN, VENT, FIREBOX, GEARBOX, REINFORCED, CIRCUITRY |
| 物品输入总线 | `BlockInputBus` | 9 个分级：tiny, small, normal, reinforced, big, huge, ludicrous, insane, mega；同 `ItemBusType` 枚举 |
| 物品输出总线 | `BlockOutputBus` | 同上 |
| 流体输入仓 | `BlockFluidInputHatch` | tiny / small / normal / huge / reinforced / vacuum |
| 流体输出仓 | `BlockFluidOutputHatch` | tiny / small / normal / huge / reinforced / ludicrous |
| 能源输入仓 | `BlockEnergyInputHatch` | tiny / small / normal / huge |
| 能源输出仓 | `BlockEnergyOutputHatch` | tiny / small / normal / huge |
| 升级仓 | `BlockUpgradeBus` | normal / reinforced / elite / super |
| 智能接口 | `BlockSmartInterface` | `SmartInterfaceTypeEnum.NUMBER` |
| 并行控制器 | `BlockParallelController` | 5 个等级 `ParallelControllerData`：NORMAL(4), REINFORCED(16), ELITE(64), SUPER(256), ULTIMATE(512) |
| ME 物品输入总线 | `BlockMEItemInputBus` | AE2 + AE2 Extended Life + AE2 Fluid Crafting Rework 兼容 |
| ME 物品输出总线 | `BlockMEItemOutputBus` | 同上 |
| ME 流体输入仓 | `BlockMEFluidInputBus` | AE2 Fluids |
| ME 流体输出仓 | `BlockMEFluidOutputBus` | AE2 Fluids |
| ME 气体输入仓 | `BlockMEGasInputBus` | Mekanism Energistics |
| ME 气体输出仓 | `BlockMEGasOutputBus` | 同上 |
| ME Pattern Provider | `BlockMEPatternProvider` | 自定义 ME 模式提供器（极强） |
| ME Pattern Mirror Image | `BlockMEPatternMirrorImage` | 镜像模式提供器（用于跨网络合成） |

### 10.1 ModularMagic 方块

`kport.modularmagic.common.block`：

- `BlockAspectProvider`（星辉 aspect）：统一方块 + variants `INPUT`/`OUTPUT`。
- `BlockAuraProviderInput/Output`（灵气）。
- `BlockConstellationProvider`（星座）。
- `BlockGridProviderInput/Output`（神秘源汇）。
- `BlockImpetusProvider`（阻抗）。
- `BlockLifeEssenceProviderInput/Output`（生命精华）。
- `BlockManaProviderInput/Output`（魔力）。
- `BlockRainbowProvider`（彩虹）。
- `BlockStarlightProviderInput/Output`（星光）。
- `BlockWillProviderInput/Output`（意志）。

### 10.2 工具类

- `BlockMachineComponent` / `BlockStatedMachineComponent` / `BlockMEMachineComponent` / `BlockCustomName` / `BlockDynamicColor` / `BlockVariants` / `BlockBus` / `BlockEnergyHatch` / `BlockFluidHatch`。
- `BlockParallelController` 4 等级；`ItemBlockCustomName`、`ItemBlockMachineComponent`、`ItemBlockMachineComponentCustomName`、`ItemBlockMEMachineComponent`、`ItemBlockController`。

---

## 11. TileEntity 矩阵

| TileEntity | 父类 | 简介 |
|---|---|---|
| `TileMachineController` | `TileMultiblockMachineController` | 单线程机器控制器 |
| `TileFactoryController` | `TileMultiblockMachineController` | 多线程（FactoryRecipeThread） |
| `TileItemInputBus` / `TileItemOutputBus` | `TileItemBus` | 物品总线 |
| `TileFluidInputHatch` / `TileFluidOutputHatch` | `TileFluidTank` | 流体仓 |
| `TileEnergyInputHatch` / `TileEnergyOutputHatch` | `TileEnergyHatch` | FE 仓 |
| `TileUpgradeBus` | `TileColorableMachineComponent` | 升级仓 |
| `TileSmartInterface` | `TileMultiblockMachineController` 的辅助 Tile | 数值接口 |
| `TileParallelController` | `TileColorableMachineComponent` | 并行 |
| `MEItemInputBus` / `MEItemOutputBus` | `MEItemBus` → `MEMachineComponent` | ME 物品 |
| `MEFluidInputBus` / `MEFluidOutputBus` | `MEFluidBus` → `MEMachineComponent` | ME 流体 |
| `MEGasInputBus` / `MEGasOutputBus` | `MEGasBus` → `MEMachineComponent` | ME 气体 |
| `MEPatternProvider` | `MEMachineComponent` | 自研 ME 模式提供器 |
| `MEPatternMirrorImage` | `MEMachineComponent` | 镜像模式 |
| `TileEntitySynchronized` | base | 同步 NBT + 客户端 RPC |
| `TileEntityRestrictedTick` | base | 受控 tick 防卡顿 |
| `TileColorableMachineComponent` | inherits sync + 可染色 | 公共基础设施 |
| `MachineComponentTile` / `MachineComponentTileNotifiable` | base | 组件 Tile 抽象 |
| `TileFluidTank` / `TileInventory` / `TileItemBus` / `TileEnergyHatch` | sync base | 各自专用 |

### 11.1 ModularMagic TileEntity

| Tile | mod 资源 |
|---|---|
| `TileAspectProvider` | Astral Sorcery |
| `TileConstellationProvider` | Astral Sorcery |
| `TileStarlightInput` / `TileStarlightOutput` | Astral Sorcery |
| `TileAuraProvider` | Nature's Aura |
| `TileGridProvider` | Botania |
| `TileManaProvider` | Botania |
| `TileRainbowProvider` | Botania |
| `TileLifeEssenceProvider` | Blood Magic |
| `TileWillProvider` | Blood Magic |
| `TileImpetusComponent` | Thaumcraft |

每个 Tile 都有对应的 `MachineComponentXxxProvider`（提供 `MachineComponent` 包装）。

### 11.2 关键基础设施

- `ComponentRestriction`（`machine`）：约束按位置 / 朝向选择组件。
- `ComponentSelectorTag`：被 `TaggedPositionBlockArray` 使用。
- `SelectiveUpdateTileEntity`：只同步少量字段。
- `ColorableMachineTile`：可染色（机器定义颜色）。
- `MachineGroupInput`：多物品仓共享同一组 input。
- `GTEnergyContainer`：GTCeu 兼容的能量能力。

---

## 12. 智能接口（SmartInterface）

### 12.1 `SmartInterfaceType`

`hellfirepvp.modularmachinery.common.util.SmartInterfaceType`

- 字段：`type`, `defaultValue`, `headerInfo`, `valueInfo`, `footerInfo`, `notEqualMessage`, `jeiTooltip`, `jeiTooltipArgsCount`, `priority`。
- 创建：`SmartInterfaceType.create(type, defaultValue)`（ZenScript）。
- `setHeaderInfo(...)` / `setValueInfo(...)` / `setFooterInfo(...)` / `setNotEqualMessage(...)` / `setPriority(...)` / `setJeiTooltip(tooltip, argsCount)`。
- `compareTo`：按 `priority` 倒序。

### 12.2 `SmartInterfaceData`

`hellfirepvp.modularmachinery.common.util.SmartInterfaceData`

- 字段：`pos, parent, type, value`。
- NBT 序列化（`serialize()` / `deserialize()`）。
- 通过 `PktSmartInterfaceUpdate` 与服务器同步玩家修改。

### 12.3 `TileSmartInterface`

- 主机 `pos` / `parent` / `type` / `value`。
- `readCustomNBT` / `writeCustomNBT`。
- 接收 `PktSmartInterfaceUpdate` 更新值。
- 触发 `SmartInterfaceUpdateEvent`。
- 默认情况下承担 `interface_number` 组件。

### 12.4 工作流

1. 玩家在 GUI 里调整 `SmartInterface` 数值。
2. 客户端发送 `PktSmartInterfaceUpdate`。
3. 服务器更新 `SmartInterfaceData.value`。
4. 触发 `SmartInterfaceUpdateEvent`（可被 CraftTweaker 拦截）。
5. 配方检查时 `RequirementInterfaceNumInput` 比对 `SmartInterfaceData.value`。

---

## 13. 并行 / 工厂控制器

### 13.1 `TileParallelController`

- 字段：`maxParallelism` / `parallelism`（受 NBT 控制）。
- `ParallelControllerProvider`（内部 `MachineComponent`）将并行数提供给结构。
- `ParallelControllerData` 枚举：NORMAL(4) / REINFORCED(16) / ELITE(64) / SUPER(256) / ULTIMATE(512)。**可被配置文件覆盖**。
- GUI：`GuiContainerParallelController`。
- 网络：`PktParallelControllerUpdate`（设置并行数）。

### 13.2 `TileFactoryController`

- 继承 `TileMultiblockMachineController`。
- 通过 `FactoryRecipeThread` 提供多线程执行。
- `FactoryRecipeThread` 字段：`threadName`, `maxThreads`, `parallelism`, `recipeList` 等。
- 线程运行 `RecipeSearchTask` / `FactoryRecipeSearchTask`：
  - `RecipeSearchTask.computeTask()`：从 `recipeList` 找到第一条成功 check 的配方。
  - `FactoryRecipeSearchTask`：工厂版本，能产出多个 `ActiveMachineRecipe`。
- 一台机器可包含多个 `FactoryRecipeThread`，`DynamicMachine.addCoreThread(...)` 预配置。

### 13.3 `MachineRecipeThread`

`hellfirepvp.modularmachinery.common.machine.MachineRecipeThread`

- 普通机器（非工厂）单线程引擎。
- `ComputedRecipeThread` 也使用 `RecipeSearchTask`。

---

## 14. 升级系统（Upgrade）

### 14.1 类

- `MachineUpgrade`（抽象）：含 `UpgradeType`，提供 `copy(ItemStack)`、`getDescriptions()`、`getBusGUIDescriptions()`、`addEventHandler(Class, UpgradeEventHandlerCT)`、`getEventHandlers(Class)`、`setParentBus(...)`、`increment/decrementStackSize`。
- `DynamicMachineUpgrade`：可应用动态机器 modifier。
- `SimpleMachineUpgrade` / `SimpleDynamicMachineUpgrade`：CraftTweaker 端默认实现。
- `UpgradeType`：
  - `name`, `localizedName`, `level`, `maxStackSize`。
  - `compatibleMachines` / `incompatibleMachines`（互斥集合）。
  - `isCompatible(machine)`。

### 14.2 注册

- `RegistryUpgrade`（`github.kasuminova.mmce.common.upgrade.registry`）：注册用户的 `UpgradeType`。
- `UpgradeInfo`：升级卡描述 + 物品栈。

### 14.3 流程

1. 在 `TileUpgradeBus` 中放入物品，机器结构判定后绑定每个 `MachineUpgrade`。
2. 控制器 Tick 时调用各 `MachineUpgrade` 关联的 `UpgradeEventHandler` / `UpgradeMachineEventHandler`。
3. `MachineUpgrade` 可访问 `TileUpgradeBus` 自身 (`getParentBus()`)，可监听 `MachineEvent` / `RecipeEvent`。

---

## 15. 并发与执行

### 15.1 `TaskExecutor`

`github.kasuminova.mmce.common.concurrent.TaskExecutor`

- MMCE 创新点：fork/join 风格执行器。
- `ThreadFactory`：`CustomForkJoinWorkerThreadFactory` / `CustomThreadFactory`。
- `THREAD_COUNT`：根据 `Runtime.availableProcessors()` 计算。
- 通过 `ModularMachinery.NET_CHANNEL` 触发：`MinecraftForge.EVENT_BUS.register(ModularMachinery.EXECUTE_MANAGER)`。
- `ActionExecutor` / `ExecuteGroup` / `TimeRecordingAction` / `TimeRecordingTask` / `SequentialTaskExecutor` / `Queues` / `ReadWriteLockProvider` 提供基础原语。

### 15.2 `RecipeSearchTask`

`github.kasuminova.mmce.common.concurrent.RecipeSearchTask`

- `extends TimeRecordingTask<RecipeCraftingContext>`。
- `computeTask()`：遍历所有 `MachineRecipe`，对每个创建 `ActiveMachineRecipe` + `RecipeCraftingContext`，调用 `controller.onCheck(context)`。

### 15.3 `FactoryRecipeSearchTask`

- 工厂版本：搜索多个有效配方并按优先级并行执行。

### 15.4 `RecipeCraftingContextPool`

- `Sync`（`github.kasuminova.mmce.common.concurrent.Sync`）：上下文锁。
- `borrowCtx` / `returnCtx`：上下文对象池。

### 15.5 `TimeRecorder`

`github.kasuminova.mmce.common.util.TimeRecorder`

- 记录平均 Tick 时间（`usedTimeAvg`）、配方研究时间、tick 占用时间。
- 暴露给 `/mm performance` 命令。

### 15.6 `MMWorldEventListener`

`github.kasuminova.mmce.common.world.MMWorldEventListener`

- 全局世界事件：`onBlockNeighborChangeNotify` / `MachineComponentManager` 增删组件。
- `MachineComponentManager`：缓存结构中所有 `TileEntity` → `Component` 映射。

### 15.7 `InfItemFluidHandler`

- 通用的「物品 + 流体 + 气体」复合 inventory（实现 `IItemHandlerModifiable`、`IFluidHandler`、`IExtendedGasHandler`）。

### 15.8 `OredictCache` / `HashedItemStack` / `PatternItemFilter`

- 用于 Oredict 匹配加速、AE2 模式物品过滤。

---

## 16. 事件系统

### 16.1 `MachineEvent`

`github.kasuminova.mmce.common.event.machine.MachineEvent`

- 继承 `net.minecraftforge.fml.common.eventhandler.Event`。
- 字段：`controller`（`TileMultiblockMachineController`）。
- `postEvent()` 三段：
  1. `postEventToComponents()`：广播给所有 `MachineComponentTileNotifiable`。
  2. `UpgradeMachineEventHandler.onMachineEvent(this)`。
  3. 调用 `postCrTEvent()` → 触发 `DynamicMachine` 中注册的 `IEventHandler<MachineEvent>`。
- `isCancelable()` 默认 true。

#### 16.1.1 机器事件

| 事件 | 时机 |
|---|---|
| `MachineStructureFormedEvent` | 结构成形触发 |
| `MachineStructureUpdateEvent` | 结构状态更新（任何 tick） |
| `MachineTickEvent` | 控制器 Tick（带 `Phase` `START` / `END`） |
| `SmartInterfaceUpdateEvent` | 智能接口数值变更 |

### 16.2 `RecipeEvent`

`github.kasuminova.mmce.common.event.recipe.RecipeEvent`

- 继承 `MachineEvent`，字段：`activeRecipe`, `context`, `recipeThread`。
- `postCrTEvent()` 还会触发 `MachineRecipe.getRecipeEventHandlers()`。

#### 16.2.1 配方事件

| 事件 | 时机 |
|---|---|
| `RecipeCheckEvent` | `recipe` check 阶段（START / END） |
| `RecipeStartEvent` | 配方开始 |
| `RecipeFinishEvent` | 配方完成 |
| `RecipeFailureEvent` | 配方失败 |
| `RecipeTickEvent` | 配方 Tick（START / END） |
| `FactoryRecipeStartEvent` / `FactoryRecipeFinishEvent` / `FactoryRecipeFailureEvent` / `FactoryRecipeTickEvent` | 工厂版本 |
| `ResultChanceCreateEvent` | 配方输出抽样 chance（可被替换） |

### 16.3 客户端事件

`github.kasuminova.mmce.common.event.client`：

- `ControllerGUIRenderEvent`（GUI 渲染钩子）。
- `ControllerModelGetEvent`（模型钩子）。
- `ControllerModelAnimationEvent`（GeckoLib 动画钩子）。

### 16.4 Forge 事件

- Forge `WorldTickEvent` / `BlockEvent` / `PlayerInteractEvent` 等常规钩子通过 `EventHandler`、`UpgradeEventHandler`、`ClientHandler` 等处理。

### 16.5 `Phase`

- `START` / `END`，用于所有带阶段的 Event。

---

## 17. 网络数据包

总共注册 15 个 `PktXxx`（在 `ModularMachinery.preInit` 注册）。

### 17.1 客户端发布（C → S）

| ID | 类 | 用途 |
|---|---|---|
| – | `PktCopyToClipboard` | GUI 复制 |
| – | `PktSyncSelection` | 蓝图工具选择方块同步 |
| – | `PktSmartInterfaceUpdate` | 智能接口数值更新 |
| – | `PktGroupInputConfig` | 物品组输入共享配置 |
| – | `PktInteractFluidTankGui` | 流体仓 GUI 操作 |
| – | `PktParallelControllerUpdate` | 并行数更新 |
| – | `PktAutoAssemblyRequest` | 一键组装请求 |
| – | `PktMEPatternProviderAction` | Pattern Provider 操作 |
| – | `PktMEPatternProviderHandlerItems` | Pattern Provider 持有的物品推送给客户端 |
| – | `PktMEInputBusInvAction` | MEItemInputBus 容器操作 |
| – | `PktMEInputBusRecipeTransfer` | JEI → MEItemInputBus 传输 |
| – | `PktMEOutputBusStackSizeChange` | MEItemOutputBus 改变堆叠大小 |
| – | `PktSwitchGuiMEOutputBus` | 切换 MEItemOutputBus GUI 模式 |

### 17.2 服务端响应（S → C）

| ID | 类 | 用途 |
|---|---|---|
| – | `PktPerformanceReport` | `/mm performance` 报告 |
| – | `PktAssemblyReport` | 自动组装结果报告 |
| – | `StarlightMessage`（kport） | Astral Sorcery 兼容，星辉 |

---

## 18. 命令

`hellfirepvp.modularmachinery.common.command`：

| 命令 | 描述 |
|---|---|
| `/mm syntax` (`CommandSyntax`) | 输出机器 JSON 语法 |
| `/mm hand` (`CommandHand`) | 给出玩家手中物品的处理建议 |
| `/mm blueprint` (`CommandGetBluePrint`) | 输出当前控制器对应的 Blueprint 物品 |
| `/mm performance` (`CommandPerformanceReport`) | 输出控制器 Tick 性能报告 |
| `/mm reload` (`CommandCTReload`) | ZenUtils 在场时注册：重启 CraftTweaker + 机器载回 |

`kport.modularmagic` 与 `ink.ikx.mmce` 也通过 Forge 事件而非独立命令。

---

## 19. GUI 与客户端

### 19.1 `GuiContainerBase` / `GuiContainerDynamic`

- 自定义 GUI 基类。

### 19.2 客户端 GUI

| GUI | 用途 |
|---|---|
| `GuiMachineController` | 主控制器 |
| `GuiFactoryController` | 工厂控制器 |
| `GuiContainerItemBus` | 物品总线 |
| `GuiContainerFluidHatch` | 流体仓 |
| `GuiContainerEnergyHatch` | 能源仓 |
| `GuiContainerUpgradeBus` | 升级仓 |
| `GuiContainerSmartInterface` | 智能接口 |
| `GuiContainerParallelController` | 并行控制器 |
| `GuiContainerGroupInputConfig` | 物品组输入设置 |
| `GuiScreenBlueprint` | 蓝图 |
| `GuiMEItemInputBus` / `GuiMEItemOutputBus` / `GuiMEItemOutputBusStackSize` | ME 物品 |
| `GuiMEFluidInputBus` / `GuiMEFluidOutputBus` | ME 流体 |
| `GuiMEGasInputBus` / `GuiMEGasOutputBus` | ME 气体 |
| `GuiMEPatternProvider` | 自研 Pattern Provider |
| `GuiBlueprintScreenJEI` | Blueprint 在 JEI 中的嵌入页 |
| `GuiScreenDynamic` | 通用基础 |

### 19.3 自定义 Widget

`github.kasuminova.mmce.client.gui.widget`：

- `base.WidgetController` / `Widget` / `MouseEventHandler` / `RenderEventHandler`。
- `Button`、`Button4State`、`Button5State`、`ButtonElements`。
- `MultiLineLabel`、`HorizontalLine`、`Scrollbar`、`TextureOverlay`。

### 19.4 widget 实现

`...client.gui.widget.impl`：

- `preview` 包：`MachineStructurePreviewPanel`、`UpgradeIngredientList`、`StructurePreviewTitle`、`WorldSceneRendererWidget` 等。
- `patternprovider` 包：`PatternProviderIngredientList` 等。

### 19.5 Container

- `ContainerController` / `ContainerFactoryController` / `ContainerBase` / `ContainerItemBus` / `ContainerFluidHatch` / `ContainerEnergyHatch` / `ContainerUpgradeBus` / `ContainerSmartInterface` / `ContainerParallelController` / `ContainerGroupInputConfig`。
- ME 系列：`ContainerMEItemInputBus`、`ContainerMEItemOutputBus`、`ContainerMEItemOutputBusStackSize`、`ContainerMEFluidInputBus`、`ContainerMEFluidOutputBus`、`ContainerMEGasInputBus`、`ContainerMEGasOutputBus`、`ContainerMEPatternProvider`。

### 19.6 客户端工具

- `hellfirepvp.modularmachinery.client.util.DynamicMachineRenderContext`：渲染上下文。
- `hellfirepvp.modularmachinery.client.ClientScheduler`：自定义调度器。

---

## 20. 结构预览渲染

### 20.1 `WorldSceneRenderer`（com.cleanroommc）

`com.cleanroommc.client.preview.renderer.scene.WorldSceneRenderer`

- 基于 OpenGL 的假世界（DummyWorld）渲染器：把多方块结构当作一个 3D 场景绘制。
- 支持 `EntityCamera` / `Quat` / `Vector3` / `ShapeUtils` / `RenderUtils` / `RayTraceUtils` / `LRMap` / `LRVertexBuffer`。
- 调用 `TrackedDummyWorld` / `DummyWorld` / `DummyChunkProvider` / `DummySaveHandler` / `LRDummyWorld` 作为底层世界。

### 20.2 `FBOWorldSceneRenderer` / `ImmediateWorldSceneRenderer`

- `FBOWorldSceneRenderer` 渲染到 FBO（用于 GUI 嵌入）。
- `ImmediateWorldSceneRenderer` 直接渲染（用于 In-World Preview）。

### 20.3 `ShaderManager`

`com.cleanroommc.client.shader.ShaderManager`

- 加载 OptiFine 兼容的 Shader Pack（Bloom 效果）。

### 20.4 Mixin 钩子

- `MixinRenderGlobal.hookTESRComplete(...)`（`github.kasuminova.mmce.mixin.minecraft`）→ 调用 `ControllerModelRenderManager.INSTANCE.draw()` 让 GeckoLib 模型在主世界中显示。

---

## 21. 蓝图 / 工具 / 投影

### 21.1 `ItemBlueprint`

`hellfirepvp.modularmachinery.common.item.ItemBlueprint`

- 一个绑定特定机器的物品，记录机器 registryName + 几何缓存。
- `setAssociatedMachine(stack, machine)` / `getAssociatedMachine(stack)`。
- 玩家手持可右键控制器 → GUI 中显示 `MachineStructurePreviewPanel`。

### 21.2 `ItemConstructTool`

- 创造模式工具：选择多个方块 → `PlayerStructureSelectionHelper` 选中 → 点击控制器生成 Blueprint。

### 21.3 `ItemDebugStruct`

- 调试用，使用 `ItemMachineProjector` 投影当前结构。

### 21.4 `ItemModularium`

- 装饰物品，可染色（`ItemDynamicColor`）。

### 21.5 `MachineProjector`（youyihj）

`youyihj.mmce.common.item.MachineProjector`

- 在世界中按方向投出当前机器结构（用于测试）。

### 21.6 `StructurePreviewHelper`（youyihj）

- 简易的预览辅助类。

---

## 22. 自动组装（ikx）

### 22.1 `AssemblyConfig`

`ink.ikx.mmce.common.assembly.AssemblyConfig`（`ModuleDataHolder` 上一段）

- `assemblyBefore` / `assemblyCreative` / `assemblyItemBlocks` / `assemblyFluidBlocks` / `replaceCheck` / `assemblyWaitTime` 等开关。

### 22.2 `MachineAssembly`

`ink.ikx.mmce.common.assembly.MachineAssembly`

- 步骤：
  1. 玩家拿着 Blueprint + 大量材料 → 在控制器上右键 → `PktAutoAssemblyRequest` → 服务端构造 `MachineAssembly`。
  2. `buildIngredients(consumeInventory)`：从玩家背包挑选对应物品 / 流体。
  3. `assembly(consumeInventory)`：逐 tick 把方块放到对应位置；`replaceCheck` 防止破坏植物 / 流动液体。
  4. `assemblyCreative()`：创造模式直接放置。
- `checkAllItems(...)` 实时扫描玩家库存，缺失时返回 `PktAssemblyReport` 提示。

### 22.3 `AssemblyEventHandler`

`ink.ikx.mmce.core.AssemblyEventHandler`

- 监听 `PlayerInteractEvent.RightClickBlock` → 触发组装。
- `MachineAssemblyManager`：管理「同时进行的多个组装」。

### 22.4 `StructureIngredient`

`ink.ikx.mmce.common.utils.StructureIngredient`

- 拆分结构为 `ItemIngredient`（每方块 1 个选项链）和 `FluidIngredient`（流体方块）。

---

## 23. CraftTweaker 集成

### 23.1 全局 API

`@ZenClass("mods.modularmachinery.MachineRegistry")`：`MachineRegistry` 静态方法。
`@ZenClass("mods.modularmachinery.MachineController")`：`MachineController.getControllerAt(IWorld, IBlockPos)`。

### 23.2 `MachineBuilder`

`@ZenClass("mods.modularmachinery.MachineBuilder")`

- 构造：`MachineBuilder.createBuilder(registryName, localizedName)` / `createBuilder(...)` 多重载。
- `getBuilder(registryName)` 取已注册。
- 方法：
  - `addBlock(...)` / `addBlock(blockInfo)`。
  - `setLocalizedName(...)` / `setPrefix(...)`。
  - `setRequiresBlueprint(...)` / `setFailureAction(...)` / `setDefinedColor(color)`。
  - `setHasFactory(...)` / `setFactoryOnly(...)` / `setFactoryMaxThreads(...)` / `setFactoryRecipeThreadMaxParallelism(...)`。
  - `setMaxParallelism(int)` / `setInternalParallelism(int)` / `setMaxThreads(int)`。
  - `addCoreThread(FactoryRecipeThread)` / `setMachineGeoModel(...)`。
  - `setSmartInterfaceType(...)` / `addDynamicPattern(...)`。
  - `addModifier(...)` / `addMultiBlockModifier(...)`。
  - `register()` / `getPattern()` / `getMachine()`。

### 23.3 `MachineModifier`

`@ZenClass("mods.modularmachinery.MachineModifier")`

- 静态方法：`addSmartInterfaceType` / `setMaxParallelism` / `setInternalParallelism` / `setMaxThreads` / `addCoreThread` / `setMachineGeoModel` / `setMachinePrefix`。
- 累计到 `WAIT_FOR_MODIFY` 队列，待 `postInit` 由 `MachineModifier.loadAll()` 执行。

### 23.4 `RecipePrimer`

`@ZenClass("mods.modularmachinery.RecipePrimer")`

- 构造：`RecipePrimer.create(machineName, registryName, tickTime, priority)` / `createRecipe(machineName, registryName, tickTime, priority, voidPerTickFailure)`。
- 工具方法：
  - `addItemInput(IItemStack, ...)` / `addItemOutput(...)` / `addFluidInput(...)` / `addFluidOutput(...)` / `addGas(...)` / `addEnergy(...)`（含 perTick 变体）。
  - `addIngredientArray(...)`（多选一）。
  - `addAspectInput(...)` / `addStarlight(...)` / `addAura(...)` / `addMana(...)` / `addLifeEssence(...)` / `addRequirement(...)`。
  - `setParallelized(...)` / `setParallelizeUnaffected(...)` / `setChance(...)` / `addTooltip(...)`。
  - `setRecipeEventHandler(RecipeEvent, function)` / `setFactoryRecipeThread(...)`。
  - `addModifier(RecipeModifier)`。
  - `setMaxThreads(int)` / `setThreadName(String)` / `setLoadJEI(boolean)`。
  - `build()` / `register()`。

### 23.5 `RecipeBuilder`

- `RecipeBuilder.newBuilder(machineName, registryName, tickTime, priority)`。
- 立刻构造的语法糖，返回 `RecipePrimer`。

### 23.6 `RecipeAdapterBuilder`

- `RecipeAdapterBuilder.create(machineName, parentMachineName)`。
- 把 `parentMachine` 的所有配方复制到 `machineName`（带 `RecipeModifier`）。
- `addModifier(...)`。

### 23.7 `RecipeModifierBuilder`

- `RecipeModifierBuilder.newBuilder()` / `setRequirementType(...)` / `setIOType(...)` / `setOperation(...)` / `setValue(...)` / `isAffectChance(...)` / `build()`。

### 23.8 `BlockArrayBuilder`

- JSON 风格的 `BlockArray` 构造器（链式 `addBlock` / `addAir` / `addRotations` / `build`）。

### 23.9 `IngredientArrayBuilder` / `IngredientArrayPrimer`

- 包装 `RequirementIngredientArray`。

### 23.10 `StatedMachineComponentBuilder`

- 构造 `ComponentSelectorTag` + `BlockArray.BlockInformation`。

### 23.11 `MachineUpgradeBuilder` / `DynamicMachineUpgradeBuilder`

- `MachineUpgradeBuilder.create(upgradeTypeName, ...)`。
- `setMaxStackSize` / `setLevel` / `setLocalizedName` / `setCompatibleMachines(...)` / `setIncompatibleMachines(...)`。
- `onMachineEvent(eventClass, function)` / `onRecipeEvent(eventClass, function)`：注册 `UpgradeEventHandlerCT`。
- `build()` → `SimpleMachineUpgrade` 或 `SimpleDynamicMachineUpgrade`。

### 23.12 `MMEvents`

`@ZenClass("mods.modularmachinery.MMEvents")`

- `onControllerConstruct(eventClass, function)`。
- `onControllerTick(eventClass, phase, function)` / `onMachineStructureFormed(...)` / `onMachineStructureUpdate(...)`。
- `onRecipeStart(...)` / `onRecipeTick(...)` / `onRecipeFinish(...)` / `onRecipeFailure(...)` / `onRecipeCheck(...)`。
- `onSmartInterfaceUpdate(...)`。
- `registryAll()` 在 `postInit` 时统一挂载。

### 23.13 `MultiBlockModifierBuilder`

- 构建 `MultiBlockModifierReplacement`。

### 23.14 `IFunction` / `AdvancedItemCheckerCT` / `AdvancedBlockCheckerCT` / `AdvancedItemModifierCT`

- 高级匹配函数（per-item / per-block）与 modifier 包装。

### 23.15 `GeoMachineModel`

`@ZenClass("mods.modularmachinery.GeoMachineModel")`

- 客户端注册 GeckoLib 模型。

### 23.16 `UpgradeEventHandlerCT` / `UpgradeEventHandlerWrapper`

- 把 CraftTweaker function 包装为 `UpgradeEventHandlerCT`。

### 23.17 工具类

- `CommandCTReload` / `CommandCTReloadClient`：ZenUtils 协作的重载命令。
- `BlockArrayGenerator` / `MachineGenerator` / `RecipeGenerator`：JSON 生成器基类。

---

## 24. JEI 集成

### 24.1 主入口

`hellfirepvp.modularmachinery.common.integration.ModIntegrationJEI`

- 实现 `IModPlugin`（JEI 7+ 风格）。
- `registerCategories(...)`：每台机器一个 `CategoryDynamicRecipe` + 一个 `CategoryStructurePreview`。
- `registerRecipes(...)`：遍历 `MachineRegistry.getLoadedMachines()` → `getAvailableRecipes()`。
- 提供 `getCategoryStringFor(machine)`。
- 提供 `jeiHelpers` 静态引用。

### 24.2 `CategoryDynamicRecipe`

`hellfirepvp.modularmachinery.common.integration.recipe.CategoryDynamicRecipe`

- 构造时调用 `buildRecipeComponents()`：收集所有 requirement 的 `RecipeLayoutPart`。
- `setRecipe(IRecipeLayoutBuilder, MachineRecipe, IFocusGroup)`：把每个 requirement 映射到合适的 slot。
- `drawExtras(...)` / `getTooltip(...)` 渲染额外信息（如 modifier 缩放后值）。

### 24.3 `DynamicRecipeWrapper`

- 把 `MachineRecipe` 包装成 JEI 可识别的对象。

### 24.4 `RecipeLayoutPart`

- 内部类：
  - `RecipeLayoutPart.Energy(Point)`：能量槽。
  - `RecipeLayoutPart.Item(Point)`：物品槽（带 `IngredientItemStackRenderer`；自动布局）。

### 24.5 `RecipeLayoutHelper`

- 提供 `PART_INVENTORY_CELL` 等 IDrawable 静态资源。

### 24.6 `IngredientItemStack` / `IngredientItemStackRenderer`

- 自定义 JEI 物品渲染（显示 NBT / chance）。

### 24.7 `HybridFluid` / `HybridFluidRenderer` / `HybridStackHelper`

- 给 TE / botania 等 mod 提供「混合流体」JEI 显示。

### 24.8 `CategoryStructurePreview` / `StructurePreviewWrapper`

- 在 JEI 中渲染一个可点击的 3D 预览图，连接到 `GuiBlueprintScreenJEI`。
- `StructurePreviewWrapper.getWrapper(layout)` / `getRecipeLayouts(gui)`：静态辅助。

### 24.9 JEI 事件

- `RecipeTransferToGuiHandler` 钩子：将 JEI 物品拖到总线槽。
- `MixinRecipesGui` / `MixinRecipeLayout`（详见 §31）：调整 JEI GUI 的渲染上下文。

### 24.10 ModularMagic JEI

`kport.modularmagic.common.integration.jei`：

- `JeiPlugin` 注册 `LayoutXxx` 与 `JEIComponentXxx` 与 `Renderer`（Aspect / Starlight / Aura / Mana / LifeEssence / Will / Impetus / Rainbow / Grid / Constellation）。
- `LayoutStarlight` / `LayoutMana` 等定义 JEI 槽位布局。
- `AspectRenderer` / `AuraRenderer` / `ConstellationRenderer` / `DemonWillRenderer` / `GridRenderer` / `ImpetusRender` / `LifeEssenceRenderer` / `ManaRenderer` / `RainbowRenderer` / `StarlightRenderer`：自定义渲染。

---

## 25. TheOneProbe 集成

`hellfirepvp.modularmachinery.common.integration.theoneprobe.MMInfoProvider`：

- 实现 `IProbeInfoProvider`。
- 处理 `IMachineController` / `TileMultiblockMachineController`：显示配方进度、配方名、剩余时间、线程状态、所有者。
- 处理 `TileSmartInterface` / `TileParallelController` / `TileUpgradeBus` / `TileEnergyHatch` / `TileFluidTank` / `TileItemBus`。
- 处理 `MEItemInputBus` / `MEItemOutputBus` / `MEFluidInputBus` / `MEFluidOutputBus` / `MEGasInputBus` / `MEGasOutputBus` / `MEPatternProvider`：显示当前存储 / 状态。
- 处理 `TileAuraProvider` / `TileAspectProvider` 等 ModularMagic Tile。

---

## 26. AE2 / ME 集成

### 26.1 基础

- 依赖：`ae2-extended-life`、`ae2-fluid-crafting-rework`、`mekanism-energistics`、`nae2`。
- `MEMachineComponent`：
  - 实现 `IAEAppEngInventory` / `IUpgradeableHost` / `IConfigManagerHost` / `SelectiveUpdateTileEntity` / `MachineComponentTile` / `ColorableMachineTile` / `MachineGroupInput`。
  - 通过 `CreateModIntegrationAE2.securityCheck(...)` 兼容 AE2 安全系统。
- `MachineCombinationComponent`：组合多种 `MachineComponent`（如 Pattern Provider 多面）。
- `BlockMEMachineComponent`：Me 风格方块基类。

### 26.2 物品总线

- `MEItemInputBus`：
  - 在 `tickingRequest()` 中调用 `extractStackFromAE()`。
  - 通过 `MEItemInputBus.getConfigInventory()` 暴露配置 Inventory。
  - 支持 `SettingsTransfer` 接口（GUI 共享设置）。
- `MEItemOutputBus`：
  - 同样支持 `tickingRequest`。
  - 拥有「max stack size」动态设定（点击 GUI 切换 1/16/64/256/2k）。

### 26.3 流体总线

- `MEFluidInputBus` / `MEFluidOutputBus` 继承 `MEFluidBus`：
  - `TANK_SLOT_AMOUNT = 9`、`TANK_DEFAULT_CAPACITY = 8000`mB。
  - 通过 `AEFluidInventoryUpgradeable` 存储。
  - 容量卡片安装后容量按 `4^(N+1) * 8000/4` 缩放。
  - `changedSlots`：脏槽记录。
- `use` 容量升级时 `updateTankCapacity()`。

### 26.4 气体总线

- `MEGasInputBus` / `MEGasOutputBus` 继承 `MEGasBus`（`MEFluidBus` 类似），使用 `MultiGasTank` 包装 Mekanism 气体。
- 与 `mekeng` 兼容。

### 26.5 `MEPatternProvider`

`github.kasuminova.mmce.common.tile.MEPatternProvider`

- 自研 AE2 模式提供器：
  - 4 行 9 列 = 36 个样板槽。
  - 多面共享：`getCombinationComponents()` 提供 4 个 `MachineComponent<InfItemFluidHandler>`（共享同一内部 Item/Fluid inventory）。
  - 有独立的 `subItemHandler` / `subFluidHandler`（用作模式样板共享总线）。
  - 多线程安全：`handler.setOnItemChanged` / `setOnFluidChanged` 触发 `markChunkDirty()`。
  - `SecurityPermissions.BUILD` 检查（与 AE2 安全系统联动）。
- 网络：
  - `PktMEPatternProviderHandlerItems`：登录同步内容。
  - `PktMEPatternProviderAction`：跟踪样板操作。
- `MEItemInputBus.getNextStack()` 也可从中提取。
- `PatternItemFilter` 过滤同步用。

### 26.6 `MEPatternMirrorImage`

- 镜像模式：把别的 Pattern Provider 的样板反射到本机（用于跨网络）。

### 26.7 Mixin

- `ae2/MixinContainerInterfaceTerminal` / `MixinDualityInterface`：调整 AE2 接口面板的行为（看到 PM 后能处理 Pattern Provider）。
- `ae2.nae2/MixinContainerPatternMultiTool`：NAE2 调整。

---

## 27. ModularMagic（kport）

### 27.1 总览

`kport.modularmagic` 是 MMCE 内的「魔法合成支持模块」，集中提供 Botania / Blood Magic / Astral Sorcery / Nature's Aura / Thaumcraft / Botania 特殊合成的支持。

### 27.2 物品

`ModularMagicItems.initItems()`：

- 提供 `ItemAspect` / `ItemAura` / `ItemConstellation` / `ItemMana` / `ItemLifeEssence` / `ItemImpetus` / `ItemStarlight` / `ItemRainbow` / `ItemWill` / `ItemGrid` 等虚拟物品（仅在 JEI 中使用）。

### 27.3 组件

`ModularMagicComponents.initComponents()`（10 个 ComponentType 注册）。

### 27.4 需求类型

`ModularMagicRequirements.initRequirements()`（10 个 RequirementType 注册）。

### 27.5 CraftTweaker

`MagicPrimer`:

- `MagicPrimer.createStarlight(...)` / `createAura(...)` / `createMana(...)` / `createAspect(...)` / `createLifeEssence(...)` / `createImpetus(...)` / `createWill(...)` / `createRainbow(...)` / `createGrid(...)` / `createConstellation(...)`。
- 提供 `addStarlightInput(...)` 等链式 API 给 `RecipePrimer` 复用。

### 27.6 JEI 集成

- `LayoutXxx` 类 + `JEIComponentXxx` + `XxxRenderer`（详见 §24.10）。
- `Aura` / `Constellation` / `DemonWill` / `Grid` / `Impetus` / `LifeEssence` / `Mana` / `Rainbow` / `Starlight` JEI ingredient。
- `AuraHelper` / `AspectHelper` / `ConstellationHelper` / `DemonWillHelper` / `GridHelper` / `ImpetusHelper` / `LifeEssenceHelper` / `ManaHelper` / `RainbowHelper` / `StarlightHelper`：调用相应 mod API。

### 27.7 网络

- `StarlightMessage` / `StarlightMessageHandler`：当 Astral Sorcery 在场时注册。

### 27.8 事件

- `RegistrationEvent`：负责 `initItems / initComponents / initRequirements`。
- `StarlightEventHandler`：监听 Astral Sorcery 事件。

---

## 28. Flux Networks 集成

`hellfirepvp.modularmachinery.common.integration.fluxnetworks`

- `ModIntegrationFluxNetworks.preInit()` 注册事件。
- `MMEnergyHandler`（与 `MMWorldEventListener` 协同）：当 Flux Networks 在场时，让 FE 仓可被任意网络读取。
- 受 `Config.enableFluxNetworksIntegration` 控制。

---

## 29. GT CEu 集成

`github.kasuminova.mmce.common.integration.gregtech`

- `ModIntegrationGTCEU.initialize()`。
- `MachineComponentProxy` / `MachineComponentProxyRegistry` / `SpecialItemBlockProxy` / `SpecialItemBlockProxyRegistry`：
  - 允许把 GTCu 多方块元件（如某些方块 / 机器）作为机器的组件代理。
- `componentproxy` / `handlerproxy` / `patternproxy` 子包分别处理 Component / IItemHandler / Pattern 三个维度。
- `GTEnergyContainer`：EU 适配。

---

## 30. GeckoLib 模型

### 30.1 系统

- `MachineControllerModel`（`github.kasuminova.mmce.client.model`）：从 Lumenized + GeckoLib 加载。
- `DynamicMachineModelRegistry.INSTANCE`：
  - `registerMachineDefaultModel(machine, model)`。
  - `getMachineModel(name)`。
- `GeoModelRenderTask` / `BloomGeoModelRenderer`：渲染（带 Bloom）。
- `MachineControllerRenderer`：自定义 TileEntityRenderer。
- `ControllerModelRenderManager`：管理所有 `MachineControllerRenderer` 实例。
- `BloomGeoModelRenderer` 在 `MixinRenderGlobal` 的 `hookTESRComplete` 钩子中调用。

### 30.2 Lumenized 集成

- 注释 `dependencies = "after:lumenized"`。
- `ShaderManager` 通过 `isOptifineShaderPackLoaded()` 决定是否开启 Bloom。

### 30.3 Animatable

- `TileMultiblockMachineController` 实现 `software.bernie.geckolib3.core.IAnimatable`（`@Optional.Interface`）。

---

## 31. Mixin 补丁

| Mixin JSON | 包 | 目标 |
|---|---|---|
| `mixins.mmce_minecraft.json` | `github.kasuminova.mmce.mixin.minecraft` | `MixinRenderGlobal` (`RenderGlobal.renderEntities` 钩子)、`MixinTileEntityRendererDispatcher` |
| `mixins.mmce_ae2.json` | `github.kasuminova.mmce.mixin.ae2` | `MixinContainerInterfaceTerminal` / `MixinContainerInterfaceTerminal$AccessorInvTracker` / `MixinDualityInterface` |
| `mixins.mmce_jei_hacky.json` | `github.kasuminova.mmce.mixin.jei` | `MixinRecipeLayout` (`GlStateManager.translate` 钩子)、`MixinRecipesGui` / `MixinRecipesGui$AccessorRecipeLayout` / `MixinRecipesGui$AccessorStructurePreviewWrapper` |
| `mixins.mmce_nae2.json` | `github.kasuminova.mmce.mixin.ae2.nae2` | `MixinContainerPatternMultiTool` |

`EarlyMixinLoader`（manifest 指定 `FMLCorePlugin = "github.kasuminova.mmce.mixin.MMCEEarlyMixinLoader"`）负责注册以上 mixin。

---

## 32. 配置（Config）

`hellfirepvp.modularmachinery.common.data.Config`

通用：
- `enableFluxNetworksIntegration = true`
- `enableSterlingBonus`（保留字段）
- `enableFullDataSync`（控制器 NBT 全量同步）
- `enableSecuritySystem`（是否启用机器所有者校验）
- `enableBloomEffect`
- `parallelizeRecipeThreads`
- `machineParallelizeEnabledByDefault` / `recipeParallelizeEnabledByDefault`
- `maxMachineParallelism`
- `enableFactoryControllerByDefault`
- `defaultFactoryMaxThread`
- `machineColor`（默认机器颜色）
- `totalTimeMultiplier` / `itemInputTimeMultiplier` / `itemOutputTimeMultiplier` / `fluidInputTimeMultiplier` / `fluidOutputTimeMultiplier`
- `clientWorldCleanCacheIntervalSeconds`
- `meshCacheMaxSize`
- `recipeDurationMultiply` / `recipeEnergyConsumptionMultiply`
- `machineBoundingBoxDefaultEnable`

控制器（`TileMultiblockMachineController.loadFromConfig`）：
- `structure-check-delay`（默认 30 tick）
- `delayed-structure-check`（默认 true）
- `max-structure-check-delay`（默认 100 tick）
- `clean-custom-data-on-structure-check-failed`

并行（`ParallelControllerData.loadFromConfig`）：
- 每个等级最大并行数（默认 4 / 16 / 64 / 256 / 512，可由配置文件覆盖）。

`RecipeFailureActions.loadFromConfig`：`default-failure-actions`（reset / still / decrease）。

`DataLoadProfiler`：状态行 / 进度。

`ModDataHolder`：所有 JSON 机器 / 配方 / 变量路径。

---

## 33. 资源 / 默认机器

### 33.1 默认机器（`assets/modularmachinery/default_machinery/`）

- `alloy_furnace.json`（合金炉）
- `assembly_line.json`（装配线）
- `iron_centrifuge.json`（铁离心机）
- `power_transformer.json`（能量变压器）

### 33.2 默认配方（`default_recipes/`）

- `alloy_smelter/`（含 `alloy_smelter_diamond.json`、`alloy_smelter_furnaces.adapter.json`、`alloy_smelter_modularium.json`）
- `centrifuge/`（含 `centrifuge_centrifuge_blaze_powder.json`、`..._grass.json`、`..._magma_cream.json`、`..._wool.json`、`centrifuge_wash_glowstone.json`、`centrifuge_wash_redstone.json`）
- `power_transformer_energy_transform.json`

### 33.3 默认变量（`default_variables/`）

- `casings.var.json`：定义 `casing_firebox/casing_plain/casing_reinforced` 等变量。

### 33.4 资源包语言

- `en_US.lang` / `zh_CN.lang`（双重翻译）。

### 33.5 完整方块 / 物品 JSON

- `blockstates/`：控制器 / 外壳 / 各种 buses / hatches / upgrades / smart interface / parallel / factory / ME 系列 / ModularMagic 系列。
- `models/block/` / `models/item/`。
- `textures/blocks/`, `textures/items/`, `textures/gui/`。
- `recipes/`：modularium 锭、casing 系列、energy/fluid/item 输入输出仓的合成表。

### 33.6 Logo

- `textures/logo.png`（9.3 KB）。

---

## 34. 公共工具类

- `ItemUtils`（23.6K）：物品 / 伤害 / NBT 匹配、`ItemStackIterable`。
- `BlockArray`（26.9K）：JSON / NBT 序列化 + 旋转。
- `BlockArrayCache`（4.4K）：按 `EnumFacing` 缓存。
- `BlockInformationVariable`（3.2K）：变量替换。
- `BlockCompatHelper`（6.2K）：跨 mod 兼容。
- `IBlockStateDescriptor`（3.9K）：状态描述。
- `FuelItemHelper`（3.0K）：燃料扫描。
- `HybridFluidUtils` / `HybridTank` / `HybridGasTank`：跨 mod 流体 / 气体。
- `IEnergyHandler` / `IEnergyHandlerAsync` / `IEnergyHandlerImpl`：异步 FE。
- `IOInventory`（7.2K）：可限制 IO 槽。
- `IItemHandlerImpl`（12.4K）：自定义物品 inventory。
- `InventoryUpdateListener`：inventory 变更监听。
- `PriorityProvider`：优先级计算。
- `RedstoneHelper`：红石信号。
- `ResultChance`：chance 计算。
- `SmartInterfaceData` / `SmartInterfaceType`：接口数据 + 类型。
- `MultiFluidTank` / `MultiGasTank`：多流体仓。
- `Sides`：方块 6 面枚举操作。
- `TimeRecorder`（2.0K）：性能统计。
- `OredictCache` / `HashedItemStack`：加速匹配。
- `PatternItemFilter`：AE 模式匹配。
- `InfItemFluidHandler`：combo inventory。
- `AEFluidInventoryUpgradeable`：AE fluid 容器。
- `CapabilityUpgrade` / `CapabilityUpgradeProvider`：升级能力。
- `BlockPos2ValueMap`：高效 BlockPos → 列表 map。
- `BlockModelHider`（`github.kasuminova.mmce.client.world`）：隐藏结构匹配时的多余方块。

---

## 35. 其它易忽略点

### 35.1 安全系统

- `enableSecuritySystem` 配置启用后：
  - `TileMultiblockMachineController` 写入 `owner`（`GameProfile`）。
  - `onBlockActivated` 检查非所有者被拒。
  - `getOwnerName()` / `getOwnerUUIDString()` 暴露给 JEI / TOP。
- AE2 安全系统通过 `ModIntegrationAE2.securityCheck(player, proxy)` 联动。

### 35.2 自定义数据

- `customData`：`NBTTagCompound`，结构成功时不被清除，可用于 CraftTweaker / 升级。
- `cleanCustomDataOnStructureCheckFailed` 配置。

### 35.3 全数据同步

- `enableFullDataSync`：在配方启动 / 完成时把全 NBT 推送到客户端。

### 35.4 性能监控

- `TimeRecorder` 在每次 `doControllerTick` 后写入耗时。
- `MMInfoProvider` 在 TOP 中也会显示。
- `/mm performance` 命令输出汇总。

### 35.5 控制器组件管理

- `MachineComponentManager` 缓存一个 `Map<Long, Map<TileEntity, ProcessingComponent<?>>>`。
- `MMWorldEventListener` 监听 `BlockEvent.NeighborNotify` / `TickEvent`，增量更新缓存。

### 35.6 额外网络

- `StarlightMessage`（kport）：Astral Sorcery 兼容。
- `ThreadingHelper` / `CustomThreadFactory`（kasuminova）：定制的 Worker Thread。

### 35.7 自定义数据包字段

- `dataWatcher` 字段用于快速同步（颜色、激活状态、workMode 等）。
- `WorkMode` 枚举（`MACHINE` / `BUS` / `BIOPROCESS` 等）。

### 35.8 翻译

- `en_US.lang` 28K / `zh_CN.lang` 26.8K，原生中文支持。

### 35.9 Hard-coded 升级等级

- `level`：浮点数，用在 `RequirementType.register` 时筛选。

### 35.10 模块耦合图（简化）

```
                            Mod Entry
                                │
                ┌───────────────┼───────────────┐
                │               │               │
        CommonProxy       Network Channel    EventBus
                │               │               │
   ┌────────┬───┴────┬───────────┼───────────────┬──┐
   │        │        │           │               │  │
Registries  │  MachineRegistry  │            Event │
   │        │     ├→ MachineLoader  │         ↑  │
   │        │     ├→ DynamicMachine    │ Machine↑  │
   │        │     ├→ MachineComponent   │ Event ↓  │
   │        │     │        │           │ (CraftTweaker)
   │        │     │        ▼           │   ↑   │
   │        │     │  TileEntity....    │  Modifier  │
   │        │     │  (Buses/Hatches)   │   ↑   │
   │  ┌─────┴──────┴─┐                 │   │   │
   │  │ JEI/TOP/AE2  │   Integration ──┘   │   │
   │  └──────────────┘                     │   │
   │  ┌──────────────┐                     │   │
   │  │ ModularMagic │                     │   │
   │  └──────────────┘                     │   │
   │  ┌──────────────┐                     │   │
   │  │ ikx Assembly │                     │   │
   │  └──────────────┘                     │   │
   │  ┌──────────────┐                     │   │
   │  │ GeckoLib     │                     │   │
   │  └──────────────┘                     │   │
   ▼                                        ▼   ▼
Lib / Tools / Config / Mixin
```

### 35.11 退出 / 维护

- README 注明："This project is scheduled for archiving in the near future. For reference to future alternative projects, please see [PrototypeMachinery](https://github.com/NovaEngineering-Source/PrototypeMachinery)."

### 35.12 彩蛋

- `MC 1.12.2` 上的 `universal bucket` 静态启用。
- `acceptableRemoteVersions = [2.1.0, 2.4.0)` 限制版本区间。
- `doAction` / `Action` / `SequentialTaskExecutor` 借鉴了 Lux 等框架。

---

## 36. 总结：MMCE 相对原版 HellFirePvP ModularMachinery 的关键扩展

1. **并行支持**：
   - `TileParallelController` + `ParallelControllerData`。
   - Recipe / Machine 层级 `parallelized` / `parallelism` / `maxParallelism` / `internalParallelism`。
   - `parallelizeRecipeThreads` 全局开关。
2. **工厂控制器**：
   - `TileFactoryController` + `FactoryRecipeThread` + `FactoryRecipeSearchTask`。
   - `RecipeCraftingContextPool` 对象池。
   - 多个 `FactoryRecipeThread` 可由 `coreThread` 预设。
3. **智能接口**：
   - `SmartInterfaceType` / `SmartInterfaceData` / `TileSmartInterface` / `SmartInterfaceUpdateEvent`。
   - `interface_number_input` 配方需求。
4. **升级系统**：
   - `MachineUpgrade` / `DynamicMachineUpgrade` / `UpgradeType` / `RegistryUpgrade`。
   - `UpgradeEventHandlerCT` 可挂 CraftTweaker 函数。
5. **自动组装（ikx）**：
   - `MachineAssembly` + `AssemblyEventHandler` + `AssemblyConfig`。
   - `PktAutoAssemblyRequest` / `PktAssemblyReport`。
6. **ME 集成强化**：
   - 自研 `MEPatternProvider` / `MEPatternMirrorImage`。
   - `MEItemInputBus` / `MEItemOutputBus` / `MEFluidInputBus` / `MEFluidOutputBus` / `MEGasInputBus` / `MEGasOutputBus`。
   - `MachineCombinationComponent`。
7. **ModularMagic（kport）**：Botania / Blood Magic / Astral Sorcery / Nature's Aura / Thaumcraft 集成。
8. **大型动态结构**：
   - `DynamicPattern` + `DynamicMachine` + `BlockArrayCache`。
   - `MultiBlockModifierReplacement`。
9. **GTCEu 集成**：
   - `MachineComponentProxy` / `SpecialItemBlockProxy` 机制。
10. **CraftTweaker 完善**：
    - `MachineBuilder` / `MachineModifier` / `RecipePrimer` / `RecipeAdapterBuilder` / `MachineUpgradeBuilder` / `DynamicMachineUpgradeBuilder` / `MultiBlockModifierBuilder` / `MMEvents` / `GeoMachineModel` / `MagicPrimer` / `CommandCTReload` / `IngredientArrayPrimer` …
    - 各类 `MagicPrimer` 使添加魔法资源直接链式。
11. **JEI 强化**：
    - `CategoryDynamicRecipe` / `DynamicRecipeWrapper` / `RecipeLayoutPart` / `RecipeLayoutHelper` / `CategoryStructurePreview` / `StructurePreviewWrapper`。
    - 自定义 Mixin 让 Blueprint 在 JEI 内可交互。
12. **可视化升级**：
    - GeckoLib + Lumenized 渲染 + Bloom：`BloomGeoModelRenderer` / `MachineControllerRenderer` / `ControllerModelRenderManager`。
    - `MachineControllerModel` / `DynamicMachineModelRegistry` / `GeoMachineModel`。
13. **安全系统**：
    - `enableSecuritySystem` / `owner` GameProfile 校验。
    - `AE2SecurityCheck` 联动。
14. **性能系统**：
    - `TimeRecorder` / `usedTimeAvg` / `/mm performance` 命令 / `PktPerformanceReport`。
15. **Blueprint 工具**：
    - `ItemBlueprint` / `ItemConstructTool` / `ItemDebugStruct` / `MachineProjector`。
    - `PlayerStructureSelectionHelper` 选择 → 同步 → 输入控制器。
16. **Mixin 化 AE2 / JEI / RenderGlobal**：
    - 4 个 mixin 包。
17. **数据粒度**：
    - `cleanCustomDataOnStructureCheckFailed`、`enableFullDataSync`、`delayed-structure-check`、`max-structure-check-delay` 等精细开关。
18. **Gson 两阶段反序列化**：
    - `PRELOAD_GSON` + `GSON` + `MachineLoader.preload/load`。
    - 让配方可以引用后续加载的机器。

---

## 37. 结语

MMCE 是在原 ModularMachinery 之上的一次大规模扩展：
- 保留 JSON/Core 引擎；
- 新增了并行 / 工厂 / 智能接口 / 升级 / 自动组装 / ME Pattern / ModularMagic / GeckoLib 渲染 / 性能监控 / 安全系统 / 完整 CraftTweaker 等多个体系；
- 使用 Mixin 兼容 JEI / AE2 / RenderGlobal；
- 几乎覆盖了 1.12.2 主流科技/魔法 mod 的多方块合成需求。

写完本份文档后，无论是想重制到更新版本（如 1.18+ / 1.20+），还是想搬迁 / 移植到 KubeJS / PrototypeMachinery，都有完整的索引可以使用。
