# Phase 2 下一步继续操作指南：Requirement Runtime 切换

日期：2026-08-05

关联文档：

- `docs/superpowers/specs/2026-08-05-phase2-remaining-execution.md`
- `docs/superpowers/specs/2026-08-05-phase2-requirement-component-design.md`
- `.superpowers/sdd/progress.md`

## 0. 读这份文档前必须知道的结论

当前 P2 已经从“继续修 MachineIngredient 路径”进入“正式 requirement runtime 切换”的阶段。下一步不要再把精力放在 `RecipeCraftingContext` 里给 `MachineIngredient.ItemIngredient`、`MachineIngredient.FluidIngredient`、`MachineIngredient.EnergyIngredient` 增加更多分支上。那些分支只应作为旧 JSON、旧构造器、旧测试的兼容入口存在，不能继续扩展为新的运行时真源。

本轮已经完成的关键跃迁是：

- `MachineRecipe` 已经新增 `requirements()`。
- `MachineRecipe` 内部真源已经改为 `List<MachineRequirement>`。
- 旧 `inputs()`、`outputs()`、`fluidOutputs()` 现在是从 requirements 派生出来的兼容 getter。
- `MachineRequirement`、`ItemRequirement`、`FluidRequirement`、`EnergyRequirement` 已经建好最小数据模型和 codec。
- recipe codec 已经支持旧字段派生、新 requirements 优先、编码 shape 稳定输出 requirements。
- `compileJava` 和 `cn.howxu.mmcr.api.recipe.*` 测试已经通过。

这意味着下一步的核心不是“继续证明 requirements 能存在”，而是“让 requirements 自己实际接管 IO 路由”。换句话说，P2 的下一阶段应该从批次 B/C 开始，而不是回头再围绕 codec 做更多边角优化。

## 1. 当前 worktree 状态

### 1.1 进度账本

`.superpowers/sdd/progress.md` 当前已经记录到 Task 6：

- Task 1 到 Task 5：此前 worktree 边角改动和测试 unblock 已完成，均未提交。
- Task 6：batch A requirement model + `MachineRecipe` codec/derivation 已完成，`compileJava` 和 recipe API 测试通过，未提交。

后续继续推进时不要重复 dispatch 或重复实现 Task 1 到 Task 6。恢复上下文时以账本为准，以 git diff 辅助确认。

### 1.2 Task 6 的代码事实

已新增包：

- `src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java`
- `src/main/java/cn/howxu/mmcr/api/recipe/requirement/ItemRequirement.java`
- `src/main/java/cn/howxu/mmcr/api/recipe/requirement/FluidRequirement.java`
- `src/main/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirement.java`

`MachineRecipe` 当前重要行为：

- `CODEC` 仍是 `RecordCodecBuilder.mapCodec(...)`，但旧字段已改成 optional。
- `inputs`、`outputs`、`fluid_outputs` 的 `forGetter` 已固定返回 empty list，避免编码时继续写出旧字段。
- `requirements` 字段为 optional，解码时如果为空就从旧 fields 派生。
- 构造器仍保留旧签名，内部调用新增的完整构造器。
- 完整构造器中：如果 `requirements` 为 null 或 empty，则用 `deriveRequirements(inputs, outputs, fluidOutputs)`；否则直接使用 requirements。
- `inputs()`、`outputs()`、`fluidOutputs()` 都从 `requirements` 重新派生。
- `equals()` 和 `hashCode()` 已基于 requirements，而不是旧字段列表。

### 1.3 当前尚未切换的代码事实

`RecipeCraftingContext` 仍然是旧 runtime 形态：

- `ioTick(MachineRecipe recipe)` 仍遍历 `recipe.inputs()`，再判断 `MachineIngredient.EnergyIngredient`。
- `simulateInputs(MachineRecipe recipe)` 仍遍历 `recipe.inputs()`，再判断 item/fluid/energy ingredient。
- `simulateOutputs(MachineRecipe recipe)` 仍分别遍历 `recipe.outputs()` 和 `recipe.fluidOutputs()`。
- `commitInputs(MachineRecipe recipe)` 仍依赖 item/fluid route 列表按旧 ingredient 顺序读取。
- `commitOutputs(MachineRecipe recipe)` 仍依赖 item output list 和 fluid output list 的 index。
- route 结构还是 `ItemInputRoute`、`ItemOutputRoute`、`FluidInputRoute`、`FluidOutputRoute` 四个 list，不是按 requirement index 归档。
- failure 只有 `lastFailureUnloc` 字符串，没有结构化 shortage / searched / matched 结果。

这就是下一步真正要切的地方。

## 2. 下一步总目标

下一步建议把工作定义为“批次 B + 批次 C 的最小闭环”，即：

1. 引入 requirement route 和 failure 的结构化模型。
2. 让 item input/output runtime 先从 `MachineRequirement` 路径跑起来。
3. 旧 `MachineIngredient` helper 可以短暂保留，但 `RecipeCraftingContext` 的 item 主路径不应再直接遍历 `recipe.inputs()` / `recipe.outputs()`。
4. 完成后，item 路径应满足：simulate、commit、failure 都能按 requirement index 对齐。

不要试图在同一个大改里同时完成 B、C、D、E、F、G、H。最稳的切法是：

