# Controller Runtime MMCE-Faithful Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port three MMCE 1.12.2 controller-runtime behaviors onto the existing simplified `MachineIngredient` model: per-tick energy drain, fluid output support, and configurable failure actions. Keep `E2ERecipeRunGameTest.ironCompressorRuns` passing.

**Architecture:** Introduce two new types — `MachineOutput` (sealed `ItemOutput`/`FluidOutput`) and `RecipeFailureActions` (`RESET`/`STILL`/`DECREASE`, default `STILL`). Migrate `MachineRecipe.outputs()` to `List<MachineOutput>`. Add `RecipeCraftingContext.ioTick(int)` that drains `fePerTick` per tick. Rewrite `ActiveMachineRecipe.tick()` to call `ioTick` before advancing tick and to apply the configured failure action on `FAILURE`. Wire `Machine`/`DynamicMachine` to expose the failure action.

**Tech Stack:** Java 21, Minecraft 26.1.2 NeoForge, Mojang Codec, existing `MachineIngredient` sealed interface, NeoForge `FluidStack`. No new dependencies.

## Global Constraints

- Spec scope only: no new gameplay features beyond `2026-08-04-controller-runtime-mmce-faithful-design.md`.
- Internal storage of `MachineRecipe.outputs` becomes `List<MachineOutput>`. The 5-arg constructor with `List<ItemStack>` is **kept** as a legacy adapter that wraps each `ItemStack` in `ItemOutput`. The 8-arg constructor signature changes to `List<MachineOutput>` and `PreparedRecipe.toMachineRecipe()` must wrap before calling.
- `RecipeCraftingContext` constructor gains a third `Machine machine` parameter; existing 2-arg callers (`MachineControllerBlockEntity.tryStartNewRecipe`, `loadAdditional`) are updated to pass the bound `machine`.
- `commitInputs(MachineRecipe)` no longer touches energy. Energy is drained exclusively via `ioTick`.
- `ActiveMachineRecipe.doFailureAction(boolean reset)` is removed; `doFailureAction(RecipeFailureActions action)` replaces it.
- `Machine.failureAction()` is a default method returning `STILL`. `DynamicMachine` records add a fifth `failureAction` component and a 4-arg constructor delegating with `STILL`.
- Do not change `RecipeCraftingContext` JSON / NBT persistence shape; the new `Machine` field is transient.
- Do not change `PktMachineStatePayload` / `MachineMenuScreen` contracts.
- Keep `E2ERecipeRunGameTest.ironCompressorRuns` passing (40 ticks, 80 FE/tick drained, energy = 6800 FE remaining, output = 1 iron nugget, input consumed).
- Per AGENTS.md "测试只是辅助", no new tests are required. Verification is `compileJava` + existing GameTest + existing unit tests.
- Project conventions: no comments unless behaviour is non-obvious, no unrelated refactors, no `@author` Javadoc on every new class.
- New top-level classes (`MachineOutput`, `RecipeFailureActions`) **do** carry the project's `@author` Javadoc header per AGENTS.md ("新建类名添加 javadoc 作者信息").

---

## File Structure

**Create:**
- `src/main/java/cn/howxu/mmcr/api/recipe/MachineOutput.java` — sealed interface + `ItemOutput` / `FluidOutput` records + `CODEC`.
- `src/main/java/cn/howxu/mmcr/api/machine/RecipeFailureActions.java` — enum mirroring MMCE.

**Modify:**
- `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java` — `outputs` field, codec, constructors, equals/hashCode, assemble.
- `src/main/java/cn/howxu/mmcr/api/recipe/PreparedRecipe.java` — wrap outputs in `toMachineRecipe()`.
- `src/main/java/cn/howxu/mmcr/api/machine/Machine.java` — default `failureAction()`.
- `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java` — record component + 4-arg constructor.
- `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java` — `Machine` field, `ioTick`, fluid output, remove energy from `commitInputs`.
- `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java` — `tick()` rewrite, `doFailureAction(RecipeFailureActions)`.
- `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java` — pass `machine` to context constructor in `tryStartNewRecipe` and `loadAdditional`.
- `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeBuilderJS.java` — `fluidOutput` builder method.
- `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeSchema.java` — `fluidOutput` schema key (optional, see Task 8).

**No new blocks, no new entities, no new tests.**

---

## Task 1: Add `MachineOutput` sealed interface

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/recipe/MachineOutput.java`

**Interfaces:**
- Consumes: nothing (leaf type).
- Produces:
  - `public sealed interface MachineOutput`
  - `public record ItemOutput(ItemStack stack) implements MachineOutput`
  - `public record FluidOutput(net.neoforged.neoforge.fluids.FluidStack stack) implements MachineOutput`
  - `public Codec<MachineOutput> CODEC` — JSON shape `{ "type": "item"|"fluid", "stack": {ItemStack|FluidStack codec} }`.
  - `String type()` returning `"item"` or `"fluid"`.

- [ ] **Step 1: Create the file**

Create `src/main/java/cn/howxu/mmcr/api/recipe/MachineOutput.java` with this exact content:

```java
package cn.howxu.mmcr.api.recipe;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * @author howxu <dev@howxu.cn>
 */
public sealed interface MachineOutput {

    Codec<MachineOutput> CODEC = Codec.of(MachineOutput::encode, MachineOutput::decode);

    String type();

