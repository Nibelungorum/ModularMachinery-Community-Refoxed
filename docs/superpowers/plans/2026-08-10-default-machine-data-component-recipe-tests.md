# Default Machine Data Component Recipe Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Java API integration tests that exercise Data Component item inputs and outputs across all five registered default machines, including keep and probabilistic consumption.

**Architecture:** Add one parameterized test class under `src/test/java/cn/howxu/mmcr/api/recipe`. It reuses the existing lightweight `LevelStub`/block-entity fixture pattern, constructs `MachineRecipe` directly, registers each test recipe, and drives `RecipeSearchTask`, `ActiveMachineRecipe`, and `RecipeCraftingContext` so the new recipe candidate index is exercised as well as the component matcher.

**Tech Stack:** Java 21, NeoForge 26.1.2 Data Components, JUnit 5 parameterized tests, AssertJ, Gradle.

## Global Constraints

- Tests only; do not change production code unless a test exposes a concrete regression.
- Cover `blast_furnace`, `alloy_furnace`, `cracker`, `reactor`, and `thermal_smelting_furnace`.
- Construct recipes using Java API types, not KubeJS or JSON recipe decoding.
- Preserve the existing worktree changes from recipe candidate indexing.
- Bind every item used by the test before constructing or displaying `ItemStack` values.
- Do not use uncontrolled random outcomes for assertions; derive expected probabilistic consumption from the persisted `InputConsumptionPlan` selected at recipe start.
- Do not run `./gradlew runClient --no-daemon`.

## File Structure

- Create: `src/test/java/cn/howxu/mmcr/api/recipe/DefaultMachineDataComponentRecipeTest.java` — parameterized Java API execution coverage and focused test fixtures.
- Create: `docs/superpowers/plans/2026-08-10-default-machine-data-component-recipe-tests.md` — this implementation plan.

### Task 1: Add Parameterized Default-Machine Scenario Tests

**Files:**
- Create: `src/test/java/cn/howxu/mmcr/api/recipe/DefaultMachineDataComponentRecipeTest.java`

**Interfaces:**
- Consume `TestBootstrap.registerRuntimeBuiltins()`, `RecipeCandidateIndex.build`, `RecipeSearchTask`, `ActiveMachineRecipe.start/tick`, and `RecipeCraftingContext`.
- Produce seven scenario methods executed for every default machine ID.

- [ ] **Step 1: Write the test class and scenario data first**

Use `@ParameterizedTest(name = "{0} / {1}")` with a `@MethodSource` returning the Cartesian product of the five machine IDs and seven scenario names. Define scenarios as a local enum or record with a `MachineRecipe create(Identifier machineId, Identifier recipeId)` function and input/output setup expectations.

The scenario set must include:

```java
enum Scenario {
    CHANCED_COMPONENT_INPUT,
    NON_CONSUMABLE_COMPONENT_INPUT,
    COMPONENT_INPUT_TO_PLAIN_OUTPUT,
    PLAIN_INPUT_TO_COMPONENT_OUTPUT,
    COMPONENT_INPUT_TO_COMPONENT_OUTPUT,
    MIXED_COMPONENT_INPUTS,
    MIXED_COMPONENT_OUTPUTS
}
```

Each recipe must use at least one item requirement and a plain `ItemStack` output or component-bearing `ItemStack` output as appropriate. Use `DataComponents.CUSTOM_NAME` with distinct names to make presence and value assertions unambiguous.

- [ ] **Step 2: Run the new focused test to verify the fixture is incomplete**

Run: `./gradlew test --no-daemon --tests '*DefaultMachineDataComponentRecipeTest'`

Expected: the test source may fail to compile or fail at runtime until the fixture and scenario execution are completed; record the first concrete failure and fix only the test implementation.

- [ ] **Step 3: Build reusable item-bus/controller fixtures**

Create test-local helpers equivalent to the existing `RecipeSearchTaskTest` fixture:

```java
private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) throws Exception;
private static ItemOutputBusBlockEntity itemOutputBus(BlockPos pos) throws Exception;
private static MachineControllerBlockEntity controllerWithComponents(BlockEntity... ports) throws Exception;
private static void bindItemComponents(Item... items);
```

