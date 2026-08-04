# Controller Crafting Tick Design

Date: 2026-08-04

## Context

Recipes have already been initially ported: `MachineRecipe`, `MachineIngredient`, `RecipeRegistry`, KubeJS builder support, and simplified item/fluid/energy recipe inputs exist. `MachineControllerBlockEntity` already runs a working prototype that binds the machine, checks structure, scans recipes, tracks `tickCounter`, simulates outputs, and consumes/produces at completion — see `tryStartNewRecipe`, `canAcceptInputs`, `canAcceptOutputs`, `tickActiveRecipe`, `consumeAndProduce` in `MachineControllerBlockEntity`.

The next migration step is **a refactor**, not a first implementation: move the recipe execution details out of `MachineControllerBlockEntity` behind a small MMCE-shaped runtime boundary made of `ActiveMachineRecipe` (runtime state, already implemented) and a new `RecipeCraftingContext` (component I/O simulation and commit, currently inlined as `findAndCheck*` helpers). The controller keeps lifecycle responsibilities but delegates the recipe mechanics to those two objects.

## Goals

- Let a formed machine controller start and complete one matching recipe.
- Support the currently ported ingredient types: item input, fluid input, and energy input.
- Support item outputs into item output buses. Fluid outputs are not part of this step and are explicitly deferred.
- Keep controller tick readable by moving recipe execution details out of `MachineControllerBlockEntity`.
- Preserve current simple behavior before adding full MMCE systems.
- Document deferred systems explicitly so temporary simplifications are visible.

## Non-Goals

- No parallel recipe threads or factory controller scheduling.
- No recipe event bus, start/tick/failure/finish events, or command sender support.
- No full MMCE `ComponentRequirement` / requirement routing system.
- No Recipe Adapter, CraftTweaker, JEI, TOP, or tooltip migration.
- No per-tick energy drain yet; energy remains a total completion cost for this step.
- No failure action policy beyond stopping or waiting when requirements are not met.

## Recommended Approach

Use a structured runtime loop modeled after MMCE but intentionally smaller:

1. `MachineControllerBlockEntity.serverTick()` remains responsible for server-side orchestration: bind machine, check structure, start active recipe, tick active recipe, persist active state, and broadcast state.
2. `ActiveMachineRecipe` owns active runtime state: recipe reference, current tick, total tick time, max parallelism, parallelism, and an arbitrary data compound. An instance already exists; this design adopts it as the controller's active state field.
3. `RecipeCraftingContext` is new. It owns recipe I/O simulation and commit behavior for the currently formed controller and hides `IItemHandler` / `IFluidHandler` / energy storage access from the controller.
4. On startup, the controller creates an `ActiveMachineRecipe` and a `RecipeCraftingContext` only after both input simulation and output simulation pass for the first matching recipe.
5. Completion re-simulates inputs and outputs, commits outputs first then inputs (so a failed commit does not lose recipe ingredients), clears the active recipe and context, and resets progress.

This is intentionally not the full MMCE executor. It gives later phases stable names and seams without carrying over the 1.12.2 thread pool, event, and requirement complexity too early.

## Runtime Flow

### Controller Tick

On each server tick:

1. Return on client side or missing level.
2. Bind the default machine if the controller has not been bound yet.
3. Run structure detection.
4. If the structure is not formed, reset active crafting state.
5. If formed and no recipe is active, scan recipes for the formed machine.
6. Build a `RecipeCraftingContext` for the first recipe that can satisfy inputs and outputs.
7. If a recipe is active, call `ActiveMachineRecipe.tick(controller, context)`.
8. Broadcast formed state, active recipe id, and progress-compatible state as currently supported.

### Recipe Startup

Recipe selection stays simple and deterministic:

- Read recipes from `RecipeRegistry.byMachine(machine)` plus datapack recipes from server recipe access, keeping current behavior.
- Preserve recipe order from the registry/datapack merge unless existing priority ordering is already provided by `RecipeRegistry`.
- A candidate can start only if every input is available and every output can be inserted by simulation.
- Starting stores the active recipe and resets progress to zero.

