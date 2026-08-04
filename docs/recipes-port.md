# Recipes 移植范围（dev/neo/26.1.2 → 核心运行时 + Modifier）

> 本文对应 `docs/scope.md` 中「配方系统（`MachineRecipe` / `RecipeRegistry` / `RecipeThread`）」的**第一阶段**细化。
> 决策摘要：只 port 核心运行时 + RecipeModifier 系统；其他 recipe 相关子系统（requirement 完整链、adapter、CraftTweaker、JEI、TOP、event、command、线程调度）暂不 port。
>
> 与既有文档关系：
> - `docs/MMCE.md §6 配方系统` —— MMCE 1.12.2 全量参考。
> - `docs/MMCE.md §7 需求类型` / `§8 组件类型` / `§9 Modifier` / `§15 并发与执行` / `§16 事件系统` —— 本文涉及但只摘出首期要做的。
> - `docs/scope.md` —— 首期全模块范围（机器、结构、配方、方块 tile），本文只谈「配方」这一块。

---

## 0. 决策摘要

| 维度 | MMCE 1.12.2 | MMCR 26.1.2（本轮） |
|---|---|---|
| `MachineRecipe` 形态 | 22.2K 巨型类 + 解析/序列化/优先级/线程引用 | **重构现有 record** → 完整类（带 `ioModifier`/`tickTimeModifier`/modifier 链 + `PreparedRecipe` 引用） |
| 需求表达 | `RequirementItem/Fluid/Energy/Gas/Catalyst/IngredientArray/...` + `ComponentRequirement` + `JEIComponent*` | **占位**：`MachineIngredient` sealed interface（item / fluid / energy）+ 三个 record；后续 Requirement 系统在 Phase 2 再扩 |
| Modifier | `RecipeModifier` + 4 个 Replacement + `ModifierRegistry` | **一起 port** —— 6 个文件，支持 input/output/chance/duration 的 OPERATION_ADD/MULTIPLY/SUBTRACT/DIVIDE |
| 执行调度 | `MachineRecipeThread` / `FactoryRecipeThread` / `RecipeSearchTask` / `RecipeCraftingContextPool` | **不做** —— 本轮不实现 tick loop，只交付数据类 + 注册表。tick 调度放 Phase 3 |
| 事件 | 12 个 RecipeEvent（Start/Finish/Tick/Check/Failure/...） | **不做** —— 与执行调度一并留 Phase 3 |
| Recipe Adapter | 17 个（含 ic2/nco/tc6/tconstruct/te5） | **不做** —— 零第三方 mod 依赖 |
| CraftTweaker | ~25 个文件（Builder/Primer/Generator/Helper/Command/Event） | **不做** —— 已用 KubeJS 替代（`compat/kubejs/`） |
| JEI 集成 | 4 个 + 8 个 JEIComponent | **不做** —— JEI 留 Phase 2 |
| TOP 集成 | 1 个 | **不做** |
| Recipe Command | 3 个（`/mm recipe ...`） | **不做** —— 命令系统统一留 Phase 5 |

---

## 1. 本轮 In Scope（要 port / 要新建）

### 1.1 重构现有 4 个文件

`src/main/java/cn/howxu/mmcr/api/recipe/`：

| 文件 | 现状 | 本轮动作 |
|---|---|---|
| `MachineRecipe.java` | 74 行 record，5 字段 | **删除 record**，改为完整类：保留 codec 序列化、添加 modifier 链（`List<RecipeModifier>`）、active recipe 引用、`getConfiguredPriority()`、`getMaxThreads()` 等运行时元数据 |
| `MachineRecipeSerializer.java` | 24 行 JSON + StreamCodec | 跟着 `MachineRecipe` 改 codec 字段，保持现有 `StreamCodec` 接口（sync 协议不变） |
| `MachineIngredient.java` | 80 行 sealed interface，item/fluid/energy | **保留**，但增加 `BaseComponent` 引用 + 抽取公共 `codify()` 工具（与 mmce `Requirement` 的工具类对齐） |
| `RecipeRegistry.java` | 33 行单 Map | **重写**：参照 mmce 形态 `Map<Identifier, MachineRecipe>` + `Map<Identifier, TreeMap<Integer, TreeSet<MachineRecipe>>>`（按 machine + priority 索引）；保留 `register/getRecipesFor/clearAll` |