- 先做 B 的 route/failure 框架。
- 再把 C 的 item input/output 接到这个框架。
- 只在 item 绿了以后，再迁移 fluid。
- energy 单独做，因为它是 per-tick IO，不完全等同 start/finish 阶段 IO。
- selector tag 放在 item/fluid runtime 稳定后做，否则会把匹配问题和 route 问题混在一起。

## 3. 建议提交边界

当前 worktree 已经有很多未提交改动。后续最好不要继续无限堆积。建议从现在开始，至少按下面边界拆提交：

### Commit A：当前已完成内容整理提交

建议提交内容：

- Task 1 到 Task 6 已完成的 worktree 变更。
- 新增 P2 执行文档和本继续操作文档。
- Jade、item bus 菜单、输出 bus capability、默认配方、MachineRecipe requirements codec 等当前已完成内容。

提交前建议跑：

- `rtk gradlew compileJava --no-daemon`
- `rtk gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon`
- 如果时间允许再跑 `rtk gradlew test --no-daemon`

如果暂时不想提交，也至少不要让下一批 route runtime 混进未记录状态。继续做之前先跑 `rtk git diff --stat`，确认自己知道哪些变更属于旧进度，哪些属于新进度。

### Commit B：route/failure structure + item runtime

建议包含：

- 新增 route/failure 数据结构。
- `ItemRequirement` 增加匹配、simulate、commit 能力。
- `RecipeCraftingContext` item 路径改为遍历 requirements。
- 对 item input/output 的单测补齐。

不建议包含：

- fluid runtime 完整迁移。
- energy per-tick 改造。
- selector tag metadata。
- GUI 聚合展示。

### Commit C：fluid runtime

建议包含：

- `FluidRequirement` 接管 fluid input/output。
- `RecipeCraftingContext` fluid 路径改为 requirements。
- fluid input 多 hatch 聚合、output 空间不足、fluid-only assemble 行为测试。

### Commit D：energy runtime + context 收敛

建议包含：

- `EnergyRequirement` 接入 `ioTick`。
- per-tick failure 保持既有 failure action 行为。
- 清理 context 中对旧 energy ingredient 的直接主路径消费。
- 能量测试继续通过。

### Commit E：selector tag metadata

建议包含：

- `ProcessingComponent` tag 改为多 tag 或明确单 tag。
- `BlockArray` 增加 tag metadata。
- rotate/cache 逻辑同步 tag map。
- controller updateComponents 将结构 tag 填进 ProcessingComponent。
- requirement matching 支持 tag 命中/排除。

### Commit F：controller GUI 聚合展示

建议包含：

- controller energy/fluid 聚合 getter。
- menu 同步槽或 owner fallback。
- screen 渲染 energy/fluid preview。
- 翻译 polish。

## 4. 下一步具体执行：批次 B

批次 B 的目的不是一次写完整 IO 实现，而是把“模拟结果快照”从散落 list 变成 requirement-indexed route。这个动作很重要，因为如果直接让 `ItemRequirement` 自己扣物品，但 route 仍按旧 list 保存，会出现几个很难 debug 的中间态：

- simulate 用的是 requirements，但 commit 用旧 inputs index。
- item route 和 fluid route 各有一个 index，跨类型 requirement 的顺序无法表达。
- failure 只能返回 missing input，无法说明是第几个 requirement 缺、差多少、查过哪些组件。
- 后续 selector tag 过滤时，无法告诉开发者“有组件但 tag 不匹配”。

### 4.1 新增 route key

route key 推荐用 recipe requirement index，而不是 requirement 实例。

原因：

- codec roundtrip 后 requirement 实例可能是新对象。
- `MachineRecipe.inputs()` 等兼容 getter 会派生新对象。
- 后续 modifier / parallelism 可能包装 requirement，不适合依赖 object identity。
- index 更容易在日志和 failure 里表达：“requirement[2] 缺少 500mB water”。

建议在 `RecipeCraftingContext` 中引入：

```java
private final Map<Integer, RequirementRoute> routes = new HashMap<>();
```

或者如果想区分阶段：

```java
private final Map<Integer, RequirementRoute> inputRoutes = new HashMap<>();
private final Map<Integer, RequirementRoute> outputRoutes = new HashMap<>();
```

短期更推荐一个 `routes` map，但每次 `simulateInputs` / `simulateOutputs` 开始前只清理对应 direction 的 route。实现上如果怕误删，可以直接清空全部 route，因为目前 start/finish 的调用顺序是 simulate input/output 再 commit，不存在跨 recipe 多阶段并行缓存需求。但为了后续 active recipe 保存 route，最好不要过度绑定当前调用时序。

### 4.2 新增 RequirementRoute sealed interface

建议新建包内类，位置可以是：

- `src/main/java/cn/howxu/mmcr/api/recipe/requirement/RequirementRoute.java`

也可以先放在 `RecipeCraftingContext` 内部 private sealed interface。但如果 requirement 自己要返回 route，就放到 requirement 包更合理。

建议结构：

```java
public sealed interface RequirementRoute permits ItemRequirementRoute, FluidRequirementRoute, EnergyRequirementRoute {
    int requirementIndex();
}
```

item route：

```java
public record ItemRequirementRoute(int requirementIndex, List<ItemTransfer> transfers) implements RequirementRoute {}
```

