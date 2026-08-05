# Phase 2 Requirement Runtime Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Continue directly from completed Task 7 and finish Phase 2 by making `MachineRecipe.requirements()` the runtime IO source for item, fluid, energy, selector tags, structured failures, and controller aggregate display.

**Architecture:** Keep old `MachineIngredient` fields only as decode/constructor compatibility. Runtime simulation and commit should move requirement-by-requirement, with routes keyed by recipe requirement index and failures carrying enough structured data for tests, debug UI, Jade, and future selector tag diagnostics. Do not add a generic third-party adapter registry in Phase 2; support vanilla item, NeoForge fluid, and NeoForge energy only.

**Tech Stack:** Java 21, Minecraft 26.1.2, NeoForge, Gradle, JUnit 5, AssertJ, existing MMCR test stubs.

## Global Constraints

- Current state starts after `.superpowers/sdd/progress.md` records Task 7 complete.
- Do not redo Tasks 1-7; treat their worktree changes as authoritative unless verification shows a direct regression.
- Do not return to `MachineIngredient` as a runtime main path; old fields are compatibility input only.
- `MachineRecipe.CODEC` must continue encoding stable `requirements` shape and must not re-emit `inputs`, `outputs`, or `fluid_outputs`.
- Commit must never rescan buses/hatches; commit uses simulate routes and validates every route before any mutation.
- Output simulation must preserve current behavior: merge into matching stacks/tanks first where applicable, then use empty space.
- Handler capability side remains `null`; do not introduce side-aware routing in Phase 2.
- Do not implement JEI or third-party requirements in this phase.
- New Java classes must include Javadoc author: `@author howxu <dev@howxu.cn>`.
- Use `rtk` commands for status and Gradle verification.
- Do not claim completion without fresh `compileJava` and relevant tests.

---

## Current Task 7 Baseline

Task 7 has already landed the first item runtime cut:

- `RecipeCraftingContext` item input/output simulate traverses `recipe.requirements()`.
- item routes are aligned to requirement index.
- item commit validates stored routes before mutation.
- `RequirementFailure` exists with `requirementIndex`, `kind`, `required`, `available`, and `shortAmount`.
- `CraftCheck.failure(String, RequirementFailure)` exists.
- `RecipeCraftingContext.getLastRequirementFailure()` exists.
- `rtk gradlew compileJava --no-daemon` passed.
- `rtk gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon` passed after sequential rerun.

Before starting Task 8, run:

```bash
rtk git status --short --branch
rtk git diff --stat
rtk gradlew compileJava --no-daemon
```

Expected: compile succeeds. If status contains unrelated multiblock detector/model/texture edits, do not revert them; ignore unless they directly conflict with this plan.

---

### Task 8: Fluid Requirement Runtime

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RequirementFailure.java` if `Kind` needs commit-lost variants
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/MachineRecipeTest.java` if fluid-only assemble coverage is missing

**Interfaces:**
- Consumes: `MachineRecipe.requirements(): List<MachineRequirement>`
- Consumes: `FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack)`
- Consumes: `RecipeCraftingContext.getLastRequirementFailure(): @Nullable RequirementFailure`
- Produces: Fluid input/output routes aligned by requirement index in `RecipeCraftingContext`
- Produces: Fluid failures with `RequirementFailure.Kind.MISSING_INPUT` / `MISSING_OUTPUT`

- [ ] **Step 1: Write fluid input requirement tests**

Add tests to `RecipeCraftingContextTest` that construct explicit `FluidRequirement` recipes with legacy fields empty. Use the existing unsafe allocation pattern from item bus tests and add helper allocation for `FluidInputHatchBlockEntity` / `FluidOutputHatchBlockEntity` if no helper exists.

Test names and assertions:

```java
@Test
void explicitFluidInputRequirementRunsWhenLegacyInputsAreEmpty() {
    FluidInputHatchBlockEntity input = fluidInputHatch(new BlockPos(1, 0, 0));
    input.getFluidTank(null).setFluid(new FluidStack(Fluids.WATER, 1000));
    MachineControllerBlockEntity controller = controllerWithComponents(input);
    MachineRecipe recipe = explicitRequirementRecipe(
            "explicit_fluid_input",
            List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1000, FluidStack.EMPTY))
    );
    RecipeCraftingContext context = new RecipeCraftingContext(controller);

    assertThat(context.simulateInputs(recipe)).isTrue();
    assertThat(context.commitInputs(recipe)).isTrue();
    assertThat(input.getFluidTank(null).getFluidAmount()).isZero();
}

@Test
void explicitFluidInputRequirementAggregatesAcrossHatches() {
    FluidInputHatchBlockEntity first = fluidInputHatch(new BlockPos(1, 0, 0));
    FluidInputHatchBlockEntity second = fluidInputHatch(new BlockPos(2, 0, 0));
    first.getFluidTank(null).setFluid(new FluidStack(Fluids.WATER, 400));
    second.getFluidTank(null).setFluid(new FluidStack(Fluids.WATER, 600));
    MachineControllerBlockEntity controller = controllerWithComponents(first, second);
    MachineRecipe recipe = explicitRequirementRecipe(
            "fluid_multi_hatch_input",
            List.of(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 1000, FluidStack.EMPTY))
    );
    RecipeCraftingContext context = new RecipeCraftingContext(controller);

    assertThat(context.simulateInputs(recipe)).isTrue();
    assertThat(context.commitInputs(recipe)).isTrue();
    assertThat(first.getFluidTank(null).getFluidAmount()).isZero();
    assertThat(second.getFluidTank(null).getFluidAmount()).isZero();
}
```

