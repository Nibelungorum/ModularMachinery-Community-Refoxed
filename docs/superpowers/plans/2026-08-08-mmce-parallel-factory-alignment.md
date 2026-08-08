# MMCE Parallel Factory Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework MMCR parallelism and factory execution to match MMCE semantics as closely as possible while preserving NeoForge 26.1.2 compatibility.

**Architecture:** Recipe search selects a startable recipe and context only; parallel amount is calculated at start/restart using MMCE-style `RecipeCraftingContext.canStartCrafting()` semantics and GTCEu-style input-first/output-limit calculation. Factory execution is modeled as MMCE-like recipe threads with independent active recipe, context, status, modifiers, restart behavior, and shared remaining parallelism budget.

**Tech Stack:** Java 25, Minecraft 26.1.2, NeoForge 26.1.2, Gradle, JUnit 5, AssertJ, existing MMCR recipe/component APIs.

## Global Constraints

- Do not run `./gradlew runClient --no-daemon`.
- Do not upgrade Gradle, NeoForge, Minecraft, or core dependencies.
- Follow existing MMCR package layout and naming; do not add `MMCR` prefixes to new class names.
- New Java classes must include javadoc author line: `@author howxu <dev@howxu.cn>`.
- Preserve current dynamic machine controller and dynamic I/O overlay client model behavior.
- Keep max parallelism as `int`; support `Integer.MAX_VALUE` as the maximum safe Java positive parallel value.
- Do not use fixed search candidates such as `512/256/64/16/4/1` for recipe selection.
- Prefer MMCE behavior over local simplifications when the two differ.

---

## Current Gap Summary

- Current `RecipeSearchTask` has been corrected to search at 1x, but `ActiveMachineRecipe.start()` still owns parallel calculation instead of a context-level MMCE `canStartCrafting()` API.
- Current factory implementation uses `FactoryRecipeLane`, which is close to a simple lane but not close enough to MMCE `RecipeThread` / `FactoryRecipeThread` semantics.
- Current factory lane startup passes `getMaxParallelism()` to every lane, so lanes can over-allocate total parallel budget unless startup happens to fail from shared inputs.
- Current factory implementation lacks MMCE concepts: core thread presets, thread recipe filters, permanent/semi-permanent modifiers per thread, idle timeout cleanup, restart handling, and per-thread status.
- Current parallel calculation uses binary search over full recipe simulation; GTCEu has a more optimized input-first then output-limit model that should be ported in MMCR terms.
- Current recipe requirements use `int` counts/amounts and `Math.multiplyExact`; this must be protected before allowing `Integer.MAX_VALUE` parallel controllers broadly.

## Reference Behavior To Match

- MMCE `ActiveMachineRecipe.canStartCrafting(context)` calls `calculateExtraParallelism(context)` and then delegates to `RecipeCraftingContext.canStartCrafting()`.
- MMCE `RecipeCraftingContext.canStartCrafting()` calculates max parallel from all parallelizable requirements, calls `setParallelism(max)`, then checks startability.
- MMCE `RecipeThread` owns `activeRecipe`, `context`, `status`, permanent modifiers, semi-permanent modifiers, and `searchTask`.
- MMCE `FactoryRecipeThread` extends `RecipeThread`, supports core threads, optional recipe set, idle timeout, and restart.
- MMCE `TileFactoryController.getAvailableParallelism()` returns factory total parallelism minus active thread parallelism.
- GTCEu `ParallelLogic.getParallelAmount()` first limits by inputs, then limits by output merging, avoiding repeated full simulation when possible.

---

## File Structure

- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java`  
  Own serialized recipe state, duration refresh, `canStartCrafting`, `canRestartCrafting`, `start`, and `tick` transitions.
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`  
  Own MMCE-style context-level start/restart/finish checks, max parallel calculation, route preparation, and parallel-safe requirement scaling.
- Create: `src/main/java/cn/howxu/mmcr/api/recipe/ParallelRecipeCalculator.java`  
  Own GTCEu-inspired max-parallel calculation by input and output limits. Pure calculation wrapper over existing context simulations.
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchTask.java`  
  Keep search at 1x and return context without applying craft parallelism.
- Create: `src/main/java/cn/howxu/mmcr/internal/recipe/RecipeThread.java`  
  MMCE-like base recipe thread for controller/factory execution.
- Create: `src/main/java/cn/howxu/mmcr/internal/recipe/MachineRecipeThread.java`  
  Single-controller thread adapter used by `MachineControllerBlockEntity` outside factory mode.
- Create: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeThread.java`  
  Factory thread implementation with core-thread flag, recipe filters, idle timeout, restart, and status.
- Replace: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeLane.java`  
  Remove after `FactoryRecipeThread` covers its responsibilities.
- Modify: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeScheduler.java`  
  Turn scheduler into MMCE-style thread manager: core threads, simple threads, pending searches, idle cleanup, available parallelism.
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/FactoryControllerBlockEntity.java`  
  Store thread limit, scheduler, serialization hooks, status/count helpers, and cleanup lifecycle.
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`  
  Delegate recipe execution to `MachineRecipeThread` or factory scheduler and expose available parallelism helpers.
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/Machine.java` and `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java`  
  Add optional core factory thread presets and keep existing constructors source-compatible.
- Create: `src/main/java/cn/howxu/mmcr/api/machine/FactoryThreadSpec.java`  
  API data for MMCE-style factory core thread preset: name, recipe ids, permanent modifiers.
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeSchema.java`  
  Keep current parallel/factory recipe flags compatible; add factory thread preset exposure only if existing KubeJS dynamic machine schema already exposes machine fields.
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/ParallelRecipeCalculatorTest.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeSearchTaskTest.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipeTest.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeThreadTest.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeSchedulerTest.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`

---

### Task 1: Lock Recipe Search To 1x Only

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchTask.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeSearchTaskTest.java`

