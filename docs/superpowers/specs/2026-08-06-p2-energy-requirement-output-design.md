# P2 Energy Requirement OUTPUT 补齐与反应堆配方修整 设计

> Spec 路径：`docs/superpowers/specs/2026-08-06-p2-energy-requirement-output-design.md`
> 对应 `docs/main-roadmap.md` Phase 2 §3.1 Requirement 类型 / §3.2 Component 类型 的 OUTPUT 侧缺口

## Goal

把 `EnergyRequirement` 从"INPUT only"补齐为与 `ItemRequirement` / `FluidRequirement` 对称的"INPUT / OUTPUT 双向"模型，让反应堆等能量产出型机器的配方可以声明每 tick 推入 energy output hatch 的 FE 量，并修整当前方向反了的默认反应堆配方。

满足 `docs/main-roadmap.md` 第 27 行 Phase 2 "Requirement / Component 正式层已闭环"描述的实际语义完整：item / fluid / energy 都拥有 INPUT 与 OUTPUT 两条路径。

## Current State

- `cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement`（`src/main/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirement.java:13`）硬编码 `io() return INPUT`，仅支持每 tick 从 `EnergyInputHatchBlockEntity` 抽取 FE。
- `cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIo`（`src/main/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIo.java:17`）只有 `consumeInputs / canConsumeInputs`，无 `produceOutputs / canProduceOutputs`。
- `cn.howxu.mmcr.api.recipe.RecipeCraftingContext`（`src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java:394`）的 `taggedEnergyStorages / taggedAvailableEnergy / energyComponentTraces` 只查 `IOType.INPUT`；无 energy OUTPUT 镜像方法。
- `cn.howxu.mmcr.api.recipe.MachineIngredient.EnergyIngredient`（`src/main/java/cn/howxu/mmcr/api/recipe/MachineIngredient.java:75`）字段 `int fePerTick`，未带方向。
- `cn.howxu.mmcr.api.recipe.MachineRecipe`（`src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java:166`）的 `inputs()` 假设所有 EnergyRequirement 都是输入；无 `energyOutputs()` 派生。
- `cn.howxu.mmcr.api.recipe.requirement.MachineRequirement`（`src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java:102`）的 `decodeByType("energy", ...)` 不读 io 字段（默认 INPUT）。
- 反应堆机器 `mmcr:reactor`（`src/main/java/org/nibelungorum/DefaultMachines.java:188`）端口要求至少 1 个 `ENERGY_OUTPUT`，但其默认配方 `mmcr:reactor_diamond_water`（`src/main/java/org/nibelungorum/DefaultRecipes.java:91`）仍声明 `100FE/t input`，方向与机器要求相反。
- MMCE 原版 `reference/mmce/src/main/resources/assets/modularmachinery/default_recipes/power_transformer_energy_transform.json` 已使用 `io-type: output + energyPerTick`，证明 OUTPUT 语义为 perTick。

## Target State

- `EnergyRequirement` 携带 `RecipeModifier.IOType io`，`io()` 返回该字段；`simulate()` 与 `ioTick()` 按 io 分支调用 INPUT 或 OUTPUT 路径。
- `EnergyRecipeIo` 新增 `produceOutputs(...) / canProduceOutputs(...)`，与 `consumeInputs / canConsumeInputs` 对称。
- `RecipeCraftingContext` 新增 energy OUTPUT 镜像方法：`taggedEnergyOutputs / taggedAvailableOutputEnergy / energyOutputComponentTraces / energyOutputComponentSummary / simulateEnergyInput / simulateEnergyOutput`。
- `MachineIngredient.EnergyIngredient` 携带 `RecipeModifier.IOType io`，codec encode / decode 写 / 读 io 字段（缺省默认 INPUT）。
- `MachineRecipe` 新增 `energyOutputs()` 派生方法；`inputs()` 仅收集 IOType.INPUT 的 energy requirement；`MachineRequirement.fromInput` 处理 energy ingredient 的 io。
- `MachineRequirement.CODEC` 已有 `io` 字段写出（`src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java:64`）；decode 端补齐 energy 分支读取 io，缺省默认 INPUT，向后兼容存量 JSON。
- `DefaultRecipes` 反应堆配方 `mmcr:reactor_diamond_water`：`100FE/t input` 改为 `100FE/t output`，原料 / 产物 / 时长 / priority 保持不变。
- `DefaultRecipesTest` 反应堆相关断言改为验证 energy OUTPUT 方向。

