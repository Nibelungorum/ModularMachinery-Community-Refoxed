# MMCE Long-Term Port Roadmap

> 本文是 MMCE 1.12.2 → MMCR NeoForge 26.1.2 的长期移植路线图。它不是功能愿望单，而是移植清单：每一项都写清楚 MMCE 来源、MMCR 目标形态、依赖顺序、验收标准，以及明确不移植的内容。

## 0. 移植原则

- **先核心闭环，再周边体验**：控制器成型、配方启动、item/fluid/energy IO、运行状态持久化必须先稳定，之后再做 JEI、蓝图、预览、并行、自动组装。
- **逐项映射，不重新发明**：每个 MMCE 功能只能落入四类之一：直译、重映射、删除、延后。无映射的功能不写。
- **NeoForge 优先**：注册、配方、Codec、capability、菜单、网络均使用 NeoForge 26.1.2 标准 API；不移植 Forge 1.12.2 时代的 GSON loader、SimpleNetworkWrapper、GameRegistry 包装层。
- **可选联动后置**：JEI 可以作为 Phase 2 做，因为它提升可用性；AE2、Mekanism、GTCeu、ModularMagic、Jade 等都在核心稳定后按需做。
- **每个阶段单独 spec / plan / commit**：本文件只排长线，不替代阶段设计。进入实施前必须开对应 `docs/superpowers/specs/*` 和 `docs/superpowers/plans/*`。

## 1. 当前基线

### 已基本落地

- Mod 入口、DeferredRegister、基础配置、创造栏、方块/物品/BE 注册。
- `Machine` / `DynamicMachine` / `MachineRegistry` / `BlockArray` / `BlockPredicate` / `StructureMatcher`。
- 控制器方块与 BE：机器绑定、结构匹配、成型状态、active recipe 基础状态。
- 基础 IO 端口：item input/output bus、fluid input/output hatch、energy input/output hatch。
- 基础 capability：NeoForge item/fluid/energy capability 暴露。
- 基础菜单和屏幕：控制器、item bus、fluid hatch、energy hatch 的简单 GUI。
- `MachineRecipe` / `RecipeRegistry` / `ActiveMachineRecipe` / `RecipeCraftingContext` / modifier 最小集。
- Java API 注册默认机器/配方，KubeJS builder 初步入口，datagen 资源生成。
- Debug wrench 与 debug infinite source，用于手动验证 IO。
- Phase 2 Requirement / Component 正式层已闭环：只保留 `api.recipe.requirement.*` 一套 requirement，item/fluid/energy runtime 由 requirement 分派，selector tag 成功与 mismatch failure 均有测试覆盖。

### 当前优先收尾

- 修复当前 dirty runtime 改动导致的 `RecipeApiSmokeTest.recipe_codec_roundtrip_preserves_modifiers_and_priority` 失败。
- 完成 matched pattern 组件上下文：`MachineComponentTile` → `ProcessingComponent` → `RecipeCraftingContext`。
- 确认端口只要在 matched pattern 内就能参与 recipe IO，且 pattern 外端口不会被误用。
- Phase 2 后续仅保留非阻塞收敛项：继续把 context 内部 item/fluid/energy route helper 拆成更薄的 adapter；当前主调度和 public requirement 模型已完成闭环。
- 在此阶段提交前必须通过 `./gradlew compileJava --no-daemon` 和 `./gradlew test --no-daemon`；相关 GameTest 能跑时再跑结构/配方 E2E。

## 2. Phase 1：核心运行时闭环

**目标**：把 MMCE 的 `TileMultiblockMachineController` + `RecipeCraftingContext` 最小运行语义移植完整：结构成型后使用结构内组件执行配方，而不是扫描固定范围。

### 2.1 结构组件上下文

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `MachineComponentTile` | `cn.howxu.mmcr.api.recipe.MachineComponentTile` | 直译，端口 BE 提供组件 |
| `MachineComponent` | `cn.howxu.mmcr.api.recipe.MachineComponent` | 重映射，只保留 `IOPortKind + IOType` |
| `ProcessingComponent` | `api.recipe.helper.ProcessingComponent` | 直译简化，记录 component、container、world pos、relative pos、selector tag |
| `TileMultiblockMachineController.updateComponents()` | `MachineControllerBlockEntity.updateComponents()` | 直译简化，遍历 matched pattern 收集 BE 组件 |

