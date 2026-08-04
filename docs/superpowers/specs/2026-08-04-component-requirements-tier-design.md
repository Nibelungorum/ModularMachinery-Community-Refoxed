# MMCR 组件需求与接口等级设计

日期：2026-08-04

## 1. 背景

MMCR 当前的控制器检测已经按 `multiblock-detection-design.md` 阶段 A 升级到 MMCE 风格的控制器状态机：`MachineControllerBlockEntity` 内保存 `foundMachine` / `foundPattern` / `controllerFacing`，每 tick 复验已成型结构，必要时扫描注册表候选机器。IO 端口也已经按 `basic-io-ports-design.md` 拆成六个固定方向的实体：item input/output bus、fluid input/output hatch、energy input/output hatch。

但目前机器对"组件数量"和"接口等级"的约束是隐式的：

- **组件数量**：完全由 pattern 决定。Pattern 里放几个 input bus，结构里就只有几个；pattern 里不写 fluid input hatch，配方需要流体输入时只能静默失败。
- **接口等级**：所有 `ItemBusBlockEntity` / `FluidHatchBlockEntity` 通过构造函数硬编码容量（`new ItemStackHandler(6)` / `new FluidTank(8000)` / `EnergyStorage(100000, ...)`），没有 tier 抽象。MMCE 提供七级 `ItemBusSize` / `FluidHatchSize` / `EnergyHatchData`（TINY/SMALL/NORMAL/REINFORCED/BIG/HUGE/LUDICROUS），并且控制器可以通过 `updateComponents` 阶段识别每个端口的 tier。

本 spec 引入机器级别的「组件需求」和「接口等级需求」两个抽象，为后续多 tier 端口实现、调试提示、JEI 工具显示、机器配方适用性检查留出接口边界。本文档只规定 API 形态和状态机扩展，**不实现 tier 实体和实际数量校验**。

## 2. 目标

- 在 `Machine` sealed interface 上新增两个只读方法：`componentRequirements()` 和 `minInterfaceTier()`，默认实现为零需求 / 全部 NORMAL，使现有机器在能力上完全等价。
- 引入 `ComponentRequirements` 记录：描述六项基本 IO 组件在结构中的最低数量，按 `IOPortKind.id()` 索引。
- 引入 `ComponentTierRequirements` 记录和 `InterfaceTier` 枚举：TINY/SMALL/NORMAL/REINFORCED/BIG/HUGE/LUDICROUS 七级，对齐 mmce ItemBusSize / FluidHatchSize / EnergyHatchData。
- 扩展 `MachineControllerBlockEntity` 结构状态机为四态：`UNFORMED` / `FORMED_MISSING_COMPONENT` / `FORMED_TIER_TOO_LOW` / `FORMED`。
- 实施阶段 B / C 实施 ComponentRequirements 数量校验和 ComponentTierRequirements 等级校验时不需要再改 API。

## 3. 非目标

- 不实现 tier 实体方块（每 tier 一套 Block / BlockEntity / 注册 / 资源）。
- 不实现 `MachineComponent` / `ProcessingComponent` 容器包装（已规划在 `multiblock-component-context-design.md`，该文档内同样以 API 阶段 / 实施阶段编号）。
- 不实现 `ComponentSelectorTag` / `TaggedPositionBlockArray` 标签系统。
- 不实现机器 ↔ tier 兼容性检查之外的 JEI 高亮 / 调试 tooltip 渲染。
- 不实现 `DynamicPattern` 可变长度结构。
- 不实现 `Machine.failureAction()` 之外的机器级拒绝策略细化。
- 不修改 `BlockArray` 或 `BlockPredicate` 内部数据结构。
- 不做旧 API 兼容——`Machine` 之前没有 `componentRequirements()` / `minInterfaceTier()` 方法，新增 default 实现对子类无影响。

## 4. MMCE 对应位置

### 4.1 组件数量隐式 vs 显式