## Architecture

沿用 `ItemRequirement` / `FluidRequirement` 已有的 INPUT / OUTPUT 镜像模式：

```
MachineRequirement (sealed interface)
├─ ItemRequirement(io, item, count, stack, tags)         ← 既有
├─ FluidRequirement(io, fluid, amount, stack, tags)      ← 既有
└─ EnergyRequirement(io, fePerTick, tags)                ← 新增 io 字段
```

```
EnergyRecipeIo (helper, pure)
├─ canConsumeInputs(storages, fe, mul) / consumeInputs(...)    ← 既有
└─ canProduceOutputs(storages, fe, mul) / produceOutputs(...) ← 新增
```

```
RecipeCraftingContext (orchestration)
├─ taggedEnergyStorages(tags)         [INPUT]
├─ taggedAvailableEnergy(tags)        [INPUT]
├─ energyComponentTraces(tags)        [INPUT]
├─ simulateEnergyInput(idx, energy)   [INPUT]
├─ taggedEnergyOutputs(tags)          [OUTPUT] ← 新增
├─ taggedAvailableOutputEnergy(tags)  [OUTPUT] ← 新增
├─ energyOutputComponentTraces(tags)  [OUTPUT] ← 新增
├─ simulateEnergyOutput(idx, energy)  [OUTPUT] ← 新增
└─ energyOutputComponentSummary()     [OUTPUT] ← 新增
```

`MachineIngredient.EnergyIngredient(io, fePerTick)` 与 `MachineIngredient.ItemIngredient / FluidIngredient` 保持同构：所有 IO 方向信息都在 record 字段中，不依赖方法。

`MachineRecipe` 派生方法：

- `inputs()`：收集 INPUT 三类（item / fluid / energy），不包含 OUTPUT energy。
- `outputs()`：收集 item OUTPUT。
- `fluidOutputs()`：收集 fluid OUTPUT。
- `energyOutputs()`（新）：收集 energy OUTPUT 的 fePerTick。

## Components

### EnergyRequirement

```java
public record EnergyRequirement(
        RecipeModifier.IOType io,
        int fePerTick,
        List<String> tags) implements MachineRequirement {

    public EnergyRequirement {
        if (io == null) io = RecipeModifier.IOType.INPUT;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public EnergyRequirement(int fePerTick) {
        this(RecipeModifier.IOType.INPUT, fePerTick, List.of());
    }

    public EnergyRequirement(int fePerTick, List<String> tags) {
        this(RecipeModifier.IOType.INPUT, fePerTick, tags);
    }

    public EnergyRequirement(RecipeModifier.IOType io, int fePerTick) {
        this(io, fePerTick, List.of());
    }

    @Override public String type() { return "energy"; }
    @Override public RecipeModifier.IOType io() { return io; }

    @Override
    public boolean simulate(RecipeCraftingContext context, int requirementIndex) {
        return io == RecipeModifier.IOType.INPUT
                ? context.simulateEnergyInput(requirementIndex, this)
                : context.simulateEnergyOutput(requirementIndex, this);
    }

    @Override
    public boolean commit(RecipeCraftingContext context, int requirementIndex) {
        return io == RecipeModifier.IOType.INPUT
                ? context.collectEnergyInputRoute(requirementIndex)
                : context.collectEnergyOutputRoute(requirementIndex);
    }

    @Override
    public boolean ioTick(RecipeCraftingContext context, int requirementIndex) {
        if (io == RecipeModifier.IOType.INPUT) {
            return EnergyRecipeIo.consumeInputs(
                    context.taggedEnergyStorages(tags), fePerTick, 1);
        }
        return EnergyRecipeIo.produceOutputs(
                context.taggedEnergyOutputs(tags), fePerTick, 1);
    }
}
```

### EnergyRecipeIo.produceOutputs / canProduceOutputs

对称 consumeInputs：iterate output hatches；`receiveEnergy(remaining, true)` simulate；累计可达量；`receiveEnergy(received, false)` 提交；任一 hatch 不可达则失败（per-tick strict，不允许部分提交后放弃）。