fluid route：

```java
public record FluidRequirementRoute(int requirementIndex, List<FluidTransfer> transfers) implements RequirementRoute {}
```

energy route 可以暂时不存 transfers，因为目前 energy per-tick 继续委派给 `EnergyRecipeIo`。可以先不建 `EnergyRequirementRoute`，等批次 E 再决定。不要为了对称过早增加没用字段。

### 4.3 transfer 是否放 requirement 包

有两种选择：

方案一：transfer 放在 `RecipeCraftingContext` 内部。

- 优点：最小改动。
- 缺点：`ItemRequirement` 想自治时仍要依赖 context 内部类型，不方便。

方案二：transfer 放到 requirement/helper 包。

- 优点：requirement 可以直接返回 route。
- 缺点：会暴露 `IItemHandler`、`IFluidHandler` 到 api recipe requirement 包。

建议短期采用折中：

- `RequirementRoute` / `RequirementFailure` 放到 `api.recipe.requirement`。
- 具体 transfer record 可以先 package-private 放同包。
- 如果发现 public API 暴露过多，再在后续 G 批次做整理。

不要为了“API 很纯”把 route 做成 Object 或字符串。P2 明确要求结构化 failure，route 也必须结构化。

### 4.4 新增 RequirementFailure / RequirementShortage

建议新建：

- `RequirementFailure`
- `RequirementShortage`
- 可选 `RequirementComponentTrace`

最小字段建议：

```java
public record RequirementFailure(
        int requirementIndex,
        MachineRequirement requirement,
        FailureKind kind,
        RequirementShortage shortage,
        List<String> searchedComponents,
        List<String> matchedComponents
) {}
```

`FailureKind` 可以包含：

- `MISSING_INPUT`
- `MISSING_OUTPUT`
- `MISSING_ENERGY`
- `COMMIT_LOST_INPUT`
- `COMMIT_LOST_OUTPUT`
- `TAG_MISMATCH`

短期如果不想做太复杂，也可以先只做：

- `MISSING_INPUT`
- `MISSING_OUTPUT`
- `MISSING_ENERGY`

但要预留 `kind` 字段，不要只靠 unlocalized string。

`RequirementShortage` 最小字段：

```java
public record RequirementShortage(long required, long available, long shortAmount) {}
```

为什么用 long：

- item count 当前 int 足够，但 fluid amount / FE 未来可能放大 parallelism。
- Phase 5 parallelism 会按并行数放大需求，long 更安全。

### 4.5 CraftCheck 兼容扩展

`CraftCheck.failure(String)` 需要保留，因为已有测试和调用依赖它。新增结构化入口：

```java
public static CraftCheck failure(String unlocMessage, RequirementFailure failure)
```

`CraftCheck` 里新增字段：

```java
@Nullable
private final RequirementFailure requirementFailure;
```

并新增 getter：

```java
public Optional<RequirementFailure> requirementFailure()
```

如果不想引入 Optional，也可以 `@Nullable RequirementFailure getRequirementFailure()`，项目已有风格倾向直接 nullable，可以沿用。

注意：不要改变 `CraftCheck.failure(String)` 的现有行为，不要让旧单测因为 equality 改变而失败。如果给 `CraftCheck.equals()` 加 failure 字段，要确认旧 singleton / failure string 测试仍符合预期。

### 4.6 RecipeCraftingContext failure 保存

`RecipeCraftingContext` 当前只有：

```java
private @Nullable String lastFailureUnloc;
```

建议新增：

```java
private @Nullable RequirementFailure lastRequirementFailure;
```

并提供：

```java
public @Nullable RequirementFailure getLastRequirementFailure()
```

`setFailure(String key)` 保留，再加 overload：

```java
private void setFailure(String key, RequirementFailure failure)
```

这样 controller/Jade/HUD 暂时仍读字符串，但测试和后续 UI 可以读结构化 failure。

### 4.7 批次 B 的测试优先级

先补这些测试：

1. `RecipeCraftingContextTest`：simulate item input 不足时，`getLastRequirementFailure()` 不为空，short 数量正确。
2. `RecipeCraftingContextTest`：simulate output bus 满时，failure kind 是 `MISSING_OUTPUT`。
3. `CraftCheck` 测试：`CraftCheck.failure(String, failure)` 能保留 unloc message 和结构化对象。
4. `MachineRecipeTest` 或新 `MachineRequirementTest`：requirements index 与派生 getter 顺序保持一致。

测试不要只断言文案。文案已经有旧路径覆盖，P2 这一批要证明结构化对象存在并可断言。

## 5. 下一步具体执行：批次 C

批次 C 的目标是让 `ItemRequirement` 接管 item input/output。这里要特别小心，因为当前 item 路由逻辑已经修过“输出先合并同类 stack，再占用空槽”。迁移时不能退化。

### 5.1 ItemRequirement 应承担的职责

`ItemRequirement` 现在只是数据 record：

```java
public record ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack) implements MachineRequirement
```

下一步至少需要具备这些行为：

- 判断自己是否 input 还是 output。
- 匹配 item input/output component。
- input simulate：从多个 handler/slot 聚合可抽取数量。
- input commit：严格按 simulate route 执行抽取。
- output simulate：先同类 stack 合并空间，再空槽空间。
- output commit：严格按 simulate route 执行插入。