### 1.2 新建文件清单（按 mmce 同名同路径的简化版）

```
src/main/java/cn/howxu/mmcr/api/recipe/
├── MachineRecipe.java              # 重构（见 1.1）
├── MachineRecipeSerializer.java    # 重构（见 1.1）
├── MachineIngredient.java          # 保留扩展
├── RecipeRegistry.java             # 重构
├── ActiveMachineRecipe.java        # 新建：运行时活跃配方状态机（IDLE/CHECKING/RUNNING/DONE/FAILED）
├── PreparedRecipe.java             # 新建：KubeJS/JSON 注册前的预制形态
├── ComponentType.java              # 新建：ComponentType 注册表（与 mmce 1.12.2 同名）
├── IntegrationTypeHelper.java      # 新建：整合 ItemStack/FluidStack/Integer 的辅助
├── helper/
│   ├── CraftCheck.java             # 新建：tick 时检查输入是否满足
│   ├── CraftingStatus.java         # 新建：枚举 IDLE/CHECKING/RUNNING/DONE/FAILED
│   ├── ProcessingComponent.java    # 新建：组件在配方中的处理状态
│   ├── RequirementComponents.java  # 新建：按 io 方向（input/output）聚合 requirements
│   └── ComponentOutputRestrictor.java # 新建：输出限流（占位，Phase 3 才有用）
└── modifier/
    ├── RecipeModifier.java         # 新建：modifier 链（OPERATION_ADD/MULTIPLY/SUBTRACT/DIVIDE）
    ├── ModifierRegistry.java       # 新建：modifier 注册表
    ├── AbstractModifierReplacement.java   # 新建：抽象基类
    ├── SingleBlockModifierReplacement.java  # 新建：单方块 modifier（I/O 速度）
    ├── MultiBlockModifierReplacement.java   # 新建：多方块 modifier（并行/全局倍率）
    └── DynamicModifierReplacement.java # 新建：动态 modifier（运行时计算）
```

**预计 16 个新文件 + 4 个重构 = 20 个文件。** 估计代码量 1500-2200 行（含 codec 序列化样板）。

### 1.3 与 `compat/kubejs/` 的边界

KubeJS 端的 `MachineRecipeBuilderJS` / `MachineRecipeSchema` 仍按当前形态工作；本轮 port 不动 KubeJS 侧，但 `PreparedRecipe` 内部会暴露 `KubeJS` 友好的 setter（`withModifier(...)` / `withDuration(int)` / `withPriority(int)`），KubeJS builder 后续按需接。

---

## 2. 本轮 OUT of Scope（不 port）

> **本节是用户明确要求记录的内容**——把 mmce 1.12.2 recipe 周边但本轮不动的全部列入。

### 2.1 完整 Requirement 系统（60+ 文件）

MMCE 1.12.2 的 `common/crafting/requirement/` + `requirement/type/` + `requirement/jei/`：

- ❌ `RequirementItem` / `RequirementFluid` / `RequirementEnergy` / `RequirementGas` / `RequirementCatalyst` / `RequirementIngredientArray` / `RequirementItemDurability` / `RequirementFluidPerTick` / `RequirementGasPerTick` / `RequirementInterfaceNumInput`（10 个）
- ❌ 对应 `RequirementType*` 注册表（10 个）
- ❌ `JEIComponent*` 8 个（仅 JEI 显示用）
- ❌ `RequirementDuration`（配方时长 modifier）—— **本轮由 `MachineRecipe.duration` 字段直接表达**，不抽象成 Requirement

**为什么不 port**：当前项目 `MachineIngredient` 已能表达 item/fluid/energy 输入；Requirement 链主要是 mmce 1.12.2 时代为了支持自定义 mod 物品/耐久度/概率/可选性而设计的，26.1.2 + NeoForge `Ingredient` + `DataComponent` 体系下可以走更轻量路线（Phase 2 评估）。

