# MMCR 项目规划与进度

> ModularMachinery-Community-Refoxed：把 MMCE 1.12.2 移植到 NeoForge 26.1.2 的多阶段计划。
>
> 本文档定位：
> 1. **§1 当前基线**——盘点项目已经实现的内容（2026-08-06 调研）。
> 2. **§2 总体规划**——按阶段排列剩余工作，明确各阶段目标、依赖、验收。
> 3. **§3 实施准则**——移植原则、依赖策略、验收门槛。
> 4. **§4 文档体系**——本文档在项目文档中的位置与索引。
>
> 阅读建议：先看 §1.1 「已完成」与 §1.2 「当前优先收尾」确认状态；按 §2 选择要深入的阶段。
>
> 相关文档：
> - [`MMCE.md`](./MMCE.md) —— MMCE 1.12.2 功能全景 + API 变动（参考基线）。
> - [`api-mapping.md`](./api-mapping.md) —— NeoForge 26.1.2 API 逐项映射。
> - [`architecture.md`](./architecture.md) —— MMCR 26.1.2 包结构与翻译策略。
> - [`kubejs-integration.md`](./kubejs-integration.md) —— KubeJS 桥接层设计。

---

## 1. 当前基线（2026-08-06）

> 数据来源：直接盘点 `src/main/java/cn/howxu/mmcr/**` 源码 + 引用 `reference/mmce` 对照 + Git log。
> Git 分支：`dev/neo/26.1.2` 阶段 3B implementation baseline `386b210 test(stage3b): cover rotated position modifiers`。

### 1.1 已基本落地（按 MMCR 模块）

#### 1.1.1 Mod 骨架

- `cn.howxu.mmcr.MMCR` 主类：`@Mod("mmcr")`，NeoForge 26.1.2 入口，注册 `DeferredRegister.Blocks/Items/BE/RecipeTypes/UI/DataComponents/CreativeTabs/AttachmentTypes`。
- `cn.howxu.mmcr.Client` 客户端入口（`Dist.CLIENT` 隔离）。
- 配置：`cn.howxu.mmcr.config.Config`（`ModConfigSpec` TOML 注册为 COMMON）。
- 创造栏：图标 `basic_casing`，`ModItems.ITEMS` 全量展示。
- 内建机器/配方：`org.nibelungorum.BuiltinMachines` + `org.nibelungorum.DefaultMachines` + `org.nibelungorum.DefaultRecipes`。
- `cn.howxu.mmcr.registry.*`：`ModBlocks` / `ModItems` / `ModBlockEntities` / `ModDataComponents` / `ModRecipeTypes` / `ModUIs` / `PortKinds`。
- 命令（NeoForge `RegisterCommandsEvent`）：`ReloadCommand`（KubeJS reload） / `BuildCommand`（debug build） / `ExportCommand`（multiblock export）。

#### 1.1.2 机器系统

- `cn.howxu.mmcr.api.machine.Machine`（抽象）/ `DynamicMachine` / `MachineRegistry` / `MachineDefinitions`。
- `BlockArray`（`Map<BlockPos, BlockPredicate>`） / `BlockPredicate`（sealed） / `BlockRotator` / `BlockArrayCache`（按 facing 缓存）。
- 编译期优化：`CompiledMachinePattern` / `CompiledDynamicPattern` / `MachinePatternCompiler` / `DynamicPatternSpec` / `DynamicPatternMatch` / `MachineControllerSpec` / `PortRequirementSpec` / `MachineSelector`。
- `StructureMatcher`：在 `Level` 上做结构匹配，支持 6 朝向 + 镜像。
- `RecipeFailureActions`：`RESET` / `STILL` / `DECREASE`（机器级 default）。

#### 1.1.3 控制器

- `cn.howxu.mmcr.internal.block.MachineControllerBlock` + `MachineControllerBlockEntity`。
- 机器绑定 / 结构匹配 / 成型状态 / active recipe 基础状态。
- **多方向控制器**：机器级 opt-in 启用 UP/DOWN 控制器朝向（默认水平四向），并能记录竖直结构的成型态。Controller 在结构重置时同步清空 components / active recipe / context。
- 端口要求 spec（`PortRequirementSpec` / `MachineControllerSpec`）：机器定义可声明成型所需的端口集合与朝向，控制器在成型时校验。

#### 1.1.4 IO 端口（item / fluid / energy）