不要让 commit 重新扫描 bus。commit 重新扫描会产生 simulate/commit 不一致：simulate 时物品在 slot0，commit 时 slot0 被玩家拿走，重新扫描可能从 slot1 抽到别的物品，看似成功但破坏事务语义。正确做法是 commit 按 route 验证 route 仍然可执行，不可执行就返回 false。

### 5.2 matches(component) 的最小逻辑

批次 C 可以先不接 selector tag，但方法签名要留得下：

```java
public boolean matches(ProcessingComponent component)
```

初始匹配：

- `io == INPUT`：component container 是 `ItemInputBusBlockEntity`，或 component kind/IOType 指向 item input。
- `io == OUTPUT`：component container 是 `ItemOutputBusBlockEntity`，或 component kind/IOType 指向 item output。

当前 `ProcessingComponent` 有：

- `MachineComponent component`
- `BlockEntity container`
- `BlockPos relativePos`
- nullable `ComponentType type`
- nullable `String tag`
- `BlockPos pos`

目前 `RecipeCraftingContext.liveComponents(Class<T>)` 直接按 concrete BE class 找。批次 C 如果不想立刻做 G，可以先让 context 提供 candidate components 给 requirement，requirement 再从 `component.getContainer()` 取 handler。不要一步到位消灭所有 concrete BE，因为那是批次 G 的目标。

但至少 item requirement runtime 主流程不应继续写成：

```java
for (MachineIngredient ingredient : recipe.inputs()) { ... }
```

而应写成：

```java
List<MachineRequirement> requirements = recipe.requirements();
for (int i = 0; i < requirements.size(); i++) {
    MachineRequirement requirement = requirements.get(i);
    if (requirement instanceof ItemRequirement item && item.io() == INPUT) { ... }
}
```

### 5.3 input simulate 迁移方式

当前逻辑在 `RecipeCraftingContext.ItemInputState.extract(...)` 中：

- snapshot 每个 slot 的 stack。
- 匹配 ingredient。
- 从多个 slot 聚合扣减 remaining。
- 记录 transfer。
- 如果 remaining > 0，simulate false。

迁移时可以先复用内部 `ItemInputState`，但由 `ItemRequirement` 或 context 的 `simulateItemRequirement(...)` 调用。

最小落地方案：

- `RecipeCraftingContext.simulateInputs` 改为遍历 `recipe.requirements()`。
- 遇到 `ItemRequirement INPUT` 时调用 private 方法 `simulateItemInputRequirement(index, item, itemStates)`。
- 该方法返回 `ItemRequirementRoute` 或 failure。
- 成功时放入 `routes.put(index, route)`。
- 失败时写 `RequirementFailure`，并返回 false。

这样第一步先不把所有逻辑塞进 `ItemRequirement`，但主入口已经从 requirements 走。后续如果要更自治，可以把 private 方法下沉。

### 5.4 input commit 迁移方式

当前 commit 逻辑把所有 input route flatten 后统一 `canExtract` / `extract`。

迁移后建议：

- `commitInputs` 遍历 `recipe.requirements()`。
- 对每个 `ItemRequirement INPUT`，从 `routes.get(index)` 取 route。
- route 不存在时返回 false，并记录 commit failure。这可以抓到“没 simulate 就 commit”或“simulate 失败后误 commit”。
- 先对该 route 做 canExtract。
- 所有 input requirement 都 canExtract 后，再统一执行 extract。

为什么要先全体 can 再执行：

- 如果第一个 input commit 成功，第二个 input 失败，就会吞输入。
- 必须保证所有 route 都仍可执行后再执行任何实际 mutation。

最小做法：

```java
List<ItemInputTransfer> itemTransfers = new ArrayList<>();
for each requirement input route:
    itemTransfers.addAll(route.transfers());
if (!canExtract(itemTransfers)) return false;
extract(itemTransfers);
```

这和当前逻辑相近，但 route 来源改为 requirement index。

### 5.5 output simulate 迁移方式

当前 output simulate 有两个阶段：

1. 遍历所有 output states，先 `insertIntoMatchingStack`。
2. 再遍历所有 output states，调用 `insert`，允许空槽。

迁移后必须保留这个顺序。

测试里已经有：

- `commitOutputsMergesMatchingStacksBeforeUsingEmptySlots`
- duplicate outputs exceed room 等边界。

新增 requirement runtime 后要确保这些测试仍然覆盖新路径，而不是通过 `recipe.outputs()` 兼容 getter 走旧逻辑。可以增加一个测试直接构造含 explicit `ItemRequirement OUTPUT` 的 recipe，旧 `outputs` 留空，确保 runtime 仍能输出。

### 5.6 output commit 迁移方式

和 input 一样，commit 不能重新搜索。必须：

- 读取 `routes.get(index)`。
- route 不存在则 false。
- 先 `canInsert(allTransfers)`。
- 再 `insert(allTransfers)`。

注意 `IItemHandler.insertItem` 的返回值目前没有被严格检查。当前代码 simulate 后先 canInsert，再 execute，通常够用。后续如果 handler 在 execute 时行为和 simulate 不一致，可能仍有边界风险；批次 C 不必过度泛化，但可以在 failure 里记录 `COMMIT_LOST_OUTPUT`。