MMCE 并未提供机器级「至少 N 个 input bus」API；该约束隐式由 pattern 中放置的 IO 位置数量决定。MMCR 显式声明组件需求的价值在于：

- **明确的语义**：机器定义直接说明 does this machine need 1 item input bus + 1 item output bus + 1 energy input hatch?
- **调试 UI**：debug wrench / JEI 描述可直接显示 `minItemInput=1, minItemOutput=1, minEnergyInput=2, minFluidInput=0`。
- **机器复用**：相同 pattern 不同机器可以声明不同的最小数量。
- **未来接口稳定性**：即使后续 selector tag、组件路由、并行化机制叠加，组件需求接口不必再改。

### 4.2 接口等级来源

MMCE 中：

- `ItemBusSize`（7 级）定义在 `reference/mmce/src/main/java/hellfirepvp/modularmachinery/common/block/prop/ItemBusSize.java`。
- `FluidHatchSize`（8 级，含 VACUUM）定义在 `FluidHatchSize.java`。
- `EnergyHatchData`（8 级，含 ULTIMATE）定义在 `EnergyHatchData.java`。

MMCR 选择 7 级（TINY/SMALL/NORMAL/REINFORCED/BIG/HUGE/LUDICROUS），与 mmce 的 `ItemBusSize` 对齐；流体和能量使用同一套 7 级枚举，容量 / 传输速率映射在后续 spec 中定义。

### 4.3 控制器状态机

MMCE 在 `TileMultiblockMachineController.checkStructure()` 中按 pattern 匹配 + 蓝图 + 父机器 + 注册表搜索顺序检查，结构状态通过 `CraftingStatus` 枚举（MISSING_STRUCTURE / CHUNK_UNLOADED / WORKING ...）驱动 GUI 提示。

MMCR 已落地 `multiblock-detection-design.md` 阶段 A 的控制器状态化，`isFormed()` 二态已能满足 MVP。本 spec 把二态扩为四态，对应「结构匹配 / 组件数量 / 组件等级 / 完全 ready」四种含义。

## 5. 目标架构

### 5.1 新类型

```java
// src/main/java/cn/howxu/mmcr/api/machine/InterfaceTier.java
public enum InterfaceTier {
    TINY(0),
    SMALL(1),
    NORMAL(2),
    REINFORCED(3),
    BIG(4),
    HUGE(5),
    LUDICROUS(6);

    private final int level;
    public int level() { return level; }
    public boolean atLeast(InterfaceTier other) { return this.level >= other.level; }
}
```

```java
// src/main/java/cn/howxu/mmcr/api/machine/ComponentRequirements.java
public record ComponentRequirements(
        Map<String, Integer> minimums) {

    public static ComponentRequirements empty() {
        return new ComponentRequirements(Map.of());
    }

    public int getMinimum(String kindId) {
        return minimums.getOrDefault(kindId, 0);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<String, Integer> minimums = new LinkedHashMap<>();

        public Builder require(String kindId, int count) {
            if (count < 0) throw new IllegalArgumentException("count must be >= 0 for " + kindId);
            if (count > 0) minimums.put(kindId, count);
            return this;
        }

        public ComponentRequirements build() {
            return new ComponentRequirements(Map.copyOf(minimums));
        }
    }
}
```

索引键取 `IOPortKind.id()` 字符串（"item_input_bus" / "item_output_bus" / "fluid_input_hatch" / "fluid_output_hatch" / "energy_input_hatch" / "energy_output_hatch"），避免 `Machine` 接口对 `IOPortKind` 类型的反向依赖。`Machine` 位于 `api/machine` 包，`IOPortKind` 位于 `internal/port`，反向依赖会破坏单向包结构。