    private static <T> DataResult<T> encode(MachineOutput output, DynamicOps<T> ops, T prefix) {
        var builder = ops.mapBuilder().add("type", ops.createString(output.type()));
        if (output instanceof ItemOutput item) {
            return builder
                    .add("stack", item.stack(), ItemStack.CODEC)
                    .build(prefix);
        }
        if (output instanceof FluidOutput fluid) {
            return builder
                    .add("stack", fluid.stack(), FluidStack.CODEC)
                    .build(prefix);
        }
        return DataResult.error(() -> "Unknown machine output: " + output);
    }

    private static <T> DataResult<Pair<MachineOutput, T>> decode(DynamicOps<T> ops, T input) {
        return ops.get(input, "type")
                .flatMap(ops::getStringValue)
                .flatMap(type -> decodeByType(type, ops, input))
                .map(output -> Pair.of(output, input));
    }

    private static <T> DataResult<MachineOutput> decodeByType(String type, DynamicOps<T> ops, T input) {
        return switch (type) {
            case "item" -> ops.get(input, "stack")
                    .flatMap(value -> ItemStack.CODEC.parse(ops, value))
                    .map(ItemOutput::new);
            case "fluid" -> ops.get(input, "stack")
                    .flatMap(value -> FluidStack.CODEC.parse(ops, value))
                    .map(FluidOutput::new);
            default -> DataResult.error(() -> "Unknown output type: " + type);
        };
    }

    record ItemOutput(ItemStack stack) implements MachineOutput {
        @Override public String type() {
            return "item";
        }
    }

    record FluidOutput(FluidStack stack) implements MachineOutput {
        @Override public String type() {
            return "fluid";
        }
    }
}
```

- [ ] **Step 2: Compile to confirm the file builds in isolation**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`. The file is currently unused outside itself.

- [ ] **Step 3: Commit**

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/recipe/MachineOutput.java
rtk git commit -m "feat(recipe): add MachineOutput sealed interface with ItemOutput/FluidOutput"
```

---

## Task 2: Add `RecipeFailureActions` enum

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/machine/RecipeFailureActions.java`

**Interfaces:**
- Produces:
  - `public enum RecipeFailureActions { RESET, STILL, DECREASE }`
  - `public static RecipeFailureActions getDefaultAction()` returning `STILL`.

- [ ] **Step 1: Create the file**

Create `src/main/java/cn/howxu/mmcr/api/machine/RecipeFailureActions.java` with this exact content:

```java
package cn.howxu.mmcr.api.machine;

/**
 * @author howxu <dev@howxu.cn>
 */
public enum RecipeFailureActions {
    RESET,
    STILL,
    DECREASE;

    public static RecipeFailureActions getDefaultAction() {
        return STILL;
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/machine/RecipeFailureActions.java
rtk git commit -m "feat(machine): add RecipeFailureActions enum (RESET, STILL, DECREASE)"
```

---