**Interfaces:**
- Consumes: `RecipeCraftingContext.simulateInputs(MachineRecipe)`, `RecipeCraftingContext.simulateOutputs(MachineRecipe)`.
- Produces: `RecipeSearchResult.success(ActiveMachineRecipe activeRecipe, RecipeCraftingContext context, Identifier machineId, long structureVersion, boolean conflictProne)` where `activeRecipe.getParallelism() == 1` after search.

- [ ] **Step 1: Write the failing test for search not scaling requirements**

Add this test to `RecipeSearchTaskTest`:

```java
@Test
void search_uses_one_x_recipe_requirements_even_when_max_parallelism_is_huge() throws Exception {
    Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
    ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
    bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(8));
    MachineControllerBlockEntity controller = controllerWithComponents(bus);
    RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
    MachineRecipe recipe = inputRecipe("parallel_search_one_x", machineId, List.of(itemInput(Items.IRON_INGOT, 2)), true);

    RecipeSearchResult result = new RecipeSearchTask(controller, machineId, 21, Integer.MAX_VALUE, List.of(recipe), pool).compute();

    assertThat(result.success()).isTrue();
    assertThat(result.activeRecipe().getMaxParallelism()).isEqualTo(Integer.MAX_VALUE);
    assertThat(result.activeRecipe().getParallelism()).isEqualTo(1);
    assertThat(bus.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(8);
}
```

- [ ] **Step 2: Run the focused test and verify red**

Run: `rtk gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeSearchTaskTest --no-daemon`

Expected before implementation: FAIL because search either scales requirements or returns a non-1 parallelism.

- [ ] **Step 3: Implement search-at-1x**

Make `RecipeSearchTask.compute()` use exactly this shape:

```java
for (int recipeIndex = 0; recipeIndex < ordered.size(); recipeIndex++) {
    MachineRecipe recipe = ordered.get(recipeIndex);
    ActiveMachineRecipe activeRecipe = new ActiveMachineRecipe(recipe, maxParallelism);
    RecipeCraftingContext context = contextPool.borrow(activeRecipe, controller);
    if (context.simulateInputs(recipe) && context.simulateOutputs(recipe)) {
        activeRecipe.setMaxParallelism(maxParallelism);
        activeRecipe.setParallelism(1);
        boolean conflictProne = hasMoreSpecificPendingInputCandidate(recipe, recipeIndex, ordered);
        return RecipeSearchResult.success(activeRecipe, context, machineId, structureVersion, conflictProne);
    }

    float validity = validity(context.getLastFailureUnloc(), context.getLastRequirementFailure());
    if (validity > bestValidity) {
        bestValidity = validity;
        bestFailureUnloc = context.getLastFailureUnloc();
        bestFailure = context.getLastRequirementFailure();
    }
    contextPool.returnContext(context);
}
```

Remove any `candidateParallelism(...)` method from `RecipeSearchTask`.

- [ ] **Step 4: Verify focused test passes**

Run: `rtk gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeSearchTaskTest --no-daemon`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchTask.java src/test/java/cn/howxu/mmcr/api/recipe/RecipeSearchTaskTest.java
rtk git commit -m "fix: keep recipe search independent from parallelism"
```

---

### Task 2: Move Parallel Calculation Into Context Checks

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/recipe/ParallelRecipeCalculator.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/ParallelRecipeCalculatorTest.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipeTest.java`

**Interfaces:**
- Consumes: `RecipeCraftingContext.simulateInputs(MachineRecipe, int)`, `RecipeCraftingContext.simulateOutputs(MachineRecipe, int)`.
- Produces: `RecipeCraftingContext.canStartCrafting(ActiveMachineRecipe): boolean`, `RecipeCraftingContext.canRestartCrafting(ActiveMachineRecipe): boolean`, `ParallelRecipeCalculator.maxStartableParallelism(RecipeCraftingContext, MachineRecipe, int): int`.

- [ ] **Step 1: Write calculator tests for GTCEu-style max parallel**

Create `ParallelRecipeCalculatorTest` with this full class:

```java
package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelRecipeCalculatorTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(Items.IRON_INGOT);
    }

    @Test
    void max_parallel_is_limited_by_available_input_not_fixed_tiers() throws Exception {
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(7));
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        MachineRecipe recipe = inputRecipe("parallel_limit_input", Identifier.fromNamespaceAndPath("mmcr", "machine"), Items.IRON_INGOT, 2);

        int result = ParallelRecipeCalculator.maxStartableParallelism(context, recipe, Integer.MAX_VALUE);

        assertThat(result).isEqualTo(3);
        assertThat(bus.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(7);
    }

    @Test
    void max_parallel_returns_zero_when_one_x_cannot_start() throws Exception {
        ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
        bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance());
        MachineControllerBlockEntity controller = controllerWithComponents(bus);
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        MachineRecipe recipe = inputRecipe("parallel_limit_zero", Identifier.fromNamespaceAndPath("mmcr", "machine"), Items.IRON_INGOT, 2);

        int result = ParallelRecipeCalculator.maxStartableParallelism(context, recipe, Integer.MAX_VALUE);

        assertThat(result).isZero();
    }

    private static MachineRecipe inputRecipe(String path, Identifier machineId, Item item, int count) {
        return new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", path),
                machineId,
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), count, ItemStack.EMPTY)),
                true);
    }

    private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        ItemInputBusBlockEntity bus = (ItemInputBusBlockEntity) unsafe.allocateInstance(ItemInputBusBlockEntity.class);
        setField(BlockEntity.class, bus, "type", null);
        setField(BlockEntity.class, bus, "worldPosition", pos);
        setField(BlockEntity.class, bus, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        setField(cn.howxu.mmcr.internal.tile.ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
        return bus;
    }

    private static MachineControllerBlockEntity controllerWithComponents(BlockEntity... ports) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
        var level = LevelStub.createWithBlockEntities(List.of(ports));
        setField(BlockEntity.class, controller, "level", level);
        setField(MachineControllerBlockEntity.class, controller, "components", new ArrayList<ProcessingComponent>());
        for (BlockEntity port : ports) {
            setField(BlockEntity.class, port, "level", level);
            @SuppressWarnings("unchecked")
            List<ProcessingComponent> components = (List<ProcessingComponent>) fieldValue(MachineControllerBlockEntity.class, controller, "components");
            components.add(new ProcessingComponent(new MachineComponent(PortKinds.ITEM_INPUT, cn.howxu.mmcr.util.IOType.INPUT), port, port.getBlockPos(), BlockPos.ZERO, (String) null));
        }
        return controller;
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object fieldValue(Class<?> declaringClass, Object target, String name) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
```

