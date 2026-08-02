# MMCR 移植范围（首期）

> ModularMachinery Community Refoxed —— 把 MMCE 1.12.2 的核心多方块引擎移植到 NeoForge 26.1.2 的「第一阶段」范围说明书。
> 与 `docs/MMCE.md`（37 章全量参考）的关系：MMCE.md 是教科书，本文是教纲——只摘出首期要做的。

## 0. 决策摘要（先把结论摆上台面）

| 维度 | 1.12.2 MMCE | 26.1.2 MMCR（首期） |
|---|---|---|
| 配方定义形式 | JSON 文件 + CraftTweaker/ZenScript 插件 | **Java API 优先** + KubeJS 可选绑定；机器 JSON 不再使用 |
| Mod 脚本 API | `@ZenClass`/`@ZenMethod`（CraftTweaker） | **Java API（必需）** + KubeJS 绑定层（**可选**，未装时仍能跑） |
| 物品 / 流体 / 能量 | 自定义 Capability 拼凑 | **直接用 NeoForge 官方能力**：`Capabilities.ItemHandler` / `Capabilities.FluidHandler` / `Capabilities.EnergyStorage` |
| 第三方 mod 联动 | AE2 / Mekanism / GTCeu / Botania / GeckoLib / Lumenized 等 10+ 模组深度耦合 | **零深度依赖**——仅保留 Vanilla + NeoForge + KubeJS（可选） + JEI（已在 build.gradle 内） |
| 渲染 | GeckoLib 模型 + Lumenized Bloom | **Neoforge 原生 + vanilla 模型 JSON**——首期不引入第三方渲染库（GeckoLib / Lumenized 全部 OUT） |
| 配置加载 | 1.12.2 Forge `@Config` | NeoForge `ModConfigSpec`（注册 `Config` 类型即可） |
| 数据序列化 | 自写 GSON 双阶段 + NBT 工具 | NeoForge `MapCodec` / `Codec` + `CompoundTag` |
| 注册机制 | `GameRegistry.register` + 反射 | `DeferredRegister`（NeoForge 26.1.2 标准） |

## 1. 首期「In Scope」清单

### 1.1 核心多方块引擎（必做，无可妥协）

- `MachineRegistry` —— 单例，按 `ResourceLocation` 索引已注册机器。
- `Machine`（抽象） + `DynamicMachine`（JSON/数据驱动的具体类——但首期**仅通过 Java API 注册**）。
- `MachineRecipe` + `RecipeRegistry` + 配方执行状态机（`RecipeStatus`：IDLE / CHECKING / RUNNING / DONE / FAILED）。
- `BlockArray` —— 通用多方块结构匹配数据结构。
- `StructureMatcher` —— 在 `Level` 上做结构匹配；支持 6 朝向 + 镜像（可选）。
- `MachineControllerBlock` + `MachineControllerBlockEntity`（控制器方块 + 实体）。

### 1.2 三类原生 Requirement / Component（基于 NeoForge 官方能力）

| MMCE 1.12.2 类型 | NeoForge 26.1.2 替代 | 备注 |
|---|---|---|
| `RequirementTypeItem`（`ItemStack` 匹配） | NeoForge `Ingredient` + `ItemStackHandler` | NBT / count / `DataComponent` 全部走官方 |
| `RequirementTypeFluid`（`FluidStack`） | NeoForge `FluidStack` + `IFluidHandler` capability | `FluidType` + `Fluid` 双层 |
| `RequirementTypeEnergy`（FE） | NeoForge `IEnergyStorage` capability | FE 即 NeoForge `EnergyStorage` |
| `ComponentItem` / `ComponentFluid` / `ComponentEnergy` | `BlockCapability<…, Direction>` | 直接 attach 到 controller / bus 上 |

**所有 ME 仓 / 气体仓 / GTCEu 适配均 OUT。**

### 1.3 必备方块 / Tile

