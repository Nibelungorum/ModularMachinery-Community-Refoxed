## Task 6 Report

Status: completed; commit follows this report update.

TDD:
- Added a finite-energy round-robin regression in `SharedIoCoordinatorTest`; it initially exposed the missing runtime commit API.
- Added `commitIoTick` atomic full-energy validation and blocked-final-tick regressions in `RecipeCraftingContextTest`; the new API test initially failed to compile as expected.
- Re-ran the specified test classes after the minimal implementation.

Implementation:
- Active recipes now only apply coordinator grants; they do not mutate live IO directly.
- Recipe threads enqueue running tick and finish requests with the independent tick/finish cursors.
- A successful final tick enqueues a finish request that is resolved later in the same domain pass.
- Per-tick IO validates all energy requirements before any mutation, and output commits resimulate live capacity.
- Blocked final output keeps the recipe at `totalTick - 1` and observes the existing finish retry cooldown.

Verification:
- `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon`
- Result: `BUILD SUCCESSFUL` (17 actionable tasks: 3 executed, 14 up-to-date).

Implementation SHA: `a90015eba12f85f7048a192dd837cc3e44baf7f3`.

## Review Follow-up

Status: fixed.

- `commitIoTick` now simulates all item/fluid inputs and reserves energy across all requirements by live handler identity before any mutation. Two 20 FE requirements against a 30 FE hatch leave the active recipe at tick zero and the hatch at 30 FE.
- Non-domain `RecipeThread` final ticks commit outputs before applying `FINISHED`; factory lanes use the same coordinator-free commit path.
- `ActiveMachineRecipe.start` no longer commits live handlers. All production start callers now use `RecipeCraftingContext.commitStart`.
- Updated the active-recipe compatibility tests and added the multiple-energy requirement regression. Coordinator fairness and same-domain finish-pass coverage remain in `SharedIoCoordinatorTest`.

Verification:
- `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.api.recipe.ActiveMachineRecipeTest --no-daemon`
- Result: `BUILD SUCCESSFUL` (17 actionable tasks: 2 executed, 15 up-to-date).

## Final Review Follow-up

Status: fixed.

- `RecipeThread` now records the explicit `finishPending` state after a successful final `commitIoTick`. While it is set, the thread observes the finish retry cooldown and enqueues only a `FinishRequest`; it never repeats the final tick's IO commit.
- Output commits rebuild live output routes before committing, so a shared handler that becomes full after planning blocks correctly.
- Added a `RecipeThread -> SharedIoCoordinator -> ResourceDomain` regression with real item input, energy, and output ports. It proves blocked output consumes the final-tick input and 10 FE once, then succeeds after cooldown without a second consumption.
- Added a coordinator regression proving a successful tick callback that enqueues a finish request resolves that finish in the same domain pass.

Verification:
- `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`
- Result: `BUILD SUCCESSFUL` (17 actionable tasks: 1 executed, 16 up-to-date).

## Re-review Follow-up

Status: fixed.

- `RecipeThread.searchAndStartRecipe()` already routes non-shared starts through `RecipeCraftingContext.commitStart`, while shared starts retain Task 5's pending request and coordinator callback path; both install the actual granted parallelism only after a successful commit.
- `commitIoTick` now commits validated item and fluid inputs before its pre-reserved energy requirements, making real per-tick item inputs participate in the same atomic tick grant.
- `sharedFinalOutputRetryDoesNotRepeatItsLastTickIo` now starts with exactly one real per-tick item input. Its blocked final tick consumes that input and 10 FE once; after output capacity opens, the finish-only retry consumes neither input nor energy and creates the output once.

Verification:
- `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --no-daemon`
- Result: `BUILD SUCCESSFUL` (17 actionable tasks: 2 executed, 15 up-to-date).

## Final Re-review Follow-up

Status: fixed.

- Added `commitSynchronousIoTick` and `commitSynchronousOutputs` for no-domain execution. Both delegate to the same private atomic implementations used by the coordinator APIs, preserving full live validation and output behavior without calling `commitIoTick` or `commitOutputs`.
- Migrated the no-domain paths in `RecipeThread`, `FactoryRecipeLane`, and `MachineControllerBlockEntity` to the synchronous wrappers. The only production calls to `commitIoTick` and `commitOutputs` now remain in `RecipeThread`'s `SharedIoCoordinator.TickRequest` and `FinishRequest` callbacks.
- Strengthened `sharedFinalOutputRetryDoesNotRepeatItsLastTickIo` to count iron ingots across every output slot: zero while blocked and exactly one after the finish-only retry.

Verification:
- `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`
- Result: `BUILD SUCCESSFUL` (17 actionable tasks: 1 executed, 16 up-to-date).

## Completion Follow-up

Status: fixed.

- `ActiveMachineRecipe` persists an explicit `finishPending` state. Once a final output is blocked after final-tick IO commits, no-domain execution retries only outputs and observes the ten-tick finish cooldown.
- `FactoryRecipeLane` and `MachineControllerBlockEntity` now preflight final outputs before synchronous IO, then use the finish-only path after a post-IO output block. This prevents both the preflight case from consuming IO and the post-IO retry from consuming it twice.
- `RecipeCraftingContext.commitIoTick` and `commitOutputs` are private implementation methods. Shared `RecipeThread` receives coordinator callbacks through `coordinatorIoTick` and `coordinatorOutputs`; no-domain execution uses `commitSynchronousIoTick` and `commitSynchronousOutputs`. Legacy `finishCrafting` uses the synchronous output wrapper.
- Added direct private factory-lane and controller regressions. Each causes final-tick input consumption to fill outputs, confirms cooldown skips IO, then frees capacity and verifies one final output with no second input or energy consumption.
- Corrected the existing shared-output regression fixture so it blocks after final synchronous IO rather than before the required output preflight.

Verification:
- `./gradlew test --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon`
- Result: `BUILD SUCCESSFUL in 17s` (17 actionable tasks: 2 executed, 15 up-to-date).
- `git diff --check` completed without output.

## Final Important Follow-up

Status: fixed.

- Shared-domain `RecipeThread` now performs a live `simulateOutputs` preflight inside its final `TickRequest` transaction before calling `coordinatorIoTick`.
- A failed preflight is treated as an ungranted tick: it leaves the active recipe at its prior tick, retains inputs and energy, and does not enter `finishPending`.
- Added `sharedFinalTickPreflightsFullOutputBeforeConsumingIo`, a real shared-domain integration regression using item input, energy input, and item output ports. It starts with every output slot full, proves the old implementation's forbidden consumption is prevented, then frees one slot and proves exactly one subsequent input/energy consumption and output production.
- The existing post-IO output-block regression remains unchanged, preserving finish-only retry behavior when IO side effects make an initially valid output unavailable.

TDD failure evidence:

- Before the preflight implementation, `./gradlew test --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon` failed with `sharedFinalTickPreflightsFullOutputBeforeConsumingIo()`.

Verification:

- `./gradlew test --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon`
- Result: `BUILD SUCCESSFUL in 20s` (17 actionable tasks: 2 executed, 15 up-to-date).
- `git diff --check` completed without output.