- `cn.howxu.mmcr.internal.block.IOPortBlock` —— IN/OUT 通用基类。
- `IOPortBlockEntity` —— 抽象，区分 `IOPortKind`。
- item bus：`ItemBusBlockEntity` / `ItemInputBusBlockEntity` / `ItemOutputBusBlockEntity`。
- fluid hatch：`FluidHatchBlockEntity` / `FluidInputHatchBlockEntity` / `FluidOutputHatchBlockEntity`。
- energy hatch：`EnergyHatchBlockEntity` / `EnergyInputHatchBlockEntity` / `EnergyOutputHatchBlockEntity`。
- Capability 注册（`cn.howxu.mmcr.internal.event.ModCapabilities`）：通过 `RegisterCapabilitiesEvent` 暴露 item / fluid / energy 给邻近方块。
- 调试源：`DebugSourceBlock` + `DebugInfiniteSourceBlockEntity` / `DebugInfiniteEnergySourceBlockEntity` / `DebugInfiniteFluidSourceBlockEntity`，用于手动验证 IO。

#### 1.1.5 配方与需求系统

- 数据类（`cn.howxu.mmcr.api.recipe.*`）：
  - `MachineRecipe`（完整类，holder codec + modifier 链 + priority + active recipe 引用）
  - `MachineRecipeSerializer`（`MapCodec` + `StreamCodec`）
  - `MachineIngredient`（sealed：`ItemIngredient` / `FluidIngredient` / `EnergyIngredient`）
  - `MachineOutput`（item / fluid + chance）
  - `MachineComponent` / `ComponentType` / `IntegrationTypeHelper`
- 运行时：
  - `ActiveMachineRecipe`（状态机：IDLE/CHECKING/RUNNING/DONE/FAILED）
  - `RecipeCraftingContext`（**单线程**执行上下文，983 行）
  - `RecipeCraftingContextPool`（节流池化，**不跨 tick 复用过期 BE**）
  - `RecipeSearchTask`（单配方搜索，节流 + 失败隔离）
  - `RecipeSearchResult` / `RequirementFailure`
  - `PreparedRecipe`（KubeJS / JSON 注册前的预制形态）
- Requirement 正式层（`cn.howxu.mmcr.api.recipe.requirement.*`）：
  - `MachineRequirement`（路由核心）
  - `ItemRequirement` / `FluidRequirement` / `EnergyRequirement`
  - **Selector tag 已支持**：recipe 可限定只从指定 tag 的 bus 消耗；未声明 tag 走「所有同类组件可用」默认行为。
- Helper（`cn.howxu.mmcr.api.recipe.helper.*`）：
  - `ProcessingComponent`（component + container + world pos + relative pos + selector tag）
  - `CraftCheck` / `CraftingStatus` / `RequirementComponents` / `EnergyRecipeIo` / `ComponentOutputRestrictor`
- Modifier（`cn.howxu.mmcr.api.recipe.modifier.*`）：
  - `RecipeModifier`（OPERATION_ADD / SUBTRACT / MULTIPLY / DIVIDE，target = input/output/duration/chance）
  - `AbstractModifierReplacement` / `SingleBlockModifierReplacement` / `MultiBlockModifierReplacement` / `DynamicModifierReplacement`
  - `ModifierRegistry`
- 注册表：`cn.howxu.mmcr.api.recipe.RecipeRegistry`（按 machine + priority 索引）。

#### 1.1.6 网络 / 菜单 / 屏幕

- 网络（NeoForge `RegisterPayloadHandlersEvent`）：
  - `PktMachineStatePayload`（S → C，机器状态）
  - `PktMultiblockDetectorPickPayload`（C → S，detector 工具）
- 菜单（`cn.howxu.mmcr.internal.menu.*`）：
  - `AbstractMachineMenu` / `MenuSupport` / `DirectionalItemSlot`
  - `MachineControllerMenu` / `ItemBusMenu` / `FluidHatchMenu` / `EnergyHatchMenu`
- 客户端 GUI（`cn.howxu.mmcr.client.gui.*`）：配套屏幕，简单 widget。
- World 事件：`cn.howxu.mmcr.internal.event.StructureDirtyEvents`（`onBlockPlaced` / `onBlocksPlaced` / `onFluidPlaced` / `onBlockBroken` / `onChunkUnloaded`）→ 触发结构脏标。

#### 1.1.7 工具与调试