- [ ] **Step 2: Write fluid output and no-swallow tests**

Add tests:

```java
@Test
void explicitFluidOutputRequirementFailsWhenOutputHatchHasNoRoom() {
    FluidOutputHatchBlockEntity output = fluidOutputHatch(new BlockPos(1, 0, 0));
    output.getFluidTank(null).setFluid(new FluidStack(Fluids.LAVA, output.getFluidTank(null).getCapacity()));
    MachineControllerBlockEntity controller = controllerWithComponents(output);
    MachineRecipe recipe = explicitRequirementRecipe(
            "blocked_fluid_output",
            List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1000)))
    );
    RecipeCraftingContext context = new RecipeCraftingContext(controller);

    assertThat(context.simulateOutputs(recipe)).isFalse();
    assertThat(context.getLastRequirementFailure()).satisfies(failure -> {
        assertThat(failure.kind()).isEqualTo(RequirementFailure.Kind.MISSING_OUTPUT);
        assertThat(failure.required()).isEqualTo(1000);
        assertThat(failure.available()).isZero();
    });
}

@Test
void fluidOutputFailureDoesNotConsumeItemInput() {
    bindItemComponents(Items.IRON_INGOT);
    ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
    input.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance());
    FluidOutputHatchBlockEntity output = fluidOutputHatch(new BlockPos(2, 0, 0));
    output.getFluidTank(null).setFluid(new FluidStack(Fluids.LAVA, output.getFluidTank(null).getCapacity()));
    MachineControllerBlockEntity controller = controllerWithComponents(input, output);
    MachineRecipe recipe = explicitRequirementRecipe(
            "fluid_output_no_swallow",
            List.of(
                    new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                    new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1000))
            )
    );
    RecipeCraftingContext context = new RecipeCraftingContext(controller);

    assertThat(context.simulateInputs(recipe)).isTrue();
    assertThat(context.simulateOutputs(recipe)).isFalse();
    assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
}
```

- [ ] **Step 3: Verify tests fail before implementation**

Run:

```bash
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon
```

Expected before implementation: failures showing explicit fluid requirements are not routed by requirement index and/or helper methods/classes are missing.

- [ ] **Step 4: Implement fluid requirement route by index**

Modify `RecipeCraftingContext`:

- Replace `fluidInputRoutes = new ArrayList<>()` with a route list sized to `requirements.size()`.
- Replace `fluidOutputRoutes = new ArrayList<>()` with a route list sized to `requirements.size()`.
- In `simulateInputs`, for `FluidRequirement INPUT`, set `fluidInputRoutes.set(requirementIndex, new FluidInputRoute(transfers))`.
- In `simulateOutputs`, iterate `requirements` for `FluidRequirement OUTPUT`; do not iterate `recipe.fluidOutputs()` for the primary route.
- Keep `FluidIngredient.test(stack)` semantics; do not compare fluid ids manually.

Concrete pattern:

```java
private static List<FluidInputRoute> emptyFluidInputRoutes(int size) {
    List<FluidInputRoute> routes = new ArrayList<>(size);
    for (int i = 0; i < size; i++) routes.add(null);
    return routes;
}

private static List<FluidOutputRoute> emptyFluidOutputRoutes(int size) {
    List<FluidOutputRoute> routes = new ArrayList<>(size);
    for (int i = 0; i < size; i++) routes.add(null);
    return routes;
}
```

- [ ] **Step 5: Implement fluid commit validation before mutation**

Modify `commitInputs` and `commitOutputs`:

- Remove `fluidIdx` route indexing.
- Add `firstFluidInputFailure(requirements)` and `firstFluidOutputFailure(requirements)` matching Task 7 item route validation style.
- Validate item and fluid failures before any `extract`, `drain`, `insert`, or `fill` call.
- Flatten fluid transfers only after both item and fluid route validation has passed.

Required commit order:

```java
RequirementFailure itemFailure = firstItemInputFailure(requirements);
if (itemFailure != null) { setFailure(FAILURE_MISSING_INPUT, itemFailure); return false; }
RequirementFailure fluidFailure = firstFluidInputFailure(requirements);
if (fluidFailure != null) { setFailure(FAILURE_MISSING_INPUT, fluidFailure); return false; }
// collect transfers, then mutate
extract(itemTransfers);
drain(fluidTransfers);
```

- [ ] **Step 6: Add fluid-only assemble coverage**

If no equivalent test exists, add to `MachineRecipeTest`:

```java
@Test
void fluidOnlyRequirementRecipeAssemblesEmptyItemStack() {
    var recipe = new MachineRecipe(
            Identifier.fromNamespaceAndPath("mmcr", "fluid_only"),
            Identifier.fromNamespaceAndPath("mmcr", "machine"),
            20,
            List.of(),
            List.of(),
            List.of(),
            0,
            1,
            false,
            List.of(),
            List.of(new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1000)))
    );

    assertThat(recipe.assemble(null)).isEqualTo(ItemStack.EMPTY);
}
```

- [ ] **Step 7: Verify Task 8**

Run:

```bash
rtk gradlew compileJava --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon
```

Expected: both commands succeed. If `compileTestJava` fails only after parallel Gradle commands, rerun sequentially before debugging.

- [ ] **Step 8: Update progress ledger**

Append:

```text
Task 8: complete (fluid requirement runtime, compileJava and recipe tests pass, no commit)
```

---

### Task 9: Energy Requirement Per-Tick Runtime

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java` or add focused `RecipeCraftingContextEnergyTest.java`
- Verify: `src/test/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIoTest.java`

**Interfaces:**
- Consumes: `EnergyRequirement.fePerTick()`
- Consumes: `EnergyRecipeIo.consumeInputs(List<? extends IEnergyStorage>, int requiredFe, int multiplier)`
- Produces: `ioTick` runtime driven by explicit `EnergyRequirement`
- Produces: structured energy failure on missing FE

- [ ] **Step 1: Write explicit energy runtime tests**

Add tests that allocate `EnergyInputHatchBlockEntity` using the existing unsafe style and seed its mutable storage.

Required tests:

```java
@Test
void explicitEnergyRequirementIsConsumedByIoTick() {
    EnergyInputHatchBlockEntity hatch = energyInputHatch(new BlockPos(1, 0, 0));
    hatch.getMutableEnergyStorage(null).insertEnergy(100, false);
    MachineControllerBlockEntity controller = controllerWithComponents(hatch);
    MachineRecipe recipe = explicitRequirementRecipe(
            "explicit_energy_tick",
            List.of(new EnergyRequirement(40))
    );
    RecipeCraftingContext context = new RecipeCraftingContext(controller);

    assertThat(context.ioTick(recipe)).isTrue();
    assertThat(hatch.getMutableEnergyStorage(null).getEnergyStored()).isEqualTo(60);
}

