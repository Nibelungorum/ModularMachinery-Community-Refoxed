# Controller Crafting Tick Design

Date: 2026-08-04

## Context

Recipes have already been initially ported: `MachineRecipe`, `MachineIngredient`, `RecipeRegistry`, KubeJS builder support, and simplified item/fluid/energy recipe inputs exist. The previous recipe port intentionally left execution scheduling out of scope. The next migration step is to let a formed controller actually run a recipe while keeping the runtime structure close enough to MMCE to support later requirement, modifier, and event work.

The current `MachineControllerBlockEntity` already contains a direct prototype loop that checks structure, searches recipes, tracks `tickCounter`, and consumes/produces at completion. This design replaces that direct logic with a small MMCE-shaped runtime boundary: an active recipe state object plus a simplified crafting context.

## Goals

- Let a formed machine controller start and complete one matching recipe.
- Support the currently ported ingredient types: item input, fluid input, and energy input.
- Support item outputs into item output buses.
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

1. `MachineControllerBlockEntity.serverTick()` remains responsible for server-side orchestration: bind machine, check structure, start active recipe, tick active recipe, and broadcast state.
2. `ActiveMachineRecipe` owns active runtime state: recipe reference, current tick, total tick time, and status.
3. `RecipeCraftingContext` owns recipe I/O checks and commit behavior for the currently formed controller.
4. Controller startup creates both objects only after input and output checks pass.
5. Completion performs a second check, commits input consumption and output insertion, clears active state, and resets progress.

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
- Increment progress while requirements remain broadly valid.
- At completion, re-check inputs and output capacity.
- Commit consumption and output only after the completion check passes.
- Return a small status value so the controller can decide whether to continue, wait, finish, or clear the recipe.

For this step, checks may be conservative: if inputs disappear or output fills before completion, the active recipe waits instead of applying a complex MMCE failure action.

## Data Ownership

### `ActiveMachineRecipe`

Responsibilities:

- Store `MachineRecipe recipe`.
- Store current progress tick.
- Expose `recipe()`, `tick()`, `totalTick()`, `isCompleted()`.
- Advance execution using `RecipeCraftingContext`.
- Avoid direct world scans except through the context.

The class should be small and serializable later, but this step does not need full NBT persistence unless existing controller persistence already serializes active recipe state.

### `RecipeCraftingContext`

Responsibilities:

- Locate item input buses, fluid input hatches, energy input hatches, and item output buses around the formed machine using current controller search rules.
- Simulate all required inputs and outputs.
- Commit input consumption and output insertion.
- Hide low-level `IItemHandler`, `IFluidHandler`, and energy storage operations from the controller.

The context can be rebuilt when a recipe starts and reused while active. If component locations may change, completion checks must revalidate before committing.

### `MachineControllerBlockEntity`

Responsibilities after refactor:

- Structure lifecycle and machine binding.
- Active recipe lifecycle fields.
- Recipe lookup.
- Delegation to `RecipeCraftingContext` and `ActiveMachineRecipe`.
- Network/menu state exposure.

It should no longer contain the detailed item/fluid/energy consume and output simulation logic directly.

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

### Item Output

- Simulate inserting all outputs into item output bus slots.
- Preserve existing stack compatibility behavior: merge only with same item/components or empty slots.
- On commit, insert copies of output stacks and leave the recipe unfinished if simulation says output would not fit.

## Error Handling

- If the structure breaks, clear the active recipe and progress.
- If inputs or outputs become invalid before completion, pause/wait rather than voiding progress.
- If completion commit cannot proceed after a successful simulation, abort commit safely and keep the active recipe waiting where possible.
- Do not add broad `try/catch` wrappers around game logic unless a specific NeoForge API requires it.

## Persistence and Sync

This step should preserve current behavior first. If the controller already saves active recipe id or progress, keep it. If it does not, active progress may reset on unload in this migration step and must be recorded in the deferred work document.

Network/menu state should continue to expose enough information for the existing GUI to show formed state, active recipe id, and progress if those fields already exist. GUI improvements are not part of this step.

## Testing and Verification

Minimum verification:

- `./gradlew compileJava --no-daemon`
- Existing unit or GameTest coverage that constructs controller recipes, if it compiles in the current project state.

Behavioral checks to add or update if practical:

- A formed controller starts a matching item recipe.
- Completion consumes item input and inserts item output.
- Output-full condition prevents start or completion.
- Energy input requires total stored FE equal to `fePerTick * tickTime`.

Project guidance says tests are auxiliary for this port, so implementation should first complete the runtime loop and then adjust tests only where they already exist or compile failures require it.

## Deferred Work Document

Create or update a document near the recipe docs that records unfinished controller crafting systems:

- Active recipe NBT persistence if not completed in this step.
- Per-tick energy drain.
- Duration and I/O modifier application during execution.
- Full requirement/component routing.
- Failure actions and void-on-failure policies.
- Recipe events.
- Parallel threads and factory controller execution.
- Recipe search task optimization.
- JEI/TOP/tooltip display work.

## Acceptance Criteria

- Controller crafting runtime is structured around `ActiveMachineRecipe` and `RecipeCraftingContext`.
- Existing direct consume/produce details are removed from `MachineControllerBlockEntity` or reduced to delegation.
- A simple formed machine can complete a supported recipe using item/fluid/energy inputs and item outputs.
- Unimplemented MMCE systems are documented explicitly.
- Java compilation passes, or any unrelated pre-existing build blocker is clearly separated from this change.