The input and output buses must be registered in the `LevelStub` block-entity map, and their `ProcessingComponent` entries must use `PortKinds.ITEM_INPUT` or `PortKinds.ITEM_OUTPUT` respectively. Bind all scenario items, including plain and component-bearing inputs/outputs, before creating stacks.

- [ ] **Step 4: Register each Java API recipe and exercise the optimized search path**

For each machine/scenario pair:

```java
RecipeRegistry.register(recipe);
RecipeCandidateIndex index = RecipeCandidateIndex.build(List.of(recipe));
RecipeSearchResult result = new RecipeSearchTask(
        controller, machineId, 1L, 1, index.allCandidates(),
        new RecipeCraftingContextPool(), index).compute();
assertThat(result.success()).isTrue();
ActiveMachineRecipe active = result.activeRecipe();
assertThat(active.getRecipe()).isEqualTo(recipe);
```

Start through `active.start(result.context())`, then complete through `active.tick(result.context())` until `FINISHED`. Use one tick for the test recipes to keep the scenarios fast, and call output setup before the final tick. For recipes with only item requirements, no energy or fluid hatch is needed.

- [ ] **Step 5: Assert each scenario's input and output component behavior**

Assert the following independently for every machine:

- `CHANCED_COMPONENT_INPUT`: input has the exact component predicate and `consumeChance() == 0.5F`; after start, remaining input count equals the persisted plan decision, and no second random decision is made during completion.
- `NON_CONSUMABLE_COMPONENT_INPUT`: matching component input remains in the input bus after start and completion.
- `COMPONENT_INPUT_TO_PLAIN_OUTPUT`: named/component input is consumed; output has no `CUSTOM_NAME`.
- `PLAIN_INPUT_TO_COMPONENT_OUTPUT`: plain input is consumed; output has the expected `CUSTOM_NAME` value.
- `COMPONENT_INPUT_TO_COMPONENT_OUTPUT`: input predicate matches and is consumed; output preserves its separately configured component value.
- `MIXED_COMPONENT_INPUTS`: one named input and one plain input are both required and consumed, proving component checks do not broaden or replace ordinary item matching.
- `MIXED_COMPONENT_OUTPUTS`: one named output and one plain output are inserted; assert the name exists only on the configured component output.

For all scenarios, assert item type, count, input remaining count, output count, and exact/absent `DataComponents.CUSTOM_NAME` as applicable.

- [ ] **Step 6: Add candidate-index regression coverage for a component recipe**

Include a focused test in the same class with two recipes for one default machine and the same item prefilter: one component predicate does not match and one does. Build a `RecipeCandidateIndex` from both, run `RecipeSearchTask` with the index, and assert the matching component recipe is selected. This proves the optimization narrows by item without skipping component-level matching.

- [ ] **Step 7: Run the focused test and refine only test fixtures**

Run: `./gradlew test --no-daemon --tests '*DefaultMachineDataComponentRecipeTest'`

Expected: `BUILD SUCCESSFUL` with all machine/scenario invocations passing. If a failure is production behavior rather than fixture setup, stop and report it before modifying production code.

### Task 2: Full Verification

**Files:**
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/DefaultMachineDataComponentRecipeTest.java`

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test --no-daemon`

Expected: `BUILD SUCCESSFUL` and no failing tests.

- [ ] **Step 2: Check the diff and commit only the new test**

Run:

```bash
git diff --check
git status --short
git add src/test/java/cn/howxu/mmcr/api/recipe/DefaultMachineDataComponentRecipeTest.java
git commit -m "test: cover default machine component recipes"
```

Leave unrelated existing modifications untouched, especially `TODO.md` and the recipe optimization commit.

## Plan Self-Review

- Spec coverage: all five machines and seven requested input/output combinations are explicit in Task 1.
- Optimization coverage: Task 1 Step 6 verifies candidate indexing does not bypass component predicates.
- Determinism: probabilistic consumption is asserted against the persisted plan, not an assumed random result.
- Scope: production files are not included; the only intended code change is a test class.
- Type consistency: all helper signatures and production APIs referenced in the tasks exist on the current branch.