- `MultiblockDetectorItem` / `MultiblockDetectorSelection`（玩家点击结构 → 选中方块 → 边界渲染）。
- `MultiblockDetectorClientHandler` / `MultiblockDetectorSelectionRenderer`（客户端高亮）。
- `WrenchItem` / `WrenchDebugHandler`（debug 扳手）。
- BuildCommand（debug 自由搭建）/ ExportCommand（multiblock export）。
- ReloadCommand（KubeJS reload）。

#### 1.1.8 数据生成（`cn.howxu.mmcr.datagen.*`）

- `DataGen` / `LangProvider` / `ModelGen` / `MachineControllerVariants` / `Translations`。
- 资源自动生成 → `src/generated/resources/`，与 `src/main/resources/` 合并。

#### 1.1.9 可选 compat

- KubeJS（`cn.howxu.mmcr.compat.kubejs.*`）：KubeJS **compileOnly** 依赖（不强制绑定）。`Plugin` / `MachineBuilderJS` / `MachineRecipeBuilderJS` / `MachineRecipeFactory` / `MachineRecipeSchema`。
  - `MachineBuilderJS` 支持 `event.create('mmcr:id').localizedName(...).pattern(string, keyMap)` 字符串式结构定义。
  - `MachineRecipeSchema` 用 Java `RecipeSchema` API 程序化注册 `mmcr:machine_recipe` 配方类型（零 JSON）。
- Jade（`cn.howxu.mmcr.compat.jade.*`）：`JadePlugin` / `MachineControllerComponentProvider` / `MachineControllerDataProvider`——controller + 端口信息显示。
- JEI（`cn.howxu.mmcr.compat.jei.*`）：`JeiPlugin` / `MachineRecipeCategory` / `MachineRecipeDisplay` / `MachineRecipeTransferHandler`，展示 machine recipes 的 item/fluid/energy/duration，并支持 item input bus 的物品输入转移；未安装 JEI 时不加载该 compat 入口。
- Oritech / GeckoLib / Rhino：`runtimeOnly` 软依赖，**未启用任何代码路径**。

### 1.2 仍需收尾（当前未发布 PR / 已知 TODO）

> 本节列出未关闭的悬置项；每项都已登记在 §2 对应阶段。

- P3B pattern position modifier 已完成：支持 single-block replacement metadata、结构内位置匹配、朝向 / 旋转位置映射，以及 runtime modifier snapshot 与 recipe 运行链合并。
- P3A 已让 output chance 在 finish 时应用；**modifier 影响的 input chance 后续迭代**。
- 平行 / 工厂 / 智能接口 / 升级 / 蓝图 / 自动组装 / AE2 等高级特性**全部 OUT**（详见 §2.6 – §2.9）。
- 旧 `docs/superpowers/` 与 `docs/optimization/` 已合并到本文件 + MMCE.md，**已删除**。
- 旧 `docs/scope.md` / `recipes-port.md` / `main-roadmap.md` 内容已并入本文件 + 既有 `architecture.md` / `api-mapping.md` / `kubejs-integration.md`，**已删除**。

### 1.3 模块 / 文件总览

```
src/main/java/
├── cn/howxu/mmcr/              ── 主包
│   ├── MMCR.java               ── @Mod 入口
│   ├── Client.java             ── 客户端入口
│   ├── api/                    ── 公开 API（addon 调用）
│   │   ├── machine/            ── Machine / DynamicMachine / BlockArray / StructureMatcher / PortRequirementSpec / ...
│   │   └── recipe/             ── MachineRecipe / RecipeRegistry / RecipeCraftingContext / Modifier / Requirement / Helper
│   ├── client/                 ── 客户端类
│   │   └── gui/                ── 屏幕
│   ├── compat/                 ── 可选绑定层
│   │   ├── jade/               ── Jade provider
│   │   └── kubejs/             ── KubeJS builder + schema
│   ├── config/                 ── ModConfigSpec
│   ├── datagen/                ── 数据生成
│   ├── internal/               ── 实现细节（addon 勿用）
│   │   ├── block/              ── MachineController / IOPort / Casing / DebugSource
│   │   ├── command/            ── Reload / Build / Export
│   │   ├── event/              ── ModCapabilities / StructureDirtyEvents / WrenchDebugHandler
│   │   ├── item/               ── MultiblockDetector / Wrench
│   │   ├── menu/               ── MachineControllerMenu / ItemBusMenu / FluidHatchMenu / EnergyHatchMenu
│   │   ├── network/            ── PktMachineStatePayload / PktMultiblockDetectorPickPayload
│   │   ├── port/               ── IOPortKind
│   │   ├── registry/           ── DeferredRegister 集合
│   │   └── tile/               ── 控制器 / bus / hatch / debug source
│   ├── registry/               ── ModBlocks / ModItems / ModBlockEntities / ModDataComponents / ModRecipeTypes / ModUIs
│   └── util/                   ── IOType 等
└── org/nibelungorum/           ── 内建机器 + 内建配方
    ├── BuiltinMachines.java
    ├── DefaultMachines.java
    └── DefaultRecipes.java
```