### 5.7 批次 C 必加测试

建议新增或调整这些测试：

1. explicit item input requirement 可以从单 input bus 消耗。
2. explicit item input requirement 可以跨多个 input bus 聚合。
3. explicit item output requirement 在 output bus 满时 simulate false。
4. explicit item output requirement commit 先合并同类 stack，再占空槽。
5. explicit requirements mixed with legacy fields 时，runtime 使用 requirements，不使用 legacy fields。
6. simulate 成功后 route 中途失效，commit false，且输入不被部分扣除。
7. simulate 失败后 routes 不留下可提交 route。

第 5 条特别重要。它验证 Task 6 的 codec 优先语义不仅存在于 `MachineRecipe`，也被 runtime 尊重。

## 6. 批次 D：FluidRequirement runtime 迁移

Fluid 迁移应在 item 迁移之后做。不要 item/fluid 一起改，否则失败时很难判断是 route 框架的问题，还是 fluid handler 的问题。

### 6.1 当前 fluid 旧逻辑

`RecipeCraftingContext` 当前：

- `fluidInputStates()` 从 `FluidInputHatchBlockEntity` 获取 `IFluidHandler`。
- 每个 tank 生成 `FluidInputState`。
- `FluidInputState.drain(MachineIngredient.FluidIngredient ingredient, int remaining, transfers)` 用 `ingredient.fluid().test(stack)` 匹配。
- output 用 `FluidOutputState.fill(FluidStack input, int remaining, transfers)`。

这些内部 state/transfer 可先复用，但 route 来源必须改为 requirement index。

### 6.2 FluidRequirement input

input 行为：

- 遍历所有匹配 fluid input hatch。
- 遍历每个 tank。
- 对 `FluidIngredient` test 成功的 stack drain。
- 跨多个 hatch/tank 聚合 amount。
- remaining > 0 时 failure kind `MISSING_INPUT`，short = remaining。

注意：NeoForge `FluidIngredient` 语义不能简化成 fluid id equals。必须继续使用 `fluid.fluid().test(stack)`。

### 6.3 FluidRequirement output

output 行为：

- 遍历所有匹配 fluid output hatch。
- 对空 tank 或同 fluid/component tank 计算容量。
- 跨多个 hatch/tank 聚合 fill 空间。
- 空间不足时 simulate false，不消耗 item/fluid/energy。

注意：fluid output 如果同一 recipe 同时有 item input 和 fluid output，start 阶段不应该先扣 item 再发现 fluid output 不够。当前 controller 调用顺序需要确认。如果启动前是先 simulateInputs + simulateOutputs 再 commitInputs，就安全。如果不是，要先修 controller 启动顺序。

### 6.4 Fluid-only recipe assemble

`MachineRecipe.assemble()` 当前从 item outputs 派生：

```java
List<ItemStack> outputs = outputs();
return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst().copy();
```

这个对 fluid-only recipe 是正确的，必须保留。fluid runtime 迁移后要跑已有或新增测试，确认 fluid-only recipe 不因为 requirements 里有 fluid output 而返回非 empty item。

### 6.5 批次 D 必加测试

建议：

1. explicit fluid input requirement 单 hatch 满足。
2. explicit fluid input requirement 多 hatch 聚合满足。
3. explicit fluid input 单 hatch 不够、多 hatch 足够时启动成功。
4. fluid output hatch 空间不足时 simulate output false。
5. fluid output 空间不足时不消耗 item input。
6. fluid-only recipe assemble 仍是 `ItemStack.EMPTY`。
7. mixed shape 时 old fluid_outputs 不参与 runtime，requirements 优先。

## 7. 批次 E：EnergyRequirement per-tick 接入

Energy 不要和 input/output start/finish 完全混为一谈。当前 energy 是 per-tick IO，在 `ioTick` 中执行，不是 start 时一次性 commit。

### 7.1 当前逻辑

当前 `RecipeCraftingContext.ioTick`：

```java
for (MachineIngredient ingredient : recipe.inputs()) {
    if (!(ingredient instanceof MachineIngredient.EnergyIngredient energy)) continue;
    List<EnergyInputHatchBlockEntity> hatches = liveEnergyInputs();
    if (!EnergyRecipeIo.consumeInputs(energyStorages(hatches), energy.fePerTick(), 1)) {
        setFailure(FAILURE_MISSING_ENERGY);
        return false;
    }
}
```

迁移后应改为：

```java
for (MachineRequirement requirement : recipe.requirements()) {
    if (!(requirement instanceof EnergyRequirement energy)) continue;
    ...
}
```

保持 `EnergyRecipeIo` 公开签名不动。

### 7.2 能量 route 是否需要持久化

批次 E 不建议大改能量存储策略。能量每 tick 都可能变化，route 缓存意义和 item/fluid start/finish 不同。可以先不缓存 energy route，只在 failure 中记录 searched components 和 shortage。

如果要记录 shortage，可以先用：

- required = `fePerTick`
- available = 所有 energy input hatch 当前可 extract 的合计
- shortAmount = required - available

但消费仍交给 `EnergyRecipeIo.consumeInputs(...)`。

### 7.3 failure action 不要改语义

Phase 1 已经接了 `Machine.failureAction()`。能量不足时 active recipe 是暂停还是失败，应继续走既有策略。批次 E 只改 recipe runtime 从 requirements 找 energy，不应改变 failure action。