@Test
void mixedShapeEnergyRuntimeUsesExplicitRequirements() {
    EnergyInputHatchBlockEntity hatch = energyInputHatch(new BlockPos(1, 0, 0));
    hatch.getMutableEnergyStorage(null).insertEnergy(100, false);
    MachineControllerBlockEntity controller = controllerWithComponents(hatch);
    MachineRecipe recipe = new MachineRecipe(
            Identifier.fromNamespaceAndPath("mmcr", "mixed_energy_runtime"),
            Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
            20,
            List.of(new MachineIngredient.EnergyIngredient(90)),
            List.of(),
            List.of(),
            0,
            1,
            false,
            List.of(),
            List.of(new EnergyRequirement(40))
    );
    RecipeCraftingContext context = new RecipeCraftingContext(controller);

    assertThat(context.ioTick(recipe)).isTrue();
    assertThat(hatch.getMutableEnergyStorage(null).getEnergyStored()).isEqualTo(60);
}
```

- [ ] **Step 2: Write missing energy structured failure test**

```java
@Test
void missingEnergyRequirementRecordsStructuredFailure() {
    EnergyInputHatchBlockEntity hatch = energyInputHatch(new BlockPos(1, 0, 0));
    hatch.getMutableEnergyStorage(null).insertEnergy(25, false);
    MachineControllerBlockEntity controller = controllerWithComponents(hatch);
    MachineRecipe recipe = explicitRequirementRecipe(
            "missing_energy_runtime",
            List.of(new EnergyRequirement(40))
    );
    RecipeCraftingContext context = new RecipeCraftingContext(controller);

    assertThat(context.ioTick(recipe)).isFalse();
    assertThat(context.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_ENERGY);
    assertThat(context.getLastRequirementFailure()).satisfies(failure -> {
        assertThat(failure.kind()).isEqualTo(RequirementFailure.Kind.MISSING_ENERGY);
        assertThat(failure.required()).isEqualTo(40);
        assertThat(failure.available()).isEqualTo(25);
        assertThat(failure.shortAmount()).isEqualTo(15);
    });
}
```

- [ ] **Step 3: Verify tests fail before implementation**

Run:

```bash
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon
```

Expected before implementation: explicit energy requirement is not consumed by `ioTick`.

- [ ] **Step 4: Change `ioTick` to requirements**

In `RecipeCraftingContext.ioTick`, replace legacy traversal:

```java
for (MachineIngredient ingredient : recipe.inputs()) {
    if (!(ingredient instanceof MachineIngredient.EnergyIngredient energy)) continue;
    ...
}
```

with:

```java
List<MachineRequirement> requirements = recipe.requirements();
for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
    MachineRequirement requirement = requirements.get(requirementIndex);
    if (!(requirement instanceof EnergyRequirement energy)) continue;
    List<EnergyInputHatchBlockEntity> hatches = liveEnergyInputs();
    if (!EnergyRecipeIo.consumeInputs(energyStorages(hatches), energy.fePerTick(), 1)) {
        setFailure(FAILURE_MISSING_ENERGY, new RequirementFailure(
                requirementIndex,
                RequirementFailure.Kind.MISSING_ENERGY,
                energy.fePerTick(),
                availableEnergy(hatches)
        ));
        return false;
    }
}
```

Do not change `EnergyRecipeIo` public signatures.

- [ ] **Step 5: Preserve failure action semantics**

Do not change `MachineControllerBlockEntity` active recipe failure handling in this task unless tests prove `ioTick` is called in a way that bypasses existing failure action. If a controller test is needed, add it narrowly and keep `RecipeFailureActions` behavior unchanged.

- [ ] **Step 6: Verify Task 9**

Run:

```bash
rtk gradlew compileJava --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIoTest --no-daemon
```

Expected: all commands succeed.

- [ ] **Step 7: Update progress ledger**

Append:

```text
Task 9: complete (energy requirement per-tick runtime, compileJava and energy/recipe tests pass, no commit)
```

---

### Task 10: Structured Failure Trace Completion

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RequirementFailure.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`

**Interfaces:**
- Consumes: existing `RequirementFailure(int requirementIndex, Kind kind, int required, int available, int shortAmount)`
- Produces: trace fields for `searchedComponents` and `matchedComponents`
- Produces: optional new failure kinds `COMMIT_LOST_INPUT`, `COMMIT_LOST_OUTPUT`, `TAG_MISMATCH`

- [ ] **Step 1: Write trace tests before changing model**

Add tests that assert missing input/output failures include non-empty searched component descriptions and matched counts. Use simple string expectations like block positions to avoid coupling to full object identity.

Example:

```java
@Test
void missingItemInputFailureRecordsSearchedComponents() {
    bindItemComponents(Items.IRON_INGOT);
    ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
    MachineControllerBlockEntity controller = controllerWithComponents(input);
    MachineRecipe recipe = explicitRequirementRecipe(
            "missing_item_trace",
            List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY))
    );
    RecipeCraftingContext context = new RecipeCraftingContext(controller);

    assertThat(context.simulateInputs(recipe)).isFalse();
    assertThat(context.getLastRequirementFailure().searchedComponents()).isNotEmpty();
    assertThat(context.getLastRequirementFailure().matchedComponents()).isNotNull();
}
```

- [ ] **Step 2: Extend `RequirementFailure` compatibly**

Change record shape to include immutable lists while preserving the existing constructor:

```java
public record RequirementFailure(
        int requirementIndex,
        Kind kind,
        long required,
        long available,
        long shortAmount,
        List<String> searchedComponents,
        List<String> matchedComponents
) {
    public RequirementFailure(int requirementIndex, Kind kind, long required, long available) {
        this(requirementIndex, kind, required, available, Math.max(0, required - available), List.of(), List.of());
    }

    public RequirementFailure(int requirementIndex, Kind kind, long required, long available, long shortAmount) {
        this(requirementIndex, kind, required, available, shortAmount, List.of(), List.of());
    }

    public RequirementFailure {
        searchedComponents = List.copyOf(searchedComponents);
        matchedComponents = List.copyOf(matchedComponents);
    }
}
```

Use `long` for shortage fields now to avoid another model churn when fluid/parallelism grows amounts.

- [ ] **Step 3: Populate trace from context candidates**

In `RecipeCraftingContext`, add helpers:

```java
private static String componentTrace(BlockEntity entity) {
    return entity.getClass().getSimpleName() + "@" + entity.getBlockPos().toShortString();
}
```

For item/fluid/energy failures, pass searched component strings from the relevant live component list and matched strings for handlers/hatches that passed kind/io matching. Before selector tags, searched and matched will often be the same for same-type components; that is acceptable.

- [ ] **Step 4: Use commit-lost failure kinds for route invalidation**