- [ ] **Step 2: Run calculator tests and verify red**

Run: `rtk gradlew test --tests cn.howxu.mmcr.api.recipe.ParallelRecipeCalculatorTest --no-daemon`

Expected before implementation: FAIL because `ParallelRecipeCalculator` does not exist.

- [ ] **Step 3: Implement `ParallelRecipeCalculator`**

Create `src/main/java/cn/howxu/mmcr/api/recipe/ParallelRecipeCalculator.java`:

```java
package cn.howxu.mmcr.api.recipe;

/**
 * Calculates the craft parallel amount from current input and output availability.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ParallelRecipeCalculator {
    private ParallelRecipeCalculator() {
    }

    public static int maxStartableParallelism(RecipeCraftingContext context, MachineRecipe recipe, int parallelLimit) {
        if (context == null) throw new IllegalArgumentException("context null");
        if (recipe == null) return 0;
        if (!recipe.isParallelized()) return context.simulateInputs(recipe) && context.simulateOutputs(recipe) ? 1 : 0;
        int limit = Math.max(1, parallelLimit);
        if (!(context.simulateInputs(recipe) && context.simulateOutputs(recipe))) return 0;
        if (limit == 1) return 1;
        int inputLimit = maxByInput(context, recipe, limit);
        if (inputLimit <= 0) return 0;
        return limitByOutput(context, recipe, inputLimit);
    }

    private static int maxByInput(RecipeCraftingContext context, MachineRecipe recipe, int limit) {
        int low = 1;
        int high = limit;
        int best = 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (context.simulateInputs(recipe, mid)) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private static int limitByOutput(RecipeCraftingContext context, MachineRecipe recipe, int inputLimit) {
        int low = 1;
        int high = inputLimit;
        int best = 0;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (context.simulateOutputs(recipe, mid)) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }
}
```

- [ ] **Step 4: Move start/restart checks into `RecipeCraftingContext`**

Add these methods to `RecipeCraftingContext`:

```java
public boolean canStartCrafting(ActiveMachineRecipe activeRecipe) {
    if (activeRecipe == null || activeRecipe.getRecipe() == null) return false;
    int parallelism = ParallelRecipeCalculator.maxStartableParallelism(this, activeRecipe.getRecipe(), activeRecipe.getMaxParallelism());
    if (parallelism <= 0) return false;
    activeRecipe.setParallelism(parallelism);
    return true;
}

public boolean canRestartCrafting(ActiveMachineRecipe activeRecipe) {
    if (activeRecipe == null || activeRecipe.getRecipe() == null) return false;
    int current = activeRecipe.getParallelism();
    int max = activeRecipe.getMaxParallelism();
    if (current > max) {
        activeRecipe.setParallelism(max);
        if (simulateInputs(activeRecipe.getRecipe(), activeRecipe.getParallelism())
                && simulateOutputs(activeRecipe.getRecipe(), activeRecipe.getParallelism())) {
            return true;
        }
        activeRecipe.setParallelism(1);
    }
    return canStartCrafting(activeRecipe);
}
```

Update `startCrafting(MachineRecipe recipe, int parallelism)` to recompute input and output routes for the selected parallelism:

```java
public boolean startCrafting(MachineRecipe recipe, int parallelism) {
    List<MachineRequirement> requirements = scaledRequirements(recipe, parallelism);
    if (!simulateInputs(requirements)) return false;
    if (!simulateOutputs(requirements)) return false;
    return commitInputs(requirements);
}
```

- [ ] **Step 5: Update `ActiveMachineRecipe` to mirror MMCE call flow**

Add these methods to `ActiveMachineRecipe`:

```java
public boolean canStartCrafting(RecipeCraftingContext context) {
    refreshTotalTick(context);
    return context.canStartCrafting(this);
}

public boolean canRestartCrafting(RecipeCraftingContext context) {
    refreshTotalTick(context);
    return context.canRestartCrafting(this);
}
```

Change `start` to require the already-selected parallelism:

```java
public boolean start(RecipeCraftingContext context) {
    if (recipe == null) {
        LOG.debug("ActiveMachineRecipe#{} start(): no recipe attached → refused", instanceId);
        return false;
    }
    boolean started = context.startCrafting(recipe, parallelism);
    if (started) {
        refreshTotalTick(context);
    }
    LOG.info("ActiveMachineRecipe#{} start(): recipe {} started={} totalTick={} parallelism={}", instanceId, recipe.id(), started, totalTick, parallelism);
    return started;
}
```

- [ ] **Step 6: Add active recipe start test**

In `ActiveMachineRecipeTest`, add:

```java
@Test
void can_start_sets_parallelism_before_start_commits_inputs() throws Exception {
    ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
    bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(8));
    MachineControllerBlockEntity controller = controllerWithComponents(bus);
    MachineRecipe recipe = inputRecipe("active_parallel_context_start", MMCR.id("blast_furnace"), Items.IRON_INGOT, 2);
    ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 16);
    RecipeCraftingContext context = new RecipeCraftingContext(controller);

    assertThat(active.canStartCrafting(context)).isTrue();
    assertThat(active.getParallelism()).isEqualTo(4);
    assertThat(active.start(context)).isTrue();
    assertThat(bus.getItemStackHandler(null).getStackInSlot(0).getCount()).isZero();
}
```

- [ ] **Step 7: Verify and commit**

Run: `rtk gradlew test --tests cn.howxu.mmcr.api.recipe.ParallelRecipeCalculatorTest --tests cn.howxu.mmcr.api.recipe.ActiveMachineRecipeTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon`

Expected: BUILD SUCCESSFUL.