**遗留风险**：
- 当前 `MachineIngredient.EnergyIngredient(fePerTick)` 是简化版，mmce 是「每 tick 消耗固定 FE + 总消耗 = fePerTick × tickTime」。本轮保持每 tick 语义（实际扣减由 Phase 3 的 tick 调度实现）。
- 无 NBT 匹配 / 耐久匹配 / 概率匹配——后续 Phase 2 评估是否走 NeoForge `DataComponentPredicate`。

### 2.2 Component 系统（8 文件）

`common/crafting/component/`：

- ❌ `ComponentItem` / `ComponentFluid` / `ComponentEnergy` / `ComponentGas` / `ComponentItemFluid` / `ComponentParallelController` / `ComponentSmartInterface` / `ComponentUpgradeBus`

**为什么不 port**：当前项目 tile 直接暴露 NeoForge `ItemHandler` / `FluidHandler` / `EnergyStorage` capability（见 `docs/scope.md §1.3`），不需要在 `MachineRecipe` 侧做 component 路由。`ComponentType` 保留为空注册表（占位）。

### 2.3 Recipe Adapter 系统（17 文件）

`common/crafting/adapter/` + 子包（ic2 / nco / tc6 / tconstruct / te5）：

- ❌ `RecipeAdapter` / `RecipeAdapterAccessor` / `RecipeAdapterRegistry` / `DynamicMachineRecipeAdapter`
- ❌ `AdapterMinecraftFurnace` / `AdapterIC2Compressor` / `AdapterIC2Macerator` / `AdapterNCOAlloyFurnace` / `AdapterNCOChemicalReactor` / `AdapterNCOInfuser` / `AdapterNCOMelter` / `AdapterTC6InfusionMatrix` / `AdapterSmelteryAlloyRecipe` / `AdapterSmelteryMeltingRecipe` / `InsolatorRecipeAdapter` 等 12 个外部 mod 适配

**为什么不 port**：与 `docs/scope.md §2.1` 第三方 mod 联动 OUT 一致——本项目零深度依赖，配方完全由 datapack JSON / KubeJS / Java API 三入口注册。

### 2.4 执行调度与多线程（5 文件）

`common/machine/` + `common/concurrent/` + `kasuminova/.../concurrent/`：

- ❌ `MachineRecipeThread` / `FactoryRecipeThread` / `RecipeThread` / `RecipeFailureActions`
- ❌ `RecipeSearchTask` / `FactoryRecipeSearchTask` / `RecipeCraftingContextPool`
- ❌ `TaskExecutor` fork/join 线程池 / `SequentialTaskExecutor` / `Sync`

**为什么不 port**：本轮只交付数据类 + 注册表，不实现 tick 调度。`ActiveMachineRecipe` 类只定义状态机字段，**不实现 `tick()`**——具体 tick loop 放 Phase 3（`docs/scope.md §7 Phase 3`）。

**遗留风险**：
- `MachineRecipe.getMaxThreads()` 方法本轮返回 1（恒单线程）。
- 没有 `RecipeSearchTask` 意味着配方查找走 O(n) 遍历 `RecipeRegistry.getRecipesFor(machine)`，配方数 < 50 时性能可接受；超过后 Phase 3 再优化。

### 2.5 事件系统（12 文件）

`kasuminova/.../event/recipe/`：

- ❌ `RecipeEvent` / `RecipeStartEvent` / `RecipeFinishEvent` / `RecipeTickEvent` / `RecipeCheckEvent` / `RecipeFailureEvent`
- ❌ `FactoryRecipeEvent` / `FactoryRecipeStartEvent` / `FactoryRecipeTickEvent` / `FactoryRecipeFailureEvent` / `FactoryRecipeFinishEvent`
- ❌ 各自 Factory 类

**为什么不 port**：事件依附于 tick 调度，调度 OUT 则事件 OUT。`MachineEvent` 系统本轮在 `internal/event/` 仅做占位接口预留，**不**出实现。

