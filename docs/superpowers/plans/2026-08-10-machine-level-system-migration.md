# Machine Level System Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Safely integrate the machine-level system into the current main branch without changing behavior for machines and recipes that do not opt in.

**Architecture:** Retain current main as the source of truth for controller lifecycle, recipe pause persistence, and fluid route recovery. Add immutable startup-defined levels, attach optional level-slot metadata to structures, resolve a persisted level snapshot only after normal formation succeeds, and consume it only where a recipe declares level requirements or modifiers are present.

**Tech Stack:** Java 25, NeoForge 26.1.2, KubeJS, JEI, Gradle.

## Global Constraints

- Start from current main commit `1bddc5b`; do not merge the level worktree directly.
- Preserve existing no-level structure, recipe search, paused-recipe recovery, fluid route recovery, and menu behavior.
- Machine levels are registered only in KubeJS startup scripts and remain immutable during server resource reloads.
- Use no new dependencies and retain existing package and formatting conventions.
- Run only the final focused verification command and one final `compileJava` command.

---

### Task 1: Add lifecycle-bound machine-level declarations

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/machine/level/LevelType.java`
- Create: `src/main/java/cn/howxu/mmcr/api/machine/level/LevelModifier.java`
- Create: `src/main/java/cn/howxu/mmcr/api/machine/level/MachineLevel.java`
- Create: `src/main/java/cn/howxu/mmcr/api/machine/level/MachineLevelRegistry.java`
- Test: `src/test/java/cn/howxu/mmcr/api/machine/level/MachineLevelRegistryTest.java`

**Interfaces:**
- Produces `LevelType(Identifier id, Component displayName)`.
- Produces `LevelModifier(double durationMultiplier, double energyMultiplier, double outputMultiplier, int parallelismBonus, int factoryThreadBonus)` with `IDENTITY`.
- Produces `MachineLevel(Identifier id, Identifier typeId, int priority, BlockPredicate statePredicate, ItemStack representative, LevelModifier modifier)`.
- Produces registry methods `beginRegistration()`, `freezeRegistration()`, `registerType(LevelType)`, `registerLevel(MachineLevel)`, `getType(Identifier)`, `getLevel(Identifier)`, `levelsForType(Identifier)`, and `findLevel(BlockState)`.

- [ ] **Step 1: Add registry tests for lifecycle, duplicate priority, duplicate state, and state lookup.**

```java
@Test
void rejectsRegistrationsOutsideStartupPhase() {
    assertThrows(IllegalStateException.class, () -> MachineLevelRegistry.registerType(type()));
    MachineLevelRegistry.beginRegistration();
    MachineLevelRegistry.registerType(type());
    MachineLevelRegistry.freezeRegistration();
    assertThrows(IllegalStateException.class, () -> MachineLevelRegistry.registerLevel(level("test:copper", 1, Blocks.COPPER_BLOCK)));
}

@Test
void resolvesOnlyOneRegisteredLevelForAnExactState() {
    MachineLevelRegistry.beginRegistration();
    MachineLevelRegistry.registerType(type());
    MachineLevel copper = level("test:copper", 1, Blocks.COPPER_BLOCK);
    MachineLevelRegistry.registerLevel(copper);
    assertSame(copper, MachineLevelRegistry.findLevel(Blocks.COPPER_BLOCK.defaultBlockState()).orElseThrow());
    assertThrows(IllegalStateException.class, () -> MachineLevelRegistry.registerLevel(level("test:duplicate", 1, Blocks.IRON_BLOCK)));
}
```

- [ ] **Step 2: Implement records with non-null validation, positive multiplier validation, and an insertion-ordered registry.**

```java
public record LevelModifier(double durationMultiplier, double energyMultiplier, double outputMultiplier,
                            int parallelismBonus, int factoryThreadBonus) {
    public static final LevelModifier IDENTITY = new LevelModifier(1D, 1D, 1D, 0, 0);

    public LevelModifier {
        if (durationMultiplier <= 0D || energyMultiplier <= 0D || outputMultiplier <= 0D) {
            throw new IllegalArgumentException("Machine level multipliers must be positive");
        }
    }
}
```

Require `BlockPredicate.OfBlockState` for a registered level, index its exact `BlockState`, reject duplicate level IDs, duplicate priorities per type, and duplicate states. `beginRegistration()` clears previous startup values so `/kubejs reload_startup_scripts` gets a clean definition set; `freezeRegistration()` makes all registry maps immutable views.

- [ ] **Step 3: Add a KubeJS startup lifecycle hook around startup-script registration.**

Modify `src/main/java/cn/howxu/mmcr/compat/kubejs/Plugin.java` so the hook opens the registry before MMCR startup builders run and freezes it afterward. Use the same KubeJS startup event registration mechanism already used by `Plugin`; do not perform this operation in server scripts or recipe reload callbacks.

- [ ] **Step 4: Commit the declaration foundation.**

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/level src/main/java/cn/howxu/mmcr/compat/kubejs/Plugin.java src/test/java/cn/howxu/mmcr/api/machine/level
```