```java
public static boolean produceOutputs(List<? extends IEnergyStorage> outputs,
                                     int producedFe, int multiplier) {
    int produced = totalRequired(producedFe, multiplier);
    if (produced <= 0) return true;
    if (outputs == null || outputs.isEmpty()) return false;

    int remaining = produced;
    for (IEnergyStorage output : outputs) {
        if (output == null) continue;
        int received = output.receiveEnergy(remaining, false);
        if (received <= 0) continue;
        remaining -= received;
        if (remaining <= 0) return true;
    }
    return false;
}

public static boolean canProduceOutputs(List<? extends IEnergyStorage> outputs,
                                        int producedFe, int multiplier) {
    int produced = totalRequired(producedFe, multiplier);
    if (produced <= 0) return true;
    if (outputs == null || outputs.isEmpty()) return false;

    int remaining = produced;
    for (IEnergyStorage output : outputs) {
        if (output == null) continue;
        int received = output.receiveEnergy(remaining, true);
        if (received <= 0) continue;
        remaining -= received;
        if (remaining <= 0) return true;
    }
    return false;
}
```

**注意**：`produceOutputs` 单次 `receiveEnergy(N, false)` 不会破坏现有 hatch 状态（NeoForge `EnergyStorage` 在 receive 已达 maxReceive 时不变），所以失败时无回滚需要。`receiveEnergy` 的返回值才是真正注入量，失败时返回值 < N 但部分能量已写入是可接受的。

### RecipeCraftingContext 新增方法

```java
private List<EnergyHatchBlockEntity> liveEnergyOutputs() {
    return liveComponents(EnergyHatchBlockEntity.class, IOType.OUTPUT, List.of());
}

public List<IEnergyStorage> taggedEnergyOutputs(List<String> requiredTags) {
    return energyStorages(liveComponents(EnergyHatchBlockEntity.class, IOType.OUTPUT, requiredTags));
}

public int taggedAvailableOutputEnergy(List<String> requiredTags) {
    long available = 0;
    for (EnergyHatchBlockEntity hatch :
            liveComponents(EnergyHatchBlockEntity.class, IOType.OUTPUT, requiredTags)) {
        IEnergyStorage storage = hatch.getEnergyStorage(null);
        long headroom = (long) storage.getMaxEnergyStored() - storage.getEnergyStored();
        available += headroom;
        if (available >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
    }
    return (int) available;
}

public List<String> energyOutputComponentTraces(List<String> requiredTags) {
    List<EnergyHatchBlockEntity> matches =
            liveComponents(EnergyHatchBlockEntity.class, IOType.OUTPUT, requiredTags);
    List<EnergyHatchBlockEntity> tagExcluded =
            excludedLiveComponents(EnergyHatchBlockEntity.class, IOType.OUTPUT, requiredTags);
    List<BlockEntity> traces = new ArrayList<>(matches);
    traces.addAll(tagExcluded);
    return componentTraces(traces);
}

public String energyOutputComponentSummary() {
    return liveEnergyOutputs().stream()
            .map(hatch -> hatch.getBlockPos()
                    + ":energy_output_hatch=stored/max="
                    + hatch.getEnergyStorage(null).getEnergyStored()
                    + "/"
                    + hatch.getEnergyStorage(null).getMaxEnergyStored())
            .toList()
            .toString();
}

public boolean simulateEnergyInput(int requirementIndex, EnergyRequirement energy) {
    if (EnergyRecipeIo.canConsumeInputs(
            taggedEnergyStorages(energy.tags()), energy.fePerTick(), 1)) return true;
    long available = taggedAvailableEnergy(energy.tags());
    setRequirementFailure(FAILURE_MISSING_ENERGY, new RequirementFailure(
            requirementIndex,
            RequirementFailure.Kind.MISSING_ENERGY,
            energy.fePerTick(),
            available,
            Math.max(0, energy.fePerTick() - available),
            energyComponentTraces(energy.tags()),
            List.of()));
    return false;
}

public boolean simulateEnergyOutput(int requirementIndex, EnergyRequirement energy) {
    if (EnergyRecipeIo.canProduceOutputs(
            taggedEnergyOutputs(energy.tags()), energy.fePerTick(), 1)) return true;
    long available = taggedAvailableOutputEnergy(energy.tags());
    setRequirementFailure(FAILURE_MISSING_OUTPUT, new RequirementFailure(
            requirementIndex,
            RequirementFailure.Kind.MISSING_OUTPUT,
            energy.fePerTick(),
            available,
            Math.max(0, energy.fePerTick() - available),
            energyOutputComponentTraces(energy.tags()),
            List.of()));
    return false;
}

public boolean collectEnergyInputRoute(int requirementIndex) {
    return true;  // INPUT 在 ioTick 真正执行，commit 阶段总是成功
}

public boolean collectEnergyOutputRoute(int requirementIndex) {
    return true;  // OUTPUT 在 ioTick 真正执行，commit 阶段总是成功
}
```