## Task 3: Migrate `MachineRecipe.outputs()` to `List<MachineOutput>`

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/PreparedRecipe.java`

**Interfaces:**
- `MachineRecipe.outputs()` getter now returns `List<MachineOutput>`. Field type changes accordingly.
- Codec `outputs` field switches from `ItemStack.CODEC.listOf()` to `MachineOutput.CODEC.listOf()`.
- 5-arg constructor `MachineRecipe(id, machineId, tickTime, inputs, List<ItemStack> outputs)` is **kept** as a legacy adapter that wraps each `ItemStack` in `ItemOutput` before delegating to the 8-arg constructor.
- 8-arg constructor `MachineRecipe(id, machineId, tickTime, inputs, List<MachineOutput> outputs, modifiers, priority, maxThreads)` becomes the primary.
- `assemble(RecipeInput)` returns the first `ItemOutput.stack().copy()`, or `ItemStack.EMPTY` if the first output is a `FluidOutput` or the list is empty.
- `equals` / `hashCode` compare `List<MachineOutput>` instead of `List<ItemStack>`.
- `PreparedRecipe.toMachineRecipe()` wraps `outputs` in `ItemOutput` before calling the 8-arg `MachineRecipe` constructor.

- [ ] **Step 1: Replace `MachineRecipe.java` with the migrated content**

Replace the contents of `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java` with this exact file:

```java
package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.registry.ModRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MachineRecipe implements Recipe<RecipeInput> {

    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(MachineRecipe::id),
            Identifier.CODEC.fieldOf("machine").forGetter(MachineRecipe::machineId),
            Codec.INT.fieldOf("tick_time").forGetter(MachineRecipe::tickTime),
            MachineIngredient.CODEC.listOf().fieldOf("inputs").forGetter(MachineRecipe::inputs),
            MachineOutput.CODEC.listOf().fieldOf("outputs").forGetter(MachineRecipe::outputs),
            RecipeModifier.CODEC.listOf().optionalFieldOf("modifiers", Collections.emptyList()).forGetter(MachineRecipe::modifiers),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MachineRecipe::priority),
            Codec.INT.optionalFieldOf("max_threads", 1).forGetter(MachineRecipe::maxThreads)
    ).apply(instance, MachineRecipe::new));

    private final Identifier id;
    private final Identifier machineId;
    private final int tickTime;
    private final List<MachineIngredient> inputs;
    private final List<MachineOutput> outputs;
    private final List<RecipeModifier> modifiers;
    private final int priority;
    private final int maxThreads;

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<ItemStack> outputs) {
        this(id, machineId, tickTime, inputs, wrapItemOutputs(outputs), Collections.emptyList(), 0, 1);
    }

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<MachineOutput> outputs,
                         List<RecipeModifier> modifiers,
                         int priority,
                         int maxThreads) {
        if (id == null) {
            throw new IllegalArgumentException("Recipe id must not be null");
        }
        if (machineId == null) {
            throw new IllegalArgumentException("Recipe machineId must not be null");
        }
        this.id = id;
        this.machineId = machineId;
        this.tickTime = Math.max(1, tickTime);
        this.inputs = inputs == null ? Collections.emptyList() : List.copyOf(inputs);
        this.outputs = outputs == null ? Collections.emptyList() : List.copyOf(outputs);
        this.modifiers = modifiers == null ? Collections.emptyList() : List.copyOf(modifiers);
        this.priority = priority;
        this.maxThreads = Math.max(1, maxThreads);
    }

    public Identifier id() {
        return id;
    }

    public Identifier machineId() {
        return machineId;
    }

    public int tickTime() {
        return tickTime;
    }

    public List<MachineIngredient> inputs() {
        return inputs;
    }

    public List<MachineOutput> outputs() {
        return outputs;
    }

    public List<RecipeModifier> modifiers() {
        return modifiers;
    }

    public int priority() {
        return priority;
    }

    public int maxThreads() {
        return maxThreads;
    }

    public Identifier getRegistryName() {
        return id;
    }

    public Identifier getOwningMachineIdentifier() {
        return machineId;
    }

    public int getRecipeTotalTickTime() {
        return tickTime;
    }

    public int getConfiguredPriority() {
        return priority;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return true;
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
        for (MachineOutput output : outputs) {
            if (output instanceof MachineOutput.ItemOutput item) {
                return item.stack().copy();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
        return ModRecipeTypes.MACHINE_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<RecipeInput>> getType() {
        return ModRecipeTypes.MACHINE_RECIPE_TYPE.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return new RecipeBookCategory();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MachineRecipe that)) return false;
        return tickTime == that.tickTime
                && priority == that.priority
                && maxThreads == that.maxThreads
                && id.equals(that.id)
                && machineId.equals(that.machineId)
                && inputs.equals(that.inputs)
                && outputs.equals(that.outputs)
                && modifiers.equals(that.modifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads);
    }

    private static List<MachineOutput> wrapItemOutputs(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return Collections.emptyList();
        }
        List<MachineOutput> wrapped = new java.util.ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            wrapped.add(new MachineOutput.ItemOutput(stack));
        }
        return wrapped;
    }
}
```

- [ ] **Step 2: Update `PreparedRecipe.toMachineRecipe()` to wrap outputs**

Open `src/main/java/cn/howxu/mmcr/api/recipe/PreparedRecipe.java` and replace the `toMachineRecipe()` method (lines 90–101) with:

```java
    public MachineRecipe toMachineRecipe() {
        List<MachineOutput> wrappedOutputs = outputs == null || outputs.isEmpty()
                ? java.util.Collections.emptyList()
                : java.util.stream.Collectors.toUnmodifiableList(outputs, stack -> new MachineOutput.ItemOutput(stack));
        return new MachineRecipe(
                net.minecraft.resources.Identifier.parse(registryName),
                net.minecraft.resources.Identifier.parse(machineId),
                tickTime,
                inputs,
                wrappedOutputs,
                modifiers,
                priority,
                maxThreads
        );
    }
```

Note: the helper call uses the fully-qualified name because `Stream.toUnmodifiableList` lives in `java.util.stream.Collectors`. If the project already uses `var` or a different import style, mirror the surrounding `java.util` import style. If `java.util.stream.Collectors` is not currently imported, add `import java.util.stream.Collectors;` at the top of the file.

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`. All call sites (GameTest, KubeJS builder, tests) keep working because the 5-arg `List<ItemStack>` constructor is preserved.

- [ ] **Step 4: Run existing recipe unit tests**

Run: `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.recipe.RecipeApiSmokeTest --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest`
Expected: `BUILD SUCCESSFUL`. Existing `recipe_codec_*` and `prepared_recipe_*` tests pass. They exercise the codec round-trip and the legacy constructor — both remain correct.

- [ ] **Step 5: Commit**

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java src/main/java/cn/howxu/mmcr/api/recipe/PreparedRecipe.java
rtk git commit -m "feat(recipe): migrate MachineRecipe.outputs to List<MachineOutput>"
```

---

## Task 4: Add `failureAction` to `Machine` interface and `DynamicMachine` record

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/Machine.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java`

**Interfaces:**
- `Machine` sealed interface gains `default RecipeFailureActions failureAction() { return RecipeFailureActions.STILL; }`. No other methods change.
- `DynamicMachine` record gains `RecipeFailureActions failureAction` as the fifth component.
- Existing 3-arg constructor `DynamicMachine(registryName, localizedName, pattern)` keeps working by delegating to the new 5-arg constructor with `STILL` and the default `controller` spec.
- Existing 4-arg constructor `DynamicMachine(registryName, localizedName, pattern, controller)` keeps working by delegating with `STILL`.

- [ ] **Step 1: Modify `Machine.java`**

Replace `src/main/java/cn/howxu/mmcr/api/machine/Machine.java` with this exact content:

```java
package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

public sealed interface Machine permits DynamicMachine {
    Identifier registryName();

    String localizedName();

    BlockArray pattern();

    MachineControllerSpec controller();

    default RecipeFailureActions failureAction() {
        return RecipeFailureActions.STILL;
    }
}
```

- [ ] **Step 2: Modify `DynamicMachine.java`**

Replace `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java` with this exact content:

```java
package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

public record DynamicMachine(
        Identifier registryName,
        String localizedName,
        BlockArray pattern,
        MachineControllerSpec controller,
        RecipeFailureActions failureAction) implements Machine {
    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern) {
        this(registryName, localizedName, pattern, MachineControllerSpec.defaultsFor(registryName), RecipeFailureActions.STILL);
    }

    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern, MachineControllerSpec controller) {
        this(registryName, localizedName, pattern, controller, RecipeFailureActions.STILL);
    }

    public DynamicMachine {
        if (registryName == null) throw new IllegalArgumentException("registryName null");
        if (localizedName == null) throw new IllegalArgumentException("localizedName null");
        if (pattern == null) throw new IllegalArgumentException("pattern null");
        if (controller == null) throw new IllegalArgumentException("controller null");
        if (failureAction == null) throw new IllegalArgumentException("failureAction null");
    }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`. All existing `new DynamicMachine(...)` call sites that used 3-arg or 4-arg constructors keep working.

- [ ] **Step 4: Run existing unit tests**

Run: `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest`
Expected: `BUILD SUCCESSFUL`. The `registry_filters_recipes_by_machine_and_rejects_null_id` test uses the 3-arg `DynamicMachine` constructor; auto-generated `equals` / `hashCode` cover the new `failureAction` field correctly.

- [ ] **Step 5: Commit**

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/machine/Machine.java src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java
rtk git commit -m "feat(machine): add failureAction to Machine/DynamicMachine"
```

---

## Task 5: Refactor `RecipeCraftingContext`

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`

**Interfaces:**
- Adds:
  - `public enum IoTickResult { SUCCESS, FAILURE }` (nested static enum on the class).
  - `public IoTickResult ioTick(int currentTick)` — for each `MachineIngredient.EnergyIngredient` in the recipe, scan 3×3 for an `EnergyInputHatchBlockEntity`, simulate `extractEnergy(fePerTick, true)`. If simulated `< fePerTick`, return `FAILURE`. Otherwise commit `extractEnergy(fePerTick, false)` and continue. If no hatch matched at all, return `FAILURE`.
  - `public Machine machine()` accessor.
  - `private List<FluidOutputSlot> fluidOutputSlots()` and `private List<FluidOutputSlotState> fluidOutputSlotStates()` — mirrors of the existing `outputSlots()` / `outputSlotStates()` for fluid output hatches.
- Removes:
  - The energy branch in `commitInputs(MachineRecipe)`.
  - The `recipe` parameter usage from `findAndCheckEnergyHatch` — replaced by a new helper `findEnergyHatchForTick(int fePerTick)` used by `ioTick`.
- Changes:
  - Constructor gains a third `Machine machine` parameter. Signature becomes `RecipeCraftingContext(Level, BlockPos, Machine)`.
  - `simulateOutputs(MachineRecipe)` and `commitOutputs(MachineRecipe)` iterate over `MachineOutput`: `ItemOutput` uses the existing item logic; `FluidOutput` uses the new fluid logic against `FluidOutputHatchBlockEntity` blocks in the 3×3 ring.
  - Existing item-output logic (`OutputSlot`, `OutputSlotState`, `outputSlots`, `outputSlotStates`) is preserved unchanged.

- [ ] **Step 1: Replace `RecipeCraftingContext.java` with the refactored content**

Replace the entire contents of `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java` with this exact file:

```java
package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

public final class RecipeCraftingContext {

    public enum IoTickResult {
        SUCCESS,
        FAILURE
    }

    private final Level level;
    private final BlockPos controllerPos;
    private final Machine machine;

    public RecipeCraftingContext(Level level, BlockPos controllerPos, Machine machine) {
        this.level = level;
        this.controllerPos = controllerPos;
        this.machine = machine;
    }

    public Machine machine() {
        return machine;
    }

    public IoTickResult ioTick(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
                EnergyInputHatchBlockEntity hatch = findEnergyHatchForTick(energy.fePerTick());
                if (hatch == null) {
                    return IoTickResult.FAILURE;
                }
                int simulated = hatch.getEnergyStorage(null).extractEnergy(energy.fePerTick(), true);
                if (simulated < energy.fePerTick()) {
                    return IoTickResult.FAILURE;
                }
                hatch.getEnergyStorage(null).extractEnergy(energy.fePerTick(), false);
            }
        }
        return IoTickResult.SUCCESS;
    }

    public boolean simulateInputs(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item && findAndCheckItemBus(item) == null) return false;
            if (ingredient instanceof MachineIngredient.FluidIngredient fluid && findAndCheckFluidHatch(fluid) == null) return false;
            if (ingredient instanceof MachineIngredient.EnergyIngredient energy && findEnergyHatchForTick(energy.fePerTick()) == null) return false;
        }
        return true;
    }

    public boolean simulateOutputs(MachineRecipe recipe) {
        for (MachineOutput output : recipe.outputs()) {
            if (output instanceof MachineOutput.ItemOutput item) {
                if (!simulateItemOutput(item.stack())) return false;
            } else if (output instanceof MachineOutput.FluidOutput fluid) {
                if (!simulateFluidOutput(fluid.stack())) return false;
            }
        }
        return true;
    }

    public boolean commitOutputs(MachineRecipe recipe) {
        for (MachineOutput output : recipe.outputs()) {
            if (output instanceof MachineOutput.ItemOutput item) {
                if (!commitItemOutput(item.stack())) return false;
            } else if (output instanceof MachineOutput.FluidOutput fluid) {
                if (!commitFluidOutput(fluid.stack())) return false;
            }
        }
        return true;
    }

    public boolean commitInputs(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item) {
                ItemInputBusBlockEntity bus = findAndCheckItemBus(item);
                if (bus == null) continue;
                IItemHandler handler = bus.getItemHandler(null);
                int left = item.count();
                for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (item.item().test(stack)) {
                        int taken = Math.min(left, stack.getCount());
                        handler.extractItem(slot, taken, false);
                        left -= taken;
                    }
                }
            } else if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
                FluidInputHatchBlockEntity hatch = findAndCheckFluidHatch(fluid);
                if (hatch != null) hatch.getFluidHandler(null).drain(fluid.amount(), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        return true;
    }

    private boolean simulateItemOutput(ItemStack output) {
        ItemStack remaining = output.copy();
        for (OutputSlotState slot : outputSlotStates()) {
            if (remaining.isEmpty()) break;
            remaining = slot.insert(remaining);
        }
        return remaining.isEmpty();
    }

    private boolean commitItemOutput(ItemStack output) {
        ItemStack remaining = output.copy();
        for (OutputSlot slot : outputSlots()) {
            if (remaining.isEmpty()) break;
            remaining = slot.handler().insertItem(slot.slot(), remaining, false);
        }
        return remaining.isEmpty();
    }

    private boolean simulateFluidOutput(FluidStack stack) {
        FluidStack probe = stack.copy();
        for (FluidOutputSlotState slot : fluidOutputSlotStates()) {
            FluidStack remainder = slot.fill(probe, true);
            if (remainder.isEmpty()) {
                return true;
            }
            probe = remainder;
        }
        return false;
    }

    private boolean commitFluidOutput(FluidStack stack) {
        FluidStack remaining = stack.copy();
        for (FluidOutputSlot slot : fluidOutputSlots()) {
            if (remaining.isEmpty()) break;
            remaining = slot.handler().fill(remaining, IFluidHandler.FluidAction.EXECUTE);
        }
        return remaining.isEmpty();
    }

    private ItemInputBusBlockEntity findAndCheckItemBus(MachineIngredient.ItemIngredient ingredient) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof ItemInputBusBlockEntity bus) {
                IItemHandler handler = bus.getItemHandler(null);
                int count = 0;
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (ingredient.item().test(stack)) count += stack.getCount();
                }
                if (count >= ingredient.count()) return bus;
            }
        }
        return null;
    }

    private FluidInputHatchBlockEntity findAndCheckFluidHatch(MachineIngredient.FluidIngredient ingredient) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof FluidInputHatchBlockEntity hatch
                    && ingredient.fluid().test(hatch.getFluidHandler(null).getFluidInTank(0))
                    && hatch.getFluidHandler(null).getFluidInTank(0).getAmount() >= ingredient.amount()) return hatch;
        }
        return null;
    }

    private EnergyInputHatchBlockEntity findEnergyHatchForTick(int fePerTick) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof EnergyInputHatchBlockEntity hatch
                    && hatch.getEnergyStorage(null).getEnergyStored() >= fePerTick) return hatch;
        }
        return null;
    }

    private List<OutputSlot> outputSlots() {
        List<OutputSlot> slots = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof ItemOutputBusBlockEntity bus) {
                IItemHandler handler = bus.getItemHandler(null);
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    slots.add(new OutputSlot(handler, slot));
                }
            }
        }
        return slots;
    }

    private List<OutputSlotState> outputSlotStates() {
        return outputSlots().stream().map(OutputSlotState::new).toList();
    }

    private List<FluidOutputSlot> fluidOutputSlots() {
        List<FluidOutputSlot> slots = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof FluidOutputHatchBlockEntity hatch) {
                IFluidHandler handler = hatch.getFluidHandler(null);
                for (int slot = 0; slot < handler.getTanks(); slot++) {
                    slots.add(new FluidOutputSlot(handler, slot));
                }
            }
        }
        return slots;
    }

    private List<FluidOutputSlotState> fluidOutputSlotStates() {
        return fluidOutputSlots().stream().map(FluidOutputSlotState::new).toList();
    }

    private record OutputSlot(IItemHandler handler, int slot) {}

    private static final class OutputSlotState {
        private final OutputSlot slot;
        private ItemStack stack;

        private OutputSlotState(OutputSlot slot) {
            this.slot = slot;
            this.stack = slot.handler().getStackInSlot(slot.slot()).copy();
        }

        private ItemStack insert(ItemStack input) {
            ItemStack accepted = input.copy();
            ItemStack simulatedRemainder = slot.handler().insertItem(slot.slot(), accepted, true);
            accepted.shrink(simulatedRemainder.getCount());
            if (accepted.isEmpty()) return input;
            if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, accepted)) return input;

            int limit = Math.min(slot.handler().getSlotLimit(slot.slot()), accepted.getMaxStackSize());
            int space = stack.isEmpty() ? limit : limit - stack.getCount();
            int inserted = Math.min(space, accepted.getCount());
            if (inserted <= 0) return input;

            if (stack.isEmpty()) {
                stack = accepted.copyWithCount(inserted);
            } else {
                stack.grow(inserted);
            }
            ItemStack remaining = input.copy();
            remaining.shrink(inserted);
            return remaining;
        }
    }

    private record FluidOutputSlot(IFluidHandler handler, int slot) {}

    private static final class FluidOutputSlotState {
        private final FluidOutputSlot slot;
        private FluidStack stack;

        private FluidOutputSlotState(FluidOutputSlot slot) {
            this.slot = slot;
            this.stack = slot.handler().getFluidInTank(slot.slot()).copy();
        }

        private FluidStack fill(FluidStack input, boolean simulate) {
            FluidStack accepted = input.copy();
            FluidStack simulatedRemainder = slot.handler().fill(accepted, IFluidHandler.FluidAction.SIMULATE);
            accepted.shrink(simulatedRemainder.getAmount());
            if (accepted.isEmpty()) return input;
            if (!stack.isEmpty() && !stack.getFluid().equals(accepted.getFluid())) return input;

            int capacity = slot.handler().getTankCapacity(slot.slot());
            int space = stack.isEmpty() ? capacity : capacity - stack.getAmount();
            int inserted = Math.min(space, accepted.getAmount());
            if (inserted <= 0) return input;

            if (stack.isEmpty()) {
                stack = accepted.copyWithAmount(inserted);
            } else {
                stack.grow(inserted);
            }
            FluidStack remaining = input.copy();
            remaining.shrink(inserted);
            return remaining;
        }
    }
}
```

