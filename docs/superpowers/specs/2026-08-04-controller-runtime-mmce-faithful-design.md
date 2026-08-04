# Controller Runtime MMCE-Faithful Port Design

Date: 2026-08-04

## Context

The controller-crafting-tick refactor (commit `65b4c21`..`9acbe4f`) delivered the staged `RecipeCraftingContext` + `ActiveMachineRecipe.tick` runtime loop, but kept three MMCE behaviors in their simplified form:

- Energy is drained **once at completion** as `fePerTick * tickTime`. MMCE drains `fePerTick` **every tick** through `ioTick(...)`.
- Recipes have **no fluid outputs**. `MachineRecipe.outputs()` is `List<ItemStack>`. `FluidOutputHatchBlockEntity` is registered but unused by the runtime.
- Tick failure only **pins the recipe at `totalTick - 1`** (wait/pause). MMCE lets each machine declare a `failureAction` ∈ `{RESET, STILL, DECREASE}`.

These three items are listed in the previous spec's "Deferred Work" section as future work. They are pulled forward now because:

- They map 1:1 to MMCE 1.12.2 source — no design invention needed, just faithful port.
- They are self-contained inside the controller runtime and do not require the full Requirement/Component system that the rest of `docs/scope.md §7 Phase 2` defers.
- Without per-tick energy drain, the existing `E2ERecipeRunGameTest.ironCompressorRuns` masks energy starvation bugs that would surface as soon as a recipe runs longer than its stored energy reserve.

The goal is **MMCE-faithful semantics inside the existing simplified `MachineIngredient` model**, not a structural rewrite toward MMCE's full Requirement / Component system.

## Goals

- Per-tick energy drain: `ActiveMachineRecipe.tick` invokes `RecipeCraftingContext.ioTick(tick)` which extracts `fePerTick` from an `EnergyInputHatchBlockEntity` each tick. Insufficient energy triggers `Machine.failureAction`.
- Fluid output support: recipes can declare fluid outputs through `MachineOutput.FluidOutput(FluidStack)`. `RecipeCraftingContext.simulateOutputs` / `commitOutputs` write to `FluidOutputHatchBlockEntity` blocks around the controller.
- Failure actions: `Machine.failureAction()` returns one of `RESET`, `STILL`, `DECREASE` (default `STILL`). `ActiveMachineRecipe.doFailureAction(action)` applies the configured branch.
- Keep `E2ERecipeRunGameTest.ironCompressorRuns` passing: 40 ticks × 80 FE/tick = 3200 FE drained (final FE = 6800).

## Non-Goals

- No full MMCE `ComponentRequirement` / requirement-routing system — `MachineIngredient` sealed interface stays.
- No `RecipeModifier` application to duration / I/O during execution. `MachineRecipe.modifiers` remain in codec but the runtime does not consume them this step.
- No parallel threads, factory controllers, or parallel recipe threads.
- No recipe events (`StartEvent`, `TickEvent`, `FailureEvent`, `FinishEvent`).
- No auto-void / item-voiding on failure — STILL means tick is preserved.
- No JEI / TOP integration changes.
- No new third-party mod support (Mekanism gas, AE2 ME, etc.).
- No per-tick item consumption. Items remain a one-shot deduction at completion, matching MMCE `RequirementItem` (which is `PerTrigger`, not `PerTick`).
- No per-tick fluid consumption. Fluid input is also one-shot at completion. MMCE `RequirementFluid` is `PerTrigger` by default; `RequirementFluidPerTick` is a separate type we are not porting.

## Recommended Approach

Mirror MMCE's `ActiveMachineRecipe.tick(ctrl, context)` shape on top of our existing objects. Three concrete moves:

1. **Per-tick energy via `ioTick`**: introduce `RecipeCraftingContext.ioTick(int currentTick)` returning a small `IoTickResult { SUCCESS, FAILURE }`. The controller's per-tick path becomes: `ioTick` → on success `tick++` → on failure `doFailureAction(failureAction)`. Energy is removed from `commitInputs`.