### Task 2: Expose documented KubeJS API and optional structure slots

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/machine/level/LevelSlot.java`
- Create: `src/main/java/cn/howxu/mmcr/compat/kubejs/LevelTypeBuilderJS.java`
- Create: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineLevelBuilderJS.java`
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/Plugin.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/MachineStructureDefinition.java`
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineStructureBuilderJS.java`
- Test: `src/test/java/cn/howxu/mmcr/compat/kubejs/MachineLevelBuilderJSTest.java`
- Test: `src/test/java/cn/howxu/mmcr/compat/kubejs/MachineStructureBuilderJSTest.java`

**Interfaces:**
- Produces `LevelSlot(Identifier typeId)` and `MachineStructureDefinition.levelSlots(): Map<BlockPos, Identifier>`.
- Exposes `MMCR.levelTypes.create(id)`, `MMCR.levels.create(id)`, and `MMCR.levelSlot(typeId)`.

- [ ] **Step 1: Add tests that invoke the actual `MMCR` object API, register a type and level, and build a pattern with a level slot.**

```java
assertThat(plugin.mmcr().levelTypes().create("test:coil").displayName("Coils").register()).isNotNull();
assertThat(plugin.mmcr().levels().create("test:copper").type("test:coil").priority(1)
        .state("minecraft:copper_block").register()).isNotNull();
MachineStructureDefinition definition = builder.pattern("C", Map.of("C", plugin.mmcr().levelSlot("test:coil"))).createObject();
assertThat(definition.levelSlots()).containsEntry(BlockPos.ZERO, Identifier.parse("test:coil"));
assertThat(MachineLevelRegistry.getLevel(Identifier.parse("test:copper")).representative().is(Items.COPPER_BLOCK)).isTrue();
```

- [ ] **Step 2: Implement builders and bind a nested `MMCR` API object rather than only raw builder classes.**

```java
public MachineLevel createObject() {
    BlockState resolved = Objects.requireNonNull(state, "state must be set");
    return new MachineLevel(id, typeId, priority, new BlockPredicate.OfBlockState(resolved),
            new ItemStack(resolved.getBlock()), modifier);
}
```

Accept string block IDs and `BlockState` values for `.state`. Apply omitted modifier fields from `LevelModifier.IDENTITY`. Let `MachineStructureBuilderJS` turn a `LevelSlot` into a predicate containing all registered states of its type, while separately retaining every slot coordinate in `MachineStructureDefinition`. Existing non-level pattern keys must retain their current conversion path.

- [ ] **Step 3: Commit KubeJS API and structure metadata.**

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/MachineStructureDefinition.java src/main/java/cn/howxu/mmcr/api/machine/level/LevelSlot.java src/main/java/cn/howxu/mmcr/compat/kubejs src/test/java/cn/howxu/mmcr/compat/kubejs
```

### Task 3: Resolve and persist formed machine levels

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/machine/level/LevelMismatch.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/StructureMatcher.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerLevelTest.java`

**Interfaces:**
- Produces `StructureMatcher.resolveLevels(MachineStructureDefinition, BlockArray, Level, BlockPos): Either<LevelMismatch, Map<Identifier, MachineLevel>>`.
- Produces `MachineControllerBlockEntity.getFoundLevels(): Map<Identifier, MachineLevel>`.

- [ ] **Step 1: Add formation and persistence tests.**

```java
@Test
void rejectsMixedLevelsAndreportsTheConflictingWorldPosition() {
    placeLevel(COPPER, 0);
    placeLevel(KANTHAL, 2);
    checkStructure();
    assertThat(controller.isStructureFormed()).isFalse();
    assertThat(controller.getLastStructureError()).isInstanceOf(LevelMismatch.class);
}

@Test
void restoresLevelSnapshotBeforeTheNextStructureCheck() {
    formWith(COPPER);
    ValueOutput output = saveController(controller);
    MachineControllerBlockEntity loaded = loadController(output);
    assertThat(loaded.getFoundLevels()).containsEntry(COIL_TYPE, copperLevel());
}
```