### 7.4 批次 E 必加测试

建议：

1. explicit `EnergyRequirement` 能被 `ioTick` 消费。
2. 旧 `MachineIngredient.EnergyIngredient` 构造 recipe 后派生成 `EnergyRequirement`，仍能被 `ioTick` 消费。
3. mixed shape 中旧 energy input 和 explicit energy requirement 同时存在时，只用 explicit requirements。
4. 能量不足时 `lastFailureUnloc` 是 missing energy。
5. 如果实现了 structured failure，short 数量正确。
6. `EnergyRecipeIoTest` 继续通过。

## 8. 批次 F：Selector tag 全链路

Selector tag 不要在 item runtime 还没稳定时做。否则 failure 可能同时来自 route bug 和 tag bug。

### 8.1 tag 模型选择

此前文档建议直接多 tag。这仍然是建议方向。

现有 `ProcessingComponent` 是 nullable single `String tag`。下一步有两种改法：

方案一：保留 single tag。

- 短期改动小。
- 但后续 smart interface / upgrade / selector group 很容易返工。

方案二：改成 `List<String> tags`。

- 更符合后续扩展。
- 需要调整构造器、getter、测试。

建议做方案二，但保留一个兼容构造器：

```java
public ProcessingComponent(..., @Nullable String tag) {
    this(..., tag == null ? List.of() : List.of(tag));
}
```

这样现有调用点可以慢慢迁移。

### 8.2 BlockArray metadata

需要在 `cn.howxu.mmcr.api.machine.BlockArray` 侧确认当前数据结构。目标是增加 per-position tag map：

```java
Map<BlockPos, List<String>> tagsByPosition
```

要求：

- tag 挂在结构相对坐标上。
- `BlockArray.tagged(BlockPos pos, String... tags)` 能设置 tag。
- copy / offset / rotate / cache 不能丢 tag。
- 旧机器没有 tag 时默认 empty list。

### 8.3 BlockArrayCache 旋转

这是最容易漏的点。结构 pattern 旋转后，tag map 必须跟着同样旋转。不能只旋转 block pattern，否则 controller 在 rotated pattern 里找到的 component relative pos 和 tag map 对不上。

验收时必须构造一个非零相对坐标的 tagged bus，旋转机器后仍能匹配 tag。

### 8.4 matching 规则

固定规则：

- requirement tag 为空：允许所有同类组件。
- component tag 为空：只允许无 tag requirement。
- 两边都有 tag：任一 tag 重叠即匹配。

建议 `MachineRequirement` 增加通用 tags 字段前先想清楚 record shape。当前 `ItemRequirement` / `FluidRequirement` / `EnergyRequirement` 没 tag 字段。批次 F 需要新增：

- `List<String> tags` 或 `Set<String> tags`。
- codec optional `tags` 字段。
- 旧 requirements JSON 没 tags 时 default empty list。

不要只给 `ItemRequirement` 加 tag。fluid 也必须支持，否则 selector 只能控 item bus，不能控 hatch。

Energy 是否支持 tag 可以暂时不做，但最好数据模型支持，因为后续多 energy hatch 分组也有价值。

### 8.5 tag failure

tag 不匹配时，不应该只是 missing input。结构化 failure 里至少要能看出：

- searched components：查过哪些 item input bus / fluid hatch。
- matched components：哪些通过了 kind/io/tag 匹配。
- tag excluded components：可选，但很有用。

如果不想新增 `tagExcludedComponents` 字段，至少把被 tag 排除的组件记到 searched，并在 summary message 中说明 matched 为 0。

## 9. 批次 G：Context 去具体 BE 化

这是清理批次，不建议在 C/D/E 前做。等 item/fluid/energy runtime 全部从 requirements 走后，再把 `RecipeCraftingContext` 中散落的具体 BE 分支收敛。

### 9.1 当前具体 BE 依赖

当前 context 直接 import：

- `EnergyInputHatchBlockEntity`
- `FluidInputHatchBlockEntity`
- `FluidOutputHatchBlockEntity`
- `ItemInputBusBlockEntity`
- `ItemOutputBusBlockEntity`

G 的目标不是完全禁止这些类出现在任何地方，而是不让 context 主流程散落多个 concrete branch。可以让 requirement 或 component adapter 集中处理。

### 9.2 目标形态

理想方向：

- context 负责拿 `controller.getComponents()`。
- requirement 根据 kind/io/tag 从 components 中选择候选。
- handler 解析集中在 requirement 或 helper 中。
- item 依赖 `IItemHandler`。
- fluid 依赖 `IFluidHandler`。
- energy 依赖 `IEnergyStorage`。
- side 暂时统一 null，不引入 side-aware routing。

### 9.3 不要过度设计 adapter registry

Phase 2 不要提前做第三方 requirement 注册中心，也不要为了 Mekanism gas / AE2 ME 泛化出大 adapter 框架。当前目标只覆盖 vanilla item、NeoForge fluid、NeoForge energy。

## 10. 批次 H：Controller GUI 聚合展示

GUI 批次可以最后做，因为它不是 requirement runtime 切换前置。当前 Jade provider 已经能显示 controller 状态，但 GUI 还没完整展示结构级 energy/fluid 概览。