`commitInputs` / `commitOutputs` 现有循环 `requirement.io() == INPUT / OUTPUT` 与 EnergyRequirement 新分支兼容，无需修改：

```java
for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
    MachineRequirement requirement = requirements.get(requirementIndex);
    if (requirement.io() == RecipeModifier.IOType.INPUT
            && !requirement.commit(this, requirementIndex)) return false;
}
```

### MachineIngredient.EnergyIngredient

```java
record EnergyIngredient(RecipeModifier.IOType io, int fePerTick) implements MachineIngredient {
    public EnergyIngredient(int fePerTick) {
        this(RecipeModifier.IOType.INPUT, fePerTick);
    }

    public EnergyIngredient {
        if (io == null) io = RecipeModifier.IOType.INPUT;
    }

    @Override public String type() { return "energy"; }
}
```

Codec 在 `encode` 中自动写 `io` 字段（与 item / fluid 一致），`decodeByType("energy", ...)` 读 `io` 字段缺省默认 INPUT。

### MachineRequirement.fromInput

```java
static MachineRequirement fromInput(MachineIngredient ingredient) {
    if (ingredient instanceof MachineIngredient.ItemIngredient item) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, item.item(), item.count(), ItemStack.EMPTY);
    }
    if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
        return new FluidRequirement(RecipeModifier.IOType.INPUT, fluid.fluid(), fluid.amount(), FluidStack.EMPTY);
    }
    if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
        return new EnergyRequirement(energy.io(), energy.fePerTick());
    }
    throw new IllegalArgumentException("Unknown machine ingredient: " + ingredient);
}
```

注意：方法名 `fromInput` 在 OUTPUT energy 也被调用是合理的——它现在表示"从任意方向的 ingredient 派生 requirement"，不重命名以保持调用点稳定。

### MachineRecipe.energyOutputs

```java
public List<Integer> energyOutputs() {
    List<Integer> outputs = new ArrayList<>();
    for (MachineRequirement requirement : requirements) {
        if (requirement instanceof EnergyRequirement energy
                && energy.io() == RecipeModifier.IOType.OUTPUT) {
            outputs.add(energy.fePerTick());
        }
    }
    return List.copyOf(outputs);
}
```

`inputs()` 现状已假设 energy 仅出现在 INPUT，保持不变（因为 OUTPUT energy 不会出现在 INPUT ingredient 列表）。

### MachineRequirement.CODEC energy 分支

```java
case "energy" -> {
    DataResult<RecipeModifier.IOType> ioResult = decodeIo(ops, input)
            .map(io -> io == null ? RecipeModifier.IOType.INPUT : io);
    DataResult<Integer> feResult = ops.get(input, "fe_per_tick")
            .flatMap(ops::getNumberValue)
            .map(Number::intValue);
    List<String> tags = decodeTags(ops, input);
    yield ioResult.flatMap(io -> feResult.map(fe -> new EnergyRequirement(io, fe, tags)));
}
```

`decodeIo` 现状对缺失字段返回 `DataResult.error(...)`（`src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java:148-151`）。本 spec 不修改 `decodeIo` 本身，而是在 energy decode 分支用 `.result().orElse(RecipeModifier.IOType.INPUT)` 容忍缺省；item / fluid decode 维持原行为（必须显式 io 字段，与既有 JSON 一致）。

## Data Flow

### startCrafting 路径

```
RecipeSearchTask.find() / ActiveMachineRecipe.start(context)
  ↓
context.startCrafting(recipe)              // = commitInputs(recipe)
  ├─ firstItemInputFailure / firstFluidInputFailure 检查
  ├─ for INPUT requirement: require.commit(this, idx)
  │     └─ EnergyRequirement.commit → context.collectEnergyInputRoute → true
  ├─ for INPUT requirement: collect item / fluid transfers → extract / drain
  └─ return true
```