2. **Fluid output via `MachineOutput`**: introduce `sealed interface MachineOutput { ItemOutput(ItemStack), FluidOutput(FluidStack) }`. `MachineRecipe.outputs()` becomes `List<MachineOutput>`. `RecipeCraftingContext.simulateOutputs` / `commitOutputs` iterates over both variants, using `FluidOutputHatchBlockEntity` for `FluidOutput` (already registered through `PortKinds.FLUID_OUTPUT`).

3. **Failure actions via `Machine`**: add `default RecipeFailureActions failureAction() { return STILL; }` on the `Machine` sealed interface. Add `failureAction` field on `DynamicMachine` record. Add `enum RecipeFailureActions { RESET, STILL, DECREASE }`. Replace `ActiveMachineRecipe.doFailureAction(boolean reset)` with `doFailureAction(RecipeFailureActions action)`.

This keeps the project close to MMCE behavior without porting MMCE's `ComponentRequirement` machinery.

## Runtime Flow

### Active Recipe Tick

Re-shape `ActiveMachineRecipe.tick(RecipeCraftingContext context)` to:

1. If `recipe == null` or `total <= 0`: return `TickStatus.WAITING`.
2. **If `isCompleted()`**: return `TickStatus.WAITING` — MMCE's `CraftingStatus.working()` once the recipe is finished; the controller will clear us on the next orchestration step. This preserves the existing completion-then-clear behavior.
3. Compute `int nextTick = Math.min(getTick() + 1, total)` (kept as before — we don't actually mutate tick until after `ioTick` succeeds).
4. Probe `IoTickResult probe = context.ioTick(nextTick)`:
   - `SUCCESS` → `setTick(nextTick)`. If `isCompleted()` → return `FINISHED` (commit happens in step 5). Otherwise → `CONTINUE`.
   - `FAILURE` → resolve `RecipeFailureActions action = machine.failureAction()` (default `STILL`); call `doFailureAction(action)`. Return `WAITING`.
5. On `FINISHED`: the controller invokes `commitOutputs` then `commitInputs` (the same two calls as today), then clears the active recipe. Energy is **not** deducted here because step 4 already drained it per tick.

### `RecipeCraftingContext.ioTick(int currentTick)`

A new method:

```java
public IoTickResult ioTick(int currentTick) { ... }
```

For each `MachineIngredient.EnergyIngredient` in the recipe:

1. Find a matching `EnergyInputHatchBlockEntity` (same 3×3 scan used by `findAndCheckEnergyHatch`).
2. Simulate `hatch.getEnergyStorage(null).extractEnergy(energy.fePerTick(), true)`.
3. If the simulated extraction is `< fePerTick`, return `IoTickResult.FAILURE`.
4. Otherwise commit `hatch.getEnergyStorage(null).extractEnergy(energy.fePerTick(), false)` and continue.
5. If no energy hatch matched at all, return `IoTickResult.FAILURE` — same outcome as energy starvation.

Non-energy ingredients are not touched here; they are checked at start (via `simulateInputs` / `simulateOutputs`) and consumed at completion (via `commitInputs` / `commitOutputs`).

### Output Commit

`RecipeCraftingContext.commitOutputs(MachineRecipe)` iterates over `recipe.outputs()`:

- `MachineOutput.ItemOutput(ItemStack stack)` → existing item-output logic (`outputSlots()` insert).
- `MachineOutput.FluidOutput(FluidStack stack)` → new logic: scan 3×3 for `FluidOutputHatchBlockEntity`; simulate `hatch.getFluidHandler(null).fill(stack, EXECUTE) == 0`; if simulation returns remainder != empty, return `false`; else commit by real fill.

`simulateOutputs` does the same in mirror form, plus preserving the existing `OutputSlotState` item simulation so we don't regress item output compatibility.

### Failure Action Resolution

`ActiveMachineRecipe.tick` reads `machine.failureAction()` from the controller context. Today the controller passes only `Level` + `BlockPos` to `RecipeCraftingContext`; we need to pass the active `Machine` too:

- `RecipeCraftingContext(Level level, BlockPos controllerPos, Machine machine)`.
- The machine is held by reference for `failureAction()` lookup; it is also the machine the controller already has bound, so no new state is introduced on the controller.

`RecipeCraftingContext` does not cache `Machine`; it just reads it on demand. The controller's `Machine` may change after a structure re-form, so we want the live value.

## Data Ownership

### `MachineOutput` (new file)

`src/main/java/cn/howxu/mmcr/api/recipe/MachineOutput.java`. Sealed interface with:

- `record ItemOutput(ItemStack stack) implements MachineOutput`
- `record FluidOutput(FluidStack stack) implements MachineOutput`

`Codec<MachineOutput>` follows the same `type`-dispatch pattern as `MachineIngredient.CODEC`. JSON shape:

```json
{ "type": "item",   "stack": { ... ItemStack codec ... } }
{ "type": "fluid",  "stack": { ... FluidStack codec ... } }
```

### `MachineRecipe`

- Field swap: `List<ItemStack> outputs` → `List<MachineOutput> outputs`.
- `outputs()` getter signature changes accordingly.
- Codec `outputs` field switches from `ItemStack.CODEC.listOf()` to `MachineOutput.CODEC.listOf()`.
- Existing simple constructor `MachineRecipe(id, machineId, tickTime, inputs, outputs)` is preserved by adding a sibling `MachineRecipe(id, machineId, tickTime, inputs, List<MachineOutput>)`. The old `List<ItemStack>` constructor is kept as a thin adapter that wraps each `ItemStack` in `ItemOutput(...)` so call sites like `E2ERecipeRunGameTest.ironCompressorRuns` keep compiling unchanged.
- `assemble(RecipeInput)` returns the first `ItemOutput.stack().copy()` or `ItemStack.EMPTY`. If the first output is a `FluidOutput`, return `ItemStack.EMPTY` (matches MMCE which would also fall through).
- `equals` / `hashCode` updated to compare `List<MachineOutput>`.

### `Machine` (sealed interface)

- Add `default RecipeFailureActions failureAction() { return RecipeFailureActions.STILL; }`.
- No existing methods change.

### `DynamicMachine` (record)

- Add `RecipeFailureActions failureAction` as the last record component (consistent with the existing `controller` component ordering).
- Existing 3-arg constructor `DynamicMachine(registryName, localizedName, pattern)` keeps working by delegating to the new 5-arg constructor with `RecipeFailureActions.STILL` and the default `controller` spec.
- `failureAction()` getter overrides the `Machine` default.
- `equals` / `hashCode` (auto-generated for records) includes the new field automatically.

### `RecipeFailureActions` (new file)

`src/main/java/cn/howxu/mmcr/api/machine/RecipeFailureActions.java`. Plain enum mirroring MMCE:

```java
public enum RecipeFailureActions {
    RESET,
    STILL,
    DECREASE;
    public static RecipeFailureActions getDefaultAction() { return STILL; }
}
```

A `String name` field (MMCE exposes `getName()` for config / CraftTweaker) is **not** added in this step — KubeJS binding for failure action is deferred. If a future KubeJS builder needs it, add then.

### `ActiveMachineRecipe`

- `doFailureAction(boolean reset)` is replaced by `doFailureAction(RecipeFailureActions action)`:
  - `RESET` → `tick = 0`.
  - `STILL` → no change.
  - `DECREASE` → `if (tick > 0) tick--;`.
- `tick(RecipeCraftingContext context)` is rewritten per the runtime flow section above.
- Public API additions: `IoTickResult` (nested static record) on `RecipeCraftingContext`, not `ActiveMachineRecipe`. The existing `TickStatus` enum stays.

### `RecipeCraftingContext`

- Constructor gains `Machine machine` parameter (third arg). Backwards-incompatible signature change — caller `MachineControllerBlockEntity.tryStartNewRecipe` updated to pass the bound `machine`.
- `commitInputs(MachineRecipe)` removes the energy branch (energy is drained in `ioTick`). Item and fluid input branches stay.
- New `ioTick(int currentTick): IoTickResult` method per the runtime flow.
- `simulateOutputs(MachineRecipe)` / `commitOutputs(MachineRecipe)` extended to handle `MachineOutput.FluidOutput`.

### `MachineControllerBlockEntity`

- `tryStartNewRecipe` builds the context with `(level, getBlockPos(), machine)`.
- `loadAdditional` already builds `new RecipeCraftingContext(level, getBlockPos())`; updated to pass the bound machine (look up from saved machine id via `MachineRegistry.getMachine`).

### `FluidOutputHatchBlockEntity`

No code change required — the entity already exposes `getFluidHandler(Direction)` returning a `FluidTank`. `RecipeCraftingContext` consumes it directly.

### KubeJS binding

- `MachineRecipeBuilderJS`: add `ItemOutput fluidOutput(FluidStack stack)` (KubeJS-style — returns the builder for chaining).
- `MachineRecipeSchema`: register a new `FluidStack` component (or expose `fluidOutput(FluidStack)`) so KubeJS scripts can declare fluid outputs.
- These are minimal additions; no existing API changes.

## Runtime Semantics

### Energy

- Drain happens **before `tick++`** every tick, including the tick that completes the recipe. A 40-tick recipe with `fePerTick=80` drains 3200 FE total. This is byte-identical to today's total consumption, but spread across ticks.
- If the energy hatch contains less than `fePerTick` at any tick:
  - `ioTick` returns `FAILURE`.
  - `ActiveMachineRecipe.tick` reads `Machine.failureAction()` and applies it.
  - Tick is **not** advanced.
- If no energy hatch is present in the 3×3 ring but a recipe requires energy: `simulateInputs` already returns `false` at recipe start, so the recipe never starts.

### Item / Fluid Input

- Item and fluid inputs remain **single-shot at completion** via `commitInputs`. This matches MMCE `RequirementItem` / `RequirementFluid` (both `PerTrigger`, not `PerTick`).
- Fluid input drain is unchanged: `IFluidHandler.drain(amount, EXECUTE)` on the matching `FluidInputHatchBlockEntity`.

### Item / Fluid Output

- `simulateOutputs` runs before each recipe start.
- `commitOutputs` runs at completion, **before** `commitInputs`, matching today's order. Outputs are written first so a failed output insertion never consumes inputs.

### Failure Actions

- `RESET` → `tick = 0`. Recipe starts over from scratch next tick. Matches MMCE.
- `STILL` → no tick change. Recipe waits for the missing requirement to be satisfied. **Default for `Machine.failureAction()`**. Matches MMCE default.
- `DECREASE` → `tick = Math.max(0, tick - 1)`. Recipe rewinds one tick. Matches MMCE.

In all three cases, the active recipe is **not** cleared. The controller's `tickActiveRecipe` clears it only when the recipe is `FINISHED`.

## Error Handling

- Energy starvation: `ioTick` returns `FAILURE`. Failure action applies.
- Energy hatch absent at `ioTick` time (player broke it during the recipe): `ioTick` returns `FAILURE`. Failure action applies. Recipe pauses / resets based on machine config.
- Item / fluid input hatch absent at completion: handled by the existing `findAndCheck*` paths in `commitInputs` — if `null`, the ingredient is skipped (current behavior preserved).
- Output full at completion: `commitOutputs` returns `false`. Tick is pinned to `totalTick - 1`, recipe waits — **same as today**, matches the spec's pause/wait semantics for outputs (the failure-action system applies to per-tick failures; completion-time failures use the existing wait path because we can't undo a partial write).
- Structure breaks mid-recipe: controller calls `resetMachine()`, clears active recipe and context. Unchanged.
- Recipe id missing after a chunk reload: existing `loadAdditional` behavior — drop active recipe, log once. Unchanged.