Commit:

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/recipe/ParallelRecipeCalculator.java src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java src/test/java/cn/howxu/mmcr/api/recipe/ParallelRecipeCalculatorTest.java src/test/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipeTest.java
rtk git commit -m "feat: calculate parallelism in crafting context"
```

---

### Task 3: Add Integer.MAX_VALUE Parallel Tier Safely

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/ParallelTier.java`
- Modify: `src/main/java/cn/howxu/mmcr/client/model/DynamicOverlayTextures.java`
- Modify: `src/main/java/cn/howxu/mmcr/datagen/Translations.java`
- Test: `src/test/java/cn/howxu/mmcr/registry/ParallelControllerRegistrationTest.java`
- Test: `src/test/java/cn/howxu/mmcr/client/model/DynamicOverlayTexturesTest.java`
- Test: `src/test/java/cn/howxu/mmcr/datagen/TranslationsTest.java`

**Interfaces:**
- Consumes: `ParallelTier.idSuffix()`, `ParallelTier.maxParallelism()`.
- Produces: `ParallelTier.MAX` with `maxParallelism() == Integer.MAX_VALUE` and stable id `parallel_controller_max`.

- [ ] **Step 1: Add failing tier tests**

Add assertions to existing tests:

```java
assertThat(ParallelTier.MAX.maxParallelism()).isEqualTo(Integer.MAX_VALUE);
assertThat(ParallelTier.MAX.idSuffix()).isEqualTo("parallel_controller_max");
assertThat(ModBlocks.BLOCKS).containsKey("parallel_controller_max");
assertThat(ModBlockEntities.BES).containsKey("parallel_controller_max");
assertThat(DynamicOverlayTextures.portOverlayTextureForName("parallel_controller_max"))
        .isEqualTo(MMCR.id("block/overlay_parallel_controller_ultimate"));
```

- [ ] **Step 2: Run tests and verify red**

Run: `rtk gradlew test --tests cn.howxu.mmcr.registry.ParallelControllerRegistrationTest --tests cn.howxu.mmcr.client.model.DynamicOverlayTexturesTest --tests cn.howxu.mmcr.datagen.TranslationsTest --no-daemon`

Expected before implementation: FAIL because `ParallelTier.MAX` and translations are missing.

- [ ] **Step 3: Implement enum with stable ids**

Replace `ParallelTier` enum constructor with explicit id suffix:

```java
public enum ParallelTier {
    X4(4, "parallel_controller_4"),
    X16(16, "parallel_controller_16"),
    X64(64, "parallel_controller_64"),
    X256(256, "parallel_controller_256"),
    X512(512, "parallel_controller_512"),
    MAX(Integer.MAX_VALUE, "parallel_controller_max");

    private final int maxParallelism;
    private final String idSuffix;

    ParallelTier(int maxParallelism, String idSuffix) {
        this.maxParallelism = maxParallelism;
        this.idSuffix = idSuffix;
    }

    public String idSuffix() {
        return idSuffix;
    }
}
```

- [ ] **Step 4: Map MAX overlay to ultimate texture**

In `DynamicOverlayTextures.parallelControllerOverlayTexture`, add:

```java
if (ParallelTier.MAX.idSuffix().equals(blockName)) return MMCR.id("block/overlay_parallel_controller_ultimate");
```

- [ ] **Step 5: Add translations**

In `Translations`, add both block and item entries for `en_us` and `zh_cn`:

```java
Map.entry("block.mmcr.parallel_controller_max", "Max Parallel Controller"),
Map.entry("item.mmcr.parallel_controller_max", "Max Parallel Controller"),
Map.entry("block.mmcr.parallel_controller_max", "最大并行控制器"),
Map.entry("item.mmcr.parallel_controller_max", "最大并行控制器"),
```

- [ ] **Step 6: Verify and commit**

Run: `rtk gradlew test --tests cn.howxu.mmcr.registry.ParallelControllerRegistrationTest --tests cn.howxu.mmcr.client.model.DynamicOverlayTexturesTest --tests cn.howxu.mmcr.datagen.TranslationsTest --no-daemon`

Expected: BUILD SUCCESSFUL.

Commit:

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/recipe/ParallelTier.java src/main/java/cn/howxu/mmcr/client/model/DynamicOverlayTextures.java src/main/java/cn/howxu/mmcr/datagen/Translations.java src/test/java/cn/howxu/mmcr/registry/ParallelControllerRegistrationTest.java src/test/java/cn/howxu/mmcr/client/model/DynamicOverlayTexturesTest.java src/test/java/cn/howxu/mmcr/datagen/TranslationsTest.java
rtk git commit -m "feat: add max parallel controller tier"
```

---

### Task 4: Introduce MMCE-Style RecipeThread Base

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/recipe/RecipeThread.java`
- Create: `src/main/java/cn/howxu/mmcr/internal/recipe/MachineRecipeThread.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/recipe/MachineRecipeThreadTest.java`

**Interfaces:**
- Consumes: `MachineControllerBlockEntity`, `RecipeSearchTask`, `RecipeCraftingContextPool`, `ActiveMachineRecipe.canStartCrafting`.
- Produces: `RecipeThread.tick()`, `RecipeThread.searchAndStartRecipe()`, `RecipeThread.invalidate()`, `MachineRecipeThread` for non-factory controllers.

- [ ] **Step 1: Write thread start test**

Create `MachineRecipeThreadTest` with a fixture like `RecipeSearchTaskTest` and this assertion flow:

```java
@Test
void machine_recipe_thread_searches_starts_and_ticks_active_recipe() throws Exception {
    Fixture fixture = formedMachineFixtureWithInput(Items.IRON_INGOT, 8);
    MachineRecipe recipe = inputRecipe("thread_parallel_start", fixture.machineId(), Items.IRON_INGOT, 2, true);
    RecipeRegistry.register(recipe);
    MachineRecipeThread thread = new MachineRecipeThread(fixture.controller(), RecipeCraftingContextPool.global());

    assertThat(thread.searchAndStartRecipe(List.of(recipe), 16, 1L)).isTrue();

    assertThat(thread.getActiveRecipe()).isNotNull();
    assertThat(thread.getActiveRecipe().getParallelism()).isEqualTo(4);
    assertThat(thread.getStatus()).isEqualTo(RecipeThread.Status.WORKING);
}
```

