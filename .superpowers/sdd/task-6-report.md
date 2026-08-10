## Task 6 Report

Status: committed.

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