---

## 2. 总体规划

> **移植原则**：
> 1. 先核心闭环，再周边体验。
> 2. 每个 MMCE 功能只能落入 4 类：直译 / 重映射 / 删除 / 延后。无映射 = 不写。
> 3. NeoForge 优先：注册 / 配方 / Codec / capability / 菜单 / 网络均使用 NeoForge 26.1.2 标准 API。
> 4. 可选联动后置：JEI 作为可用性补完；AE2 / Mekanism / GTCeu / ModularMagic 在核心稳定后按需做。
> 5. 每个阶段单独 spec / plan / commit：本文件只排长线，不替代阶段设计。进入实施前必须开对应 `docs/.../specs/...` 与 `plans/...`。
>
> 阶段编号沿用既有 main-roadmap（已并入本文）。已完成阶段标记 ✅，进行中标记 🚧，未开始标记 ⬜。

### 2.0 总览（一张图）

| 阶段 | 目标 | 状态 | 关联 MMCE 章节 |
|---|---|---|---|
| **阶段 1 核心运行时闭环** | 控制器成型 → 走结构内组件 → recipe start / tick / finish | ✅ 完成 | §4–§6 / §8 |
| **阶段 2 Requirement / Component 正式层** | 路由 + 错误反馈稳定 | ✅ 完成 | §7 / §8 / §15 |
| **阶段 3A Recipe Modifier 全链** | recipe-local static modifier runtime chain | ✅ 完成 | §9 |
| **阶段 3B Pattern position modifier** | pattern 位置级 modifier replacement | ✅ 完成 | §9.2 / §9.4 |
| **阶段 4 JEI 集成** | recipe category + transfer | ✅ 完成 | §23 / §24（功能） |
| **阶段 5 并行与工厂控制器** | parallel + factory 多线程 | ⬜ 未开始 | §13 / §15 |
| **阶段 6 智能接口** | interface_number + 数值输入 | ⬜ 未开始 | §12 / §7 |
| **阶段 7 升级 + 蓝图 + 预览 + 自动组装** | UX 体验层 | ⬜ 未开始 | §14 / §21 / §20 / §22 |
| **阶段 8 第三方联动** | AE2 / Mekanism / GTCeu / ModularMagic / Jade（深化） | ⬜ 未开始（按需） | §26–§29 |

### 2.1 阶段 1：核心运行时闭环 ✅

**目标**：把 MMCE 的 `TileMultiblockMachineController` + `RecipeCraftingContext` 最小运行语义移植完整：结构成型后使用结构内组件执行配方，而不是扫描固定范围。

**已完成**（§1.1.3 / §1.1.4 / §1.1.5 详述）：

- 结构组件上下文（`ProcessingComponent` + controller 内部更新）。
- 每 tick IO 与失败动作（energy per tick / `RecipeFailureActions` / `cancelIfPerTickFails`）。
- 输出系统补齐（`MachineOutput.ItemOutput` / `MachineOutput.FluidOutput`，含 chance）。
- Active recipe NBT roundtrip 保持 recipe id / tick / total tick / parallelism。

**验收门槛**：`./gradlew compileJava --no-daemon` + `./gradlew test --no-daemon` 通过；JEI 暂未集成，结构 / 配方 E2E 可通过 GameTest 跑。

### 2.2 阶段 2：Requirement / Component 正式层 ✅

**目标**：从简化 `MachineIngredient` 模型过渡到接近 MMCE 的 requirement/component 路由模型，仅 vanilla + NeoForge item/fluid/energy 三类。

**已完成**（§1.1.5 详述）：

- Requirement 类型：`ItemRequirement` / `FluidRequirement` / `EnergyRequirement` / `MachineRequirement`。
- Component 路由：pattern 内端口参与 IO，pattern 外端口不参与；context 内部 item / fluid / energy route helper。
- Selector tag 已支持（recipe 限定 tag + 未声明 tag 走默认行为）。
- 错误反馈：模拟 / 提交分离，错误日志能指出缺少哪种 requirement / 数量差 / 在哪些组件中查找过。