Use the exact helper style already present in `MachineControllerBlockEntityTest` or `RecipeSearchTaskTest`.

- [ ] **Step 2: Run test and verify red**

Run: `rtk gradlew test --tests cn.howxu.mmcr.internal.recipe.MachineRecipeThreadTest --no-daemon`

Expected before implementation: FAIL because `RecipeThread` classes do not exist.

- [ ] **Step 3: Implement `RecipeThread`**

Create `RecipeThread` with this public API:

```java
package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * MMCE-style state holder for one recipe execution thread.
 *
 * @author howxu <dev@howxu.cn>
 */
public abstract class RecipeThread {
    public enum Status { IDLE, WORKING, WAITING, FAILED }

    protected final MachineControllerBlockEntity controller;
    protected final RecipeCraftingContextPool contextPool;
    protected @Nullable ActiveMachineRecipe activeRecipe;
    protected @Nullable RecipeCraftingContext context;
    protected Status status = Status.IDLE;
    protected @Nullable String lastFailureUnloc;

    protected RecipeThread(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool) {
        this.controller = controller;
        this.contextPool = contextPool;
    }

    public boolean searchAndStartRecipe(List<MachineRecipe> candidates, int availableParallelism, long structureVersion) {
        Identifier machineId = controller.getFoundMachine() == null ? null : controller.getFoundMachine().registryName();
        if (machineId == null || availableParallelism <= 0) return false;
        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, structureVersion, availableParallelism, candidates, contextPool).compute();
        if (!result.success()) {
            lastFailureUnloc = result.failureUnloc();
            status = Status.FAILED;
            return false;
        }
        ActiveMachineRecipe next = result.activeRecipe();
        RecipeCraftingContext nextContext = result.context();
        if (!next.canStartCrafting(nextContext) || !next.start(nextContext)) {
            contextPool.returnContext(nextContext);
            lastFailureUnloc = nextContext.getLastFailureUnloc();
            status = Status.FAILED;
            return false;
        }
        activeRecipe = next;
        context = nextContext;
        status = Status.WORKING;
        lastFailureUnloc = null;
        onStarted();
        return true;
    }

    public void tick() {
        if (activeRecipe == null || context == null) return;
        ActiveMachineRecipe.TickStatus tickStatus = activeRecipe.tick(context);
        if (tickStatus == ActiveMachineRecipe.TickStatus.FINISHED) {
            onFinished();
            contextPool.returnContext(context);
            activeRecipe = null;
            context = null;
            status = Status.IDLE;
        } else if (tickStatus == ActiveMachineRecipe.TickStatus.WAITING) {
            lastFailureUnloc = context.getLastFailureUnloc();
            status = Status.WAITING;
        } else {
            status = Status.WORKING;
            lastFailureUnloc = null;
        }
    }

    public void invalidate() {
        if (context != null) contextPool.returnContext(context);
        activeRecipe = null;
        context = null;
        status = Status.IDLE;
    }

    protected abstract void onStarted();
    protected abstract void onFinished();

    public @Nullable ActiveMachineRecipe getActiveRecipe() { return activeRecipe; }
    public Status getStatus() { return status; }
    public @Nullable String getLastFailureUnloc() { return lastFailureUnloc; }
    public boolean isIdle() { return activeRecipe == null && context == null; }
    public int usedParallelism() { return activeRecipe == null ? 0 : activeRecipe.getParallelism(); }
}
```

- [ ] **Step 4: Implement `MachineRecipeThread`**

```java
package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;

/**
 * Default single recipe thread for a normal machine controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeThread extends RecipeThread {
    public MachineRecipeThread(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool) {
        super(controller, contextPool);
    }

    @Override
    protected void onStarted() {
    }

    @Override
    protected void onFinished() {
    }
}
```

- [ ] **Step 5: Verify and commit**

Run: `rtk gradlew test --tests cn.howxu.mmcr.internal.recipe.MachineRecipeThreadTest --no-daemon`

Expected: BUILD SUCCESSFUL.

Commit:

```bash
rtk git add src/main/java/cn/howxu/mmcr/internal/recipe/RecipeThread.java src/main/java/cn/howxu/mmcr/internal/recipe/MachineRecipeThread.java src/test/java/cn/howxu/mmcr/internal/recipe/MachineRecipeThreadTest.java
rtk git commit -m "feat: add mmce style recipe thread"
```

---

### Task 5: Replace FactoryRecipeLane With FactoryRecipeThread

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeThread.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeScheduler.java`
- Delete: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeLane.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeSchedulerTest.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeThreadTest.java`

**Interfaces:**
- Consumes: `RecipeThread`, `RecipeRegistry.byMachine`, `MachineRecipe`.
- Produces: `FactoryRecipeThread`, scheduler thread lists, available parallelism budget.

- [ ] **Step 1: Write available parallelism budget test**

Add this test to `FactoryRecipeSchedulerTest`:

```java
@Test
void scheduler_subtracts_active_thread_parallelism_from_available_budget() {
    FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(4);
    FactoryRecipeThread first = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
    FactoryRecipeThread second = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
    first.setActiveRecipeForTesting(activeRecipeWithParallelism(3));
    second.setActiveRecipeForTesting(activeRecipeWithParallelism(1));
    scheduler.addThreadForTesting(first);
    scheduler.addThreadForTesting(second);

    assertThat(scheduler.availableParallelism()).isZero();
}
```

Add a private helper in the test:

```java
private static ActiveMachineRecipe activeRecipeWithParallelism(int parallelism) {
    MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_parallel_" + parallelism), MMCR.id("factory_machine"), 20, List.of(), List.of());
    ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, parallelism);
    active.setParallelism(parallelism);
    return active;
}
```

- [ ] **Step 2: Run scheduler test and verify red**

Run: `rtk gradlew test --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`

Expected before implementation: FAIL because scheduler does not expose MMCE-style thread budget.