If route exists but cannot execute during commit, use `COMMIT_LOST_INPUT` or `COMMIT_LOST_OUTPUT` instead of generic missing input/output. Keep `lastFailureUnloc` as existing missing input/output string so UI remains compatible.

- [ ] **Step 5: Verify Task 10**

Run:

```bash
rtk gradlew compileJava --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon
```

Expected: all commands succeed.

- [ ] **Step 6: Update progress ledger**

Append:

```text
Task 10: complete (structured failure traces and commit-lost kinds, compileJava and recipe tests pass, no commit)
```

---

### Task 11: Selector Tag Metadata Model

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/helper/ProcessingComponent.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/BlockArray.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/BlockArrayCache.java` or the actual cache/rotation file found in this repo
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/*Test.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java` only for metadata visibility, not matching yet

**Interfaces:**
- Produces: `ProcessingComponent.tags(): List<String>`
- Preserves: legacy `ProcessingComponent(..., @Nullable String tag)` constructor if it exists
- Produces: `BlockArray.tagged(BlockPos pos, String... tags): BlockArray`
- Produces: `BlockArray.tagsAt(BlockPos pos): List<String>` or equivalent getter

- [ ] **Step 1: Locate actual structure files**

Run:

```bash
rtk git ls-files 'src/main/java/cn/howxu/mmcr/api/machine/*BlockArray*' 'src/main/java/cn/howxu/mmcr/**/BlockArrayCache.java'
```

Expected: identify the exact file names before editing. If cache class has a different name, use the real path in this task’s report.

- [ ] **Step 2: Write ProcessingComponent tag compatibility tests**

Test requirements:

```java
@Test
void processingComponentConvertsLegacySingleTagToTagList() {
    ProcessingComponent component = new ProcessingComponent(machineComponent, container, relativePos, pos, "input_a");

    assertThat(component.tags()).containsExactly("input_a");
    assertThat(component.tag()).isEqualTo("input_a");
}

@Test
void processingComponentDefaultsNullTagToEmptyList() {
    ProcessingComponent component = new ProcessingComponent(machineComponent, container, relativePos, pos, null);

    assertThat(component.tags()).isEmpty();
    assertThat(component.tag()).isNull();
}
```

Keep `tag()` as a compatibility getter if existing callers use it.

- [ ] **Step 3: Write BlockArray tag metadata tests**

Add tests for:

- `tagged(pos, "input_a", "fast")` stores both tags.
- copy constructor preserves tags.
- offset constructor shifts tag position with pattern position.
- rotation/cache preserves tags for a non-zero relative position.

Expected assertions:

```java
assertThat(blockArray.tagsAt(new BlockPos(1, 0, 0))).containsExactly("input_a", "fast");
assertThat(rotated.tagsAt(expectedRotatedPos)).contains("input_a");
```

- [ ] **Step 4: Implement ProcessingComponent multi-tag support**

Use immutable list storage:

```java
private final List<String> tags;

public ProcessingComponent(..., @Nullable String tag) {
    this(..., tag == null ? List.of() : List.of(tag));
}

public ProcessingComponent(..., List<String> tags) {
    ...
    this.tags = List.copyOf(tags);
}

public List<String> tags() { return tags; }

public @Nullable String tag() { return tags.isEmpty() ? null : tags.getFirst(); }
```

- [ ] **Step 5: Implement BlockArray tag storage**

Add `Map<BlockPos, List<String>> tagsByPosition` and preserve it in copy, offset, rotate, and cache paths. Keep empty-list behavior for machines without tags.

Do not mutate caller-provided lists. Do not return mutable internal lists.

- [ ] **Step 6: Verify Task 11**

Run:

```bash
rtk gradlew compileJava --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.api.machine.* --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon
```

Expected: all commands succeed.

- [ ] **Step 7: Update progress ledger**

Append:

```text
Task 11: complete (selector tag metadata model, compileJava and machine/recipe tests pass, no commit)
```

---

### Task 12: Requirement Tags Codec and Matching

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/requirement/ItemRequirement.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/requirement/FluidRequirement.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirement.java` if energy tags are stored now
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/MachineRecipeTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`

**Interfaces:**
- Consumes: `ProcessingComponent.tags(): List<String>` from Task 11
- Produces: `MachineRequirement.tags(): List<String>` or per-record `tags()` accessors
- Produces: tag-aware candidate filtering for item and fluid requirements
- Produces: `RequirementFailure.Kind.TAG_MISMATCH` or trace showing searched non-matching components

- [ ] **Step 1: Write codec tests for requirement tags**

In `MachineRecipeTest`, add JSON roundtrip test:

```java
@Test
void recipeRequirementTagsRoundTripAndDefaultEmpty() {
    var root = new JsonObject();
    root.addProperty("id", "mmcr:tagged");
    root.addProperty("machine", "mmcr:machine");
    root.addProperty("tick_time", 20);
    JsonObject input = itemRequirement("input", itemId(Items.IRON_INGOT), 1);
    JsonArray tags = new JsonArray();
    tags.add("input_a");
    input.add("tags", tags);
    root.add("requirements", requirements(input));

    var recipe = MachineRecipe.CODEC.codec().parse(jsonOps(), root).getOrThrow();

    assertThat(recipe.requirements().getFirst().tags()).containsExactly("input_a");
    var encoded = MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow().getAsJsonObject();
    assertThat(encoded.getAsJsonArray("requirements").get(0).getAsJsonObject().getAsJsonArray("tags"))
            .extracting(JsonElement::getAsString)
            .containsExactly("input_a");
}
```

Also assert requirements without `tags` decode to `List.of()`.

- [ ] **Step 2: Write tag matching runtime tests**

Add tests:

- requirement tag empty uses untagged and tagged same-type components.
- requirement tag `input_a` uses component tagged `input_a` and ignores untagged component.
- requirement tag `input_a` does not consume from component tagged `input_b`.
- failure includes searched components and no matched components when only wrong-tag components exist.

Example core assertion:

```java
assertThat(context.simulateInputs(recipe)).isFalse();
assertThat(context.getLastRequirementFailure().kind()).isEqualTo(RequirementFailure.Kind.TAG_MISMATCH);
assertThat(context.getLastRequirementFailure().searchedComponents()).isNotEmpty();
assertThat(context.getLastRequirementFailure().matchedComponents()).isEmpty();
```

- [ ] **Step 3: Add tags to requirement records**

Use compact constructors to preserve existing constructor call sites:

```java
public record ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack, List<String> tags)
        implements MachineRequirement {
    public ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack) {
        this(io, item, count, stack, List.of());
    }

    public ItemRequirement {
        tags = List.copyOf(tags);
    }
}
```

Apply the same pattern to `FluidRequirement`. For `EnergyRequirement`, either add tags now for shape consistency or explicitly document energy tags deferred; preferred is adding `List<String> tags` with old constructor preserved.

- [ ] **Step 4: Update `MachineRequirement.CODEC`**

Add optional `tags` list decode/encode for all requirement types. Empty tags can be omitted from encoded JSON or encoded as `[]`; choose one and lock it in tests. Keep `inputs`/`outputs` old fields suppressed in `MachineRecipe.CODEC`.

- [ ] **Step 5: Implement tag matching helpers**

Add helper:

```java
private static boolean tagsMatch(List<String> requirementTags, List<String> componentTags) {
    if (requirementTags.isEmpty()) return true;
    if (componentTags.isEmpty()) return false;
    for (String tag : requirementTags) {
        if (componentTags.contains(tag)) return true;
    }
    return false;
}
```

When building item/fluid states, carry component tags or filter components before expanding handlers/tanks. Do not filter by tag after route creation.

- [ ] **Step 6: Populate tag mismatch failure**

If same-kind components exist but all are excluded by tag, set structured failure kind to `TAG_MISMATCH` while keeping `lastFailureUnloc` as missing input/output for existing UI compatibility.

- [ ] **Step 7: Verify Task 12**

Run:

```bash
rtk gradlew compileJava --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon
```

Expected: all commands succeed.

- [ ] **Step 8: Update progress ledger**

Append:

```text
Task 12: complete (requirement tags codec and item/fluid matching, compileJava and recipe tests pass, no commit)
```

---