- [ ] **Step 2: Resolve levels only after the existing base pattern, modifier replacement, port-count, and port-tier checks succeed.**

```java
private boolean applyResolvedLevels(MachineStructureDefinition definition, BlockArray pattern) {
    var result = StructureMatcher.resolveLevels(definition, pattern, level, getBlockPos());
    if (result.right().isPresent()) {
        recordFormationFailure(foundMachine, result.right().get());
        return false;
    }
    foundLevels = result.left().orElse(Map.of());
    return true;
}
```

Use the existing orientation/compiled-pattern coordinate conversion when resolving slots. Clear `foundLevels` in every reset path. Serialize level type and level IDs under a dedicated controller NBT field and, while loading, resolve only currently registered IDs; ignore missing definitions and mark the structure dirty so normal formation recomputes state. Never replace the main branch paused active-recipe or context restoration code.

- [ ] **Step 3: Commit formed-level handling.**

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/StructureMatcher.java src/main/java/cn/howxu/mmcr/api/machine/level/LevelMismatch.java src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerLevelTest.java
```

### Task 4: Add recipe requirements and runtime modifiers without changing base recipes

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/recipe/LevelRequirement.java`
- Create: `src/main/java/cn/howxu/mmcr/api/recipe/LevelInsufficientFailure.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchTask.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchResult.java`
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeBuilderJS.java`
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeFactory.java`
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeSchema.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/MachineRecipeTest.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeSearchTaskTest.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipeTest.java`

**Interfaces:**
- Produces `LevelRequirement(Identifier typeId, Identifier levelId)` and `MachineRecipe.levelRequirements()`.
- Produces KubeJS `requiresLevel(typeId, levelId)`.

- [ ] **Step 1: Add codec, requirement, fallback-blocking, modifier-order, and no-level regression tests.**

```java
@Test
void higherPriorityInputCompatibleLevelFailureBlocksLowerPriorityFallback() {
    controller.setFoundLevels(Map.of(COIL_TYPE, copper()));
    RecipeSearchResult result = search(highPriorityRequiresKanthal(), lowPriorityRequiresCopper());
    assertThat(result.failure()).isInstanceOf(LevelInsufficientFailure.class);
}

@Test
void recipeWithoutLevelsRetainsItsCurrentDurationEnergyAndOutputs() {
    RecipeCraftingContext context = contextFor(recipeWithoutLevels());
    assertThat(context.getRecipeTotalTickTime()).isEqualTo(knownCurrentDuration);
    assertThat(context.getRequiredEnergyPerTick()).isEqualTo(knownCurrentEnergy);
}
```

- [ ] **Step 2: Extend `MachineRecipe` constructors and codec with an empty-default `List<LevelRequirement>`.**

Validate that each requirement has a unique type, refers to a registered level, and that the level belongs to its declared type. Keep existing overloads forwarding `List.of()` so all current default recipes and tests preserve their constructor behavior.

- [ ] **Step 3: In `RecipeSearchTask`, check levels after input compatibility succeeds and before starting the recipe.**

```java
private @Nullable LevelInsufficientFailure levelFailure(MachineRecipe recipe) {
    for (LevelRequirement requirement : recipe.levelRequirements()) {
        MachineLevel required = MachineLevelRegistry.getLevel(requirement.levelId());
        MachineLevel actual = controller.getFoundLevels().get(requirement.typeId());
        if (actual == null || actual.priority() < required.priority()) {
            return new LevelInsufficientFailure(requirement, actual);
        }
    }
    return null;
}
```

If inputs satisfy a highest-priority candidate but this method fails, return the failure rather than consider lower-priority candidates. Keep current handling for candidates whose inputs do not satisfy, ensuring normal input-specific recipe routing is unchanged.

- [ ] **Step 4: Apply a stable type-ID-sorted snapshot of level modifiers before existing recipe and structure modifiers.**

```java
private List<MachineLevel> sortedLevels() {
    return controller.getFoundLevels().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .toList();
}
```

Multiply duration, energy, item-output, and fluid-output values; floor scaled values and preserve a minimum of one for originally positive outputs. Add parallelism and factory-thread bonuses to the existing calculated values, respecting the final hard limits already enforced by the controller. When `foundLevels` is empty, return existing values without changing the current calculation path.

- [ ] **Step 5: Add `requiresLevel` to KubeJS recipe builders and schema.**

```java
public MachineRecipeBuilderJS requiresLevel(String typeId, String levelId) {
    Identifier type = Identifier.parse(typeId);
    MachineLevel level = Objects.requireNonNull(MachineLevelRegistry.getLevel(Identifier.parse(levelId)), "Unknown machine level");
    if (!level.typeId().equals(type)) throw new IllegalArgumentException("Level does not belong to type: " + typeId);
    levelRequirements.add(new LevelRequirement(type, level.id()));
    return this;
}
```

- [ ] **Step 6: Commit recipe integration.**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeBuilderJS.java src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeFactory.java src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeSchema.java src/test/java/cn/howxu/mmcr/api/recipe
```