### Active Recipe Tick

`ActiveMachineRecipe.tick(...)` should:

- Recompute or read total tick time from `MachineRecipe.tickTime()`.
- Increment progress (`tick = Math.min(tick + 1, totalTick)`) while requirements remain broadly valid.
- Detect completion by `tick >= totalTick`. At that point re-check inputs and output capacity before committing.
- Commit outputs first, then inputs. If the completion re-check fails, leave the recipe waiting at `totalTick - 1` (pause/wait), matching the current conservative behavior. Energy that becomes unavailable mid-recipe also takes this pause path; we do not yet support per-tick energy drain or void-on-failure.
- Return a small status value so the controller can decide whether to continue, wait, finish, or clear the recipe. Suggested enum for the plan phase: `CONTINUE` / `WAITING` / `FINISHED`.

## Data Ownership

### `ActiveMachineRecipe`

Responsibilities:

- Store `MachineRecipe recipe`, current `tick`, `totalTick`, `maxParallelism` (default `1` for this step), `parallelism` (default `1`), and a freeform `CompoundTag data`.
- Expose `recipe()`, `getTick()`, `getTotalTick()`, `isCompleted()`, plus NBT `serialize()` / deserialization constructor that already exists in the project.
- Advance execution through `RecipeCraftingContext`; do not scan the world directly.

The controller adopts this class as its active recipe field (replacing the current `MachineRecipe activeRecipe`). `maxParallelism` and `parallelism` are kept for forward compatibility but are forced to `1` in this step — the controller never constructs an `ActiveMachineRecipe` with anything larger.

### `RecipeCraftingContext`

Responsibilities:

- Locate item input buses, fluid input hatches, energy input hatches, and item output buses around the formed machine using the current controller search rules.
- Expose staged simulation/commit methods that the controller calls in order:
  - `simulateInputs(recipe): boolean` — check every ingredient can be sourced.
  - `simulateOutputs(recipe): boolean` — simulate inserting every output.
  - `commitOutputs(recipe): boolean` — perform real output insertion.
  - `commitInputs(recipe): boolean` — perform real input consumption and energy drain.
- Hide `IItemHandler`, `IFluidHandler`, and energy storage access from the controller.

The context holds no mutable per-tick state of its own beyond resolved component locations. It is rebuilt when a recipe starts and reused while active. At completion the controller must call `simulateInputs` / `simulateOutputs` again before `commitOutputs` / `commitInputs`, because components may have moved or been emptied in the meantime.

### `MachineControllerBlockEntity`

Responsibilities after refactor:

- Structure lifecycle and machine binding.
- Active recipe lifecycle fields (`ActiveMachineRecipe active`, `RecipeCraftingContext context`).
- Recipe lookup via `RecipeRegistry.byMachine(machine)` plus server datapack recipes.
- Delegation to `RecipeCraftingContext` and `ActiveMachineRecipe`.
- NBT persistence for the active recipe: add `loadAdditional` / `saveAdditional` in this step so active progress survives chunk unload.
- Network/menu state exposure.

It should no longer contain the detailed item/fluid/energy consume and output simulation logic directly. The existing `findAndCheck*` helpers and `consumeAndProduce` move into `RecipeCraftingContext`.

## Ingredient Semantics

### Item Input

- Match with `MachineIngredient.ItemIngredient.item().test(stack)`.
- Count matching items across available item input bus slots.
- On commit, extract from matching slots until the recipe count is consumed.

### Fluid Input

- Match with `MachineIngredient.FluidIngredient.fluid().test(stack)`.
- Require at least the configured amount in an input hatch.
- On commit, drain the configured amount.

### Energy Input

- Treat `MachineIngredient.EnergyIngredient.fePerTick()` as the already documented per-tick value.
- For this step, require and consume `fePerTick * recipe.tickTime()` as a total completion cost.
- Do not drain energy every tick yet.
- Energy is treated as any other input for the pause/wait path: if total stored FE drops below the required amount, the recipe waits at `totalTick - 1` instead of failing.