**验收**：

- 控制器成型后日志能列出 item input/output、fluid input/output、energy input/output 数量。
- pattern 内端口离控制器超过旧 `3x3 above controller` 范围时，配方仍能使用。
- pattern 外端口即使靠近控制器也不能参与配方。
- 结构 reset 后 `components`、active recipe、context 同步清空。

### 2.2 每 tick IO 与失败动作

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `ComponentRequirement.PerTick#doIOTick` | `RecipeCraftingContext.ioTick(MachineRecipe)` | 重映射，首期只做 energy per tick |
| `RecipeFailureActions` | `api.machine.RecipeFailureActions` | 直译 `RESET/STILL/DECREASE` |
| `ActiveMachineRecipe.tick()` | `ActiveMachineRecipe.tick(RecipeCraftingContext)` | 直译简化，tick 前先执行 per-tick IO |
| `cancelIfPerTickFails` | `Machine.failureAction()` | 重映射，机器级默认 `STILL` |

**验收**：

- energy input 从“启动时扣总量”改为“每 tick 扣 `fePerTick`”。
- 能量不足时 active recipe 根据 failure action 处理：`STILL` 停在当前 tick，`RESET` 清零，`DECREASE` 回退一 tick。
- `commitInputs()` 不再扣 energy；item/fluid 输入仍在启动/commit 边界处理。
- active recipe NBT roundtrip 保持 recipe id、tick、total tick、parallelism 信息。

### 2.3 输出系统补齐

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| item output requirement | `MachineOutput.ItemOutput` | 重映射，替代 `List<ItemStack>` |
| fluid output requirement | `MachineOutput.FluidOutput` | 重映射，NeoForge `FluidStack` |
| output slot/tank simulation | `RecipeCraftingContext.simulateOutputs()` | 直译简化，先模拟再提交 |

**验收**：

- `MachineRecipe.outputs()` 支持 item 和 fluid 两类输出。
- item output bus 满时不启动配方，不吞输入。
- fluid output hatch 空间不足时不启动配方，不吞输入。
- `assemble()` 对 vanilla recipe book 只返回第一个 item output；fluid-only recipe 返回 `ItemStack.EMPTY`。

## 3. Phase 2：Requirement / Component 正式层

**目标**：从当前 `MachineIngredient` 简化模型过渡到接近 MMCE 的 requirement/component 路由模型，但只移植 vanilla + NeoForge item/fluid/energy 三类。

### 3.1 Requirement 类型

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `RequirementItem` | `MachineIngredient.ItemIngredient` 或 `RequirementItem` | 重映射，使用 `Ingredient + count + DataComponent` |
| `RequirementFluid` | `MachineIngredient.FluidIngredient` 或 `RequirementFluid` | 重映射，使用 NeoForge `FluidStack` / `FluidIngredient` |
| `RequirementEnergy` | `MachineIngredient.EnergyIngredient` 或 `RequirementEnergy` | 直译语义，FE/t |
| `RequirementDuration` | duration modifier | 重映射，不作为独立 requirement 存储 |
| `RequirementCatalyst` | 后置 | 延后，需先有不消耗输入语义 |
| `RequirementItemDurability` | 后置 | 延后，需 DataComponent/耐久处理设计 |
| `RequirementGas` | Phase 8 Mekanism | 延后，第三方依赖 |
| `RequirementInterfaceNumInput` | Phase 6 智能接口 | 延后 |

**验收**：

- requirement 匹配、simulate、commit、ioTick、output 五个阶段边界清晰。
- 输入和输出都支持 modifier 后的 count/amount。
- 多个同类输入能跨多个组件聚合，而不是只找第一个 bus/hatch。
- 错误日志能指出缺少哪种 requirement、数量差多少、在哪些组件中查找过。