- [ ] **Step 3: Implement `FactoryRecipeThread`**

Create class:

```java
package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * MMCE-style factory recipe thread with optional core-thread recipe filtering.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRecipeThread extends RecipeThread {
    public static final int IDLE_TIMEOUT_TICKS = 200;

    private final boolean coreThread;
    private final String threadName;
    private final Set<MachineRecipe> recipeSet = new LinkedHashSet<>();
    private int idleTicks;

    private FactoryRecipeThread(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool, boolean coreThread, String threadName) {
        super(controller, contextPool);
        this.coreThread = coreThread;
        this.threadName = threadName == null ? "" : threadName;
    }

    public static FactoryRecipeThread simple(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool) {
        return new FactoryRecipeThread(controller, contextPool, false, "");
    }

    public static FactoryRecipeThread core(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool, String threadName, Set<MachineRecipe> recipes) {
        FactoryRecipeThread thread = new FactoryRecipeThread(controller, contextPool, true, threadName);
        thread.recipeSet.addAll(recipes);
        return thread;
    }

    public Set<MachineRecipe> recipeSet() { return Set.copyOf(recipeSet); }
    public boolean isCoreThread() { return coreThread; }
    public String threadName() { return threadName; }
    public boolean isTimedOut() { return !coreThread && isIdle() && idleTicks >= IDLE_TIMEOUT_TICKS; }
    public void tickIdle() { idleTicks = isIdle() ? idleTicks + 1 : 0; }

    @Override
    protected void onStarted() { idleTicks = 0; }

    @Override
    protected void onFinished() { idleTicks = 0; }

    public void setActiveRecipeForTesting(@Nullable cn.howxu.mmcr.api.recipe.ActiveMachineRecipe activeRecipe) {
        this.activeRecipe = activeRecipe;
    }
}
```

- [ ] **Step 4: Refactor scheduler to threads**

Change `FactoryRecipeScheduler` fields to:

```java
private final List<FactoryRecipeThread> coreThreads = new ArrayList<>();
private final List<FactoryRecipeThread> dynamicThreads = new ArrayList<>();
private int threadLimit;
```

Add methods:

```java
public int availableParallelism() {
    int used = 0;
    for (FactoryRecipeThread thread : allThreads()) used += thread.usedParallelism();
    return Math.max(0, threadLimit - used);
}

public List<FactoryRecipeThread> allThreads() {
    List<FactoryRecipeThread> all = new ArrayList<>(coreThreads.size() + dynamicThreads.size());
    all.addAll(coreThreads);
    all.addAll(dynamicThreads);
    return List.copyOf(all);
}

public void addThreadForTesting(FactoryRecipeThread thread) {
    dynamicThreads.add(thread);
}
```

Replace lane start/stop logic with thread operations. Delete `FactoryRecipeLane` after scheduler no longer references it.

- [ ] **Step 5: Verify and commit**

Run: `rtk gradlew test --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeThreadTest --no-daemon`

Expected: BUILD SUCCESSFUL.

Commit:

```bash
rtk git add src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeThread.java src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeScheduler.java src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeSchedulerTest.java src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeThreadTest.java
rtk git rm src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeLane.java
rtk git commit -m "refactor: replace factory lanes with recipe threads"
```

---

### Task 6: Wire Factory Scheduler Into Controller Tick Flow

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/FactoryControllerBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeScheduler.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`

**Interfaces:**
- Consumes: `FactoryRecipeScheduler.availableParallelism()`, `FactoryRecipeThread.searchAndStartRecipe(...)`.
- Produces: factory tick flow that starts idle threads only while available parallelism and thread capacity remain.

- [ ] **Step 1: Add factory budget test**

Add this test to `MachineControllerBlockEntityTest`:

```java
@Test
void factory_threads_share_machine_parallel_budget() throws Exception {
    bindItemComponents(Items.IRON_INGOT);
    bindItemComponents(Items.IRON_NUGGET);
    FactoryRuntimeFixture fixture = formedFactoryRuntimeFixture(MMCR.id("factory_parallel_budget_machine"), 4, 8);
    registerItemRecipe("factory_parallel_budget", fixture.machine().registryName(), 20);

    fixture.controller().serverTick();

    assertThat(fixture.factory().activeThreadCount()).isEqualTo(1);
    assertThat(fixture.factory().usedParallelism()).isEqualTo(4);
    assertThat(fixture.factory().availableParallelism()).isZero();
}
```

- [ ] **Step 2: Run test and verify red**

Run: `rtk gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`

Expected before implementation: FAIL because current factory can start lanes without shared parallel budget accounting.

- [ ] **Step 3: Update factory controller BE API**

Add these methods to `FactoryControllerBlockEntity`:

```java
public int activeThreadCount() { return scheduler.activeThreadCount(); }
public int usedParallelism() { return scheduler.usedParallelism(); }
public int availableParallelism() { return scheduler.availableParallelism(); }
public void stopAll() { scheduler.stopAll(); }
public void tickScheduler(MachineControllerBlockEntity controller, List<MachineRecipe> candidates, long structureVersion) {
    scheduler.tick(controller, candidates, structureVersion);
}
```

- [ ] **Step 4: Update `MachineControllerBlockEntity.tickFactoryRecipes`**

Replace current lane loop with:

```java
private void tickFactoryRecipes(FactoryControllerBlockEntity factory) {
    factory.tickScheduler(this, recipesForMachine(), structureVersion);
    setActiveState(factory.activeThreadCount() > 0);
    if (factory.activeThreadCount() > 0) lastFailureUnloc = null;
}
```

Remove `tryStartFactoryLane` from `MachineControllerBlockEntity`; scheduler now owns thread start.

- [ ] **Step 5: Verify and commit**

Run: `rtk gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`

Expected: BUILD SUCCESSFUL.

Commit:

```bash
rtk git add src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java src/main/java/cn/howxu/mmcr/internal/tile/FactoryControllerBlockEntity.java src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeScheduler.java src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java
rtk git commit -m "feat: share factory parallel budget across threads"
```

---

### Task 7: Port MMCE Core Thread Presets

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/machine/FactoryThreadSpec.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/Machine.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeScheduler.java`
- Test: `src/test/java/cn/howxu/mmcr/api/machine/DynamicMachineTest.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeSchedulerTest.java`