### 10.1 controller getter

`MachineControllerBlockEntity` 建议新增：

- `long totalStoredEnergy()`
- `long totalCapacityEnergy()`
- `FluidStack primaryFluid()`
- `FluidStack primaryOutputFluid()`

这些 getter 应该聚合当前 formed structure 中的 hatches，不应该从 recipe requirement 反推。

### 10.2 menu 同步策略

`DataSlot` 是 int。如果 energy 用 long，有三种选择：

1. 拆高低位同步 long。
2. GUI 只显示裁剪后的 int，并明确这是展示上限。
3. dedicated client 常见情况下通过 `resolvedOwner()` fallback 直接读 BE，不强行同步 long。

建议先走 3 + 必要时 1。不要静默把 long cast int，尤其不要容量超过 int 后显示负数。

### 10.3 fluid preview

fluid id 不建议强塞 DataSlot。优先用菜单 owner fallback。若 dedicated client 场景 owner 不可靠，再引入小 payload。不要做全量 inventory sync。

### 10.4 screen 位置

`MachineMenuScreen` 当前已有状态/进度/玩家背包布局。新增 energy bar 和 fluid preview 时不要遮挡：

- 状态文本。
- 进度条。
- redstone pause 信息。
- 玩家背包 slots。

建议先用最保守的小尺寸展示，等功能稳定后再 polish。

## 11. 测试策略

### 11.1 每批次必须跑的命令

每个 Java 批次至少跑：

```bash
rtk gradlew compileJava --no-daemon
```

涉及 recipe/context 后跑：

```bash
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon
```

涉及 menu/gui 逻辑后跑相关测试：

```bash
rtk gradlew test --tests cn.howxu.mmcr.internal.menu.* --no-daemon
```

P2 主体完成后跑：

```bash
rtk gradlew test --no-daemon
```

如果 GameTest 环境可用，再跑对应 GameTest。若不可用，提交说明必须写明未跑原因。

### 11.2 单元测试优先覆盖点

下一步优先补：

- structured failure short 数量。
- searched/matched components。
- explicit requirements runtime。
- mixed shape requirements 优先。
- simulate 成功后 commit 使用 route。
- simulate 失败不留下可提交 route。
- commit route 失效时不部分提交。

### 11.3 GameTest 优先覆盖点

P2 runtime 切完后，GameTest 至少覆盖：

- 多 item input bus 合计提供 ingredient。
- item output bus 满时不启动，不吞输入。
- 多 fluid hatch 合计提供 fluid。
- fluid output hatch 空间不足时不启动。
- selector tag 限定 input bus。
- tag 不匹配时不消耗输入。
- energy 不足时保持 Phase 1 语义。

## 12. 容易踩坑的地方

### 12.1 编码 shape 不要回退

Task 6 已经把编码 shape 固定为 requirements。后续给 requirements 加 tags 或更多字段时，不要不小心让 `inputs` / `outputs` 又编码出来。

检查点：

- `MachineRecipe.CODEC` 中旧字段 getter 应继续返回 empty。
- 新字段加到 `MachineRequirement.CODEC`，不要绕回 `MachineIngredient.CODEC`。

### 12.2 旧 getter 是兼容层，不是真源

`recipe.inputs()` 现在是派生 getter。后续 runtime 不应该把它当主入口。允许旧 UI、日志、兼容测试读它，但 controller IO 路由必须逐步改成 `recipe.requirements()`。

### 12.3 commit 不能重新搜索

所有 commit 都必须按 simulate route 执行。重新搜索会导致事务不一致。

正确顺序：

1. simulate 建 route。
2. commit 读取 route。
3. commit 先验证所有 route 仍可执行。
4. commit 再统一执行 mutation。

### 12.4 output 先合并再空槽不能丢

当前 worktree 已经修过 output bus 碎片化问题。迁移到 `ItemRequirement` 时必须保持：

1. 先尝试同类 stack 合并。
2. 再使用空槽。

测试 `commitOutputsMergesMatchingStacksBeforeUsingEmptySlots` 必须继续通过，而且最好新增 explicit requirement 版本。

### 12.5 failure 不能只剩字符串

P2 完成定义要求结构化 failure 至少保留 short 和 searched components。不要写一个 `setFailure("missing input: short 3")` 冒充结构化 failure。

### 12.6 不要引入 side-aware routing

handler capability 先沿用 null side。side-aware routing 是后续阶段，不要在 P2 中途扩大范围。

### 12.7 不要提前做 JEI

JEI 不算当前 P2。按 main roadmap 是 Phase 4。不要被旧 scope 或“既然有 requirements 了顺手做 JEI”带偏。

### 12.8 不要提前泛化第三方 requirement

Mekanism gas、AE2 ME、botania mana 等都不在 P2。P2 的 route/failure 结构可以为后续扩展留余地，但不能因为后续扩展把当前 runtime 做成抽象注册中心。

## 13. 推荐下一次实际操作顺序

下一次继续写代码时，建议按这个顺序：