### Task 5: Render requirements in JEI and finalize documentation

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategory.java`
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeLayout.java`
- Modify: `src/main/java/cn/howxu/mmcr/datagen/Translations.java`
- Create: `docs/architecture.md`
- Create: `docs/kubejs-integration.md`
- Test: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java`
- Test: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeLayoutTest.java`

**Interfaces:**
- Consumes `MachineRecipe.levelRequirements()` and `MachineLevel.representative()`.
- Produces an optional JEI row directly below energy information for each level requirement.

- [ ] **Step 1: Add JEI tests proving no-level layouts do not move and required-level rows use the generated representative stack.**

```java
assertThat(layoutFor(recipeWithoutLevels()).height()).isEqualTo(previousHeight);
assertThat(layoutFor(recipeRequiring(COIL_TYPE, KANTHAL)).levelRequirementY(display, 0))
        .isEqualTo(layoutFor(recipeRequiring(COIL_TYPE, KANTHAL)).energyY(display) + LINE_HEIGHT);
assertThat(levelRequirementIcons(recipeRequiring(COIL_TYPE, KANTHAL)).getFirst()).isEqualTo(new ItemStack(Blocks.IRON_BLOCK));
```

- [ ] **Step 2: Draw each level row only for defined requirements.**

Resolve the level from the frozen registry, draw its representative slot if non-empty, and use `LevelType.displayName()` plus the level identifier/name and the translated `or higher` suffix. Reserve no space when the requirement list is empty.

- [ ] **Step 3: Document exact startup/server lifecycle and intentional limitations.**

Document live API examples matching `MMCR.levelTypes.create`, `MMCR.levels.create`, `MMCR.levelSlot`, and `requiresLevel`; state mixed-level formation failure, priority semantics, persistent controller snapshot, modifier order, and lack of level-slot structure preview.

- [ ] **Step 4: Commit presentation and documentation.**

```bash
git add src/main/java/cn/howxu/mmcr/compat/jei src/main/java/cn/howxu/mmcr/datagen/Translations.java src/test/java/cn/howxu/mmcr/compat/jei
```

### Task 6: Run final focused compatibility verification

**Files:**
- Test: `src/test/java/cn/howxu/mmcr/api/machine/level/MachineLevelRegistryTest.java`
- Test: `src/test/java/cn/howxu/mmcr/compat/kubejs/MachineLevelBuilderJSTest.java`
- Test: `src/test/java/cn/howxu/mmcr/compat/kubejs/MachineStructureBuilderJSTest.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerLevelTest.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/MachineRecipeTest.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeSearchTaskTest.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipeTest.java`
- Test: existing pause/recovery tests under `src/test/java/cn/howxu/mmcr/internal/tile/`
- Test: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java`
- Test: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeLayoutTest.java`

- [ ] **Step 1: Run the single focused test command.**

```bash
./gradlew test --tests cn.howxu.mmcr.api.machine.level.MachineLevelRegistryTest --tests cn.howxu.mmcr.compat.kubejs.MachineLevelBuilderJSTest --tests cn.howxu.mmcr.compat.kubejs.MachineStructureBuilderJSTest --tests cn.howxu.mmcr.internal.tile.MachineControllerLevelTest --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --tests cn.howxu.mmcr.api.recipe.RecipeSearchTaskTest --tests cn.howxu.mmcr.api.recipe.ActiveMachineRecipeTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --tests cn.howxu.mmcr.compat.jei.MachineRecipeDisplayTest --tests cn.howxu.mmcr.compat.jei.MachineRecipeLayoutTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Compile all main Java sources once.**

```bash
./gradlew compileJava --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Check final diff and commit any verification-only test corrections.**

```bash
git diff --check
```

Expected: no whitespace errors and only intended tracked changes.