Notes on the file above:

- `ioTick(MachineRecipe recipe)` takes the recipe explicitly (no `machineRecipe()` accessor indirection on the context). `ActiveMachineRecipe.tick` (Task 6) passes `recipe` directly.
- `simulateFluidOutput` walks the slot states, refilling the probe as it goes, so a single output can spread across multiple tank slots of the same fluid (matches the item-output behavior).
- `FluidOutputSlotState.fill(...)` mirrors the existing `OutputSlotState.insert(...)` semantics for tanks: simulate first, accept only matching fluid or empty, clamp by tank capacity.

- [ ] **Step 2: Compile (expect failure — `MachineControllerBlockEntity` still passes the old constructor)**

Run: `./gradlew compileJava --no-daemon`
Expected: compile error in `MachineControllerBlockEntity.java` referencing `RecipeCraftingContext` constructor. This is expected and fixed in Task 7. **Do not commit yet.** Move to Task 6 next, then continue.

(Note: the plan intentionally breaks the build between Task 5 and Task 6. Each refactored class is committed in Task 5/6 only after Task 7 restores compileability. If desired, skip running `compileJava` here and run it once after Task 7 instead.)

---

## Task 6: Rewrite `ActiveMachineRecipe.tick()` and `doFailureAction()`

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java`

**Interfaces:**
- Removes: `public void doFailureAction(boolean reset)`.
- Adds: `public void doFailureAction(RecipeFailureActions action)` — `RESET` → `tick = 0`; `STILL` → no change; `DECREASE` → `if (tick > 0) tick--;`.
- `tick(RecipeCraftingContext context)` is rewritten per the spec runtime flow:
  1. If `recipe == null` or `total <= 0` → return `TickStatus.WAITING`.
  2. If `isCompleted()` → return `TickStatus.WAITING` (controller clears next tick).
  3. Probe `IoTickResult probe = context.ioTick(recipe)`.
  4. `SUCCESS`:
     - Compute `int nextTick = Math.min(getTick() + 1, total)` and `setTick(nextTick)`.
     - If `isCompleted()`: re-check via `simulateOutputs(recipe)` and `simulateInputs(recipe)`. If either fails, set `tick = Math.max(0, total - 1)` and return `WAITING`. Otherwise `commitOutputs(recipe)` then `commitInputs(recipe)`. If `commitOutputs` returns false or `commitInputs` returns false, set `tick = Math.max(0, total - 1)` and return `WAITING`. Otherwise return `FINISHED`.
     - Otherwise → `CONTINUE`.
  5. `FAILURE`: `doFailureAction(context.machine().failureAction())`. Return `WAITING`.

- [ ] **Step 1: Replace the `tick` and `doFailureAction` methods**

Open `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java`. Replace the existing `doFailureAction` method (currently lines 104–110) with:

```java
    public void doFailureAction(RecipeFailureActions action) {
        switch (action) {
            case RESET -> this.tick = 0;
            case STILL -> { /* no change */ }
            case DECREASE -> {
                if (this.tick > 0) this.tick--;
            }
        }
    }
```

Replace the existing `tick(RecipeCraftingContext context)` method (currently lines 154–182) with:

```java
    public TickStatus tick(RecipeCraftingContext context) {
        if (recipe == null) {
            return TickStatus.WAITING;
        }
        int total = getTotalTick();
        if (total <= 0) {
            return TickStatus.WAITING;
        }
        if (isCompleted()) {
            return TickStatus.WAITING;
        }

        RecipeCraftingContext.IoTickResult probe = context.ioTick(recipe);
        if (probe == RecipeCraftingContext.IoTickResult.FAILURE) {
            doFailureAction(context.machine().failureAction());
            return TickStatus.WAITING;
        }

        int nextTick = Math.min(getTick() + 1, total);
        setTick(nextTick);

        if (!isCompleted()) {
            return TickStatus.CONTINUE;
        }

        if (!context.simulateOutputs(recipe) || !context.simulateInputs(recipe)) {
            setTick(Math.max(0, total - 1));
            return TickStatus.WAITING;
        }

        boolean outputsOk = context.commitOutputs(recipe);
        boolean inputsOk = context.commitInputs(recipe);
        if (!outputsOk || !inputsOk) {
            setTick(Math.max(0, total - 1));
            return TickStatus.WAITING;
        }

        return TickStatus.FINISHED;
    }
```

Add the import at the top of the file (next to the existing `cn.howxu.mmcr` package):

```java
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
```

- [ ] **Step 2: Compile (expect failure — `MachineControllerBlockEntity` still passes the old constructor)**

Run: `./gradlew compileJava --no-daemon`
Expected: compile error in `MachineControllerBlockEntity.java` for `RecipeCraftingContext` constructor. Task 7 fixes this. Do not commit yet.

---

## Task 7: Update `MachineControllerBlockEntity` to pass `machine` to context

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`

**Interfaces:**
- `tryStartNewRecipe()` builds context with `new RecipeCraftingContext(level, getBlockPos(), machine)`.
- `loadAdditional(CompoundTag)` rebuilds context with `new RecipeCraftingContext(level, getBlockPos(), boundMachine)` where `boundMachine = MachineRegistry.getMachine(savedMachineId)`; if the saved id no longer resolves, the existing drop-active-recipe behavior is preserved.

