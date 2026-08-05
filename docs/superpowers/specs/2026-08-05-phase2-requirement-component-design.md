# Phase 2 设计：Requirement / Component 正式层

日期：2026-08-05

> Phase 1 收尾后，MMCR 已能基于结构内组件跑通最小闭环。Phase 2 将 `MachineIngredient` 简化模型过渡到接近 MMCE 的 `Requirement` 路由模型，仅覆盖 vanilla + NeoForge 的 item / fluid / energy 三类。

## 1. 目标

- 抽象出 `MachineRequirement`（抽象类或 sealed interface），每个 requirement 知道：
  - 自身类型（item input / item output / fluid input / fluid output / energy input）。
  - 在 `RecipeCraftingContext` 上下文里如何匹配结构内组件、如何 simulate、如何 commit、如何参与 `ioTick`。
- `RecipeCraftingContext` 不再硬编码 `ItemIngredient` / `FluidIngredient` / `EnergyIngredient` 分支，改为遍历 `recipe.requirements()` 并委派给各 requirement 实现。
- Requirement 在多组件情况下能聚合（多个 item input bus 共同提供同一份 ingredient；多个 fluid hatch 共同提供同一份 fluid）。
- Failure 错误信息能精确定位：缺少哪种 requirement / 数量差多少 / 哪些组件被搜索过。
- 保留 selector tag 的扩展点，但不强制实现；未声明 tag 的 recipe 维持现有“所有同类组件可用”行为。

## 2. 非目标

- 不引入第三方 mod 联动（AE2 / Mekanism / GTCeu / ModularMagic 等）。
- 不实现 smart interface、parallel controller、upgrade bus、factory controller。
- 不重写 KubeJS / JSON 配方 schema 顶层结构；requirement 列表在 codec 内的 key 名 `requirements` 与现有 `inputs` / `outputs` 并存一段时间，二选一切换在后续阶段做。
- 不动 `Machine.failureAction()`；failure action 已在 Phase 1 收尾阶段接通。
- 不改 `EnergyRecipeIo` 公开签名。

## 3. MMCE 对照

| MMCE 概念 | MMCR Phase 2 目标 | 移植方式 |
|---|---|---|
| `ComponentRequirement<T, RE>` | `MachineRequirement` sealed interface | 重映射 |
| `RequirementItem` | `ItemRequirement`（保留 `Ingredient + count + 可选 selectorTag`） | 重映射 |
| `RequirementFluid` | `FluidRequirement`（NeoForge `FluidIngredient + amount`） | 重映射 |
| `RequirementEnergy` | `EnergyRequirement`（FE/t） | 直译 |
| `ComponentSelectorTag` | `Requirement.tag`（`@Nullable String`）+ `ProcessingComponent.tag` | 重映射 |
| `TaggedPositionBlockArray` | `BlockArray` per-position metadata（Phase 2 仅留接口；数据生成在 Phase 7 / Phase 8） | 重映射 |
| `RequirementTypeRegistry` | 内置枚举（`ITEM` / `FLUID` / `ENERGY`），不开放注册中心 | 简化 |

## 4. 模型设计

### 4.1 `MachineRequirement` sealed interface

```java
public sealed interface MachineRequirement
        permits ItemRequirement, FluidRequirement, EnergyRequirement {

    String type();

    /** 用于失败日志：人类可读描述，例如 "1x minecraft:iron_ingot"。 */
    String describe();

    /** 是否匹配指定组件；不参与 selector tag 时直接看 component 类型 + IO 方向。 */
    boolean matches(ProcessingComponent component);

    /** simulate 阶段：尝试在已匹配组件上模拟消耗，返回 short 数量。 */
    CraftCheck simulate(RecipeCraftingContext context);

    /** commit 阶段：按 simulate 顺序执行实际 I/O；不满足时返回 false。 */
    boolean commit(RecipeCraftingContext context);

    /** ioTick 阶段（仅 energy 等持续型 requirement 使用）。 */
    default boolean ioTick(RecipeCraftingContext context) { return true; }
}
```