simulateInputs 已由 `RecipeSearchTask.find` 或 `ActiveMachineRecipe.tick` 提前调用，`simulateEnergyInput` 把"能量不足"翻译成 `RequirementFailure(FAILURE_MISSING_ENERGY)`。commitInputs 阶段 INPUT energy 不再扣能量（per-tick 在 ioTick 中扣），只返回 route 成功。

### ioTick 路径

```
ActiveMachineRecipe.tick(context)
  ├─ if nextTick >= total: context.simulateOutputs(recipe)
  │     └─ for OUTPUT requirement: require.simulate(this, idx)
  │           └─ EnergyRequirement.simulate → context.simulateEnergyOutput
  │                 → canProduceOutputs(...)  // 检查所有 output hatch 剩余空间
  │                 → 不足：FAILURE_MISSING_OUTPUT → return false → STILL 等待
  ├─ context.ioTick(recipe)
  │     └─ for all requirement: require.ioTick(this, idx)
  │           └─ EnergyRequirement.ioTick
  │                 INPUT  → consumeInputs(inputHatches, fePerTick, 1)
  │                 OUTPUT → produceOutputs(outputHatches, fePerTick, 1)
  │           └─ 失败 → context.setRequirementFailure → failureAction()
  ├─ tick++ → if !completed: return CONTINUE
  └─ context.finishCrafting(recipe)        // = commitOutputs(recipe)
        ├─ firstItemOutputFailure / firstFluidOutputFailure 检查
        ├─ for OUTPUT requirement: require.commit(this, idx) → true
        ├─ for OUTPUT requirement: collect item / fluid transfers → insert / fill
        └─ return true
```

### 失败处理

`EnergyRequirement.ioTick` INPUT 失败时设 `FAILURE_MISSING_ENERGY`；OUTPUT 失败时设 `FAILURE_MISSING_OUTPUT`。`ActiveMachineRecipe.doFailureAction(context.failureAction())` 按机器级默认（`STILL`）处理：暂停在当前 tick，下一 tick 重试。

## Error Handling

| 场景 | 处理 |
|---|---|
| OUTPUT hatch 列表空 | `simulateEnergyOutput` 设 failure，`tick` 走 `STILL` 等玩家搭建 output hatch |
| OUTPUT hatch 容量不足 | `simulateEnergyOutput` 设 failure，`tick` 走 `STILL` 等玩家导出能量 |
| OUTPUT hatch 中途移除 | `liveComponents` 已通过 `controller.getComponents()` + `level.getBlockEntity(pos)` 验证，失效组件自动剔除；`simulateEnergyOutput` 返回 false |
| `fePerTick <= 0` | `canProduceOutputs / produceOutputs` 立即返回 `true`（与 `consumeInputs` 对称） |
| `tags` 与 machine pattern tag 不匹配 | `taggedEnergyOutputs` 过滤返回空列表，触发"OUTPUT hatch 列表空"分支 |

## Reaction 配方

`src/main/java/org/nibelungorum/DefaultRecipes.java` 第 83-103 行的 `REACTOR_DIAMOND_WATER`：

```java
if (RecipeRegistry.getRecipe(REACTOR_DIAMOND_WATER_ID) == null) {
    MachineRecipe recipe = new MachineRecipe(
            REACTOR_DIAMOND_WATER_ID,
            REACTOR_ID,
            200,
            List.of(
                    new MachineIngredient.ItemIngredient(Ingredient.of(Items.DIAMOND), 1),
                    new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 500),
                    new MachineIngredient.EnergyIngredient(RecipeModifier.IOType.OUTPUT, 100)  // 方向反转
            ),
            List.of(new ItemStack(Holder.direct(Items.COAL, DataComponentMap.EMPTY), 1)),
            List.of(),
            0,
            1,
            true,
            List.of(new FluidStack(boundFluid(Fluids.LAVA), 500))
    );
    register(recipe);
}
```

`register` 内部统计 totalEnergy 的逻辑（`DefaultRecipes.java:114`）需要识别 OUTPUT energy 方向：现在它仅把 energy ingredient 当作输入统计。

```java
int totalEnergy = recipe.inputs().stream()
        .filter(i -> i instanceof MachineIngredient.EnergyIngredient)
        .mapToInt(i -> ((MachineIngredient.EnergyIngredient) i).fePerTick() * recipe.tickTime())
        .sum();
```

因为 `inputs()` 现在只收集 INPUT energy，OUTPUT energy 不计入 `inputs()`。调整：