| 方块 | Tile | 说明 |
|---|---|---|
| `controller`（控制器） | `MachineControllerBlockEntity` | 唯一可被「结构匹配」激活的方块；朝向 `facing` |
| `casing`（外壳） | 无 | 仅作结构匹配的几何占位，首期只出一种纹理 |
| `item_input_bus` / `item_output_bus` | `ItemBusBlockEntity` | 暴露 `ItemHandler` capability |
| `fluid_input_hatch` / `fluid_output_hatch` | `FluidHatchBlockEntity` | 暴露 `FluidHandler` capability |
| `energy_input_hatch` / `energy_output_hatch` | `EnergyHatchBlockEntity` | 暴露 `EnergyStorage` capability |

**没有平行机、工厂机、ME 系列、ModularMagic、智能接口、升级仓、蓝图物品。**

### 1.4 公开 API（addons 调用）

放在 `cn.howxu.mmcr.api` 包，纯 Java 公共接口；addons 通过 ServiceLoader 或直接 `mmcr` 入口类拿到：

```java
// 形态示意，非最终签名
public final class MMCR {
    public static MachineRegistry machines();
    public static RecipeRegistry recipes();
    public static RequirementTypeRegistry<Ingredient, ItemRequirement> itemRequirements();
    public static RequirementTypeRegistry<FluidStack, FluidRequirement> fluidRequirements();
    public static RequirementTypeRegistry<Integer, EnergyRequirement> energyRequirements();
    public static void registerEventHandler(Class<? extends MachineEvent> type, Consumer<MachineEvent> h);
    // ...
}
```

设计原则：
- 接口方法不接受任何 1.12.2 风格的 `World` / `BlockPos` 黑魔法；只接 NeoForge 的 `Level` / `BlockPos` / `ResourceLocation`。
- 不暴露 GSON / NBT 序列化给 addon——addon 用 NeoForge 的 `MapCodec` / `Codec` 写自己的 RecipeSerializer。

### 1.5 KubeJS 集成（**可选绑定，非必需**）

> **设计原则**：MMCR 的核心 API 必须能在「没装 KubeJS」的环境下独立工作。KubeJS 仅是「脚本玩家友好」的绑定层。

- `MMCRKubeJSPlugin` **只在 KubeJS 加载时** 才被 KubeJS 的 ServiceLoader 装入；缺 KubeJS 时 MMCR 仍能跑（仅少脚本入口）。
- 在 `src/main/resources/kubejs.plugins.txt` 注册 `cn.howxu.mmcr.kubejs.MMCRKubeJSPlugin`。
- 插件实现 `KubeJSPlugin` 的以下钩子：
  - `registerBuilderTypes`：暴露 `MachineBuilder`（脚本构造机器）+ `MachineRecipeBuilder`（构造配方）。
  - `registerBindings`：全局绑定 `MMCR.machines` / `MMCR.recipes`。
  - `registerEvents`：暴露 `MMCREvents.machineTick` / `MMCREvents.recipeComplete`。
  - `registerRecipeSchemas`：**用 Java `RecipeSchema` API 程序化注册** `mmcr:machine_recipe` 配方类型（**不放任何 JSON schema 文件**——参考 `docs/kubejs-integration.md §3`）。
  - `registerTypeWrappers`：`BlockArray pattern(...)` / `MachineComponent component(...)` 自动转换。
- 在 `MMCRKubeJSPlugin` 类顶部用 `@dev.latvian.mods.kubejs.plugin.KubeJSPlugin` 引用，但**不**直接 import 任何 KubeJS 内部类——所有调用走 `KubeJSPlugin` 接口的 default 方法。
- 检测 KubeJS 是否在场的运行时方法：`ModList.get().isLoaded("kubejs")`，仅在此条件成立时才执行 KubeJS 相关逻辑。

**注意**：`MMCRKubeJSPlugin` 类文件本身必须存在（KubeJS 会通过 FQN 反射加载），但如果 KubeJS 不在，所有方法体都不会被调用。**不需要 `@Optional` 注解**——因为我们从不调用 KubeJS API，KubeJS 来调我们。

### 1.6 配置

- `ModConfigSpec` 一份 `common.toml`：机器最大并行数（首期恒为 1）、能耗缩放、Tick 检查间隔、`enableKubeJSReloadCommand`。
- **不做任何 JSON 机器 / 配方 / 配方 schema**——机器、配方、KubeJS recipe schema 全部走 Java API；KubeJS 端 builder / recipe schema 也是 Java 程序化注册。

## 2. 首期「OUT of Scope」清单（不要做）