> Phase 2 不实现 `PerTickRequirement` 通用抽象；energy 仍走 `EnergyRecipeIo` + `ioTick` 单独路径；其余 requirement `ioTick` 默认 `true`。后续 Phase 5/6 扩展时再升级为通用形态。

### 4.2 子类签名

- `ItemRequirement(Ingredient item, int count, @Nullable String tag, IOType ioType)`：覆盖 item input / item output 两种方向；input 由 `simulate/commit` 调用 `extractItem` / `insertItem`，output 反向。
- `FluidRequirement(FluidIngredient fluid, int amount, @Nullable String tag, IOType ioType)`：覆盖 fluid input / output；通过 `IFluidHandler`。
- `EnergyRequirement(int fePerTick)`：仅 input；`ioTick` 委派给 `EnergyRecipeIo`。

> `EnergyRequirement` 在 Phase 2 仅作为契约占位；实现细节继续走 `MachineIngredient.EnergyIngredient` + `EnergyRecipeIo`，避免大改 context 的能量路径。

### 4.3 codec

- `MachineRequirement.CODEC`：`type` 字段 + 对应字段，shape：
  ```json
  { "type": "item", "item": {...}, "count": 1, "io": "input", "tag": "north_buses" }
  { "type": "fluid", "fluid": {...}, "amount": 100, "io": "output" }
  { "type": "energy", "fe_per_tick": 80 }
  ```
- `MachineRecipe` 新增 `requirements()` 返回 `List<MachineRequirement>`；codec 字段 `requirements` optionalFieldOf。
- 旧字段 `inputs` / `outputs` / `fluid_outputs` 保留为派生来源：
  - 解码时若存在 `requirements`，以 requirements 为准；否则从 `inputs` / `outputs` / `fluid_outputs` 派生。
  - 编码时优先输出 `requirements`，否则派生。
  - 这一过渡避免一次性破坏现有 JSON。
- `MachineRecipe.outputs()` 在 Phase 2 仍可保留；返回 `List<ItemStack>`，由 `requirements` 中 item output 派生。Fluid output 保留 `fluidOutputs()` getter。

## 5. 上下文与路由

### 5.1 `RecipeCraftingContext` 新职责

- 维持 controller 引用、组件列表（`getComponents()`）、failure action 来源。
- 新增 `routes` 字段：simulate 阶段为每个 requirement 建立的“已选组件 + 顺序”快照，供 commit 阶段复用。
- `simulate(recipe)`：`for (r : recipe.requirements()) r.simulate(this)`；任何 requirement 返回 failure 即终止。
- `commitInputs` / `commitOutputs` / `ioTick` / `finishCrafting` 改为遍历 `requirements()` 并委派：
  - input requirement → `commit(...)`
  - output requirement → `commit(...)`（仅在 finishCrafting 阶段）
  - energy requirement → `ioTick(...)`

### 5.2 多组件聚合

- `ItemRequirement.simulate`：
  - 候选 = `getComponents()` 中 `matches(...)` 的 `ItemInputBusBlockEntity`（或输出对应 BE），按 `controller.getComponents()` 顺序遍历。
  - 统计每个 bus 各 slot 中匹配的 `count` 之和；聚合 ≥ `count` 即视为满足，记录被选中的 bus / slot 列表到 route。
  - 若任何 selector tag 不匹配，直接跳过该组件（即使 IO 类型一致）。
- `ItemRequirement.commit`：按 route 顺序 `extractItem` / `insertItem`；如中途遇到容器失效（拆方块），返回 false。
- `FluidRequirement`：同形态，逻辑改用 `IFluidHandler.drain` / `fill`。

### 5.3 失败上下文

`simulate` 失败时，`CraftCheck.failure(...)` 携带：

- requirement 描述（`describe()`）；
- 缺失数量（`required - available`）；
- 命中的容器列表（`pos` + `kind`）。

这些信息用于：

- `tryStartNewRecipe` 跳过当前配方；不向玩家弹错误（避免每 tick 弹窗）。
- 调试日志：仅当 `LOG.debug` 开启时输出；默认 `info` 级别仍保留“no compatible recipe”一条。
- 后续 Jade / HUD 可读取 context.lastFailure 做状态展示（不在 Phase 2 实现）。

## 6. Selector Tag

