# Phase 1 收尾设计：结构组件上下文、Failure Action、Fluid Output

日期：2026-08-05

> Phase 1 已落地 `MachineComponentTile` / `ProcessingComponent` 收集路径，并修复了 `RecipeApiSmokeTest.recipe_codec_roundtrip_preserves_modifiers_and_priority` 失败。剩余三项需要在本阶段闭环：
> 1. `RecipeCraftingContext` 切到结构内组件上下文（消除仍在按 BE 类型硬找的兜底逻辑）。
> 2. Failure Action（`STILL` / `RESET` / `DECREASE`）接线到 `ActiveMachineRecipe` 和 `Machine`。
> 3. Fluid Output 通过结构内 `FluidOutputHatchBlockEntity` 走通。

## 1. 目标

- `RecipeCraftingContext` 完全读取 `MachineControllerBlockEntity.getComponents()`，不再依赖任何 `findAndCheck*` 残留路径或固定邻近范围。
- 控制器在 `tryStartNewRecipe` / `tickActiveRecipe` 调用 context 时，所看到的输入/输出集合就是 `updateComponents()` 收集到的结构内组件。
- `Machine.failureAction()` 默认 `STILL`；`ActiveMachineRecipe.doFailureAction(RecipeFailureActions)` 按 action 推进 tick，并在 `ioTick` 失败时由控制器读取机器 action 决定执行。
- 配方 `FluidOutput`（如新增）通过 `FluidOutputHatchBlockEntity` 的 capability 走 commit/simulate；失败时不吞输入。

## 2. 非目标

- 不重写 `MachineIngredient` 模型；Phase 1 沿用 `MachineIngredient.FluidIngredient` / `MachineOutput`（如果已落地）。
- 不实现 selector tag、跨组件聚合、upgrade bus、parallel controller。
- 不修改 KubeJS / JSON 配方 schema。
- 不引入新第三方依赖。

## 3. MMCE 对照

| 主题 | MMCE 入口 | 移植策略 |
|---|---|---|
| 输入/输出组件发现 | `TileMultiblockMachineController.updateComponents()` + `checkAndAddComponents()` | 已落地；本阶段只需保证 `RecipeCraftingContext` 使用它 |
| Failure Action | `RecipeFailureActions` + `ActiveMachineRecipe.doFailureAction(action)` | 直译简化：`Machine` 默认 `STILL`，context/active 接收 action |
| Fluid Output | `TileFluidOutputHatch` + `RequirementFluid.registerOutOfBoundsSearch(...)` | 简化：FluidOutputHatch BE 暴露 IFluidHandler，context 直接路由 |
| Per-tick energy | `ComponentRequirement.PerTick#doIOTick` | 已有 `EnergyRecipeIo`；本阶段无需重做 |

## 4. 当前根因（结构组件上下文）

`RecipeCraftingContext` 仍依赖 controller BE 的 `findAndCheckItemBus` / `findAndCheckFluidHatch` / `findAndCheckEnergyHatch` / `outputSlots()`，而这些方法目前都是先走 `liveComponents(...)` 取容器再做检查的；问题在于：

- `findAndCheckItemBus` 返回第一个匹配的 bus，跨组件聚合被破坏（多 bus 共担一份 ingredient 时只能命中第一个）。
- `outputSlots()` 对每个 `ItemOutputBusBlockEntity` 一次性把所有 slot 列入 `OutputSlot`，没有按 `MachineOutput.ItemOutput` 个数和 `ItemStack` 内容做“按输出类型挑目标”。
- `commitInputs` 内部 `findAndCheckItemBus` 在 commit 阶段重新扫描，又跑了一次查找；context 已经持有 components 列表，应直接路由。
- 现有 `findAndCheck*` 在 commit 后若返回 `null`，会留下半提交的状态（item 已部分提取，bus 被换走时无法回退）。

修复方向：把 context 内的 IO 路径完全切到 `getComponents()`：

- `simulateInputs`：`MachineIngredient.ItemIngredient` → 聚合所有匹配的 input bus `IItemHandler`，统计可用数量 ≥ `count` 即视为通过；记录访问过的容器 list 以供 commit 阶段按相同顺序扣减。
- `simulateOutputs`：`MachineOutput.ItemOutput` → 聚合所有匹配的 output bus `IItemHandler`，按 `insertItem(..., simulate=true)` 模拟插入；任一输出无法完全吸收即视为失败。
- `commitInputs`：复用 simulate 阶段记录的容器列表按相同顺序抽取；不再调用 `findAndCheckItemBus`。
- `commitOutputs`：复用 simulate 阶段记录的容器列表按相同顺序插入；不再调用 `outputSlots()`。
- Fluid 输入/输出同形态处理：先收集 `FluidInputHatchBlockEntity` / `FluidOutputHatchBlockEntity` 容器，按顺序 `drain` / `fill`。
- Energy 输入继续用 `EnergyRecipeIo.consumeInputs(...)`；helper 签名已就绪。