## Persistence and Sync

- The new `Machine` field on `RecipeCraftingContext` is transient — it's rebuilt at recipe start and on chunk reload. No new NBT keys.
- `ActiveMachineRecipe.serialize()` / `loadAdditional` round-trip is unchanged. The new `failureAction` is on `Machine`, not on the active recipe.
- GUI / network payload contracts are unchanged. `PktMachineStatePayload` and `MachineMenuScreen` continue to read `active.getRecipe()` and `active.getTick()`.

## Testing and Verification

Minimum verification (existing gates):

- `./gradlew compileJava --no-daemon`
- `./gradlew build --no-daemon`
- `E2ERecipeRunGameTest.ironCompressorRuns` must still pass: 40 ticks, input consumed, output inserted, energy = 6800 FE (i.e., 3200 FE drained).

If the GameTest framework supports additional tests without major setup, the plan phase may add:

- A test where the energy hatch starts with less than `fePerTick * tickTime` but ≥ `fePerTick` and `Machine.failureAction == STILL` — recipe waits at `tick = 0`, energy is not drained beyond what is available.
- A test where the energy hatch is removed mid-recipe — `ioTick` returns `FAILURE`, tick does not advance.
- A fluid-output recipe writing into `FluidOutputHatchBlockEntity`.

Per `AGENTS.md` "测试只是辅助", these are added only where existing GameTest scaffolding makes them trivial.