### 6.1 数据承载

- `MachineRequirement` 形参 `@Nullable String tag`。
- `ProcessingComponent.tag`：当前已经存在字段，但 `MachineControllerBlockEntity.updateComponents` 写入时传 `null`。Phase 2 引入结构定义 metadata：
  - `BlockArray` 增加 per-position tag map（key = `BlockPos`，value = `List<String>`）；由结构定义阶段填充。
  - `updateComponents` 在写入 `ProcessingComponent` 时按 `worldPos` 查找 tag map，将该位置的 tag 列表传入组件（可多 tag，requirement 命中其一即可）。
- KubeJS / Java API 在 Phase 2 提供 `BlockArray.tagged(pos, tags...)` 形式的方法；JSON 形式延后。

### 6.2 匹配规则

- `ProcessingComponent.tag` 为 `null` → 表示未声明 tag；requirement 视为“通配”。
- `ProcessingComponent.tag` 为非空 `List<String>`，requirement.tag 为 `null` → 通配。
- 两边都非空 → 任一 tag 重叠视为匹配。

## 7. 错误处理

- simulate 失败不抛异常，返回 `CraftCheck.failure("...")`。
- commit 失败返回 false，保留已抽取的物品（因为 simulate 失败不应启动 commit，所以正常路径不应到达该分支；异常分支时仅记录）。
- context 不捕获 RuntimeException；底层 IO 异常向上抛，让控制器在 server tick 内捕获并 resetMachine。

## 8. 测试

- `MachineRequirementTest`：
  - `ItemRequirement` 单 / 多 bus 聚合；selector tag 命中 / 不命中；数量不够时返回 short。
  - `FluidRequirement` 同形态，使用 `FluidTank` mock。
  - `EnergyRequirement` 不重做，保留 `EnergyRecipeIoTest` 覆盖。
- `RecipeCraftingContextTest`：用真实 controller BE（不依赖 mock BE 类型）做 simulate → commit 全流程。
- `MachineRecipeCodecTest`：旧 `inputs` / `outputs` JSON 仍能解码；新 `requirements` 优先；mixed shape 给出 deprecation warning（仅日志，不报错）。
- `RecipeApiSmokeTest`：新增 `requirement_codec_roundtrip`；保证 `requirements` + 旧 fields 双向 roundtrip。

## 9. 验收门槛

- `./gradlew compileJava --no-daemon` 通过。
- `./gradlew test --no-daemon` 全绿；新增 requirement / context / codec 测试通过。
- 控制器日志降噪维持：仅生命周期 + failure summary。
- GameTest（若环境支持）：多 item input bus 聚合提供 ingredient；带 tag 的输入配方在缺料时不启动；缺料时不消耗物品。

## 10. 风险

- `BlockArray` per-position tag 是破坏性数据结构扩展；已有结构和测试需要兼容老的“无 tag”路径。
- `MachineIngredient` 在过渡期内保留 codec，旧字段与新字段共存期间可能写出非确定性 JSON shape，需要在编码侧固定优先级。
- Phase 2 把 requirement 路由放到 context 内，破坏了“controller 仅生命周期调度”的边界；这是 Phase 2 不可避免的中间态，Phase 3 modifier 重构时再考虑下沉到独立 service。
- Selector tag 涉及 KubeJS / JSON 改动；本阶段仅 API 层，不输出示例 JSON。

## 11. 完成定义

- `MachineRequirement` 三类实现就位；`MachineIngredient` 不再被 `RecipeCraftingContext` 直接分支消费。
- `MachineRecipe.requirements()` 是 context 的唯一 IO 路由入口；旧 `inputs()` / `outputs()` 保留但由 requirements 派生。
- Selector tag API（tag 形参 + `BlockArray` per-position metadata + `ProcessingComponent.tag` 命中）落地，未声明 tag 的 recipe 行为不变。
- 错误信息（缺哪种 / 缺多少 / 在哪些组件中查找过）能进入 `CraftCheck.failure(...)`。
- `compileJava` / `test` 全绿；现有 E2E GameTest 不退化。
- roadmap Phase 2 验收项 3.1 / 3.2 / 3.3 全部消化。