下列项在 MMCE 1.12.2 中存在，但**首期 MMCR 一律不做**：

### 2.1 第三方 mod 联动（深度依赖）

- ❌ AE2 / ME 物品总线 / ME 流体总线 / `MEPatternProvider` / `MEPatternMirrorImage`（整个 §26）。
- ❌ Mekanism 气体总线 / `MEGasInputBus`（§10/§26.4）。
- ❌ ModularMagic：`kport.modularmagic.*` 全包——Botania / Astral Sorcery / Blood Magic / Nature's Aura / Thaumcraft 集成（整个 §27）。
- ❌ GTCeu `MachineComponentProxy` / `SpecialItemBlockProxy`（§29）。
- ❌ GeckoLib 模型 + Lumenized Bloom 渲染（§30）。
- ❌ CraftTweaker（已用 KubeJS 替代）。
- ❌ Flux Networks 适配（§28）。
- ❌ IC2 / NCO / TE5 / Tinkers Construct 等 `RecipeAdapter`（§6.6）——不引入第三方配方桥接。
- ❌ Jade 集成（虽然 `build.gradle` 里有，但首期**不主动写** Jade provider，留在后续阶段）。

### 2.2 内部高级特性（**全部 OUT——本阶段不做**）

下列项都属于「在核心引擎跑通之后才考虑」的范围。代码里只会预留扩展点（接口、`TODO` 标记），不会出功能实现：

- ❌ **并行**：并行控制器 `TileParallelController`（§13.1）、配方层 `parallelized` / `maxParallelism` / `internalParallelism`（§6.1）、全局开关 `parallelizeRecipeThreads` / `maxMachineParallelism`（§32）。首期所有机器 `maxParallelism = 1`，无并行仓。
- ❌ **多线程**：工厂控制器 `TileFactoryController`（§13.2）、`FactoryRecipeThread` / `FactoryRecipeSearchTask`（§15.3）、`MachineRecipeThread` 单线程以外的所有调度、`TaskExecutor` fork/join 线程池（§15.1）、`RecipeCraftingContextPool`（§15.4）、`SequentialTaskExecutor`。首期所有机器走 **单 tick 单 RecipeSearchTask**。
- ❌ **算力 / 计算类需求**（用户口径下的「算力系统」）：智能接口 `SmartInterface` + `interface_number` 需求（§12 / §7 的 `RequirementTypeInterfaceNumInput`）、`SmartInterfaceData` 数值接口、`RecipeModifier` 链式修饰符（§9.1 整体保留接口签名但只出最小集，详见下文 §6）。
- ❌ **升级系统**：`MachineUpgrade` / `DynamicMachineUpgrade` / `UpgradeBus` / `RegistryUpgrade`（§14 全章）。
- ❌ **自动组装**：ikx 的 `MachineAssembly` / `AssemblyEventHandler` / `AssemblyConfig`（§22 全章）。
- ❌ **蓝图 / 投影器**：`ItemBlueprint` / `ItemConstructTool` / `ItemDebugStruct` / `MachineProjector`（§21 全章）——首期玩家只能用 `/mmcr reload` + 重新进游戏来更新机器。
- ❌ **Mixin**：4 个 mixin JSON 全删（§31）——首期零 Mixin，零 AccessTransformer。
- ❌ **安全系统**：owner 校验 / `enableSecuritySystem`（§35.1）——首期任何人能交互所有机器。
- ❌ **性能监控**：`TimeRecorder` / `/mm performance` / `PktPerformanceReport`（§35.4）——首期不测时延。
- ❌ **自定义数据 / 全量同步**：`customData` / `cleanCustomDataOnStructureCheckFailed` / `enableFullDataSync`（§35.2 / §35.3）。
- ❌ **增量结构匹配缓存**：`MMWorldEventListener` 的 `BlockEvent.NeighborNotify` 增量更新（§35.5）——首期每个 tick 重新匹配，开销可接受。
- ❌ **延迟结构检查开关**：`delayed-structure-check` / `max-structure-check-delay`（§32 控制器部分）。
- ❌ **结构预览渲染**：cleanroommc 移植的 `WorldSceneRenderer` 系列（§20）——整包不进。
- ❌ **GeckoLib 模型渲染**：`BloomGeoModelRenderer` / `MachineControllerRenderer` / `ControllerModelRenderManager`（§30）——首期控制器用普通方块模型 JSON。
- ❌ **结构预览 GUI**：`MachineStructurePreviewPanel` 3D 预览（§19 / §20）。
- ❌ **自定义 Widget 系统**：`github.kasuminova.mmce.client.gui.widget.*`（§19.3）——首期 GUI 用 vanilla `Button` / `EditBox` + NeoForge `AbstractWidget`。