### Item Output

- Simulate inserting all outputs into item output bus slots.
- Preserve existing stack compatibility behavior: merge only with same item/components or empty slots.
- On commit, insert copies of output stacks and leave the recipe unfinished if simulation says output would not fit.

### Fluid Output

- Not supported in this step. Recipes that would need fluid outputs are deferred.

## Error Handling

- If the structure breaks, clear the active recipe and context, and reset progress.
- If inputs (item, fluid, or energy) or outputs become invalid before completion, pause/wait rather than voiding progress. The controller pins the active recipe at `totalTick - 1` and retries next tick.
- If completion commit cannot proceed after a successful re-simulation, abort commit safely and keep the active recipe waiting where possible.
- Commit order is fixed: outputs first, then inputs. A failed output insertion must not consume inputs.
- Do not add broad `try/catch` wrappers around game logic unless a specific NeoForge API requires it.

## Persistence and Sync

The controller does not currently implement `loadAdditional` / `saveAdditional`. This step adds them so the active recipe state survives chunk unload and reload:

- `saveAdditional`: write the `ActiveMachineRecipe` `CompoundTag` (via `ActiveMachineRecipe.serialize()`) under a known key, plus the current `tickCounter`-equivalent from `active.getTick()`. Existing block state (formed, facing, machine binding) continues to round-trip as today.
- `loadAdditional`: read the saved `CompoundTag` and reconstruct the `ActiveMachineRecipe` via its `CompoundTag` constructor. If the saved recipe id no longer resolves, drop the active recipe and log once.

Network/menu state continues to expose enough information for the existing GUI to show formed state, active recipe id, and progress. The GUI reads the active recipe id from `active.getRecipe().id()` (full `namespace:path`); note that the existing `ActiveMachineRecipe.getRegistryName()` returns only the path — either widen it to the full id or have the controller expose a `getActiveRecipeId()` helper. Pick whichever is cleaner in the plan phase. GUI improvements are not part of this step.

## Testing and Verification

Minimum verification:

- `./gradlew compileJava --no-daemon`
- Existing GameTest `E2ERecipeRunGameTest.ironCompressorRuns` must continue to pass after the refactor (40 ticks, 2 iron ingots consumed, 1 iron nugget inserted, energy drained by `80 * 40 = 3200 FE`, leaving 6800 FE stored).

Behavioral checks to add or update only if practical and existing tests already cover the path:

- A formed controller starts a matching item recipe.
- Completion consumes item input and inserts item output.
- Output-full condition prevents start or completion.
- Energy input requires total stored FE equal to `fePerTick * tickTime`.

Project guidance says tests are auxiliary for this port, so implementation should first complete the runtime loop and then adjust tests only where they already exist or compile failures require it.

## Deferred Work Document

Create or update a document near the recipe docs that records unfinished controller crafting systems:

- Active recipe NBT persistence (this step adds basic save/load; modifier/restore semantics and migration across recipe removal are still future work).
- Per-tick energy drain.
- Duration and I/O modifier application during execution.
- Fluid output support.
- Full requirement/component routing.
- Failure actions and void-on-failure policies.
- Recipe events.
- Parallel threads and factory controller execution.
- Recipe search task optimization.
- JEI/TOP/tooltip display work.

## Acceptance Criteria

- Controller crafting runtime is structured around `ActiveMachineRecipe` (controller field) and `RecipeCraftingContext` (controller field).
- Existing direct consume/produce details are removed from `MachineControllerBlockEntity` or reduced to delegation through the context.
- A simple formed machine can complete a supported recipe using item/fluid/energy inputs and item outputs.
- Controller `saveAdditional` / `loadAdditional` round-trip the active recipe across chunk reload.
- `E2ERecipeRunGameTest.ironCompressorRuns` continues to pass.
- Unimplemented MMCE systems are documented explicitly in the deferred document.
- Java compilation passes, or any unrelated pre-existing build blocker is clearly separated from this change.