```java
// src/main/java/cn/howxu/mmcr/api/machine/ComponentTierRequirements.java
public record ComponentTierRequirements(
        Map<String, InterfaceTier> minimums) {

    public static ComponentTierRequirements defaults() {
        return new ComponentTierRequirements(Map.of(
                "item_input_bus",   InterfaceTier.NORMAL,
                "item_output_bus",  InterfaceTier.NORMAL,
                "fluid_input_hatch", InterfaceTier.NORMAL,
                "fluid_output_hatch", InterfaceTier.NORMAL,
                "energy_input_hatch", InterfaceTier.NORMAL,
                "energy_output_hatch", InterfaceTier.NORMAL));
    }

    public InterfaceTier getMinimum(String kindId) {
        return minimums.getOrDefault(kindId, InterfaceTier.NORMAL);
    }
}
```

`defaults()` 使 `Machine.minInterfaceTier()` 默认实现返回的 NORMAL 对所有 kindID 是 no-op，不会产生隐式降级。

### 5.2 Machine 接口扩展

```java
// src/main/java/cn/howxu/mmcr/api/machine/Machine.java
public sealed interface Machine permits DynamicMachine {
    Identifier registryName();
    String localizedName();
    BlockArray pattern();
    MachineControllerSpec controller();

    default ComponentRequirements componentRequirements() {
        return ComponentRequirements.empty();
    }

    default ComponentTierRequirements minInterfaceTier() {
        return ComponentTierRequirements.defaults();
    }
}
```

`DynamicMachine` 暂不增加 record 字段，本 spec 阶段只依赖默认实现；实施阶段 B 增加 `ComponentRequirements` 字段与 builder 链式 API。如需后续 KubeJS 暴露，新字段按 DynamicMachine 既有惯例（先 default 后再 record 字段）逐步引入。

### 5.3 结构状态机扩展

```java
// src/main/java/cn/howxu/mmcr/api/machine/MachineStructureStatus.java
public enum MachineStructureStatus {
    UNFORMED,
    FORMED_MISSING_COMPONENT,
    FORMED_TIER_TOO_LOW,
    FORMED;

    public boolean isFormed() { return this == FORMED; }
}
```

`MachineControllerBlockEntity` 引入 `status` 字段：

```java
public MachineStructureStatus getStatus() { return status; }
public boolean isFormed() { return status == MachineStructureStatus.FORMED; }
public String getStatusReason() { return null; }  // 实施阶段 B 写，可选
```

`MachineControllerBlock.FORMED` 保留现有布尔语义（仅在 `status == FORMED` 时为 true），向后兼容现有 `MachineMenuScreen` / 客户端逻辑。新增 `MachineControllerBlock.STATUS` 枚举属性不在本 spec 阶段引入，避免在一份 spec 文档内同时改动方块属性与 API；GUI 区分的实施阶段再决定 BlockState 扩展。

### 5.4 实施路径

注意：以下命名沿用 `multiblock-detection-design.md` 的阶段 A 描述，但内部阶段仍按本 spec 独立编号——**API 阶段**对应本 spec 文档落地，**实施阶段 B / C / D** 是后续 spec 范围。

```
阶段 A（已落地，multiblock-detection）：控制器状态化 → isFormed() 布尔
API 阶段（本 spec）：                 接口 + 状态机四态定义，不引入运行时校验
实施阶段 B（本 spec 之后）：            组件数量校验 + DynamicMachine 字段
实施阶段 C：                          接口等级实装 + Entity 多 tier
实施阶段 D：                          JEI 高亮 / debug wrench 提示 / 蓝图
```

本 spec 是实施阶段 B / C 的 API 文档基线。实施阶段 B 实施 ComponentRequirements 实际校验时，结构检测 → `updateComponents()` 之后追加 `verifyComponentRequirements()` 一段；实施阶段 C 引入 tier 实体后追加 `verifyComponentTierRequirements()`。两段代码共享 `MachineStructureStatus` 状态机。

### 5.5 与组件上下文阶段的关系

`multiblock-component-context-design.md` 设计了 `MachineComponent` / `ProcessingComponent` / `MachineComponentTile` 三件套，由 `MachineControllerBlockEntity.updateComponents()` 在结构成型后调用。本 spec 不阻塞该阶段，也可以等到 component context 阶段完成后再实施 B/C。