### 3.2 Component 类型

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `ComponentItem` | item bus component | 直译简化，容器为 `IItemHandler` |
| `ComponentFluid` | fluid hatch component | 直译简化，容器为 `IFluidHandler` |
| `ComponentEnergy` | energy hatch component | 直译简化，容器为 `IEnergyStorage` |
| `ComponentParallelController` | Phase 5 | 延后 |
| `ComponentSmartInterface` | Phase 6 | 延后 |
| `ComponentUpgradeBus` | Phase 7 | 延后 |
| `ComponentGas` | Phase 8 | 延后 |
| `ComponentItemFluid` | 暂不移植 | 删除/按需重评估 |

**验收**：

- `ProcessingComponent` 可按 component type、IO type、selector tag 过滤。
- recipe context 不直接依赖具体 BE 类，优先依赖 component/container 接口。
- 仍保持无第三方 mod 硬依赖。

### 3.3 Selector Tag / 分组

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `ComponentSelectorTag` | `ProcessingComponent.tag` + machine pattern metadata | 重映射 |
| `TaggedPositionBlockArray` | `BlockArray` 附加 per-position metadata | 重映射 |

**验收**：

- 机器定义可声明某个输入只允许从指定 tag 的 bus 消耗。
- 未声明 tag 的 recipe 维持现有“所有同类组件可用”行为。

## 4. Phase 3：Recipe Modifier 全链

**目标**：移植 MMCE 的 recipe modifier 概念，让结构内 modifier 方块、机器属性或脚本能影响 duration、input、output、chance 等数值。

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `RecipeModifier` | `api.recipe.modifier.RecipeModifier` | 直译，补齐 operation 和 target |
| `ModifierRegistry` | modifier registry 或 enum-backed dispatcher | 重映射 |
| `SingleBlockModifierReplacement` | pattern position modifier | 延后到 selector/tag 后 |
| `MultiBlockModifierReplacement` | structure-wide modifier | 延后到 Phase 5/6 |
| `DynamicModifierReplacement` | runtime modifier hook | 延后，需事件/脚本设计 |
| output chance | `MachineOutput` chance 字段或 wrapper | 重映射 |

**验收**：

- duration modifier 不污染 serialized raw `tick_time`。
- input item/fluid/energy、output item/fluid、chance、duration 各自有明确 target。
- modifier 应用顺序与 MMCE 文档一致：add/subtract/multiply/divide 可预测。
- codec roundtrip 保留原始 recipe 定义，派生值只通过 getter 或 runtime context 计算。

## 5. Phase 4：JEI 集成

**目标**：移植 MMCE 的 JEI 动态机器配方展示与基础配方转移。先做 recipe category，不做 3D 结构预览。

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `CategoryDynamicRecipe` | `compat.jei.MachineRecipeCategory` | 重映射，NeoForge/JEI 29 API |
| `DynamicRecipeWrapper` | wrapper 或直接 `MachineRecipe` | 重映射 |
| `RecipeLayoutHelper` / `RecipeLayoutPart` | recipe layout builder helpers | 重映射，按 JEI 29 slot API 写 |
| `JEIComponentItem/Fluid/Energy` | item/fluid/energy display adapters | 直译简化 |
| `CategoryStructurePreview` | Phase 7 | 延后 |
| JEI mixins | 不移植 | 删除 |

**验收**：

- 每台 machine 至少有一个 JEI category 或按 machine 分组显示。
- item/fluid/energy 输入输出显示正确，duration 和 FE/t 显示正确。
- JEI recipe transfer 能把物品输入移动到 item input bus；fluid/energy transfer 不做或只显示原因。
- 未安装 JEI 时 MMCR 可正常加载。

## 6. Phase 5：并行与工厂控制器

**目标**：移植 MMCE 的并行执行能力，但先实现可验证的单控制器并行，再做 factory 多线程。

### 6.1 Parallel Controller

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `TileParallelController` | `ParallelControllerBlockEntity` | 直译简化 |
| `BlockParallelController` | `ParallelControllerBlock` | 直译简化 |
| `ComponentParallelController` | component type | 直译简化 |
| parallel levels 4/16/64/256/512 | `ParallelTier` | 直译 |
| recipe `parallelized` | `MachineRecipe.parallelized` | 直译 |

**验收**：

- 无 parallel controller 时 parallelism 恒为 1。
- 有 parallel controller 且 recipe 允许并行时，输入/输出/energy 按 parallelism 成倍模拟和提交。
- 输出空间不足时不启动并行 recipe。
- GUI/日志显示当前 parallelism。