### Task 13: Context Route Consolidation and Legacy Main Path Removal

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`

**Interfaces:**
- Consumes: item/fluid/energy requirement runtime from Tasks 8-12
- Produces: no `RecipeCraftingContext` runtime traversal of `MachineIngredient` for item/fluid/energy main path
- Produces: one route storage strategy, preferably requirement-index keyed for all start/finish IO

- [ ] **Step 1: Add guard test against legacy main path regression**

Add reflection/source-level test if acceptable in this project, or behavior tests that prove mixed legacy fields are ignored for item, fluid, and energy runtime.

Behavior coverage required:

- mixed item input uses explicit item requirement.
- mixed fluid output uses explicit fluid requirement.
- mixed energy uses explicit energy requirement.

- [ ] **Step 2: Consolidate route lists**

If `RecipeCraftingContext` still has separate list route structures for item/fluid, consolidate to a clearer requirement-indexed shape. Minimal acceptable shape:

```java
private List<ItemInputRoute> itemInputRoutes = List.of();
private List<ItemOutputRoute> itemOutputRoutes = List.of();
private List<FluidInputRoute> fluidInputRoutes = List.of();
private List<FluidOutputRoute> fluidOutputRoutes = List.of();
```

All four lists must be sized to `requirements.size()` and accessed by requirement index. Do not use per-type counters like `itemIdx` / `fluidIdx`.

Preferred shape if it simplifies code without overreach:

```java
private final Map<Integer, RequirementRoute> inputRoutes = new HashMap<>();
private final Map<Integer, RequirementRoute> outputRoutes = new HashMap<>();
```

Do not introduce a public adapter registry.

- [ ] **Step 3: Remove direct legacy runtime branches**

In `RecipeCraftingContext`, remove loops over:

- `recipe.inputs()` in `simulateInputs`, `commitInputs`, and `ioTick`.
- `recipe.outputs()` in `simulateOutputs` and `commitOutputs`.
- `recipe.fluidOutputs()` in `simulateOutputs` and `commitOutputs`.

Compatibility getters may remain in `MachineRecipe`; they are not the runtime source.

- [ ] **Step 4: Verify no route rescan during commit**

Keep route invalidation tests for item and add fluid equivalent if missing:

```java
@Test
void commitInputsValidatesAllFluidRoutesBeforeMutatingItemsOrFluids() { ... }
```

The assertion must prove no earlier item/fluid route mutates when a later fluid route has become invalid.

- [ ] **Step 5: Verify Task 13**

Run:

```bash
rtk gradlew compileJava --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon
```

Expected: all commands succeed.

- [ ] **Step 6: Update progress ledger**

Append:

```text
Task 13: complete (context runtime route consolidation and legacy main path removal, compileJava and recipe tests pass, no commit)
```

---

### Task 14: Controller Aggregate Energy/Fluid Getters

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java` or add focused aggregate test
- Read/possibly use: `src/main/java/cn/howxu/mmcr/internal/tile/EnergyInputHatchBlockEntity.java`
- Read/possibly use: `src/main/java/cn/howxu/mmcr/internal/tile/FluidInputHatchBlockEntity.java`
- Read/possibly use: `src/main/java/cn/howxu/mmcr/internal/tile/FluidOutputHatchBlockEntity.java`

**Interfaces:**
- Produces: `long totalStoredEnergy()`
- Produces: `long totalCapacityEnergy()`
- Produces: `FluidStack primaryFluid()`
- Produces: `FluidStack primaryOutputFluid()`

- [ ] **Step 1: Write aggregate getter tests**

Construct a controller with formed components and hatches. Assert:

- no hatches returns zero energy and `FluidStack.EMPTY`.
- two energy hatches sum stored/capacity.
- primary fluid returns first non-empty input hatch fluid.
- primary output fluid returns first non-empty output hatch fluid.

- [ ] **Step 2: Implement controller getters**

In `MachineControllerBlockEntity`, iterate `getComponents()` and inspect live block entities or component containers consistently with existing context logic. Use long accumulation for FE:

```java
public long totalStoredEnergy() { ... }
public long totalCapacityEnergy() { ... }
public FluidStack primaryFluid() { ... }
public FluidStack primaryOutputFluid() { ... }
```

Do not derive these values from recipe requirements.

- [ ] **Step 3: Verify Task 14**

Run:

```bash
rtk gradlew compileJava --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.internal.tile.* --no-daemon
```

Expected: all commands succeed. If tile test target compiles unrelated broken tests, use the exact new test class name.

- [ ] **Step 4: Update progress ledger**

Append:

```text
Task 14: complete (controller aggregate energy/fluid getters, compileJava and tile aggregate tests pass, no commit)
```

---