### 2.3 JSON / 数据驱动

- ❌ JSON 形式的机器定义（`.json` 放 `assets/.../machinery/`）。
- ❌ JSON 形式的配方定义。
- ❌ JSON 形式的 `*.var.json` 变量替换。
- ❌ `MachineLoader.discoverDirectory(...)` 目录扫描（§4.4）。
- ❌ GSON 两阶段反序列化 `PRELOAD_GSON` + `GSON`（§35.18）。

**首期机器 / 配方只通过 Java API 或 KubeJS 脚本注册——不读 JSON 机器 / 配方文件。**

### 2.4 渲染相关

- ❌ `WorldSceneRenderer` / `FBOWorldSceneRenderer` / `ImmediateWorldSceneRenderer`（§20）——cleanroommc 移植代码，**整包删除**。
- ❌ `BloomGeoModelRenderer` / `MachineControllerRenderer` / `ControllerModelRenderManager`（§30）。
- ❌ `MachineStructurePreviewPanel` 3D 预览 GUI（§19 / §20）。
- ❌ 自定义 Widget 系统（`github.kasuminova.mmce.client.gui.widget.*`，§19.3）——首期直接用 NeoForge `AbstractWidget` 或 vanilla `Button` / `EditBox`。

## 3. 与现有 `build.gradle` 依赖的关系

当前 `build.gradle` 已声明：

| 依赖 | 首期是否保留 | 理由 |
|---|---|---|
| `net.neoforged:neoforge:26.1.2.84` | ✅ 保留 | 必需 |
| `mezz.jei:jei-26.1.2-neoforge-api:29.21.0.65` + `runtimeOnly` | ✅ 保留（**仅 API**） | 用其写 1 个 `IRecipeCategory` 暴露机器配方即可（首期可选） |
| `curse.maven:jade-324717:8251883` | ❌ 不引入 | 留待后续阶段 |
| `dev.latvian.mods:kubejs-neoforge:26.1.2-8.0.4` | ✅ 保留为 `runtimeOnly`（可选绑定） | 缺它时 MMCR 仍能跑（仅无脚本入口） |
| Rhino `sk9knFPE:ZdLtebKH` | ✅ 保留 | KubeJS 传递依赖 |

**首期不增加任何新依赖。**

## 4. 工作量与节奏估计

粗估首期代码量：

| 模块 | 估计行数（含注释） | 文件数 |
|---|---|---|
| Mod 入口 / 注册 / 配置 | 300-400 | 3-4 |
| 机器系统（`Machine` / `MachineRegistry` / `DynamicMachine`） | 500-700 | 5-7 |
| 结构匹配（`BlockArray` / `StructureMatcher`） | 400-600 | 4-5 |
| 配方系统（`MachineRecipe` / `RecipeRegistry` / `RecipeThread`） | 600-800 | 6-8 |
| 3 类 Requirement / Component（Item/Fluid/Energy） | 400-500 | 6-8 |
| 控制器 + 5 类仓方块 / Tile | 800-1200 | 10-14 |
| 公共 API（`MMCR` 入口 + 接口） | 200-300 | 4-6 |
| KubeJS 插件（`MMCRKubeJSPlugin` + bindings + 程序化 RecipeSchema）——**可选** | 500-700 | 4-6 |
| 数据生成（注册表 → JSON） | 200-300 | 2-3 |
| **合计** | **~4000-5500 行** | **~45-60 文件** |

不含翻译文件、资源贴图、JEI 注册。

## 5. 一句话目标