## Deferred Work (continues to be deferred after this step)

The items listed in the previous spec's "Deferred Work" section **that this step does not address** stay deferred:

- Active recipe NBT persistence modifier/restore semantics and migration across recipe removal.
- Duration and I/O `RecipeModifier` application during execution (modifier chain is recorded in codec but unused at runtime).
- Full MMCE `ComponentRequirement` / requirement routing system (still Phase 2 per `docs/scope.md §7`).
- Auto-void / item-voiding on failure.
- Recipe events (`StartEvent`, `TickEvent`, `FailureEvent`, `FinishEvent`).
- Parallel threads and factory controller execution.
- Recipe search task optimization.
- JEI / TOP / tooltip display work.

## Acceptance Criteria

- `MachineOutput` sealed interface exists with `ItemOutput` / `FluidOutput` records and codec. `MachineRecipe.outputs()` returns `List<MachineOutput>`.
- `RecipeFailureActions { RESET, STILL, DECREASE }` enum exists. `Machine.failureAction()` defaults to `STILL`. `DynamicMachine` exposes the configured value.
- `RecipeCraftingContext.ioTick(int)` exists and returns `SUCCESS` / `FAILURE`. Energy is drained per tick via `EnergyInputHatchBlockEntity.extractEnergy`.
- `ActiveMachineRecipe.tick` calls `ioTick` before advancing tick; on failure applies `Machine.failureAction()`. The boolean `doFailureAction(boolean reset)` overload is removed.
- `RecipeCraftingContext.commitOutputs` handles both item and fluid outputs. `commitInputs` no longer touches energy.
- Fluid output requires a `FluidOutputHatchBlockEntity` in the 3×3 ring; simulation and commit both go through it.
- `E2ERecipeRunGameTest.ironCompressorRuns` still passes (40 ticks, 6800 FE remaining).
- `MachineRecipeBuilderJS` and `MachineRecipeSchema` expose `fluidOutput(FluidStack)`.
- `./gradlew compileJava --no-daemon` succeeds.
- `./gradlew build --no-daemon` succeeds.