## 6. 接口契约

### 6.1 `Machine.componentRequirements()`

- 必须始终返回有效 `ComponentRequirements` 实例（never null）。
- `ComponentRequirements.empty()` 是默认值；调用 `getMinimum(kindId)` 返回 0。
- 返回的 `Map` 必须是不可变的（`Map.copyOf`）；调用者不应修改。

### 6.2 `Machine.minInterfaceTier()`

- 必须始终返回有效 `ComponentTierRequirements` 实例。
- `defaults()` 是默认值；所有 kind 的最低 tier = NORMAL。
- 实施阶段 C 之前，tier 校验不会进入 hot path；存在 `kindId` 字符串不在 6 项基本 IO 范围内的映射不会立即报错，但测试应该确保 `Machine` 自定义 `defaults()` 之外的映射键如果有，应使用完整 6 项 IO 键。

### 6.3 `MachineStructureStatus`

- 枚举顺序对应控制器状态的"递进"：UNFORMED → FORMED_MISSING_COMPONENT → FORMED_TIER_TOO_LOW → FORMED。
- `isFormed()` 仅在 `FORMED` 时返回 true；其他三态对 GUI 提示颜色、配方启动、控制器逻辑为 false。
- `UNFORMED` 不意味着结构被破坏（如切走 chunk 期间），只是当前 tick 没匹配。

## 7. 测试重点

### 7.1 单元测试

- `InterfaceTier` 枚举的 `level()` 与 `atLeast(other)` 正确性；TINY < SMALL < NORMAL < REINFORCED < BIG < HUGE < LUDICROUS 顺序。
- `ComponentRequirements.empty()` 的 `getMinimum(kindId)` 返回 0；`builder.build()` 后的 `Map` 不可变。
- `ComponentTierRequirements.defaults()` 的 6 项均为 NORMAL。
- `Machine.componentRequirements()` 默认返回 `empty()`；`Machine.minInterfaceTier()` 默认返回 `defaults()`。
- 现有 `MachineRecipeTest` / `DefaultMachinesTest` 行为不变。

### 7.2 GameTest

- 阶段 A 已存在 `ControllerTickGameTest`（multiblock-detection-design 阶段 A 落地）仍通过：机器未声明任何组件需求，状态机扩展对现有 GameTest 无影响。
- 实施阶段 B 落地时新增覆盖：机器声明 `minItemInput=2` 时，只放 1 个 input bus，控制器处于 `FORMED_MISSING_COMPONENT`；补足后切回 `FORMED`。
- 实施阶段 C 落地时新增覆盖：机器声明 `minItemInput=NORMAL` 时，结构里只放 TINY 端口，状态机处于 `FORMED_TIER_TOO_LOW`；替换为 NORMAL 端口后切回 `FORMED`。

### 7.3 回归测试

- `E2ERecipeRunGameTest.ironCompressorRuns` 必须仍通过：现有 blast_furnace 没有任何组件需求 / tier 需求，状态机扩展为 4 态对配方面无影响。

## 8. 风险与注意事项

- 状态机扩展破坏向后兼容：现有 `MachineControllerBlock.FORMED` 描述「formed=true」二态 GUI 卸载/启动逻辑。新增 `FORMED_MISSING_COMPONENT` / `FORMED_TIER_TOO_LOW` 时，状态写入 BlockState 没有合适位置避免引入新方块属性。本 spec 阶段不引入新方块属性，状态机仅在 BE 内部扩展；实施阶段 B/C 引入 GUI 区分时再决定如何持久化。
- `ComponentRequirements` 的 key 是 `IOPortKind.id()` 字符串，不是 IOPortKind 本身，避免 `Machine` 接口对 `IOPortKind` 反向依赖。这同时意味着一旦 `IOPortKind.id` 变更（不应该），所有机器的声明字典也要同步——这是链路稳定性的负担，但通过单元测试与调用契约（kindId 字符串不变量）可控。
- 实施阶段 C 引入 tier 实体时，控制器拿到 `foundComponents` 后需要识别每个端口的 tier。MMCE 通过 BlockState `BUS_TYPE` 属性实现，MMCR 实施阶段 B 不引入方块属性，实施阶段 C 同时引入方块属性与 tier 识别。
- 4 状态机渲染客户端时可能产生 4 种颜色或 4 种粒子效果，本 spec 阶段不实现；后续阶段在 `MachineControllerBlock` 模型资源中预留接口位置。
- 控制器在 `resetMachine()` 中必须把 `status` 重置为 `UNFORMED`。
- `failureAction` / `RecipeFailureActions` 与本 spec 互不相关，失败处理仍按现有 controller-runtime 阶段落地。