**验收门槛**：测试覆盖匹配、simulate、commit、ioTick、output 五个阶段；多个同类输入跨多个组件聚合而非只找第一个。

### 2.3 阶段 3A：Recipe Modifier 全链 ✅

**目标**：补齐 recipe-local static modifier 链，覆盖 duration / input / output / chance；原始值与运行时派生值边界清晰。

**已完成**（§1.1.5 详述）：

- `RecipeModifier` runtime chain（OPERATION_ADD / SUBTRACT / MULTIPLY / DIVIDE，target = input / output / duration / chance）。
- `ModifierRegistry` 注册表。
- `MachineOutput` chance 字段，零 chance 输出在 finish 时被剔除。
- Codec roundtrip 保留原始 recipe 定义；派生值通过 getter 或 runtime context 计算。
- 单元测试覆盖 modifier 序列化与边界 case。

**验收门槛**：测试覆盖 raw serialization、modifier output edge case；JEI 集成前的最终值稳定。

### 2.4 阶段 3B：Pattern position modifier ✅

**目标**：在阶段 3A recipe modifier runtime chain 上移植结构位置级 modifier 替换；本阶段不依赖 JEI，JEI 仍作为阶段 4 的展示 / transfer 体验补完。

**已完成**：

- `DynamicMachine` 旁路保存 per-position `SingleBlockModifierReplacement`，base `BlockArray` 语义保持不变，并保留旧构造器可编译。
- modifier 方块只在 matched pattern 内生效；结构外同类方块不进入 machine replacement map。
- modifier 位置随 horizontal facing、vertical facing 和 roll-facing 通过 `BlockRotator` 同一公式映射。
- 结构匹配、first mismatch、compiled path 与 vertical fallback path 使用一致的 replacement 判断。
- runtime recipe search、active duration、ioTick、simulate、commit 共享合并后的 effective modifier list，且 modifier snapshot 不向调用方泄漏可变引用。
- 与 selector tag 共存时不改变已有 component route / requirement tag 语义。

**后续边界**：`MultiBlockModifierReplacement` 保持未接入；JEI recipe category / transfer 仍在阶段 4 单独实现。

**验收门槛**：modifier 方块在结构内能影响 IO / 输出 / chance / duration；旋转 / 镜像后位置映射正确；JEI 暂未实现但不阻塞本阶段 runtime 验收。

### 2.5 阶段 4：JEI 集成 ✅

**目标**：移植 MMCE 的 JEI 动态机器配方展示与基础配方转移。先做 recipe category，不做 3D 结构预览。

**已完成任务**：

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `CategoryDynamicRecipe` | `compat.jei.MachineRecipeCategory` | 重映射，NeoForge / JEI 29 API |
| `DynamicRecipeWrapper` | wrapper 或直接 `MachineRecipe` | 重映射 |
| `RecipeLayoutHelper` / `RecipeLayoutPart` | recipe layout builder helpers | 重映射，按 JEI 29 slot API 写 |
| `JEIComponentItem/Fluid/Energy` | item / fluid / energy display adapters | 直译简化 |
| JEI mixin | 不移植 | 删除 |

> 首期不做 3D 结构预览；fluid / energy recipe transfer 仅显示限制原因。

**验收门槛**：
- 每台 machine 至少有一个 JEI category 或按 machine 分组显示。
- item / fluid / energy 输入输出显示正确，duration 和 FE/t 显示正确。
- JEI recipe transfer 能把物品输入移动到 item input bus；fluid / energy transfer 不做或只显示原因。
- 未安装 JEI 时 MMCR 可正常加载。

### 2.6 阶段 5：并行与工厂控制器 ⬜

**目标**：移植 MMCE 的并行执行能力——先可验证的单控制器并行，再做 factory 多线程。

**未开始任务**：

#### 2.6.1 Parallel Controller

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `TileParallelController` | `ParallelControllerBlockEntity` | 直译简化 |
| `BlockParallelController` | `ParallelControllerBlock` | 直译简化 |
| `ComponentParallelController` | component type | 直译简化 |
| parallel levels 4/16/64/256/512 | `ParallelTier` | 直译 |
| recipe `parallelized` | `MachineRecipe.parallelized` | 直译 |