### 6.2 Factory Controller / 多线程

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `TileFactoryController` | `FactoryControllerBlockEntity` | 延后到 parallel 稳定后 |
| `FactoryRecipeThread` | factory recipe scheduler | 重映射 |
| `TaskExecutor` | Java executor 或 server tick task queue | 重映射，不能破坏 MC 主线程安全 |
| `RecipeCraftingContextPool` | context pool | 延后，只有性能需要时做 |

**验收**：

- 不在异步线程直接读写 world/BE。
- recipe 搜索可以异步预计算，但 IO commit 必须回到 server tick。
- 工厂 controller 能管理多个 recipe thread，且卸载/破坏结构时安全停止。

## 7. Phase 6：Smart Interface / 算力系统

**目标**：移植 MMCE 的智能接口数值输入，用于 recipe requirement 中的 `interface_number`。

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `TileSmartInterface` | `SmartInterfaceBlockEntity` | 直译简化 |
| `BlockSmartInterface` | `SmartInterfaceBlock` | 直译简化 |
| `SmartInterfaceType` | `SmartInterfaceType` | 直译，首期只做 NUMBER |
| `SmartInterfaceData` | `SmartInterfaceData` | 直译简化 |
| `RequirementInterfaceNumInput` | number requirement | 直译 |
| smart interface packets | menu data sync / custom payload | 重映射 |

**验收**：

- smart interface 可在 GUI 中设置数字。
- recipe 可要求某个数字输入达到阈值。
- selector tag 可限定 recipe 读取哪个 smart interface。
- 值持久化并能随 block entity NBT roundtrip。

## 8. Phase 7：升级、蓝图、结构预览、自动组装

**目标**：补 MMCE 的玩家体验层。这个阶段可以拆成多个独立 spec，不应一次做完。

### 8.1 Upgrade 系统

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `MachineUpgrade` | `MachineUpgrade` | 直译简化 |
| `DynamicMachineUpgrade` | KubeJS/Java API upgrade definition | 重映射 |
| `UpgradeBus` / `TileUpgradeBus` | `UpgradeBusBlockEntity` | 直译简化 |
| `UpgradeType` | `UpgradeType` | 直译 |
| upgrade modifier | recipe modifier hook | 重映射 |

**验收**：

- upgrade bus 可插入升级物品。
- upgrade 能影响 duration、energy、parallelism 至少一类数值。
- 未插升级时行为完全等同 Phase 6。

### 8.2 Blueprint / 结构预览

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `ItemBlueprint` | `BlueprintItem` | 直译简化 |
| `ItemConstructTool` / `ItemDebugStruct` | debug/build tooling | 重映射，已有 debug wrench 可复用 |
| `MachineStructurePreviewPanel` | GUI structure preview | 重映射，NeoForge/vanilla rendering |
| `WorldSceneRenderer` / cleanroommc preview renderer | preview renderer | 延后/重评估 |
| GeckoLib controller model preview | 不移植 | 删除 |

**验收**：

- 玩家能拿到 blueprint 并查看机器结构层级。
- GUI 能显示 required blocks/components 列表；3D 预览可以后置。
- 结构预览不引入 GeckoLib/Lumenized。

### 8.3 自动组装 / 投影器

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| ikx `MachineAssembly` | `MachineAssembly` | 直译简化 |
| `AssemblyEventHandler` | server event handler | 重映射 |
| `MachineProjector` | projector item | 直译简化 |
| `StructurePreviewHelper` | preview helper | 重映射 |

**验收**：

- 自动组装只消耗玩家/容器中真实方块，不生成免费方块。
- 组装失败能指出缺少的 block/item。
- 投影器只显示 ghost，不改变世界。

## 9. Phase 8：第三方联动

**目标**：核心稳定后按用户实际整合包需求选择移植，不预设全部做。

### 9.1 AE2 / ME 系列

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `MEItemInputBus` / `MEItemOutputBus` | AE2 item bus | 重映射到 AE2 26.x API |
| `MEFluidInputBus` / `MEFluidOutputBus` | AE2 fluid bus | 重映射 |
| `MEPatternProvider` | pattern provider | 重映射，需先查 AE2 当前 API 示例 |
| AE2 mixins | 尽量不移植 | 删除，确需 GUI 接入时另开 spec |