实现要点：

- 在 `RecipeCraftingContext` 字段中新增一次性的“路由结果”结构，例如：
  ```java
  record ItemInputRoute(List<BusWithSlots> buses, int required) {}
  record ItemOutputRoute(List<BusWithSlots> buses, ItemStack stack) {}
  record FluidInputRoute(List<HatchWithTank> hatches, int required) {}
  record FluidOutputRoute(List<HatchWithTank> hatches, FluidStack stack) {}
  ```
  simulate 阶段建立 routes，commit 阶段直接按 routes 执行；context 失败时无需回退（simulate 已确保原子）。
- 不再在 context 内部扫描世界，只通过 controller.getComponents() 取结构内组件。
- 移除 `findAndCheck*` 私有方法以及它们的 `LOG.debug` 调用；保留的日志只剩生命周期层（START / no candidate / refused / FINISHED / canceled）。

## 5. Failure Action 接线

### 5.1 引入

新增 `src/main/java/cn/howxu/mmcr/api/machine/RecipeFailureActions.java`（如已存在则沿用），值 `RESET` / `STILL` / `DECREASE`，提供 `getDefaultAction()` 返回 `STILL`。

### 5.2 `Machine` 接口扩展

```java
sealed interface Machine permits DynamicMachine {
    // 已有字段...
    default RecipeFailureActions failureAction() {
        return RecipeFailureActions.getDefaultAction();
    }
}
```

`DynamicMachine` 暂不加新字段，4-arg 构造器继续使用默认 `STILL`；如需在 KubeJS / JSON 中暴露，以后再做。

### 5.3 `ActiveMachineRecipe` 改造

- 替换现有 `doFailureAction(boolean reset)` 为 `doFailureAction(RecipeFailureActions action)`：
  - `RESET` → `this.tick = 0`
  - `DECREASE` → 若 `tick > 0` 则 `tick--`
  - `STILL` → 不动
- `tick(context)` 内：
  - `context.ioTick(recipe)` 失败时调用 `doFailureAction(context.failureAction())`，再返回 `WAITING`。
  - 不再需要 `Machine` 入参或 `doesCancelRecipeOnPerTickFailure()` 联合判断。

### 5.4 `RecipeCraftingContext` 暴露 action

构造函数已持有 controller，新增 `RecipeFailureActions failureAction()`：

```java
public RecipeFailureActions failureAction() {
    Machine m = controller.getMachine();
    return m == null ? RecipeFailureActions.getDefaultAction() : m.failureAction();
}
```

`MachineControllerBlockEntity` 在调用 `next.start(context)` 之后无需再传 `machine`，context 自取。

## 6. Fluid Output

### 6.1 模型

`MachineRecipe.outputs()` 当前返回 `List<ItemStack>`（roadmap 列出 `List<MachineOutput>`，但本阶段不引入 sealed `MachineOutput`，见 §7）。Fluid 输出在本阶段采用最小扩展：

- `MachineRecipe` 新增 `List<FluidStack> fluidOutputs()` 与对应 codec 字段 `fluid_outputs`（optional，默认空）。
- 8-arg 构造器新增 `List<FluidStack> fluidOutputs` 形参；5-arg 旧构造器把 `fluidOutputs` 视为 `Collections.emptyList()`。
- `assemble(RecipeInput)` 仍返回首个 `ItemStack`（`assemble` 不接触 fluid，fluid-only 配方返回 `ItemStack.EMPTY`）。
- `PreparedRecipe.toMachineRecipe()` 透传 fluid outputs。

### 6.2 路径

`RecipeCraftingContext`：

- `simulateOutputs` 同时处理 item outputs 与 fluid outputs：遍历 fluid outputs，对每个 `FluidStack` 调用 `FluidOutputHatchBlockEntity.getFluidHandler(null).fill(...)`（simulate=true），未完全吸收即失败。
- `commitOutputs` 在 item 提交后追加 fluid 提交：按 simulate 阶段记录的 hatch/tank 顺序 `fill`；若中途失败，整条 commit 返回 false，不回退（simulate 已保证）。
- 不引入新 sealed `MachineOutput`，把 fluid outputs 看作 outputs 的平行字段；后续 Phase 2 抽 `MachineOutput` 时一并合并。

### 6.3 验收

- 输出端口空间不足时拒绝启动配方，输入不被消耗。
- `assemble()` 对 fluid-only 配方返回 `ItemStack.EMPTY`。

## 7. 与 Phase 2 的边界

- 本阶段不引入 `sealed MachineOutput`，避免一次性把 codec 改动和 component 抽象合并为单 PR。
- 不引入 `Requirement` 抽象、不重构 `MachineIngredient`。
- `RecipeCraftingContext` 内 route 结构是临时实现：Phase 2 抽 `Requirement` 时 route 结构会下沉到 requirement 自身，本阶段代码不删，但允许被替换。

## 8. 测试