#### 2.6.2 Factory Controller / 多线程

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `TileFactoryController` | `FactoryControllerBlockEntity` | 延后到 parallel 稳定后 |
| `FactoryRecipeThread` | factory recipe scheduler | 重映射 |
| `TaskExecutor` | Java executor 或 server tick task queue | 重映射，**不能破坏 MC 主线程安全** |
| `RecipeCraftingContextPool` | context pool | 延后，只有性能需要时做 |

**验收门槛**：
- 无 parallel controller 时 parallelism 恒为 1。
- 有 parallel controller 且 recipe 允许并行时，输入 / 输出 / energy 按 parallelism 成倍模拟和提交。
- 输出空间不足时不启动并行 recipe。
- 不在异步线程直接读写 world / BE。
- 工厂 controller 能管理多个 recipe thread，且卸载 / 破坏结构时安全停止。

### 2.7 阶段 6：智能接口 ⬜

**目标**：移植 MMCE 的智能接口数值输入。

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `TileSmartInterface` | `SmartInterfaceBlockEntity` | 直译简化 |
| `BlockSmartInterface` | `SmartInterfaceBlock` | 直译简化 |
| `SmartInterfaceType` | `SmartInterfaceType` | 直译，首期只做 NUMBER |
| `SmartInterfaceData` | `SmartInterfaceData` | 直译简化 |
| `RequirementInterfaceNumInput` | number requirement | 直译 |
| smart interface packets | menu data sync / custom payload | 重映射 |

**验收门槛**：
- smart interface 可在 GUI 中设置数字。
- recipe 可要求某个数字输入达到阈值。
- selector tag 可限定 recipe 读取哪个 smart interface。
- 值持久化并能随 block entity NBT roundtrip。

### 2.8 阶段 7：升级 / 蓝图 / 预览 / 自动组装 ⬜

**目标**：补 MMCE 的玩家体验层。这个阶段可以拆成多个独立 spec，不应一次做完。

#### 2.8.1 Upgrade 系统

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `MachineUpgrade` | `MachineUpgrade` | 直译简化 |
| `DynamicMachineUpgrade` | KubeJS / Java API upgrade definition | 重映射 |
| `UpgradeBus` / `TileUpgradeBus` | `UpgradeBusBlockEntity` | 直译简化 |
| `UpgradeType` | `UpgradeType` | 直译 |
| upgrade modifier | recipe modifier hook | 重映射 |

#### 2.8.2 Blueprint / 结构预览

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `ItemBlueprint` | `BlueprintItem` | 直译简化 |
| `ItemConstructTool` / `ItemDebugStruct` | debug / build tooling | 重映射，已有 debug wrench 可复用 |
| `MachineStructurePreviewPanel` | GUI structure preview | 重映射，NeoForge / vanilla rendering |
| `WorldSceneRenderer` / cleanroommc preview renderer | preview renderer | 延后 / 重评估 |
| GeckoLib controller model preview | 不移植 | 删除 |

#### 2.8.3 自动组装 / 投影器

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| ikx `MachineAssembly` | `MachineAssembly` | 直译简化 |
| `AssemblyEventHandler` | server event handler | 重映射 |
| `MachineProjector` | projector item | 直译简化 |
| `StructurePreviewHelper` | preview helper | 重映射 |

**验收门槛**：
- 升级：upgrade bus 可插入升级物品；能影响 duration / energy / parallelism 至少一类数值；未插升级时行为完全等同阶段 6。
- 蓝图：玩家能拿到 blueprint 并查看机器结构层级；GUI 能显示 required blocks / components 列表；3D 预览可后置。
- 自动组装：只消耗玩家 / 容器中真实方块，不生成免费方块；组装失败能指出缺少的 block / item。
- 投影器：只显示 ghost，不改变世界。
- 不引入 GeckoLib / Lumenized。

### 2.9 阶段 8：第三方联动 ⬜（按需选做）

**目标**：核心稳定后按用户实际整合包需求选择移植，不预设全部做。

| Mod | 任务 | 移植方式 |
|---|---|---|
| AE2 / ME item bus | `MEItemInputBus` / `MEItemOutputBus` → `compat.ae2` | 重映射到 AE2 26.x API |
| AE2 / ME fluid bus | `MEFluidInputBus` / `MEFluidOutputBus` → `compat.ae2` | 重映射 |
| AE2 / ME pattern provider | `MEPatternProvider` → `compat.ae2.pattern` | 重映射，**需先查 AE2 当前 API 示例** |
| Mekanism Gas | `RequirementGas` / `ComponentGas` → `compat.mekanism` | 重映射 |
| GTCeu | `MachineComponentProxy` → `compat.gtceu` | 重映射 |
| ModularMagic | kport requirements / components → `compat.modularmagic` | 按实际 mod 逐个 spec |
| Jade | 深化 provider | 已落地，扩展点为主 |