**Interfaces:**
- Produces: `Machine.factoryThreads(): List<FactoryThreadSpec>` and scheduler initialization from those specs.

- [ ] **Step 1: Write core thread spec tests**

Add to `DynamicMachineTest`:

```java
@Test
void dynamic_machine_preserves_factory_thread_specs() {
    FactoryThreadSpec thread = new FactoryThreadSpec("smelting", List.of(MMCR.id("iron_recipe")));
    DynamicMachine machine = new DynamicMachine(
            MMCR.id("factory_threads_machine"),
            "Factory Threads",
            new BlockArray(Map.of(BlockPos.ZERO, BlockPredicate.any())),
            MachineControllerSpec.defaultsFor(MMCR.id("factory_threads_machine")),
            MachineAppearanceSpec.defaults(),
            PortRequirementSpec.none(),
            PortTierRequirementSpec.none(),
            List.of(),
            Map.of(),
            16,
            true,
            true,
            4,
            List.of(thread));

    assertThat(machine.factoryThreads()).containsExactly(thread);
}
```

- [ ] **Step 2: Run test and verify red**

Run: `rtk gradlew test --tests cn.howxu.mmcr.api.machine.DynamicMachineTest --no-daemon`

Expected before implementation: FAIL because `FactoryThreadSpec` and constructor are missing.

- [ ] **Step 3: Create `FactoryThreadSpec`**

```java
package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * MMCE-style core factory thread preset.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FactoryThreadSpec(String name, List<Identifier> recipeIds) {
    public FactoryThreadSpec {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name blank");
        recipeIds = List.copyOf(recipeIds == null ? List.of() : recipeIds);
    }
}
```

- [ ] **Step 4: Add machine API default**

In `Machine`:

```java
default List<FactoryThreadSpec> factoryThreads() {
    return List.of();
}
```

In `DynamicMachine`, add record component:

```java
List<FactoryThreadSpec> factoryThreads
```

Canonical constructor normalization:

```java
factoryThreads = List.copyOf(factoryThreads == null ? List.of() : factoryThreads);
```

Keep every existing constructor by delegating with `List.of()` as the final argument.

- [ ] **Step 5: Scheduler initializes core threads**

Add method to `FactoryRecipeScheduler`:

```java
public void syncCoreThreads(MachineControllerBlockEntity controller, Machine machine, List<MachineRecipe> candidates) {
    coreThreads.clear();
    Map<Identifier, MachineRecipe> byId = candidates.stream().collect(java.util.stream.Collectors.toMap(MachineRecipe::id, recipe -> recipe, (a, b) -> a, java.util.LinkedHashMap::new));
    for (FactoryThreadSpec spec : machine.factoryThreads()) {
        Set<MachineRecipe> recipes = spec.recipeIds().stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        coreThreads.add(FactoryRecipeThread.core(controller, contextPool, spec.name(), recipes));
    }
}
```

- [ ] **Step 6: Verify and commit**

Run: `rtk gradlew test --tests cn.howxu.mmcr.api.machine.DynamicMachineTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`

Expected: BUILD SUCCESSFUL.

Commit:

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/machine/FactoryThreadSpec.java src/main/java/cn/howxu/mmcr/api/machine/Machine.java src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeScheduler.java src/test/java/cn/howxu/mmcr/api/machine/DynamicMachineTest.java src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeSchedulerTest.java
rtk git commit -m "feat: add factory core thread presets"
```

---

### Task 8: Persist Factory Threads And Active Recipes

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeThread.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeScheduler.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/FactoryControllerBlockEntity.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeSchedulerTest.java`

**Interfaces:**
- Produces: `FactoryRecipeScheduler.save(ValueOutput)`, `FactoryRecipeScheduler.load(ValueInput, MachineControllerBlockEntity)`, preserved active recipes and parallelism.

- [ ] **Step 1: Add persistence test**

Add to `FactoryRecipeSchedulerTest`:

```java
@Test
void scheduler_persists_active_thread_recipe_parallelism_and_core_flag() throws Exception {
    FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(4);
    FactoryRecipeThread thread = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool());
    ActiveMachineRecipe active = activeRecipeWithParallelism(3);
    thread.setActiveRecipeForTesting(active);
    scheduler.addThreadForTesting(thread);

    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, HolderLookup.Provider.create(java.util.stream.Stream.empty()));
    scheduler.save(output.child("scheduler"));
    FactoryRecipeScheduler loaded = new FactoryRecipeScheduler(4);
    loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, HolderLookup.Provider.create(java.util.stream.Stream.empty()), output.buildResult()).childOrEmpty("scheduler"), null);

    assertThat(loaded.activeThreadCount()).isEqualTo(1);
    assertThat(loaded.usedParallelism()).isEqualTo(3);
}
```

- [ ] **Step 2: Run test and verify red**

Run: `rtk gradlew test --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`

Expected before implementation: FAIL because save/load methods are missing.

- [ ] **Step 3: Implement save/load**

Add to `FactoryRecipeThread`:

```java
public void save(ValueOutput output) {
    output.putBoolean("core", coreThread);
    output.putString("name", threadName);
    output.putInt("idle_ticks", idleTicks);
    output.putBoolean("has_active", activeRecipe != null);
    if (activeRecipe != null) activeRecipe.serialize(output.child("active_recipe"));
}

public static FactoryRecipeThread load(ValueInput input, MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool) {
    FactoryRecipeThread thread = new FactoryRecipeThread(controller, contextPool, input.getBooleanOr("core", false), input.getStringOr("name", ""));
    thread.idleTicks = input.getIntOr("idle_ticks", 0);
    if (input.getBooleanOr("has_active", false)) thread.activeRecipe = ActiveMachineRecipe.from(input.childOrEmpty("active_recipe"));
    return thread;
}
```

