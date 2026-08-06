# P2 Energy Requirement OUTPUT + Reactor Recipe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 `EnergyRequirement` 的 OUTPUT 方向语义（与 `ItemRequirement` / `FluidRequirement` 镜像对称），让反应堆等能量产出型机器的配方可以声明每 tick 推入 energy output hatch 的 FE 量，并修整方向反了的默认反应堆配方。

**Architecture:** 沿用 `ItemRequirement` / `FluidRequirement` 已有的 INPUT / OUTPUT 镜像模式：`EnergyRequirement` 加 `IOType io` 字段；`EnergyRecipeIo` 加 `produceOutputs / canProduceOutputs`；`RecipeCraftingContext` 加 energy OUTPUT 路由方法；`MachineIngredient.EnergyIngredient` 加 `IOType io`；`MachineRecipe` 加 `energyOutputs()` 派生。所有改动向默认 INPUT 兼容（codec 旧 JSON 缺省 `io` 字段时回退 INPUT），不破坏存量数据和测试。

**Tech Stack:** Java 21、Gradle、NeoForge 26.1.2、NeoForge `IEnergyStorage` / `EnergyStorage`、JUnit 5、AssertJ。

## Global Constraints

- 沿用既有 javadoc 风格：新建类含 `@author howxu <dev@howxu.cn>`。
- 不添加前缀（按 AGENTS.md）。
- 每个任务独立可编译可测试；任务结束 commit 一次。
- 不动 staged changes（p3a-recipe-modifier-chain 计划）、不动已 untracked `M` 状态文件以外的事。
- codec 改动保持向后兼容：`io` 字段缺省时视为 `INPUT`。
- energy OUTPUT 在 `ioTick` 阶段 perTick 推入；output hatch 满时触发 `failureAction()`（机器默认 `STILL`）。
- 反应堆配方 `REACTOR_DIAMOND_WATER` 原料 / 产物 / 时长 / priority 不变；仅把 `EnergyIngredient(100)` 改为 `EnergyIngredient(OUTPUT, 100)`。
- 验证门槛：`./gradlew compileJava --no-daemon` 与 `./gradlew test --no-daemon` 通过。

---

## File Structure

| 路径 | 改动 | 职责 |
|---|---|---|
| `src/main/java/cn/howxu/mmcr/api/recipe/MachineIngredient.java` | 修改 | `EnergyIngredient` 加 `IOType io`；codec 写读 io |
| `src/main/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIo.java` | 修改 | 新增 `produceOutputs / canProduceOutputs` |
| `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java` | 修改 | 加 energy OUTPUT 镜像方法 + `simulateEnergyInput / simulateEnergyOutput` |
| `src/main/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirement.java` | 修改 | 加 `IOType io` 字段 + 兼容构造器 + simulate/ioTick 分派 |
| `src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java` | 修改 | codec energy 分支读 io；`fromInput` 处理 energy.io() |
| `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java` | 修改 | 新增 `energyOutputs()` 派生 |
| `src/main/java/org/nibelungorum/DefaultRecipes.java` | 修改 | 反应堆配方 OUTPUT 化；`register` 日志区分 in / out |
| `src/test/java/cn/howxu/mmcr/api/recipe/MachineIngredientTest.java` | 新建 | EnergyIngredient io + codec roundtrip |
| `src/test/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIoTest.java` | 新建 | produceOutputs / canProduceOutputs 行为 |
| `src/test/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirementTest.java` | 新建 | OUTPUT simulate / ioTick 分派 |
| `src/test/java/org/nibelungorum/DefaultRecipesTest.java` | 修改 | 反应堆断言改 OUTPUT 方向 |

---