- `RecipeCraftingContextTest`：单 / 多 bus 聚合 item 输入；item 输出不足时 simulate 失败；commit 路径不再调用 find。
- `EnergyRecipeIoTest`：保持现有覆盖；新增 multi-hatch aggregation 的 simulate 失败 / commit 成功分支。
- `MachineControllerBlockEntityTest` / GameTest：手动注入结构 + 缺料 + 缺输出端口，验证 `doFailureAction(STILL)` 不推进 tick；`RESET` 在新结构下跳回 0；`DECREASE` 回退一 tick。
- `RecipeApiSmokeTest`：保留 codec roundtrip 用例；新增 fluid output codec roundtrip（最小：`List<FluidStack>` 列表空 / 一项）。

## 9. 验收门槛

- `./gradlew compileJava --no-daemon` 通过。
- `./gradlew test --no-daemon` 全绿；新测试可缺，但旧测试不得退化。
- `MachineControllerBlockEntity` 日志只在生命周期节点出现；模拟/扫描/候选过滤日志彻底消失。
- GameTest（或日志手测）：高炉结构成型、端口在结构内任意位置，输入/输出/能量正确；缺料触发 `STILL`，配方不推进；缺能量触发 `STILL` 且不消耗输入。

## 10. 风险

- 现有 `RecipeCraftingContextTest` 直接 mock `MachineControllerBlockEntity` 的 `getComponents()`，新结构若改变 context 字段，需要同步测试。
- `findAndCheck*` 移除后若有未覆盖的 GameTest 依赖旧行为，可能回归；需在 PR 中跑 `gameTestServer` 或保留 fast-fail 兜底（不推荐）。
- Fluid 输出和 item 输出若同时存在，commit 顺序固定为 item → fluid，simulate 也按同序；任何倒序都视为非需求改动。
- `Machine.failureAction()` 默认值变更属于行为扩展；KubeJS 端若要覆盖，应在 DynamicMachine 后续字段里加，不在本阶段处理。

## 11. 控制器 GUI 同步增量

为保持“控制器屏与运行时一致”，下面三项与 §4 / §5 / §6 同 PR 落地；不补其他阶段的视觉项。

### 11.1 Failure message 透出

- `RecipeCraftingContext` 新增只读字段 `lastFailureUnloc`（最近一次 `simulate/commit/ioTick` 失败的未本地化消息；可空）。
- `MachineControllerBlockEntity` 暴露 `getLastFailureUnloc()`，在每次失败 / 重置时更新。
- `MachineControllerMenu` 新增 `DataSlot lastFailure`，并提供 `lastFailureMessage()` getter。
- `MachineMenuScreen.renderControllerStatus` 在 `active == null` 且 `lastFailureMessage()` 非空时，追加一行 `gui.mmcr.controller.last_failure`（带参数）；不再额外弹 toast。
- 重置结构（`resetMachine`）或重启时清空。

### 11.2 红石停机

- `MachineControllerBlockEntity.serverTick` 开头读取 `level.getStrongPower(getBlockPos())`；若 >0：
  - 若有 active recipe，把 `active` 引用与 `context` 暂存到 `_paused`，`active/context = null`，不调用 `tickActiveRecipe`；状态从 `Running` 回退到 `Idle`（已通过现有 `broadcastStateIfChanged`）。
  - 不取消 active recipe 的 tick 数；红石消失后从原 tick 继续。
- `MachineControllerMenu` 新增 `DataSlot redstonePaused`，渲染时显示 `gui.mmcr.controller.redstone_stopped` 行（参考 MMCE 文案）。

### 11.3 多行换行 + 缩放

- `MachineMenuScreen.renderControllerStatus` 改用 `PoseStack.scale(0.72F)`，文本用 `font.split(component, scaledWidth)` 拆行；与 MMCE `scale=0.72` 视觉一致。
- 标题行（机器本地化名）保留原色与位置，不缩放，避免与状态行错位。
- 进度行的 dot 动画保留；`progressDots` 函数不变。

### 11.4 不在 Phase 1 收尾范围

- `ControllerGUIRenderEvent` extraInfo 钩子（KubeJS 集成）→ 留给 Phase 5/6 同步。
- Blueprint / Structure 段、`usedTimeCache` 性能行 → 留给 Phase 5/6/7。
- Controller 屏内 fluid tank / energy bar → Phase 2 spec §10 末尾合并。

## 12. 完成定义

- 控制器运行时完全基于 `getComponents()` 路由 I/O。
- `RecipeFailureActions` 已接线到 context / active recipe。
- Fluid output 通过结构内 hatch capability 走通 simulate + commit。
- Failure message 透出到控制器 GUI；红石停机逻辑与 GUI 文案就位；状态行升级为 0.72 倍缩放 + 多行换行。
- `RecipeApiSmokeTest` 全绿；`gradlew compileJava` / `gradlew test` 通过。
- roadmap 中 Phase 1 的“当前优先收尾”清单全部消化。
