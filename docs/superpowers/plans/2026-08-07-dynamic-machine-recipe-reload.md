# Dynamic Machine And Recipe Reload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an atomic, replaceable MMCR dynamic machine and recipe layer, plus an `/mmcr reload` command that refreshes only MMCR runtime content.

**Architecture:** Keep existing API registrations as the static layer and add dynamic maps to `MachineDefinitions`, `MachineRegistry`, and `RecipeRegistry`. `DynamicContentReloadService` owns a candidate session and commits its maps atomically into all three registries before rebuilding compiled structure and crafting-context caches. KubeJS is not changed in this plan; its future adapter will fill the service's candidate session and call its commit/abort operations.

**Tech Stack:** Java 25, NeoForge 26.1.2, JUnit 5, Gradle.

## Global Constraints

- Do not add, update, or test KubeJS integration; it has not been migrated to Minecraft 26.1.2.
- `/mmcr reload` reloads MMCR dynamic machines and recipes only; it must not invoke the vanilla datapack reload.
- Static/API registrations remain valid for the server lifetime and must not be overwritten by dynamic IDs.
- A failed candidate session must leave the prior dynamic layer and all runtime caches unchanged.
- Controller registry objects cannot be removed at runtime; report deleted dynamic machine IDs only to the command executor.
- Do not implement controller models, textures, translations, orientation, JEI synchronization, or `/reload` Mixins in this plan.

---

## File Structure

- Create: `src/main/java/cn/howxu/mmcr/internal/reload/DynamicContentReloadService.java` - owns dynamic candidate sessions, commits registry snapshots, refreshes caches, and returns changed ID sets.
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/MachineDefinitions.java` - separates static definition registrations from replaceable dynamic definitions while preserving merged queries.
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/MachineRegistry.java` - separates static machines from dynamic machines and rebuilds compiled caches for the merged view.
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeRegistry.java` - separates static recipes from dynamic recipes and rebuilds the merged per-machine index on snapshot replacement.
- Modify: `src/main/java/cn/howxu/mmcr/internal/command/ReloadCommand.java` - delegates `/mmcr reload` to the dynamic reload service and reports its result to the command executor.
- Create: `src/test/java/cn/howxu/mmcr/internal/reload/DynamicContentReloadServiceTest.java` - proves atomic sessions, conflict rejection, replacement, deletion, and cache refresh.
- Create: `src/test/java/cn/howxu/mmcr/internal/command/ReloadCommandTest.java` - proves command registration and execution do not initiate a vanilla server reload.

## Public Interfaces

`DynamicContentReloadService` exposes only the interfaces KubeJS will need later:

```java
public final class DynamicContentReloadService {
    public static Candidate begin();

    public static ReloadResult reload(Consumer<Candidate> producer);

    public static final class Candidate {
        public void registerMachine(Machine machine);
        public void registerRecipe(MachineRecipe recipe);
        public Machine getMachine(Identifier id);
    }

    public record ReloadResult(
            Set<Identifier> addedMachines,
            Set<Identifier> updatedMachines,
            Set<Identifier> removedMachines,
            int addedRecipes,
            int updatedRecipes,
            int removedRecipes) { }
}
```

`MachineDefinitions`, `MachineRegistry`, and `RecipeRegistry` each add a public `replaceDynamic(...)` method plus a public immutable `dynamicSnapshot()` accessor for the reload service. Normal `register(...)` stays static/API registration. Their existing public query methods remain merged-view methods.

### Task 1: Add Dynamic Machine Definition Layer

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/MachineDefinitions.java`
- Modify: `src/test/java/cn/howxu/mmcr/MachineDefinitionBootstrapTest.java`

**Interfaces:**
- Produces: `public static boolean containsStatic(Identifier id)`, `public static void replaceDynamic(Map<Identifier, Machine> machines)`, and `public static Map<Identifier, Machine> dynamicSnapshot()`.
- Produces: merged `get(Identifier)` and `all()` behavior for later registry/service tasks.

- [ ] **Step 1: Add a failing merged-view and collision test**

Add tests that first register a static `DynamicMachine` through `MachineDefinitions.register`, then replace the dynamic map and assert both IDs appear in `all()`. Assert `get()` resolves both. Assert replacing with the static ID throws `IllegalStateException` and the existing dynamic map remains unchanged.