**验收**：

- 未安装 AE2 时不加载 AE2 类。
- 安装 AE2 时 ME bus 可作为 recipe component 参与 simulate/commit。
- pattern provider 能把 AE2 pattern 转成 MMCR recipe request。

### 9.2 Mekanism Gas

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `RequirementGas` | gas requirement | 重映射到 Mekanism chemical API |
| `ComponentGas` | gas hatch component | 重映射 |
| `MEGasInputBus` / `MEGasOutputBus` | 只在 AE2 + Mekanism 同时需要时做 | 延后 |

**验收**：

- 未安装 Mekanism 时不加载 Mekanism 类。
- gas input/output 能像 fluid 一样 simulate/commit。

### 9.3 GTCeu / ModularMagic / Jade

| MMCE 来源 | MMCR 目标 | 移植方式 |
|---|---|---|
| `MachineComponentProxy` / GTCeu integration | proxy component adapter | 重映射 |
| kport ModularMagic requirements/components | Botania/BloodMagic/Astral/etc. requirements | 按实际 mod 逐个 spec |
| TOP provider | Jade provider | 重映射到 Jade |

**验收**：

- 每个联动都是可选模块，不成为 MMCR 硬依赖。
- 每个联动至少有一个 E2E 验证或手动验证说明。

## 10. 永久不移植

- Forge 1.12.2 lifecycle/proxy：`CommonProxy` / `ClientProxy` 作为生命周期容器不移植，已由 NeoForge mod bus/client event 替代。
- `GameRegistry` / `InternalRegistryPrimer` / Forge registry wrapper：不移植，使用 DeferredRegister。
- 双阶段 GSON loader、`MachineLoader.discoverDirectory`、变量 JSON：不移植，使用 Codec/datapack/KubeJS/Java API。
- CraftTweaker/ZenScript 集成：不移植，KubeJS 是替代入口。
- 旧 `SimpleNetworkWrapper` 15 个 packet：不逐个移植，按当前功能用 NeoForge `CustomPacketPayload` 重映射。
- GeckoLib / Lumenized / Bloom controller renderer：不移植，除非未来明确改目标为复刻 MMCE 视觉效果。
- MMCE 针对 AE2/JEI/GeckoLib 的旧 mixin：不移植；遇到 NeoForge/API 限制时重新写最小 mixin。
- Recipe Adapter 旧外部机器桥接（IC2/NCO/TC6/TConstruct/TE5）：默认不移植；若某整合包确需，作为 Phase 8 单独重评估。

## 11. 建议实施顺序

1. **立即收尾 Phase 1**：修复 recipe codec 测试、完成 pattern component context、per-tick energy、fluid output、failure action。
2. **Phase 2 Requirement/Component**：让 item/fluid/energy 路由和错误反馈稳定，避免后续 JEI/并行建立在简化模型上。
3. **Phase 3 Modifier 全链**：补齐 duration/input/output/chance 的原始值和派生值边界。
4. **Phase 4 JEI**：做 recipe category、显示、基础 item transfer。
5. **Phase 5 Parallel**：先 parallel controller，再 factory controller 和异步搜索。
6. **Phase 6 Smart Interface**：补 `interface_number` 和数值接口。
7. **Phase 7 UX**：upgrade、blueprint、preview、auto assembly、projector 拆开做。
8. **Phase 8 Optional Integrations**：AE2 优先，其次 Mekanism/Jade/GTCeu/ModularMagic，按实际需求逐个移植。

## 12. 每阶段通用验收门槛

- `./gradlew compileJava --no-daemon` 通过。
- `./gradlew test --no-daemon` 通过；如果某阶段因 GameTest 环境限制无法跑完整测试，需要记录未跑原因。
- 涉及 block/entity/registry/resource 时检查 datagen 输出、lang、blockstate、model、loot/table 或 creative tab 引用。
- 涉及 optional integration 时验证未安装该 mod 也能启动/编译，不出现 eager class loading。
- 每阶段完成后更新本文件的当前基线，避免路线图和实际进度脱节。