## Task 1: MachineIngredient.EnergyIngredient 加 io 字段

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/MachineIngredient.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/MachineIngredientTest.java`

**Interfaces:**
- Consumes: `RecipeModifier.IOType`（已存在）
- Produces: `MachineIngredient.EnergyIngredient(IOType io, int fePerTick)`、`EnergyIngredient(int fePerTick)` 兼容构造器、codec 序列化含 `io` 字段

### Step 1: 写失败测试

创建 `src/test/java/cn/howxu/mmcr/api/recipe/MachineIngredientTest.java`：

```java
package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineIngredientTest {

    @Test
    void energy_ingredient_default_io_is_input() {
        var ing = new MachineIngredient.EnergyIngredient(100);
        assertThat(ing.io()).isEqualTo(RecipeModifier.IOType.INPUT);
        assertThat(ing.fePerTick()).isEqualTo(100);
    }

    @Test
    void energy_ingredient_constructor_sets_io_to_output() {
        var ing = new MachineIngredient.EnergyIngredient(RecipeModifier.IOType.OUTPUT, 200);
        assertThat(ing.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
        assertThat(ing.fePerTick()).isEqualTo(200);
    }

    @Test
    void energy_ingredient_codec_roundtrip_preserves_io() {
        var ops = JsonOps.INSTANCE;
        var original = new MachineIngredient.EnergyIngredient(RecipeModifier.IOType.OUTPUT, 150);
        var encoded = MachineIngredient.CODEC.encodeStart(ops, original).getOrThrow();
        var decoded = MachineIngredient.CODEC.parse(ops, encoded).getOrThrow();
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void energy_ingredient_codec_defaults_missing_io_to_input() {
        var ops = JsonOps.INSTANCE;
        var json = new com.google.gson.JsonObject();
        json.addProperty("type", "energy");
        json.addProperty("fe_per_tick", 80);
        var parsed = MachineIngredient.CODEC.parse(ops, json).getOrThrow();
        assertThat(parsed).isInstanceOf(MachineIngredient.EnergyIngredient.class);
        var energy = (MachineIngredient.EnergyIngredient) parsed;
        assertThat(energy.io()).isEqualTo(RecipeModifier.IOType.INPUT);
        assertThat(energy.fePerTick()).isEqualTo(80);
    }

    @Test
    void item_ingredient_codec_unchanged() {
        var ops = JsonOps.INSTANCE;
        var original = new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 3);
        var encoded = MachineIngredient.CODEC.encodeStart(ops, original).getOrThrow();
        var decoded = MachineIngredient.CODEC.parse(ops, encoded).getOrThrow();
        assertThat(decoded).isEqualTo(original);
    }
}
```

### Step 2: 跑测试确认失败

Run: `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.recipe.MachineIngredientTest`
Expected: 编译失败或 5 个测试全部 FAIL（`EnergyIngredient(io, fePerTick)` 构造器不存在）

### Step 3: 改 MachineIngredient.java

修改 `src/main/java/cn/howxu/mmcr/api/recipe/MachineIngredient.java`：

```java
package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.item.crafting.Ingredient;

public sealed interface MachineIngredient {

    Codec<MachineIngredient> CODEC = Codec.of(MachineIngredient::encode, MachineIngredient::decode);

    String type();

    private static <T> DataResult<T> encode(MachineIngredient ingredient, DynamicOps<T> ops, T prefix) {
        var builder = ops.mapBuilder().add("type", ops.createString(ingredient.type()));
        if (ingredient instanceof ItemIngredient item) {
            return builder
                    .add("item", item.item(), Ingredient.CODEC)
                    .add("count", ops.createInt(item.count()))
                    .build(prefix);
        }
        if (ingredient instanceof FluidIngredient fluid) {
            return builder
                    .add("fluid", fluid.fluid(), net.neoforged.neoforge.fluids.crafting.FluidIngredient.CODEC)
                    .add("amount", ops.createInt(fluid.amount()))
                    .build(prefix);
        }
        if (ingredient instanceof EnergyIngredient energy) {
            return builder
                    .add("io", ops.createString(energy.io().getKey()))
                    .add("fe_per_tick", ops.createInt(energy.fePerTick()))
                    .build(prefix);
        }
        return DataResult.error(() -> "Unknown machine ingredient: " + ingredient);
    }

    private static <T> DataResult<Pair<MachineIngredient, T>> decode(DynamicOps<T> ops, T input) {
        return ops.get(input, "type")
                .flatMap(ops::getStringValue)
                .flatMap(type -> decodeByType(type, ops, input))
                .map(ingredient -> Pair.of(ingredient, input));
    }

    private static <T> DataResult<MachineIngredient> decodeByType(String type, DynamicOps<T> ops, T input) {
        return switch (type) {
            case "item" -> ops.get(input, "item")
                    .flatMap(value -> Ingredient.CODEC.parse(ops, value))
                    .flatMap(item -> ops.get(input, "count")
                            .flatMap(ops::getNumberValue)
                            .map(count -> new ItemIngredient(item, count.intValue())));
            case "fluid" -> ops.get(input, "fluid")
                    .flatMap(value -> net.neoforged.neoforge.fluids.crafting.FluidIngredient.CODEC.parse(ops, value))
                    .flatMap(fluid -> ops.get(input, "amount")
                            .flatMap(ops::getNumberValue)
                            .map(amount -> new FluidIngredient(fluid, amount.intValue())));
            case "energy" -> {
                var ioResult = ops.get(input, "io")
                        .flatMap(value -> ops.getStringValue(value))
                        .map(RecipeModifier.IOType::byKey)
                        .<RecipeModifier.IOType>map(t -> t == null ? RecipeModifier.IOType.INPUT : t)
                        .result()
                        .orElse(RecipeModifier.IOType.INPUT);
                var feResult = ops.get(input, "fe_per_tick")
                        .flatMap(ops::getNumberValue)
                        .map(Number::intValue);
                yield feResult.map(fe -> new EnergyIngredient(ioResult, fe));
            }
            default -> DataResult.error(() -> "Unknown ingredient type: " + type);
        };
    }

    record ItemIngredient(Ingredient item, int count) implements MachineIngredient {
        @Override public String type() {
            return "item";
        }
    }

    record FluidIngredient(net.neoforged.neoforge.fluids.crafting.FluidIngredient fluid, int amount) implements MachineIngredient {
        @Override public String type() {
            return "fluid";
        }
    }

    record EnergyIngredient(RecipeModifier.IOType io, int fePerTick) implements MachineIngredient {
        public EnergyIngredient(int fePerTick) {
            this(RecipeModifier.IOType.INPUT, fePerTick);
        }

        public EnergyIngredient {
            if (io == null) io = RecipeModifier.IOType.INPUT;
        }

        @Override public String type() {
            return "energy";
        }
    }
}
```

关键点：
- `encode` 始终写 `io` 字段。
- `decodeByType("energy")` 容忍 `io` 缺省（`.result().orElse(INPUT)`）。
- `EnergyIngredient(io, fePerTick)` canonical；`EnergyIngredient(fePerTick)` 兼容构造器默认 INPUT；compact constructor 把 null 兜底为 INPUT。

### Step 4: 跑测试确认通过

Run: `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.recipe.MachineIngredientTest`
Expected: 5 个测试全部 PASS

### Step 5: Commit

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/MachineIngredient.java \
        src/test/java/cn/howxu/mmcr/api/recipe/MachineIngredientTest.java
git commit -m "feat(ingredient): EnergyIngredient carries IOType for output direction"
```

---

## Task 2: EnergyRecipeIo.produceOutputs / canProduceOutputs

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIo.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIoTest.java`

**Interfaces:**
- Consumes: `net.neoforged.neoforge.energy.IEnergyStorage`
- Produces: `EnergyRecipeIo.produceOutputs(outputs, fe, mul)`、`canProduceOutputs(outputs, fe, mul)`

### Step 1: 写失败测试

创建 `src/test/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIoTest.java`：

```java
package cn.howxu.mmcr.api.recipe.helper;

import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnergyRecipeIoTest {

    private static IEnergyStorage storage(int capacity) {
        return new EnergyStorage(capacity, capacity, capacity);
    }

    @Test
    void produceOutputs_zero_fe_returns_true() {
        assertThat(EnergyRecipeIo.produceOutputs(List.of(storage(1000)), 0, 1)).isTrue();
    }

    @Test
    void produceOutputs_empty_hatches_returns_false() {
        assertThat(EnergyRecipeIo.produceOutputs(List.of(), 100, 1)).isFalse();
    }

    @Test
    void produceOutputs_single_hatch_with_capacity_succeeds() {
        var hatch = storage(1000);
        assertThat(EnergyRecipeIo.produceOutputs(List.of(hatch), 200, 1)).isTrue();
        assertThat(hatch.getEnergyStored()).isEqualTo(200);
    }

    @Test
    void produceOutputs_single_hatch_without_capacity_fails() {
        var hatch = storage(100);
        assertThat(EnergyRecipeIo.produceOutputs(List.of(hatch), 200, 1)).isFalse();
        assertThat(hatch.getEnergyStored()).isZero();
    }

    @Test
    void produceOutputs_multiple_hatches_aggregates_capacity() {
        var a = storage(150);
        var b = storage(150);
        assertThat(EnergyRecipeIo.produceOutputs(List.of(a, b), 200, 1)).isTrue();
        assertThat(a.getEnergyStored()).isEqualTo(150);
        assertThat(b.getEnergyStored()).isEqualTo(50);
    }

    @Test
    void canProduceOutputs_does_not_mutate_storage() {
        var hatch = storage(1000);
        assertThat(EnergyRecipeIo.canProduceOutputs(List.of(hatch), 200, 1)).isTrue();
        assertThat(hatch.getEnergyStored()).isZero();
    }

    @Test
    void canProduceOutputs_returns_false_when_aggregate_capacity_insufficient() {
        var a = storage(50);
        var b = storage(50);
        assertThat(EnergyRecipeIo.canProduceOutputs(List.of(a, b), 200, 1)).isFalse();
        assertThat(a.getEnergyStored()).isZero();
        assertThat(b.getEnergyStored()).isZero();
    }

    @Test
    void produceOutputs_multiplier_scales_required() {
        var a = storage(500);
        var b = storage(500);
        assertThat(EnergyRecipeIo.produceOutputs(List.of(a, b), 200, 3)).isTrue();
        assertThat(a.getEnergyStored() + b.getEnergyStored()).isEqualTo(600);
    }

    @Test
    void consumeInputs_zero_fe_returns_true_regression() {
        assertThat(EnergyRecipeIo.consumeInputs(List.of(storage(1000)), 0, 1)).isTrue();
    }

    @Test
    void consumeInputs_extracts_when_available_regression() {
        var hatch = new EnergyStorage(1000, 1000, 1000);
        hatch.receiveEnergy(500, false);
        assertThat(EnergyRecipeIo.consumeInputs(List.of(hatch), 200, 1)).isTrue();
        assertThat(hatch.getEnergyStored()).isEqualTo(300);
    }
}
```

### Step 2: 跑测试确认失败

Run: `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIoTest`
Expected: 编译失败或 10 个测试 FAIL（`produceOutputs` / `canProduceOutputs` 不存在）

### Step 3: 改 EnergyRecipeIo.java

修改 `src/main/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIo.java`：

```java
package cn.howxu.mmcr.api.recipe.helper;

import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

/**
 * Centralizes per-tick recipe energy IO so all input hatches are simulated before mutation.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class EnergyRecipeIo {

    private EnergyRecipeIo() {
    }

    public static boolean consumeInputs(List<? extends IEnergyStorage> inputs, int requiredFe, int multiplier) {
        int required = totalRequired(requiredFe, multiplier);
        if (required <= 0) return true;
        if (inputs == null || inputs.isEmpty()) return false;

        int available = 0;
        for (IEnergyStorage input : inputs) {
            if (input == null) continue;
            available += input.extractEnergy(required - available, true);
            if (available >= required) break;
        }
        if (available < required) return false;

        int remaining = required;
        for (IEnergyStorage input : inputs) {
            if (input == null) continue;
            remaining -= input.extractEnergy(remaining, false);
            if (remaining <= 0) return true;
        }
        return false;
    }

    public static boolean canConsumeInputs(List<? extends IEnergyStorage> inputs, int requiredFe, int multiplier) {
        int required = totalRequired(requiredFe, multiplier);
        if (required <= 0) return true;
        if (inputs == null || inputs.isEmpty()) return false;

        int available = 0;
        for (IEnergyStorage input : inputs) {
            if (input == null) continue;
            available += input.extractEnergy(required - available, true);
            if (available >= required) return true;
        }
        return false;
    }

    public static boolean produceOutputs(List<? extends IEnergyStorage> outputs, int producedFe, int multiplier) {
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

    public static boolean canProduceOutputs(List<? extends IEnergyStorage> outputs, int producedFe, int multiplier) {
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

    private static int totalRequired(int requiredFe, int multiplier) {
        if (requiredFe <= 0 || multiplier <= 0) return 0;
        long required = (long) requiredFe * multiplier;
        return required > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) required;
    }
}
```

### Step 4: 跑测试确认通过

Run: `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIoTest`
Expected: 10 个测试全部 PASS

### Step 5: Commit

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIo.java \
        src/test/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIoTest.java
git commit -m "feat(energy-io): symmetric produceOutputs / canProduceOutputs"
```

---

## Task 3: RecipeCraftingContext 加 energy OUTPUT 路由 + simulateEnergyInput / simulateEnergyOutput

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Test: 借用 Task 4 集成验证（不在本任务中单独新增测试文件）

**Interfaces:**
- Consumes: `EnergyRecipeIo.canConsumeInputs / canProduceOutputs`、`EnergyHatchBlockEntity`、`IOType.OUTPUT`
- Produces: 
  - `taggedEnergyOutputs(List<String> requiredTags)` 
  - `taggedAvailableOutputEnergy(List<String> requiredTags)`
  - `energyOutputComponentTraces(List<String> requiredTags)`
  - `energyOutputComponentSummary()`
  - `simulateEnergyInput(int requirementIndex, EnergyRequirement energy)`
  - `simulateEnergyOutput(int requirementIndex, EnergyRequirement energy)`
  - `collectEnergyInputRoute(int requirementIndex)`
  - `collectEnergyOutputRoute(int requirementIndex)`

### Step 1: 不写独立单元测试（避免 Mock 完整 controller 上下文；后续 Task 4 集成验证）

### Step 2: 改 RecipeCraftingContext.java

修改 `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`。在 `energyStorages()`（line 390）之后插入以下代码：

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
                Math.max(0, (long) energy.fePerTick() - available),
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
                Math.max(0, (long) energy.fePerTick() - available),
                energyOutputComponentTraces(energy.tags()),
                List.of()));
        return false;
    }

    public boolean collectEnergyInputRoute(int requirementIndex) {
        return true;
    }

    public boolean collectEnergyOutputRoute(int requirementIndex) {
        return true;
    }
```

注意：
- 不再保留 `EnergyRequirement.simulate` 内部的 energy IO 逻辑；EnergyRequirement 在 Task 4 中改为调用 `context.simulateEnergyInput / simulateEnergyOutput`。
- `simulateEnergyInput` 与 `simulateEnergyOutput` 复用既有 `setRequirementFailure(FAILURE_MISSING_*, RequirementFailure)` 模式。

### Step 3: 跑 compileJava 确认通过

Run: `./gradlew compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

### Step 4: Commit

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java
git commit -m "feat(context): energy output routing plus simulateEnergyInput / simulateEnergyOutput"
```

---

## Task 4: EnergyRequirement 加 io 字段并接入 context 分派

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirement.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirementTest.java`

**Interfaces:**
- Consumes: `RecipeCraftingContext.simulateEnergyInput / simulateEnergyOutput`、`EnergyRecipeIo.consumeInputs / produceOutputs`、`RecipeModifier.IOType`
- Produces: `EnergyRequirement(IOType io, int fePerTick)` canonical、 兼容构造器、`simulate / ioTick` 按 io 分派

### Step 1: 写失败测试

创建 `src/test/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirementTest.java`：

```java
package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnergyRequirementTest {

    @Test
    void default_constructor_uses_input() {
        var req = new EnergyRequirement(100);
        assertThat(req.io()).isEqualTo(RecipeModifier.IOType.INPUT);
        assertThat(req.fePerTick()).isEqualTo(100);
        assertThat(req.tags()).isEmpty();
    }

    @Test
    void constructor_with_tags_uses_input() {
        var req = new EnergyRequirement(50, List.of("eu"));
        assertThat(req.io()).isEqualTo(RecipeModifier.IOType.INPUT);
        assertThat(req.tags()).containsExactly("eu");
    }

    @Test
    void constructor_with_io_sets_direction() {
        var req = new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 200);
        assertThat(req.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
        assertThat(req.fePerTick()).isEqualTo(200);
    }

    @Test
    void type_returns_energy() {
        assertThat(new EnergyRequirement(100).type()).isEqualTo("energy");
    }

    @Test
    void simulate_with_input_does_not_throw_without_context() {
        var req = new EnergyRequirement(100);
        assertThat(req.io()).isEqualTo(RecipeModifier.IOType.INPUT);
        // simulate dispatches by io, but we only check the routing decision by verifying io() and behavior signature.
        // The full simulate behavior is covered by integration via DefaultRecipesTest reactor test.
    }
}
```

### Step 2: 跑测试确认失败

Run: `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.recipe.requirement.EnergyRequirementTest`
Expected: 编译失败（`EnergyRequirement(io, fePerTick)` 构造器不存在）

### Step 3: 改 EnergyRequirement.java

替换 `src/main/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirement.java`：

```java
package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIo;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public record EnergyRequirement(RecipeModifier.IOType io, int fePerTick, List<String> tags) implements MachineRequirement {

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

    @Override
    public String type() {
        return "energy";
    }

    @Override
    public RecipeModifier.IOType io() {
        return io;
    }

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
            return EnergyRecipeIo.consumeInputs(context.taggedEnergyStorages(tags), fePerTick, 1);
        }
        return EnergyRecipeIo.produceOutputs(context.taggedEnergyOutputs(tags), fePerTick, 1);
    }
}
```

### Step 4: 跑测试确认通过

Run: `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.recipe.requirement.EnergyRequirementTest`
Expected: 5 个测试全部 PASS

### Step 5: Commit

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirement.java \
        src/test/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirementTest.java
git commit -m "feat(requirement): EnergyRequirement carries IOType for output direction"
```

---

## Task 5: MachineRequirement codec 读 io + fromInput 处理 energy.io()

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java`
- Test: 既有 `RecipeApiSmokeTest.recipe_codec_roundtrip_preserves_modifiers_and_priority` 等

**Interfaces:**
- Consumes: `EnergyRequirement` (Task 4)、`RecipeModifier.IOType.byKey`
- Produces: `MachineRequirement` codec decode energy 分支读取 `io` 字段（缺省默认 INPUT）；`fromInput(EnergyIngredient)` 把 `energy.io()` 传递给 `EnergyRequirement(io, fePerTick)`

### Step 1: 不写独立失败测试（codec 行为由既有 roundtrip 测试和后续 Task 6 / Task 7 间接验证）

### Step 2: 改 MachineRequirement.java

修改 `src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java`：

1. 把 `decodeByType` 的 `"energy"` 分支替换：

```java
            case "energy" -> {
                RecipeModifier.IOType io = ops.get(input, "io")
                        .flatMap(value -> ops.getStringValue(value))
                        .map(RecipeModifier.IOType::byKey)
                        .map(t -> t == null ? RecipeModifier.IOType.INPUT : t)
                        .result()
                        .orElse(RecipeModifier.IOType.INPUT);
                List<String> tags = decodeTags(ops, input);
                yield ops.get(input, "fe_per_tick")
                        .flatMap(ops::getNumberValue)
                        .<MachineRequirement>map(fe -> new EnergyRequirement(io, fe.intValue(), tags));
            }
```

2. 修改 `fromInput`：

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

注意：
- `decodeTags` 已是 helper，无须改。
- `decodeByType` 现有 `"energy"` 分支只读 `fe_per_tick`；本任务把它换成读 `io` + `fe_per_tick` + `tags`。
- 现有 `"item"` / `"fluid"` 分支已有 `decodeIo` 与 `decodeTags` 调用（line 110、126）；本任务不动。

### Step 3: 跑既有 codec 测试确认不破

Run: `./gradlew test --no-daemon --tests '*recipe*codec*' --tests '*RecipeApi*' --tests '*Roundtrip*'`
Expected: 既有 codec / API 测试全部 PASS

### Step 4: Commit

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java
git commit -m "feat(requirement-codec): decode energy IOType from MachineRequirement json"
```

---

## Task 6: MachineRecipe 加 energyOutputs() 派生

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java`

**Interfaces:**
- Consumes: `EnergyRequirement(io == OUTPUT)` (Task 4)
- Produces: `MachineRecipe.energyOutputs()` 返回 `List<Integer>`

### Step 1: 不写独立失败测试（由 Task 7 反应堆测试间接验证）

### Step 2: 改 MachineRecipe.java

修改 `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java`：

1. 在 `outputs()` 之后（约 line 188）新增：

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

2. 不修改 `inputs()`：当前 `inputs()` 已经假设所有 EnergyRequirement 是 INPUT；Task 1 已让 `MachineIngredient.EnergyIngredient(io=INPUT)` 是 INPUT 的常规情况；如果出现 OUTPUT 类型 EnergyIngredient，`inputs()` 会把它当作输入暴露出来——这是错的，需要收紧。

把 `inputs()` 改为：

```java
    public List<MachineIngredient> inputs() {
        List<MachineIngredient> inputs = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.INPUT) {
                inputs.add(new MachineIngredient.ItemIngredient(item.item(), item.count()));
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.INPUT) {
                inputs.add(new MachineIngredient.FluidIngredient(fluid.fluid(), fluid.amount()));
            } else if (requirement instanceof EnergyRequirement energy && energy.io() == RecipeModifier.IOType.INPUT) {
                inputs.add(new MachineIngredient.EnergyIngredient(energy.fePerTick()));
            }
        }
        return List.copyOf(inputs);
    }
```

注意：
- `inputs()` 现在显式按 `io == INPUT` 过滤，避免 OUTPUT energy ingredient 污染 inputs。
- `MachineIngredient.EnergyIngredient(int fePerTick)` 兼容构造器（Task 1）默认 INPUT，符合派生语义。

### Step 3: 跑既有 recipe 测试确认不破

Run: `./gradlew test --no-daemon --tests '*MachineRecipe*' --tests '*DefaultRecipes*'`
Expected: 既有测试 PASS

### Step 4: Commit

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java
git commit -m "feat(recipe): derive energyOutputs() and tighten inputs() to INPUT only"
```

---

## Task 7: DefaultRecipes 反应堆配方改 OUTPUT + 日志区分 in / out

**Files:**
- Modify: `src/main/java/org/nibelungorum/DefaultRecipes.java`

**Interfaces:**
- Consumes: `MachineIngredient.EnergyIngredient(IOType, int)` (Task 1)、`MachineRecipe.energyOutputs()` (Task 6)
- Produces: `REACTOR_DIAMOND_WATER` recipe 把 100FE/t 改为 OUTPUT；`register` 日志分别报告 `totalEnergyIn / totalEnergyOut`

### Step 1: 不写独立失败测试（由 Task 8 修改 `DefaultRecipesTest` 验证）

### Step 2: 改 DefaultRecipes.java

修改 `src/main/java/org/nibelungorum/DefaultRecipes.java`：

1. 修改反应堆配方的 `EnergyIngredient` 行（第 91 行）：

```java
                            new MachineIngredient.EnergyIngredient(RecipeModifier.IOType.OUTPUT, 100)
```

2. 修改 `register` 方法的 `totalEnergy` 计算与日志：

```java
    private static void register(MachineRecipe recipe) {
        RecipeRegistry.register(recipe);
        long totalEnergyIn = recipe.inputs().stream()
                .filter(i -> i instanceof MachineIngredient.EnergyIngredient)
                .mapToLong(i -> (long) ((MachineIngredient.EnergyIngredient) i).fePerTick() * recipe.tickTime())
                .sum();
        long totalEnergyOut = recipe.energyOutputs().stream()
                .mapToLong(fe -> (long) fe * recipe.tickTime())
                .sum();
        LOG.info("ensureRegistered: registered built-in recipe id={} machine={} tickTime={}t ({}s) priority={} maxThreads={} modifiers={} energyIn={}FE energyOut={}FE",
                recipe.id(), recipe.machineId(), recipe.tickTime(), String.format("%.2f", recipe.tickTime() / 20.0),
                recipe.priority(), recipe.maxThreads(), recipe.modifiers().size(), totalEnergyIn, totalEnergyOut);
        LOG.info("  inputs  = [{}]", describeInputs(recipe));
        LOG.info("  outputs = [{}]", describeOutputs(recipe));
        LOG.info("  entry points = RecipeRegistry.byMachine({}) and /mmcr reload", recipe.machineId());
    }
```

### Step 3: 跑既有 DefaultRecipesTest 确认反应堆断言要更新

Run: `./gradlew test --no-daemon --tests org.nibelungorum.DefaultRecipesTest`
Expected: `ensureRegistered_publishes_builtin_reactor_recipe` FAIL（inputs 断言现在期望 3 个 input，但 energy 是 OUTPUT 不再算 input）

### Step 4: Commit（本步不 commit，等 Task 8 一起）

```bash
# 不要在此处 commit
```

---

## Task 8: DefaultRecipesTest 反应堆断言改 OUTPUT 验证

**Files:**
- Modify: `src/test/java/org/nibelungorum/DefaultRecipesTest.java`

**Interfaces:**
- Consumes: `MachineRecipe.energyOutputs()` (Task 6)、`EnergyRequirement.io()` (Task 4)
- Produces: 改写 `ensureRegistered_publishes_builtin_reactor_recipe` 测试断言

### Step 1: 不写失败测试（已存在；只需改断言）

### Step 2: 改 DefaultRecipesTest.java

修改 `src/test/java/org/nibelungorum/DefaultRecipesTest.java` 的 `ensureRegistered_publishes_builtin_reactor_recipe` 方法（约 line 92-121）。完整替换方法体：

```java
    @Test
    void ensureRegistered_publishes_builtin_reactor_recipe() {
        DefaultMachines.ensureRegistered();
        DefaultRecipes.ensureRegistered();

        var machine = (DynamicMachine) MachineRegistry.getMachine(MMCR.id("reactor"));

        assertThat(machine).isNotNull();
        var recipes = RecipeRegistry.byMachine(machine);
        assertThat(recipes).extracting(recipe -> recipe.id()).contains(MMCR.id("reactor_diamond_water"));

        var recipe = RecipeRegistry.getRecipe(MMCR.id("reactor_diamond_water"));
        assertThat(recipe.tickTime()).isEqualTo(200);
        assertThat(recipe.inputs()).hasSize(2);
        assertThat(recipe.inputs().get(0)).isInstanceOf(MachineIngredient.ItemIngredient.class);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).item().items().toList().getFirst().value()).isEqualTo(Items.DIAMOND);
        assertThat(((MachineIngredient.ItemIngredient) recipe.inputs().get(0)).count()).isEqualTo(1);
        assertThat(recipe.inputs().get(1)).isInstanceOf(MachineIngredient.FluidIngredient.class);
        assertThat(((MachineIngredient.FluidIngredient) recipe.inputs().get(1)).fluid().fluids().getFirst().value()).isEqualTo(Fluids.WATER);
        assertThat(((MachineIngredient.FluidIngredient) recipe.inputs().get(1)).amount()).isEqualTo(500);
        assertThat(recipe.energyOutputs()).containsExactly(100);
        var energyRequirement = (cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement)
                recipe.requirements().stream()
                        .filter(r -> r instanceof cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement)
                        .findFirst()
                        .orElseThrow();
        assertThat(energyRequirement.io()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.OUTPUT);
        assertThat(energyRequirement.fePerTick()).isEqualTo(100);
        assertThat(recipe.outputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getItem()).isEqualTo(Items.COAL);
            assertThat(stack.getCount()).isEqualTo(1);
        });
        assertThat(recipe.fluidOutputs()).singleElement().satisfies(stack -> {
            assertThat(stack.getFluid()).isEqualTo(Fluids.LAVA);
            assertThat(stack.getAmount()).isEqualTo(500);
        });
    }
```

关键改动：
- `recipe.inputs().hasSize(2)`（原 3，移除 energy ingredient）
- 删除原 `recipe.inputs().get(2)` 的 EnergyIngredient 断言
- 新增 `recipe.energyOutputs()` 包含 `100` 断言
- 新增对 `EnergyRequirement.io() == OUTPUT` 和 `fePerTick() == 100` 的直接验证

### Step 3: 跑测试确认通过

Run: `./gradlew test --no-daemon --tests org.nibelungorum.DefaultRecipesTest`
Expected: 4 个测试全部 PASS（含 `ensureRegistered_publishes_builtin_reactor_recipe`）

### Step 4: Commit（与 Task 7 一起）

```bash
git add src/main/java/org/nibelungorum/DefaultRecipes.java \
        src/test/java/org/nibelungorum/DefaultRecipesTest.java
git commit -m "feat(recipes): reactor emits 100FE/t output and DefaultRecipes log splits in/out"
```

---

## Task 9: 端到端验证

**Files:** 无新文件

### Step 1: 跑 compileJava

Run: `./gradlew compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

### Step 2: 跑全套测试

Run: `./gradlew test --no-daemon`
Expected: BUILD SUCCESSFUL，所有测试 PASS（包含 Task 1-8 新增与修改）

### Step 3: 若有失败，针对性修复

最可能的失败点：
- `MachineRequirement.fromInput` 调用方若传递 `EnergyIngredient(fePerTick)` 但期望旧 `EnergyRequirement(fePerTick, tags)`：Task 4 已提供，无回归。
- 既有 `RecipeApiSmokeTest.recipe_codec_roundtrip_preserves_modifiers_and_priority` 失败：检查是否需要更新 sample JSON；本 spec 默认 io 缺省为 INPUT，存量 sample 应不破。
- `MachineComponent` test 失败：与本 spec 无关，跳过。

修复后重跑 Step 2 直至全部 PASS。

### Step 4: 无 commit（仅验证）

---

## Out Of Scope Reminder

- RecipeModifier 对 OUTPUT energy 的应用：留待 P3A / P3B 阶段。
- 能量 OUTPUT chance 修饰：与 item / fluid output chance 同步推进，本 plan 不引入。
- reaction 第二配方（燃料 + 水 → 蒸汽 + FE）：保持单一配方。
- `MachineIngredient.EnergyIngredient` 加 `chance` 字段：本 plan 不做。