### 2.6 CraftTweaker 集成（~25 文件）

`common/integration/crafttweaker/` 全包：

- ❌ `MachineBuilder` / `RecipeBuilder` / `RecipePrimer` / `RecipeAdapterBuilder` / `RecipeModifierBuilder` / `BlockArrayBuilder` / `MachineModifier` / `MachineUpgradeBuilder` / `DynamicMachineUpgradeBuilder` / `MultiBlockModifierBuilder` / `StatedMachineComponentBuilder` / `IngredientArrayBuilder` / `IngredientArrayPrimer`
- ❌ `ModIntegrationCrafttweaker` 入口
- ❌ `command/`、`event/`、`generator/`、`helper/`、`model/`、`upgrade/` 子包

**为什么不 port**：与 `docs/scope.md §2.1` 一致——已用 KubeJS 替代。

### 2.7 JEI 集成（4 + 8 文件）

`common/integration/recipe/` + `common/crafting/requirement/jei/`：

- ❌ `CategoryDynamicRecipe` / `DynamicRecipeWrapper` / `RecipeLayoutHelper` / `RecipeLayoutPart`
- ❌ 8 个 `JEIComponent*`（与 §2.1 重复列出）

**为什么不 port**：与 `docs/scope.md §7 Phase 2` 一起做。本轮不写 `IRecipeCategory<MachineRecipe>`。

### 2.8 TheOneProbe 集成（1 文件）

`common/integration/theoneprobe/MMInfoProvider.java`：

- ❌ 不 port

**为什么不 port**：与 `docs/scope.md §2.1` 一致——零第三方 mod 深度依赖，TOP 集成可后续 Phase 6 评估。

### 2.9 Recipe Command（3 文件）

`common/crafting/command/`：

- ❌ `RecipeCommandContainer` / `RecipeRunnableCommand` / `ControllerCommandSender`

**为什么不 port**：命令系统的 recipe 入口与 Recipe Adapter 强耦合（`/mm recipe adapter` 之类）。Adapter OUT → Recipe Command OUT。本轮 `internal/command/` 只有 `BuildCommand` + `ReloadCommand`，不带 recipe 子命令。

### 2.10 Helper 中尚未 port 的（2 文件）

`common/crafting/helper/` + `tooltip/`：

- ❌ `ComponentSelectorTag`（按 NBT tag 选组件）—— mmce 时代兼容机制，26.1.2 无对应
- ❌ `ComponentOutputRestrictor` —— 本轮**保留空类占位**（签名在，方法体 throw），Phase 3 tick 调度用
- ❌ `tooltip/RequirementTip.java` + `TooltipEnergyInput.java` + `TooltipEnergyOutput.java` + `TooltipFuelInput.java` + `TooltipInterfaceNumberInput.java` —— 5 个 tooltip 文件

**为什么不 port tooltip**：当前项目 GUI 还在 `MMCRMenuScreen` 占位阶段，recipe 内的 tooltip 渲染无宿主。等 GUI 完整化（Phase 5）再补。

### 2.11 lib / registry 包装（5 文件）

`common/lib/RecipeAdaptersMM.java` / `common/lib/RequirementTypesMM.java` / `common/lib/RequirementTipsMM.java` / `common/lib/ComponentTypesMM.java` / `common/registry/Registry*` 4 个：

- ❌ 不 port

**为什么不 port**：这些是 mmce 1.12.2 Forge `GameRegistry` 时代的注册包装；26.1.2 用 `DeferredRegister`，**不**需要中间层静态字段。

---

## 3. 现有 KubeJS 侧的处理

`src/main/java/cn/howxu/mmcr/compat/kubejs/`：

| 文件 | 现状 | 本轮处理 |
|---|---|---|
| `MachineRecipeBuilderJS.java` | 2.6K builder | **不动**；本轮 `MachineRecipe` 重构后 builder 端编译可能 break，由 builder 自身改一行 setter 即可 |
| `MachineRecipeSchema.java` | 3.3K schema | **不动**；同上 |
| `MachineRecipeFactory.java` | 406B factory | **不动** |
| `Plugin.java` | 1.2K plugin | **不动** |
| `MachineBuilderJS.java` | 5.5K | **不动**（与 recipe 无关） |