> 在 NeoForge 26.1.2 上跑通一个「3x3x3 立方体外壳 + 控制器 + 1 个 item 输入仓 + 1 个 item 输出仓 + 1 个 energy 输入仓」的最小机器，里面能用 KubeJS 写一条「铁锭→铁板」的配方，每秒处理一次，能耗正确、物品正确流转。
>
> 跑通这一刻，再谈下一阶段的智能接口、升级、ME 集成等。

## 6. 首期最小 RecipeModifier（仅「占位」）

`RecipeModifier`（§9.1）整链虽 OUT，但首期配方系统要预留 modifier 入口——否则后续阶段接入要重写所有匹配逻辑。约束：

- `MachineRecipe` 持有一个 `List<RecipeModifier> modifiers`，**首期只识别 `OPERATION_ADD` / `OPERATION_MULTIPLY` 两种 operation 对 `io=input, target=item/fluid/energy` 的修改**。
- Modifier 默认 empty list；KubeJS 端不暴露 modifier builder。
- `code` 上保留 `// TODO(post-mvp): modifier for output items, chance, parallelized, duration` 注释。

这样首期只跑通「按配方字面值扣物品 / 流流体 / 扣能量」，不开放数值修饰。

## 7. 未来阶段 TODO（按实现顺序预排）

跑通「§5 一句话目标」之后，按这个顺序往后做。每个 TODO 进入正式开发前再开新 spec：

### Phase 2 — **JEI 类别与配方转移**

1. 注册 `IRecipeCategory<MachineRecipe>`：每个机器一个 category。
2. `IRecipeTransferHandler`：从 JEI 拖物品到 ItemBus。
3. 不做 `CategoryStructurePreview`（结构预览）—— 留 Phase 5。

### Phase 3 — **并行 + 多线程**

4. `Machine.maxParallelism`（per-machine config）+ Recipe-level `parallelized` 开关。
5. `ParallelControllerBlock` + 5 个等级（4/16/64/256/512）—— 暂用 `Map<Long, TileEntity>` 简单实现。
6. `FactoryControllerBlock` + `FactoryRecipeThread`。
7. `TaskExecutor` fork/join 线程池（参考 MMCE §15.1）。
8. `RecipeCraftingContextPool` 对象池。

### Phase 4 — **算力 / 智能接口**

9. `SmartInterfaceBlock` + `SmartInterfaceData`（仅 `NUMBER` 类型）。
10. `interface_number` requirement。
11. `RecipeModifier` 全链（含 output / chance / duration）。

### Phase 5 — **升级 + 自动组装 + 蓝图**

12. `UpgradeBusBlock` + `MachineUpgrade` 系统。
13. `ItemBlueprint` + 在 GUI 内显示 3D 结构预览（Phase 5 同时做 cleanroommc 移植）。
14. ikx 的 `MachineAssembly`（自动组装）。
15. `MachineProjector`（你yihj 的世界投影）。

### Phase 6 — **第三方 mod 联动**（按需选做）

16. AE2 / ME 物品 / 流体总线 + 自研 `MEPatternProvider`。
17. Mekanism 气体仓。
18. GTCeu `MachineComponentProxy`。
19. ModularMagic（Botania / Blood Magic / Astral Sorcery / Nature's Aura / Thaumcraft）。
20. GeckoLib 模型 + Lumenized Bloom。
21. Jade provider。

每条都**单独开 spec**，不复用本文档。

## 8. 不再做的项（永久 OUT）

以下项即便将来也不会做——超出「多方块合成引擎」的本职：

- 渲染管道替换（GeckoLib / Lumenized）—— NeoForge 原生渲染已足够，首期起就**只用 vanilla + NeoForge**。
- Mixin（除非遇到 NeoForge API 不可绕过的限制；首选 AccessTransformer）。
- 自定义网络协议（§17 `PktXxx` 15 个包）——首期用 NeoForge `CustomPacketPayload` + 1-2 个包搞定。
- 自定义 Config loader（MMCE 的 `ModuleDataHolder`）——首期用 `ModConfigSpec` + TOML。
- `acceptableRemoteVersions` / `Tags.VERSION` 这类魔数—— `mod_version` 直接读 `gradle.properties`。
- `enableFullDataSync` / 自定义 `dataWatcher` 同步方案——首期默认全量同步由 `BlockEntity` 自己负责（NeoForge `BlockEntityTicker` 已在 helper 里写好）。