**验收门槛**：
- 未安装对应 mod 时不加载该 mod 的类。
- 每个联动都是可选模块，不成为 MMCR 硬依赖。
- 每个联动至少有一个 E2E 验证或手动验证说明。
- AE2 / JEI 旧 mixin 默认不移植；确需 GUI 接入时另开 spec。

### 2.10 推荐实施顺序

1. **阶段 4 JEI**：补 recipe category + transfer，让玩家能看见 / 操作现有阶段 1-3B 的成果。
2. **阶段 5 Parallel**：先 parallel controller，再 factory controller。
3. **阶段 6 Smart Interface**：补 `interface_number`。
4. **阶段 7 UX**：upgrade / blueprint / preview / auto assembly / projector 拆开做。
5. **阶段 8 第三方**：AE2 优先；其次 Mekanism / Jade / GTCeu / ModularMagic，按实际需求逐个移植。

---

## 3. 实施准则

### 3.1 翻译手法

| 手法 | 说明 | 适用情形 |
|---|---|---|
| **直译** | MMCE 类的 API 形态 1:1 对应 NeoForge 同名 API | 工具类、简单数据结构 |
| **借用** | MMCE 自写的功能直接换成 NeoForge / 社区已有的等价物 | 旧 Forge 自带功能（Fluid / Energy / ItemHandler） |
| **重新映射** | MMCE 的抽象概念拆散，归并到 NeoForge 的多个对应物 | RequirementType / ComponentType / 注册机制 |
| **删除** | MMCE 的功能在 NeoForge 时代已无意义 | GSON 双阶段、Universal Bucket、acceptableRemoteVersions 等 |
| **保留接口 / TODO** | MMCE 的功能先空着，预留扩展点 | 并行 / 多线程 / 升级 / 智能接口 |

**严格遵守**：每写一个 MMCE → MMCR 映射，写明属于上面 5 类哪一类。无映射 = 不写。

### 3.2 命名