Add scheduler `save`/`load` with indexed child keys `thread_0`, `thread_1`, and `thread_count`.

- [ ] **Step 4: Wire BE persistence**

In `FactoryControllerBlockEntity.saveAdditional`, call `scheduler.save(output.child("scheduler"))`.

In `FactoryControllerBlockEntity.loadAdditional`, call `scheduler.load(input.childOrEmpty("scheduler"), ownerControllerOrNull)`; if owner controller is not available during load, bind controller on first scheduler tick.

- [ ] **Step 5: Verify and commit**

Run: `rtk gradlew test --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`

Expected: BUILD SUCCESSFUL.

Commit:

```bash
rtk git add src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeThread.java src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeScheduler.java src/main/java/cn/howxu/mmcr/internal/tile/FactoryControllerBlockEntity.java src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeSchedulerTest.java
rtk git commit -m "feat: persist factory recipe threads"
```

---

### Task 9: Add Overflow Guards For 2.1G Parallelism

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/ParallelRecipeCalculator.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/ParallelRecipeCalculatorTest.java`

**Interfaces:**
- Produces: overflow-safe parallel calculations that treat overflowing requirement scaling as unavailable, not as a crash in recipe search or start.

- [ ] **Step 1: Add overflow test**

Add to `ParallelRecipeCalculatorTest`:

```java
@Test
void max_parallel_caps_before_item_requirement_integer_overflow() throws Exception {
    ItemInputBusBlockEntity bus = itemInputBus(new BlockPos(1, 0, 0));
    bus.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(64));
    MachineControllerBlockEntity controller = controllerWithComponents(bus);
    RecipeCraftingContext context = new RecipeCraftingContext(controller);
    MachineRecipe recipe = inputRecipe("parallel_overflow_guard", Identifier.fromNamespaceAndPath("mmcr", "machine"), Items.IRON_INGOT, Integer.MAX_VALUE);

    int result = ParallelRecipeCalculator.maxStartableParallelism(context, recipe, Integer.MAX_VALUE);

    assertThat(result).isZero();
}
```

- [ ] **Step 2: Run test and verify red**

Run: `rtk gradlew test --tests cn.howxu.mmcr.api.recipe.ParallelRecipeCalculatorTest --no-daemon`

Expected before implementation: FAIL if overflow propagates as exception or wrong positive result.

- [ ] **Step 3: Add safe simulation wrappers**

In `ParallelRecipeCalculator`, wrap each simulation:

```java
private static boolean safeSimulateInputs(RecipeCraftingContext context, MachineRecipe recipe, int parallelism) {
    try {
        return context.simulateInputs(recipe, parallelism);
    } catch (IllegalArgumentException overflow) {
        return false;
    }
}

private static boolean safeSimulateOutputs(RecipeCraftingContext context, MachineRecipe recipe, int parallelism) {
    try {
        return context.simulateOutputs(recipe, parallelism);
    } catch (IllegalArgumentException overflow) {
        return false;
    }
}
```

Use these wrappers in `maxStartableParallelism`, `maxByInput`, and `limitByOutput`.

- [ ] **Step 4: Verify and commit**

Run: `rtk gradlew test --tests cn.howxu.mmcr.api.recipe.ParallelRecipeCalculatorTest --no-daemon`

Expected: BUILD SUCCESSFUL.

Commit:

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/recipe/ParallelRecipeCalculator.java src/test/java/cn/howxu/mmcr/api/recipe/ParallelRecipeCalculatorTest.java
rtk git commit -m "fix: guard parallel requirement overflow"
```

---

### Task 10: Final Integration And Performance Sanity

**Files:**
- Modify: files touched by preceding tasks only if final integration failures require it.
- Test: all recipe, factory, and controller tests.

**Interfaces:**
- Consumes: all APIs from Tasks 1-9.
- Produces: full build/test pass with MMCE-aligned semantics.

- [ ] **Step 1: Run focused integration tests**

Run:

```bash
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeSearchTaskTest --tests cn.howxu.mmcr.api.recipe.ActiveMachineRecipeTest --tests cn.howxu.mmcr.api.recipe.ParallelRecipeCalculatorTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeThreadTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run compile and full tests**

Run:

```bash
rtk gradlew compileJava --no-daemon
rtk gradlew test --no-daemon
```

Expected: both commands report BUILD SUCCESSFUL.

- [ ] **Step 3: Check diff hygiene**

Run:

```bash
rtk git diff --check
rtk git status --short
```

Expected: `git diff --check` prints no output; `git status --short` shows only intended source/test/doc files.

- [ ] **Step 4: Commit final integration fixes**

If Step 1 or Step 2 required integration fixes, commit them:

```bash
rtk git add src/main/java src/test/java docs/superpowers/plans/2026-08-08-mmce-parallel-factory-alignment.md
rtk git commit -m "test: verify mmce parallel factory alignment"
```

If no code changes remain after previous task commits, do not create an empty commit.

---

## Definition Of Done

- Recipe search never enumerates fixed parallel tiers and never scales recipe requirements to choose recipes.
- Starting/restarting a recipe computes current feasible parallelism from context-level availability checks.
- `Integer.MAX_VALUE` parallel tier exists as `parallel_controller_max` and does not crash search/start on overflow-heavy recipes.
- Factory execution uses MMCE-style thread objects, not lightweight lanes.
- Factory active threads share total available parallel budget.
- Factory core thread presets can restrict recipes by thread.
- Factory active threads and active recipe state persist across save/load.
- `rtk gradlew compileJava --no-daemon` succeeds.
- `rtk gradlew test --no-daemon` succeeds.

## Self-Review

- Spec coverage: plan covers MMCE recipe search, context-level parallel calculation, active recipe start/restart, factory recipe threads, core threads, shared parallel budget, persistence, and 2.1G safety.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: produced APIs are consistently named `ParallelRecipeCalculator`, `RecipeThread`, `MachineRecipeThread`, `FactoryRecipeThread`, `FactoryThreadSpec`, `canStartCrafting`, `canRestartCrafting`, `availableParallelism`, and `usedParallelism`.