```java
@Test
void dynamicDefinitionsMergeWithStaticAndRejectStaticIds() {
    var staticId = Identifier.parse("mmcr:static_machine");
    var dynamicId = Identifier.parse("mmcr:dynamic_machine");
    MachineDefinitions.register(new DynamicMachine(staticId, "Static", new BlockArray(Map.of())));

    MachineDefinitions.replaceDynamic(Map.of(dynamicId,
            new DynamicMachine(dynamicId, "Dynamic", new BlockArray(Map.of()))));

    assertThat(MachineDefinitions.get(staticId)).isNotNull();
    assertThat(MachineDefinitions.get(dynamicId)).isNotNull();
    assertThat(MachineDefinitions.all()).extracting(Machine::registryName)
            .containsExactly(staticId, dynamicId);
    assertThatThrownBy(() -> MachineDefinitions.replaceDynamic(Map.of(staticId,
            new DynamicMachine(staticId, "Conflict", new BlockArray(Map.of())))))
            .isInstanceOf(IllegalStateException.class);
    assertThat(MachineDefinitions.get(dynamicId)).isNotNull();
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.MachineDefinitionBootstrapTest --no-daemon`

Expected: compilation failure because `replaceDynamic` does not exist.

- [ ] **Step 3: Implement the two definition maps and replacement validation**

Replace the current `DEFINITIONS` field with `STATIC_DEFINITIONS` and `DYNAMIC_DEFINITIONS`. Keep `register(Machine)` writing only to the static map and rejecting duplicate static IDs. Implement `replaceDynamic` by validating every dynamic ID against the static map before assigning a defensive `LinkedHashMap` copy. Build the merged map in static-then-dynamic order for `all()`, and resolve static first in `get()`.

```java
static void replaceDynamic(Map<Identifier, Machine> machines) {
    var replacement = new LinkedHashMap<Identifier, Machine>();
    for (var entry : machines.entrySet()) {
        if (STATIC_DEFINITIONS.containsKey(entry.getKey())) {
            throw new IllegalStateException("Dynamic machine conflicts with static definition: " + entry.getKey());
        }
        replacement.put(entry.getKey(), entry.getValue());
    }
    DYNAMIC_DEFINITIONS.clear();
    DYNAMIC_DEFINITIONS.putAll(replacement);
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./gradlew test --tests cn.howxu.mmcr.MachineDefinitionBootstrapTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit the definition layer**

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/MachineDefinitions.java src/test/java/cn/howxu/mmcr/MachineDefinitionBootstrapTest.java
git commit -m "feat: layer dynamic machine definitions"
```