1. 查看 `.superpowers/sdd/progress.md`，确认 Task 6 是最后完成项。
2. 查看 `rtk git status --short`，确认 worktree 未被其他协作者额外改乱。
3. 写批次 B 的红测：structured failure + route by requirement index。
4. 新增 `RequirementFailure` / `RequirementShortage` / `RequirementRoute` 最小模型。
5. 扩展 `CraftCheck` 和 `RecipeCraftingContext` 保存结构化 failure。
6. 跑 targeted tests，确认 B 通过。
7. 写批次 C 的红测：explicit item requirement runtime。
8. 把 item simulate/commit 改成遍历 `recipe.requirements()`。
9. 保留当前 output merge-first 行为。
10. 跑 `compileJava` 和 recipe tests。
11. 更新 `.superpowers/sdd/progress.md`，新增 Task 7。
12. 如果测试稳定，考虑提交当前累计变更，避免 P2 worktree 越滚越大。

## 14. Task 7 建议定义

建议下一项 SDD 账本任务叫：

```text
Task 7: route/failure structure + item requirement runtime
```

Task 7 验收标准：

- `RecipeCraftingContext` item input/output 主路径遍历 `recipe.requirements()`。
- item route 以 requirement index 保存。
- commit 使用 route，不重新搜索。
- failure 有结构化对象，至少包含 requirement index、kind、required、available、shortAmount。
- explicit item requirements 可以跑 input/output。
- mixed shape runtime 使用 explicit requirements，不使用 legacy fields。
- `rtk gradlew compileJava --no-daemon` 通过。
- `rtk gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon` 通过。

Task 7 不包含：

- fluid runtime 完整迁移。
- energy per-tick requirement 遍历。
- selector tag metadata。
- controller GUI 聚合展示。

## 15. Task 8 建议定义

Task 8 建议叫：

```text
Task 8: fluid requirement runtime
```

Task 8 验收标准：

- fluid input/output 主路径遍历 `recipe.requirements()`。
- fluid route 以 requirement index 保存。
- input 跨 hatch/tank 聚合。
- output 跨 hatch/tank 聚合空间。
- output 空间不足时不启动、不吞输入。
- fluid-only recipe assemble 仍 empty。
- `rtk gradlew compileJava --no-daemon` 通过。
- recipe/context fluid 相关测试通过。

## 16. Task 9 建议定义

Task 9 建议叫：

```text
Task 9: energy requirement per-tick runtime
```

Task 9 验收标准：

- `ioTick` 遍历 `recipe.requirements()` 找 `EnergyRequirement`。
- 旧 energy ingredient 仍可派生为 `EnergyRequirement`。
- mixed shape 时 explicit requirements 优先。
- energy 不足时 failure 仍是 missing energy。
- failure action 语义不变。
- `EnergyRecipeIoTest` 继续通过。

## 17. Task 10 建议定义

Task 10 建议叫：

```text
Task 10: selector tag metadata and matching
```

Task 10 验收标准：

- `ProcessingComponent` 支持多 tag 或明确兼容单 tag。
- `BlockArray` 支持 per-position tag。
- 结构 rotate/cache 不丢 tag。
- controller updateComponents 能填充 tag。
- item/fluid requirement matching 支持 tag。
- tag 不匹配 failure 能说明 searched/matched 情况。
- 未声明 tag 的旧机器行为不变。

## 18. Task 11 建议定义

Task 11 建议叫：

```text
Task 11: controller GUI aggregate display
```

Task 11 验收标准：

- controller 有 energy/fluid 聚合 getter。
- menu/screen 展示结构级 energy/fluid 概览。
- 没有 hatch 时不崩溃。
- client-only fallback 安全。
- hatch/bus 自己 GUI 不受影响。

## 19. 完成 P2 前的总检查

P2 完成前必须逐项确认：

- `MachineRecipe.requirements()` 是 runtime IO 唯一入口。
- `RecipeCraftingContext` 不再直接以 `MachineIngredient` 作为 item/fluid/energy 主路径。
- item/fluid/energy 三类 requirement 都有 codec 和 runtime。
- old fields 仍兼容 decode。
- encode shape 稳定输出 requirements。
- item/fluid 多组件聚合都可用。
- output 空间不足不会吞 input。
- commit route 失效不会部分提交。
- selector tag 从结构 metadata 到 component 到 requirement matching 全链路可用。
- structured failure 至少能断言 requirement index、short、searched/matched components。
- controller GUI 能展示结构级 energy/fluid 概览。
- `compileJava` 通过。
- `test` 通过，或明确记录不可用测试和原因。
- GameTest 不退化，或明确记录未跑原因。

## 20. 推荐给下一位执行者的开场检查

如果下一次是新的会话继续执行，建议开场直接做：

```bash
rtk git status --short --branch
rtk git diff --stat
rtk gradlew compileJava --no-daemon
```

然后读取：

- `.superpowers/sdd/progress.md`
- `docs/superpowers/specs/2026-08-05-phase2-next-operation-guide.md`
- `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java`
- `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- `src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java`

如果 `compileJava` 已经因为其他并行改动失败，先区分是不是自己要处理的 P2 变更导致。不要回滚别人改动。只修和 P2 route/runtime 直接相关的问题。

## 21. 一句话路线图

接下来不要再围着 `MachineIngredient` 打补丁。下一步先做 structured route/failure，再让 `ItemRequirement` 接管 item IO；item 稳定后迁 fluid，之后迁 energy，最后做 selector tag 和 controller GUI。每一批都要用 explicit requirements 测试，证明 runtime 已经真正离开旧字段。