```java
int totalEnergyIn = recipe.inputs().stream()
        .filter(i -> i instanceof MachineIngredient.EnergyIngredient)
        .mapToInt(i -> ((MachineIngredient.EnergyIngredient) i).fePerTick() * recipe.tickTime())
        .sum();
int totalEnergyOut = recipe.energyOutputs().stream().mapToInt(fe -> fe * recipe.tickTime()).sum();
```

日志分别报告两个值。

## Tests

### 修改

- `src/test/java/org/nibelungorum/DefaultRecipesTest.java:92 ensureRegistered_publishes_builtin_reactor_recipe`：
  - `recipe.inputs().hasSize(2)` 改为 `hasSize(2)`（item + fluid，energy 不在 inputs）
  - 删除 `recipe.inputs().get(2)` 的 EnergyIngredient 断言
  - 新增 `recipe.energyOutputs()` 包含 `100` 的断言
  - 或断言 `recipe.requirements()` 中 EnergyRequirement 的 io == OUTPUT、fePerTick == 100

### 新增

- `src/test/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirementTest.java`：
  - `output_default_constructor_uses_input`
  - `output_constructor_sets_io_to_output`
  - `io_returns_input_field`
  - `simulate_dispatches_to_simulateEnergyInput_when_input`
  - `simulate_dispatches_to_simulateEnergyOutput_when_output`
  - `ioTick_consumes_when_input`
  - `ioTick_produces_when_output`
- `src/test/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIoTest.java`：
  - `produceOutputs_zero_fe_returns_true`
  - `produceOutputs_empty_hatches_returns_false`
  - `produceOutputs_single_hatch_with_capacity_succeeds`
  - `produceOutputs_single_hatch_without_capacity_fails`
  - `produceOutputs_multiple_hatches_aggregates_capacity`
  - `canProduceOutputs_does_not_mutate_storage`
  - 现有 `EnergyRecipeIo` 的 `consumeInputs / canConsumeInputs` 测试保持

### 现有测试兼容性

- `MachineRecipeCodecRoundtripTest`、`RecipeApiSmokeTest` 等涉及 codec 序列化测试：现有能量 requirement 的 JSON 不含 `io` 字段，decode 默认 INPUT，行为不变。
- `DefaultMachinesTest`、`MachineDefinitionBootstrapTest`、`BuiltinRecipeBootstrapTest`：与能量 IO 方向无关，保持。
- `DefaultRecipesTest` 中高炉 / 裂化器 / idempotent 测试：与方向反转无关，保持。

## Out Of Scope

- `EnergyRequirement` 之外的 IO 修饰符对 OUTPUT energy 的应用（P3A 范围）；当前 P3A modifier 应用路径在 `RecipeCraftingContext` 上游，OUTPUT energy 不需要在本 spec 内联。
- `MachineIngredient` 输出端口多端口场景下"按比例分配"语义：当前 `produceOutputs` 走简单 iterate，遇到首个满 hatch 时回退，可后续按 hatch 容量比例分配。
- 能量 OUTPUT `chance` 修饰：与 item / fluid output chance 同步推进，本 spec 不引入 chance。
- `MachineIngredient.EnergyIngredient(io, fePerTick)` 增加 `chance` 字段：本 spec 不做。
- reaction 第二配方（如燃料 + 水 → 蒸汽 + FE）：保持现状单一配方。

## Acceptance Criteria

- `compileJava` 通过，无 javadoc 警告（沿用既有规范）。
- `test` 通过，覆盖：
  - `EnergyRequirementTest`（OUTPUT 行为）
  - `EnergyRecipeIoTest`（produceOutputs / canProduceOutputs）
  - `DefaultRecipesTest.ensureRegistered_publishes_builtin_reactor_recipe` 验证能量 OUTPUT 方向
  - 现有 codec / API 测试不回归
- 反序列化能量 requirement 时缺省 io 字段 → 默认 INPUT，向后兼容。
- 反序列化 `MachineIngredient.EnergyIngredient` 缺省 io → 默认 INPUT，向后兼容。
- 反应堆机器启动时，配方自动满足 `machine.pattern` 中 `ENERGY_OUTPUT` 端口要求；output hatch 满时 recipe STILL 等待。
- 玩家在 debug 场景下用 wrench / IO 端口搭建反应堆，配方可成功每 tick 向 energy output hatch 推 100 FE。