**如果 KubeJS builder 编译失败**（预计 1-3 处 setter 改名），本轮一并修；不引入新 API。

---

## 4. 资源与默认配方

- ❌ `reference/mmce/.../default_recipes/power_transformer_energy_transform.json` —— 不复制（依赖 mmce 1.12.2 的电力机器；本项目首期机器清单不含 power_transformer）
- ✅ `data/<modid>/recipe/<id>.json` datapack 路径——已支持，由 `MachineRecipeSerializer.CODEC` 走 NeoForge 标准 datapack 加载

---

## 5. 工作量与节奏

| 任务 | 估计行数 | 文件数 | 验证手段 |
|---|---|---:|---|
| 现有 4 文件重构 | 300-500 | 4 | `./gradlew compileJava` |
| 核心运行时 7 类（`ActiveMachineRecipe` 等） | 500-700 | 7 | 同上 |
| helper 5 类 | 200-300 | 5 | 同上 |
| Modifier 6 类 | 400-600 | 6 | 同上 |
| 验证 + 编译 | — | — | `./gradlew compileJava` + `./gradlew build` |
| **合计** | **~1500-2200** | **22** | — |

按 AGENTS.md「先完成整体，再调整测试」，本轮不写新测试；如 `MachineRecipe` 重构破坏 KubeJS builder，由 builder 端补 1-3 行 setter 适配。

---

## 6. 验证清单（编译通过为最低标准）

1. `./gradlew compileJava` —— 重构后无编译错误
2. `./gradlew build` —— 完整构建通过
3. 启动 dev 环境（`./gradlew runClient`）—— 加载 0 个 datapack recipe 不报错
4. KubeJS builder 端（`MachineRecipeBuilderJS`）编译通过——如 break 按 §3 处理
5. `RecipeRegistry.register / byMachine / clearAll` 行为与现有 record 形态一致（保留公共方法签名）

---

## 7. 未来阶段 TODO（按依赖顺序预排）

按 `docs/scope.md §7` 框架补全 recipe 周边：

### Phase 2 — 完整 Requirement 系统

1. `RequirementType<ItemStack>` / `RequirementType<FluidStack>` / `RequirementType<Integer>` 三类注册表
2. `RequirementItem`（含 `Ingredient` + `count` + 可选 NBT）+ `JEIComponentItem`
3. `RequirementFluid` + `JEIComponentFluid`
4. `RequirementEnergy`（perTick + duration） + `JEIComponentEnergy`
5. `RequirementDuration`（配方时长 modifier 化）

### Phase 3 — 执行 + 事件

6. `MachineRecipeThread`（单 thread） + `ActiveMachineRecipe.tick(Level)`
7. `RecipeSearchTask`（单配方查找）+ `RecipeCraftingContext`
8. 12 个 RecipeEvent 实现 + `internal/event/` 入口

### Phase 4 — Modifier 扩展

9. `OPERATION_SUBTRACT` / `OPERATION_DIVIDE` 真支持
10. `RecipeModifier` 影响 output / chance / duration
11. `DynamicModifierReplacement` 接到 upgrade bus（与 Phase 5 联动）

### Phase 5 — UI + Tooltip

12. `RequirementTip` 5 个 tooltip
13. Recipe 进度在 controller GUI 显示

### Phase 6 — 第三方联动（按需选做）

14. 12 个外部 mod Adapter
15. CraftTweaker 兼容层（可选——本项目优先级低）

---

## 8. 一句话目标

> 在 NeoForge 26.1.2 上交付完整的 `MachineRecipe` 数据类 + 注册表 + Modifier 系统，能让 KubeJS / datapack JSON 两种入口注册的配方**结构正确、modifier 链正确**，但**不**真的让机器跑起来（tick 调度 OUT）。跑通编译与 unit-level 数据正确性后，再谈 Phase 3 的 tick 调度。