### Task 2: Add Dynamic Runtime Machine Layer And Cache Rebuild

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/MachineRegistry.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/MachineRegistryTest.java`

**Interfaces:**
- Consumes: `MachineDefinitions.replaceDynamic(Map<Identifier, Machine>)` from Task 1.
- Produces: `public static void replaceDynamic(Map<Identifier, Machine> machines)`, `public static boolean containsStatic(Identifier id)`, and `public static Map<Identifier, Machine> dynamicSnapshot()`.
- Produces: merged `getMachine`, `getAll`, `getCompiled`, and `getAllCompiled` views.

- [ ] **Step 1: Add a failing dynamic replacement test**

Add a test that registers one static machine, replaces the dynamic layer with a second machine, verifies both compile, then replaces the dynamic layer with an empty map. Verify the dynamic machine and compiled pattern disappear, while the static machine remains.

```java
@Test
void replacingDynamicMachinesRebuildsMergedCompiledCache() {
    var staticMachine = new DynamicMachine(Identifier.parse("mmcr:static"), "Static", new BlockArray(Map.of()));
    var dynamicMachine = new DynamicMachine(Identifier.parse("mmcr:dynamic"), "Dynamic", new BlockArray(Map.of()));
    MachineRegistry.register(staticMachine);

    MachineRegistry.replaceDynamic(Map.of(dynamicMachine.registryName(), dynamicMachine));

    assertThat(MachineRegistry.getCompiled(staticMachine.registryName())).isNotNull();
    assertThat(MachineRegistry.getCompiled(dynamicMachine.registryName())).isNotNull();

    MachineRegistry.replaceDynamic(Map.of());

    assertThat(MachineRegistry.getMachine(staticMachine.registryName())).isSameAs(staticMachine);
    assertThat(MachineRegistry.getMachine(dynamicMachine.registryName())).isNull();
    assertThat(MachineRegistry.getCompiled(dynamicMachine.registryName())).isNull();
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.MachineRegistryTest --no-daemon`

Expected: compilation failure because `replaceDynamic` does not exist.

- [ ] **Step 3: Implement static/dynamic merged machine storage**

Rename current machine storage to `STATIC_MACHINES`, add `DYNAMIC_MACHINES`, and build a temporary static-then-dynamic merged map for cache rebuilding. `register` remains static-only. `replaceDynamic` validates every ID against `STATIC_MACHINES`, replaces the dynamic map, then calls `rebuildCompiledCache`. `getMachine` checks static then dynamic. `getAll` returns an unmodifiable copy of the merged view. `rebuildCompiledCache` must call `BlockArrayCache.buildCache` and compile the merged values.

```java
public static void rebuildCompiledCache() {
    Map<Identifier, Machine> machines = mergedMachines();
    BlockArrayCache.buildCache(machines.values());
    COMPILED.clear();
    for (Machine machine : machines.values()) {
        COMPILED.put(machine.registryName(), MachinePatternCompiler.compile(machine));
    }
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.MachineRegistryTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit the runtime machine layer**

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/MachineRegistry.java src/test/java/cn/howxu/mmcr/api/machine/MachineRegistryTest.java
git commit -m "feat: replace dynamic runtime machines"
```

### Task 3: Add Dynamic Recipe Layer And Rebuildable Index

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeRegistry.java`
- Create: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeRegistryTest.java`

**Interfaces:**
- Consumes: `MachineRegistry.containsStatic(Identifier)` and merged `MachineRegistry.getMachine(Identifier)` from Task 2.
- Produces: `public static void replaceDynamic(Map<Identifier, MachineRecipe> recipes)`, `public static boolean containsStatic(Identifier id)`, and `public static Map<Identifier, MachineRecipe> dynamicSnapshot()`.
- Produces: merged recipe query methods and monotonic `reloadVersion()` after every successful dynamic replacement.

- [ ] **Step 1: Add a failing recipe merge/replacement test**

Create a test with a helper that constructs `MachineRecipe` instances. Register a static recipe, replace the dynamic map with a recipe for a different machine, and assert `recipes()` and `byMachineId()` return both appropriate entries. Replace the dynamic map with empty and assert only the static recipe remains and `reloadVersion()` increased.

```java
@Test
void replacingDynamicRecipesRebuildsMergedMachineIndex() {
    var staticRecipe = recipe("mmcr:static_recipe", "mmcr:static_machine");
    var dynamicRecipe = recipe("mmcr:dynamic_recipe", "mmcr:dynamic_machine");
    RecipeRegistry.register(staticRecipe);
    long version = RecipeRegistry.reloadVersion();

    RecipeRegistry.replaceDynamic(Map.of(dynamicRecipe.id(), dynamicRecipe));

    assertThat(RecipeRegistry.byMachineId(staticRecipe.machineId())).containsExactly(staticRecipe);
    assertThat(RecipeRegistry.byMachineId(dynamicRecipe.machineId())).containsExactly(dynamicRecipe);

    RecipeRegistry.replaceDynamic(Map.of());

    assertThat(RecipeRegistry.getRecipe(dynamicRecipe.id())).isNull();
    assertThat(RecipeRegistry.getRecipe(staticRecipe.id())).isSameAs(staticRecipe);
    assertThat(RecipeRegistry.reloadVersion()).isGreaterThan(version);
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeRegistryTest --no-daemon`

Expected: compilation failure because `replaceDynamic` does not exist.

- [ ] **Step 3: Implement static/dynamic recipes and rebuilt merged index**

Replace `RECIPES` with `STATIC_RECIPES` and `DYNAMIC_RECIPES`; retain a derived `BY_MACHINE` for the merged query view. `register` writes to the static map. `replaceDynamic` rejects any ID present in `STATIC_RECIPES`, copies the candidate map, rebuilds `BY_MACHINE` from static then dynamic recipes, and increments `reloadVersion` only after success. `recipes`, `getRecipe`, `registeredRecipeCount`, and `byMachineId` use merged data.

```java
private static void rebuildIndex() {
    BY_MACHINE.clear();
    for (MachineRecipe recipe : mergedRecipes().values()) {
        TreeMap<Integer, TreeSet<MachineRecipe>> priorities = BY_MACHINE.computeIfAbsent(
                recipe.machineId(), ignored -> new TreeMap<>());
        priorities.computeIfAbsent(recipe.priority(), ignored ->
                new TreeSet<>(Comparator.comparing(MachineRecipe::id))).add(recipe);
    }
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeRegistryTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit the dynamic recipe layer**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/RecipeRegistry.java src/test/java/cn/howxu/mmcr/api/recipe/RecipeRegistryTest.java
git commit -m "feat: layer dynamic machine recipes"
```

### Task 4: Add Atomic Dynamic Content Reload Service

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/reload/DynamicContentReloadService.java`
- Create: `src/test/java/cn/howxu/mmcr/internal/reload/DynamicContentReloadServiceTest.java`

**Interfaces:**
- Consumes: public replacement and snapshot methods from Tasks 1-3 and `RecipeCraftingContextPool.onGlobalReload()`.
- Produces: `begin()`, `reload(Consumer<Candidate>)`, `Candidate.registerMachine`, `Candidate.registerRecipe`, `Candidate.getMachine`, and `ReloadResult` from the Public Interfaces section.

- [ ] **Step 1: Write failing atomic commit and rollback tests**

Create tests that seed a successful dynamic snapshot, then call `reload` with a producer that registers one replacement machine and throws. Assert the original dynamic machine and recipe still resolve. Add a success test that replaces a machine, removes one machine, and checks `ReloadResult.removedMachines()` contains the removed ID and that its compiled pattern and recipes are gone.

```java
@Test
void producerFailureRetainsPreviousDynamicSnapshot() {
    DynamicContentReloadService.reload(candidate -> {
        candidate.registerMachine(machine("mmcr:old"));
        candidate.registerRecipe(recipe("mmcr:old_recipe", "mmcr:old"));
    });

    assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate -> {
        candidate.registerMachine(machine("mmcr:new"));
        throw new IllegalStateException("script failed");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(MachineRegistry.getMachine(Identifier.parse("mmcr:old"))).isNotNull();
    assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr:old_recipe"))).isNotNull();
    assertThat(MachineRegistry.getMachine(Identifier.parse("mmcr:new"))).isNull();
}
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.reload.DynamicContentReloadServiceTest --no-daemon`

Expected: compilation failure because `DynamicContentReloadService` does not exist.

- [ ] **Step 3: Implement candidate validation and atomic commit order**

`Candidate.registerMachine` rejects duplicate candidate IDs and IDs claimed by static definitions or static runtime machines. `Candidate.registerRecipe` rejects duplicate candidate IDs, static recipe collisions, and unknown `machineId`; `getMachine` resolves candidate first, then merged runtime registry. `reload` calls the producer before mutating any registry. After producer success, obtain old copies from `MachineRegistry.dynamicSnapshot()` and `RecipeRegistry.dynamicSnapshot()`, replace definitions, machines, and recipes using copies of candidate maps, call `RecipeCraftingContextPool.onGlobalReload()`, and return ID diffs. Do not catch producer exceptions: allowing them to propagate preserves the old snapshot by construction.

```java
public static ReloadResult reload(Consumer<Candidate> producer) {
    Candidate candidate = begin();
    producer.accept(candidate);
    Map<Identifier, Machine> oldMachines = MachineRegistry.dynamicSnapshot();
    Map<Identifier, MachineRecipe> oldRecipes = RecipeRegistry.dynamicSnapshot();
    MachineDefinitions.replaceDynamic(candidate.machines);
    MachineRegistry.replaceDynamic(candidate.machines);
    RecipeRegistry.replaceDynamic(candidate.recipes);
    RecipeCraftingContextPool.onGlobalReload();
    return ReloadResult.from(oldMachines, candidate.machines, oldRecipes, candidate.recipes);
}
```

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.reload.DynamicContentReloadServiceTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit the reload service**

```bash
git add src/main/java/cn/howxu/mmcr/internal/reload/DynamicContentReloadService.java src/test/java/cn/howxu/mmcr/internal/reload/DynamicContentReloadServiceTest.java
git commit -m "feat: add atomic dynamic content reload"
```

### Task 5: Route `/mmcr reload` Through The Service

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/command/ReloadCommand.java`
- Create: `src/test/java/cn/howxu/mmcr/internal/command/ReloadCommandTest.java`

**Interfaces:**
- Consumes: `DynamicContentReloadService.reload(Consumer<Candidate>)` and `ReloadResult` from Task 4.
- Produces: `/mmcr reload` command that commits the currently supplied empty dynamic snapshot while KubeJS is unavailable, reports counts, and reports deleted controller IDs only to the source.

- [ ] **Step 1: Write a failing command tree test**

Create a Brigadier dispatcher with a permissioned test `CommandSourceStack`, call `ReloadCommand.register`, execute `mmcr reload`, and assert success. Assert the response contains `MMCR reload` and no vanilla reload command is invoked or registered by MMCR. Seed a dynamic machine via the service before executing, then assert the response includes its ID and `next server restart` after the command commits the empty snapshot.

```java
@Test
void reloadCommandClearsDynamicSnapshotAndReportsRemovedControllers() throws Exception {
    DynamicContentReloadService.reload(candidate -> candidate.registerMachine(machine("mmcr:removed")));
    CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    ReloadCommand.register(dispatcher);

    int result = dispatcher.execute("mmcr reload", source);

    assertThat(result).isEqualTo(1);
    assertThat(messages).anyMatch(message -> message.contains("mmcr:removed"));
    assertThat(messages).anyMatch(message -> message.contains("next server restart"));
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.command.ReloadCommandTest --no-daemon`

Expected: FAIL because the existing command does not use the dynamic service or report removed IDs.

- [ ] **Step 3: Replace the placeholder command implementation**

Remove `DefaultMachines.ensureRegistered()`, `DefaultRecipes.ensureRegistered()`, direct cache refreshes, and the KubeJS placeholder log. Call `DynamicContentReloadService.reload(candidate -> {})`. Send a success message containing added/updated/removed machine and recipe counts. For each `removedMachines()` entry, send a second message explaining that the controller block remains registered until the next server restart. Return `1`; do not access Minecraft server resource reload APIs.

```java
var result = DynamicContentReloadService.reload(candidate -> {});
source.sendSuccess(() -> Component.literal("MMCR reload: machines +%d ~%d -%d, recipes +%d ~%d -%d"
        .formatted(result.addedMachines().size(), result.updatedMachines().size(), result.removedMachines().size(),
                result.addedRecipes(), result.updatedRecipes(), result.removedRecipes())), false);
for (Identifier id : result.removedMachines()) {
    source.sendSuccess(() -> Component.literal("Removed machine " + id
            + "; its controller block remains registered until the next server restart."), false);
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.command.ReloadCommandTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Run the affected non-KubeJS test suite**

Run: `./gradlew test --tests cn.howxu.mmcr.MachineDefinitionBootstrapTest --tests cn.howxu.mmcr.api.machine.MachineRegistryTest --tests cn.howxu.mmcr.api.recipe.RecipeRegistryTest --tests cn.howxu.mmcr.internal.reload.DynamicContentReloadServiceTest --tests cn.howxu.mmcr.internal.command.ReloadCommandTest --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit the command integration**

```bash
git add src/main/java/cn/howxu/mmcr/internal/command/ReloadCommand.java src/test/java/cn/howxu/mmcr/internal/command/ReloadCommandTest.java
git commit -m "feat: reload dynamic mmcr content"
```

### Task 6: Compile And Review The Completed Server-Side Slice

**Files:**
- Modify: only files required to resolve compiler errors from Tasks 1-5.

**Interfaces:**
- Consumes: all completed tasks.
- Produces: a compiled, reviewed server-side dynamic reload foundation with no KubeJS behavior.

- [ ] **Step 1: Compile production code**

Run: `./gradlew compileJava --no-daemon`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew test --no-daemon`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Review the final diff against the design**

Run: `git diff --check HEAD~5..HEAD` and `git diff HEAD~5..HEAD -- src/main/java/cn/howxu/mmcr/api/machine/MachineDefinitions.java src/main/java/cn/howxu/mmcr/api/machine/MachineRegistry.java src/main/java/cn/howxu/mmcr/api/recipe/RecipeRegistry.java src/main/java/cn/howxu/mmcr/internal/reload/DynamicContentReloadService.java src/main/java/cn/howxu/mmcr/internal/command/ReloadCommand.java`

Expected: no whitespace errors; no KubeJS dependency, Mixin, client model, JEI synchronization, or vanilla reload invocation.

- [ ] **Step 4: Commit any verification-only corrections**

```bash
git add src/main/java src/test/java
git commit -m "fix: review dynamic reload integration"
```

Only create this commit if Step 3 required a source correction; otherwise do not create an empty commit.

## Self-Review

- Spec coverage: Tasks 1-3 add separated static/dynamic registry layers; Task 4 implements candidate atomicity, validation, deletion diffs, cache refresh, and rollback; Task 5 provides the MMCR-only command and executor-only deletion warning; Task 6 verifies compilation and scope. KubeJS, `/reload` Mixin, JEI sync, and client control rendering are expressly deferred per the approved scope.
- Placeholder scan: no unfinished markers or unspecified implementation steps remain. The only deferred work is explicitly excluded by the global constraints.
- Type consistency: all later task references use `DynamicContentReloadService.Candidate`, `ReloadResult`, `replaceDynamic(Map<Identifier, ...>)`, and existing `Machine`/`MachineRecipe` types defined in the Public Interfaces or prior tasks.