- [ ] **Step 1: Update `tryStartNewRecipe`**

Open `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`. Locate the `tryStartNewRecipe` method. Change the line that builds the context:

- Replace:
  ```java
              RecipeCraftingContext candidate = new RecipeCraftingContext(level, getBlockPos());
  ```
- With:
  ```java
              RecipeCraftingContext candidate = new RecipeCraftingContext(level, getBlockPos(), machine);
  ```

- [ ] **Step 2: Update `loadAdditional`**

Locate the `loadAdditional` method. The existing structure is:

```java
        ActiveMachineRecipe restored = new ActiveMachineRecipe(recipeTag);
        if (restored.getRecipe() == null) {
            active = null;
            context = null;
            return;
        }
        active = restored;
        context = new RecipeCraftingContext(level, getBlockPos());
        setChanged();
```

Replace the two-line block that assigns `active` and rebuilds `context` with:

```java
        Machine boundMachine = MachineRegistry.getMachine(restored.getRecipe().machineId());
        if (boundMachine == null) {
            active = null;
            context = null;
            return;
        }
        active = restored;
        context = new RecipeCraftingContext(level, getBlockPos(), boundMachine);
        setChanged();
```

Add an import for `cn.howxu.mmcr.api.machine.MachineRegistry` at the top of the file if not already present.