## 9. 阶段化落地建议

### API 阶段（本 spec 文档范围）

- 新增 `InterfaceTier` enum。
- 新增 `ComponentRequirements` 记录 + builder。
- 新增 `ComponentTierRequirements` 记录 + defaults。
- `Machine` sealed interface 增加两个 default 方法。
- 新增 `MachineStructureStatus` 枚举。
- 单元测试覆盖以上类型。
- 不实现任何运行时校验 / 状态机扩展。

### 实施阶段 B（后续 spec）

- `DynamicMachine` 增加 `ComponentRequirements` 字段 + 4 参构造函数，新增 builder 链式 API。
- `MachineControllerBlockEntity.checkStructure()` 之后追加 `verifyComponentRequirements()`。
- 状态机扩展：4 态流转。
- 现有 `MachineControllerBlock.FORMED` 仅在 `status == FORMED` 时为 true。
- 测试覆盖 number 不足 → FORMED_MISSING_COMPONENT。

### 实施阶段 C（后续 spec）

- `InterfaceTier` 实体化：每个 tier 一对 Block / BlockEntity。
- `BlockArray` / `BlockPredicate` 扩展识别 tier。
- `MachineControllerBlockEntity` 增加 `verifyComponentTierRequirements()`。
- 状态机扩展：保留 4 态，FORMED_TIER_TOO_LOW 路径。
- Json 资源 + KubeJS builder 暴露 tier 配置。

### 实施阶段 D（待排）

- JEI 突出显示机器允许的 tier 范围。
- debug wrench 显示当前结构状态。
- 自动化组装 / 蓝图 / 投影系统识别 tier。

## 10. 验收标准

- `Machine.componentRequirements()` 默认实现返回 `ComponentRequirements.empty()`。
- `Machine.minInterfaceTier()` 默认实现返回 `ComponentTierRequirements.defaults()`。
- `InterfaceTier` 7 级枚举顺序、level()、atLeast() 行为正确。
- `ComponentRequirements` 不可变，builder 简单且 fail-fast。
- `ComponentTierRequirements.defaults()` 6 项均为 NORMAL。
- `MachineStructureStatus` 枚举 4 态，`isFormed()` 仅 FORMED 为 true。
- 现有 `MachineRecipeTest` / `DefaultMachinesTest` / `E2ERecipeRunGameTest.ironCompressorRuns` 全部通过。
- 不引入新方块属性 / BlockState 字段。
- 不引入 `MachineComponent` / `ProcessingComponent` 类（属于 component-context 阶段）。
- 不实现任何 tier 实体。

## 11. 决策摘要

- 声明主体：`Machine` 级。
- 状态机：4 态细分（UNFORMED / FORMED_MISSING_COMPONENT / FORMED_TIER_TOO_LOW / FORMED）。
- 等级体系：mmce 7 级，对齐 `ItemBusSize`。
- 文档范围：仅设计规范，本 spec 不写代码。
- 实施阶段路径（API 阶段 → 实施阶段 B → 实施阶段 C → 实施阶段 D）清晰。
- 与 component-context 阶段保持独立，可在 component-context 之前或之后实施。