- 不为类名加 `MMCR` 前缀（沿用 MMCE 命名风格：`MachineRecipe` / `BlockArray` / `StructureMatcher` 等）。
- 新建类添加 javadoc 作者信息 `@author howxu <dev@howxu.cn>`。
- 内部类（`internal/`）不暴露给 addon 调用。
- KasumiNova 移植包名按 `api` / `internal` / `compat` 收编，**不**保留 `github.kasuminova.mmce` 前缀（参考 [`MMCE.md §11.8`](./MMCE.md#118-迁移时的命名建议)）。

### 3.3 依赖策略

- **不引入新硬依赖**：JEI / KubeJS / Jade 已是可选绑定层，不升级或换源。
- KubeJS 走 `compileOnly`（当前 `build.gradle` 已是如此），让插件类在 KubeJS 不在时不被加载。
- 第三方 compat（AE2 / Mekanism / GTCeu / ModularMagic）**只**作为 `runtimeOnly` 软依赖，**任何**代码路径都不能在 mod 构造器阶段硬引。

### 3.4 每阶段通用验收门槛

- `./gradlew compileJava --no-daemon` 通过。
- `./gradlew test --no-daemon` 通过；如果某阶段因 GameTest 环境限制无法跑完整测试，需要记录未跑原因。
- 涉及 block / entity / registry / resource 时检查 datagen 输出、lang、blockstate、model、loot / table 或 creative tab 引用。
- 涉及 optional integration 时验证未安装该 mod 也能启动 / 编译，不出现 eager class loading。
- 每阶段完成后更新本文档的「当前基线」（§1），避免路线图和实际进度脱节。

### 3.5 永久不移植

> 以下项即便将来也不会做——超出「多方块合成引擎」的本职，或在 NeoForge 时代无意义。

- Forge 1.12.2 lifecycle / proxy：`CommonProxy` / `ClientProxy` 作为生命周期容器不移植，已由 NeoForge mod bus / client event 替代。
- `GameRegistry` / `InternalRegistryPrimer` / Forge registry wrapper：不移植，使用 `DeferredRegister`。
- 双阶段 GSON loader / `MachineLoader.discoverDirectory` / 变量 JSON：不移植，使用 Codec / datapack / KubeJS / Java API。
- CraftTweaker / ZenScript 集成：不移植，KubeJS 是替代入口。
- 旧 `SimpleNetworkWrapper` 15 个 packet：不逐个移植，按当前功能用 NeoForge `CustomPacketPayload` 重映射。
- GeckoLib / Lumenized / Bloom controller renderer：不移植，除非未来明确改目标为复刻 MMCE 视觉效果。
- MMCE 针对 AE2 / JEI / GeckoLib 的旧 mixin：不移植；遇到 NeoForge / API 限制时重新写最小 mixin。
- Recipe Adapter 旧外部机器桥接（IC2 / NCO / TC6 / TConstruct / TE5）：默认不移植；若某整合包确需，作为阶段 8 单独重评估。

---

## 4. 文档体系

### 4.1 文档清单

| 文档 | 定位 |
|---|---|
| [`MAIN.md`](./MAIN.md) | **本文档**：项目规划、阶段安排、当前基线。 |
| [`MMCE.md`](./MMCE.md) | MMCE 1.12.2 功能全景 + KasumiNova 扩展层可移植性分析 + API 变动。 |
| [`api-mapping.md`](./api-mapping.md) | NeoForge 26.1.2 API 逐项映射（Item / Energy / Fluid / Capability / Recipe）。 |
| [`architecture.md`](./architecture.md) | MMCR 26.1.2 包结构与翻译策略。 |
| [`kubejs-integration.md`](./kubejs-integration.md) | KubeJS 桥接层设计。 |

### 4.2 文档之间关系

```
MAIN.md ──────────── 总规划（你看这里）
   │
   ├── MMCE.md ────── 参考基线（我们要复刻什么）
   │     │
   │     └── api-mapping.md ── 1.12.2 → 26.1.2 逐项 API 差异
   │
   ├── architecture.md ───── 翻译策略与包结构
   │
   └── kubejs-integration.md ── 可选绑定层设计
```

### 4.3 阶段实现前的 spec / plan 模板

每个阶段进入实施前应开：

- spec：`docs/.../specs/YYYY-MM-DD-<topic>-design.md` —— 设计文档（沿用 superpowers 模板）。
- plan：`docs/.../plans/YYYY-MM-DD-<topic>-plan.md` —— 实施计划（来自 writing-plans 技能）。

完成后回填本文档的「当前基线」与对应阶段状态。

---

## 5. 进度速览

> 详细 diff 见 `git log --oneline dev/neo/26.1.2`。

- `386b210` test(stage3b): cover rotated position modifiers（**阶段 3B implementation baseline**）
- `edfd455` fix(recipe): clarify modifier snapshots
- `1d2192e` fix(recipe): refresh active duration after modifier changes
- `6d94b23` fix(recipe): keep active context routes on modifier refresh
- `2e41a94` feat(recipe): apply structure position modifiers at runtime
- `d06758e` fix(machine): refresh matched modifiers during formed checks
- `249faaa` feat(machine): collect matched position modifiers
- `e9199f7` fix(machine): preserve replacement matches after formation
- `b13d516` feat(machine): match single block replacements
- `363a11b` feat(machine): compile position modifier rotations
- `e40a68c` feat(machine): expose position modifier replacements
- `a8a82ed` test(modifier): document single block replacement test
- `a0f47c7` feat(modifier): add single block replacement metadata
- 阶段 3B 最终验证（2026-08-06）：`./gradlew compileJava --no-daemon` → `BUILD SUCCESSFUL in 5s`；`./gradlew test --no-daemon` → `BUILD SUCCESSFUL in 9s`；`./gradlew check --no-daemon` → `BUILD SUCCESSFUL in 9s`。
- `4d0e6c2` release: stage 3 in
- `f6ff6aa` Merge: work/p3a-recipe-modifier-chain → dev/neo/26.1.2
- `50f95a0` fix(recipe): skip impossible zero chance outputs
- `7a2f380` feat(recipe): expose modifiers through recipe authoring
- `e67dab1` feat(recipe): apply output chance on finish
- `54ee3a8` feat: optimize structure detection cache
- `ff2788f` feat(recipe): add throttled search context pooling
- `92068d4` release: some fix and some feats
- `6573601` release: P1 finish
- `8115706` release: P2 finished