Rationale: if the saved recipe's machine id no longer resolves (recipe datapack removed, machine registry cleared), the controller drops the active recipe — same pattern as the existing `if (restored.getRecipe() == null)` guard. Otherwise, the context is rebuilt with the live machine reference so `failureAction()` lookup at tick time always sees the current value.

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`. Tasks 5 + 6 + 7 together restore compileability.

- [ ] **Step 4: Run the GameTest**

Run: `./gradlew gametest --no-daemon --tests cn.howxu.mmcr.E2ERecipeRunGameTest`
Expected: `ironCompressorRuns` passes. 40 ticks drain 3200 FE (energy hatch starts at 10000, ends at 6800), 2 iron ingots consumed, 1 iron nugget inserted.

If the runner is not configured for GameTest, fall back to `./gradlew build --no-daemon` and confirm `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java
rtk git commit -m "feat(runtime): per-tick energy drain via ioTick; wire failureAction; add fluid output support"
```

---

## Task 8: KubeJS binding — `fluidOutput` on builder and schema

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeBuilderJS.java`
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeSchema.java`

**Interfaces:**
- `MachineRecipeBuilderJS.outputs` field changes from `List<ItemStack>` to `List<MachineOutput>`. The existing `itemOutput(...)` builder wraps the `ItemStack` in `MachineOutput.ItemOutput` and appends.
- `MachineRecipeBuilderJS` gains a `fluidOutput(String fluidId, int amount)` builder method that wraps a `FluidStack` in `MachineOutput.FluidOutput` and appends.
- `MachineRecipeBuilderJS.build()` calls `new MachineRecipe(..., List.copyOf(outputs))` which now takes `List<MachineOutput>` (the 5-arg legacy adapter constructor is `List<ItemStack>`, so this needs the static factory or an explicit cast). To avoid overload ambiguity, build the recipe via the 8-arg constructor and pass `MachineOutput` directly:

  ```java
  RecipeRegistry.register(new MachineRecipe(id, machine.registryName(), tickTime, List.copyOf(recipeInputs), List.copyOf(outputs), java.util.Collections.emptyList(), 0, 1));
  ```
- `MachineRecipeSchema` is **not** modified in this step. The existing `OUTPUTS` key already passes JSON elements to the builder; KubeJS scripts that emit a JSON object with `"type": "fluid"` flow into the codec on the datapack side. The builder-side `fluidOutput(...)` is the only API addition. This is intentional and matches the spec's "minimal KubeJS additions, no existing API changes".

- [ ] **Step 1: Update `MachineRecipeBuilderJS`**

Replace `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeBuilderJS.java` with this exact content:

```java
package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MachineRecipeBuilderJS {
    public Machine machine;
    public int tickTime = 40;
    public final List<MachineIngredient> inputs = new ArrayList<>();
    public final List<MachineOutput> outputs = new ArrayList<>();
    public int energyPerTick = 0;

    private Identifier id;

    public MachineRecipeBuilderJS(String id) {
        this.id = Identifier.parse(id);
    }

    public MachineRecipeBuilderJS(Identifier id) {
        this.id = id;
    }

    public MachineRecipeBuilderJS id(String id) {
        this.id = Identifier.parse(id);
        return this;
    }

    public MachineRecipeBuilderJS machine(String id) {
        var machineId = Identifier.parse(id);
        this.machine = MachineRegistry.getMachine(machineId);

        if (this.machine == null) {
            throw new IllegalArgumentException("Machine not found: " + id);
        }

        return this;
    }

    public MachineRecipeBuilderJS tickTime(int tickTime) {
        this.tickTime = tickTime;
        return this;
    }

    public MachineRecipeBuilderJS itemInput(String itemId, int count) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        inputs.add(new MachineIngredient.ItemIngredient(Ingredient.of(item), count));
        return this;
    }

    public MachineRecipeBuilderJS itemOutput(String itemId, int count) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        outputs.add(new MachineOutput.ItemOutput(new ItemStack(item, count)));
        return this;
    }

    public MachineRecipeBuilderJS fluidOutput(String fluidId, int amount) {
        var fluid = BuiltInRegistries.FLUID.getValue(Identifier.parse(fluidId));
        if (fluid == null) {
            throw new IllegalArgumentException("Fluid not found: " + fluidId);
        }
        outputs.add(new MachineOutput.FluidOutput(new FluidStack(fluid, amount)));
        return this;
    }

    public MachineRecipeBuilderJS energyPerTick(int energyPerTick) {
        this.energyPerTick = energyPerTick;
        return this;
    }

    public void build() {
        if (machine == null) {
            throw new IllegalStateException("machine() not called");
        }

        var recipeInputs = new ArrayList<>(inputs);

        if (energyPerTick > 0) {
            recipeInputs.add(new MachineIngredient.EnergyIngredient(energyPerTick));
        }

        RecipeRegistry.register(new MachineRecipe(
                id,
                machine.registryName(),
                tickTime,
                List.copyOf(recipeInputs),
                List.copyOf(outputs),
                Collections.emptyList(),
                0,
                1
        ));
    }
}
```

Note: `BuiltInRegistries.FLUID` exists in NeoForge 26.1.x as the canonical fluid registry accessor; if the project uses a different accessor (e.g. `Registries.FLUID`), mirror whatever the rest of the codebase already uses for fluid id parsing. Verify by searching for `BuiltInRegistries.FLUID` or `Registries.FLUID` references in adjacent files.

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run existing KubeJS compile / build**

Run: `./gradlew build --no-daemon`
Expected: `BUILD SUCCESSFUL`. The KubeJS adapter is loaded at runtime, but the class compile path is what matters here. If `build` is configured to skip KubeJS when not present, that's fine.

- [ ] **Step 4: Commit**

```bash
rtk git add src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeBuilderJS.java
rtk git commit -m "feat(kubejs): add fluidOutput() to MachineRecipeBuilderJS"
```

---

## Task 9: Final verification

**Files:**
- No new code changes expected.
- Optional inspect-only: `src/main/java/cn/howxu/mmcr/client/gui/MachineMenuScreen.java` (confirm `getActiveRecipe()` + `getTickCounter()` still produce the same values).

**Interfaces:**
- No API changes. This task is a final compile + GameTest + spot-check pass.

- [ ] **Step 1: Full project compile + build**

Run: `./gradlew build --no-daemon`
Expected: `BUILD SUCCESSFUL`. Any pre-existing unrelated build blocker that appears here is recorded as a known issue (per spec acceptance criterion 5) and is not fixed by this refactor.

- [ ] **Step 2: Re-run the GameTest from a clean state**

Run: `./gradlew clean gametest --no-daemon --tests cn.howxu.mmcr.E2ERecipeRunGameTest`
Expected: `BUILD SUCCESSFUL` and the `ironCompressorRuns` test passes against a clean build. 40 ticks, input consumed, output inserted, energy = 6800 FE.

- [ ] **Step 3: Spot-check GUI / network payload contract**

Open `src/main/java/cn/howxu/mmcr/client/gui/MachineMenuScreen.java` and confirm:
- It still calls `owner.getActiveRecipe()` and `owner.getTickCounter()` only.
- The translated status string keys and progress text remain unchanged.

Open `src/main/java/cn/howxu/mmcr/internal/network/PktMachineStatePayload.java` and confirm:
- The payload still takes the active recipe id as a `String` and the formed state as a `boolean`, matching the values produced by `broadcastState()` in the controller.

If a divergence surfaces, fix it in the smallest possible way (controller-side getter adjustment, not GUI / network changes) and rerun `./gradlew compileJava --no-daemon`. Otherwise no code change is needed.

- [ ] **Step 4: Final commit (only if step 3 needed a tweak)**

If step 3 surfaced and required a fix:

```bash
rtk git add src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java
rtk git commit -m "fix(controller): align getters with GUI and network payload contract"
```

If no code change was required, skip this commit and end the plan here.

---

## Self-Review Notes

- **Spec coverage:**
  - Goals (per-tick energy, fluid output, failure actions) — Tasks 5, 6, 7.
  - `MachineOutput` sealed interface — Task 1.
  - `RecipeFailureActions` enum, `Machine.failureAction()` default, `DynamicMachine` field — Tasks 2, 4.
  - `MachineRecipe.outputs` migration — Task 3 (with `PreparedRecipe` caller updated).
  - KubeJS `fluidOutput` — Task 8.
  - `E2ERecipeRunGameTest.ironCompressorRuns` kept passing — Tasks 7 and 9 verification gates.
- **Placeholder scan:** no `TBD` / `TODO` / "implement later" placeholders. Task 5 step 1 originally had a `machineRecipe()` stub that was inlined into the `ioTick(recipe)` redesign within the same step. Task 7 step 2 has a "locate every `new RecipeCraftingContext(...)` call" instruction because the exact line number depends on how `loadAdditional` evolves; the surrounding sentence gives the engineer enough to find it.
- **Type consistency:**
  - `MachineOutput` records `ItemOutput` / `FluidOutput` are referenced consistently across Tasks 1, 3, 5, 8.
  - `MachineOutput.CODEC` JSON shape (`type` + `stack`) is referenced in Task 1 and consumed in Task 3.
  - `RecipeCraftingContext.IoTickResult.SUCCESS` / `FAILURE` are referenced consistently across Tasks 5, 6.
  - `RecipeCraftingContext(Level, BlockPos, Machine)` constructor is used in Task 5 and updated in Task 7.
  - `doFailureAction(RecipeFailureActions)` is the only overload across Tasks 5, 6.
  - `Machine.failureAction()` default STILL is referenced in Tasks 4 and 6.