### Task 15: Controller GUI Aggregate Display

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/menu/MachineControllerMenu.java`
- Modify: `src/main/java/cn/howxu/mmcr/client/gui/MachineMenuScreen.java`
- Modify: `src/main/resources/assets/mmcr/lang/en_us.json` and any existing zh_cn/lang file if present
- Modify: `src/test/java/cn/howxu/mmcr/internal/menu/MachineControllerMenuTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/client/gui/MenuScreenTest.java`

**Interfaces:**
- Consumes: controller aggregate getters from Task 14
- Produces: controller GUI preview for structure-level energy/fluid state
- Preserves: item bus, fluid hatch, and energy hatch GUI behavior

- [ ] **Step 1: Write menu/screen tests**

Tests should cover:

- `MachineControllerMenu` can expose aggregate FE values without int overflow.
- screen layout leaves status/progress/redstone text visible.
- no hatches does not crash and renders no aggregate bars/text or renders `0 / 0` safely.

If long DataSlot sync is not implemented, tests should assert owner fallback behavior for local/client menu:

```java
assertThat(menu.resolvedOwner()).isSameAs(controller);
```

- [ ] **Step 2: Implement menu accessors**

Prefer owner fallback for fluid identity and long energy values first. Do not silently cast `long` to `int` if capacity can exceed `Integer.MAX_VALUE`.

Acceptable methods:

```java
public long totalStoredEnergy() { return resolvedOwner() == null ? 0L : resolvedOwner().totalStoredEnergy(); }
public long totalCapacityEnergy() { return resolvedOwner() == null ? 0L : resolvedOwner().totalCapacityEnergy(); }
public FluidStack primaryFluid() { return resolvedOwner() == null ? FluidStack.EMPTY : resolvedOwner().primaryFluid(); }
public FluidStack primaryOutputFluid() { return resolvedOwner() == null ? FluidStack.EMPTY : resolvedOwner().primaryOutputFluid(); }
```

- [ ] **Step 3: Render conservative aggregate preview**

In `MachineMenuScreen.renderControllerStatus`, add compact lines after status/progress and before redstone/failure only if values exist. Do not overlap inventory slots.

Example text keys:

- `gui.mmcr.controller.energy`: `Energy: %s / %s FE`
- `gui.mmcr.controller.fluid_input`: `Input Fluid: %s %s mB`
- `gui.mmcr.controller.fluid_output`: `Output Fluid: %s %s mB`

Use existing `NUMBER_FORMAT` for amounts.

- [ ] **Step 4: Verify GUI/menu task**

Run:

```bash
rtk gradlew compileJava --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.internal.menu.* --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.client.gui.* --no-daemon
```

Expected: all commands succeed.

- [ ] **Step 5: Update progress ledger**

Append:

```text
Task 15: complete (controller GUI aggregate display, compileJava and menu/gui tests pass, no commit)
```

---

### Task 16: P2 Final Verification and Commit Boundary Cleanup

**Files:**
- Modify: `.superpowers/sdd/progress.md`
- Modify: docs only if final notes reveal outdated Task 7 guidance
- Do not modify Java unless final verification finds a direct regression

**Interfaces:**
- Consumes: completed Tasks 8-15
- Produces: final P2 completion record and clean handoff summary

- [ ] **Step 1: Run final static checks**

Run:

```bash
rtk git status --short --branch
rtk git diff --stat
```

Expected: worktree contains only intended P2 changes plus any explicitly unrelated pre-existing changes. Do not revert unrelated multiblock detector/resource edits.

- [ ] **Step 2: Run final compile and unit tests**

Run sequentially, not in parallel:

```bash
rtk gradlew compileJava --no-daemon
rtk gradlew test --no-daemon
```

Expected: both commands succeed. If full `test` fails because of known environment/game bootstrap issues, immediately run the narrower suites below and document exact failure:

```bash
rtk gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.api.machine.* --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.internal.menu.* --no-daemon
rtk gradlew test --tests cn.howxu.mmcr.client.gui.* --no-daemon
```

- [ ] **Step 3: Run final P2 checklist**

Confirm each item manually and record gaps if any:

- `MachineRecipe.requirements()` is the runtime IO entry for item/fluid/energy.
- `RecipeCraftingContext` no longer traverses `MachineIngredient` for runtime main path.
- item/fluid/energy requirements have codec and runtime behavior.
- old fields decode into requirements.
- encode shape remains requirements-only.
- item/fluid multi-component aggregation works.
- output space failure does not consume inputs.
- route invalidation does not partially commit.
- selector tags flow from structure metadata to component to requirement matching.
- structured failure includes requirement index, shortage, searched components, and matched components.
- controller GUI shows aggregate energy/fluid safely.

- [ ] **Step 4: Update progress ledger**

Append:

```text
Task 16: complete (P2 final verification complete; compileJava and final test status recorded, no commit)
```

If full `test` did not pass, write the exact blocked status instead of “complete”.

- [ ] **Step 5: Commit recommendation**

If the user wants commits, split commits by the boundaries already used:

```bash
rtk git add .superpowers/sdd/progress.md docs/superpowers/specs docs/superpowers/plans src/main/java src/test/java src/main/resources
rtk git commit -m "feat(recipe): complete requirement runtime phase 2"
```

Only commit after inspecting `rtk git diff --stat` and confirming no unrelated files are staged accidentally. If unrelated multiblock detector texture/model edits remain, either exclude them from the P2 commit or ask the user which commit they belong to.

---

## Self-Review Checklist

- Spec coverage: Tasks 8-16 cover fluid runtime, energy runtime, structured failure traces, selector tag metadata/matching, context cleanup, controller aggregate getters, GUI display, and final verification.
- Placeholder scan: no `TBD`, no generic “write tests” without concrete examples, no unnamed commands.
- Type consistency: tasks use existing `MachineRecipe.requirements()`, `ItemRequirement`, `FluidRequirement`, `EnergyRequirement`, `RequirementFailure`, `ProcessingComponent`, and controller/menu/screen classes.
- Scope control: JEI, third-party requirements, side-aware routing, and adapter registries are explicitly excluded.
- Verification: each task has concrete `rtk gradlew` commands and expected outcomes.
