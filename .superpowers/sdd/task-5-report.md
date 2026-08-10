# Task 5 Report

## Changes

- Added `RecipeCraftingContext.commitStart(MachineRecipe, int)`, which re-simulates live routes, commits output feasibility before input extraction, reduces requested parallelism when necessary, and commits only the granted amount.
- Made `RecipeSearchTask` select an ordered recipe candidate without reading or mutating IO handlers.
- Routed shared-domain recipe starts through `SharedIoCoordinator.StartRequest`. The coordinator callback invokes `commitStart`, applies the actual grant, refreshes duration, and installs the active recipe atomically. Zero-grant starts return their borrowed context and remain idle.
- Added pending-start state so a lane cannot be scheduled again before its queued request resolves or is invalidated.
- Added stable lane IDs: `base`, `core-<threadName>`, and monotonic `factory-<n>` IDs for generated lanes.
- Added direct shared-inventory transactional-start coverage and factory lane ID coverage. Updated the existing unsafe port fixture to initialize the inherited linked-controller map.

## RED

`./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`

Before implementation, `compileTestJava` failed as expected because `RecipeCraftingContext.commitStart(MachineRecipe, int)` did not exist.

## Verification

`./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`

Result: `BUILD SUCCESSFUL` with 62 tests completed.

## Commits

- Implementation and report commit: `5b526f3 feat: coordinate shared recipe starts`

## Review Follow-up

- Routed cached factory restarts through `RecipeThread.startRecipe`, so shared-domain restarts enqueue the same `StartRequest` and commit only in the coordinator callback.
- Restored `RecipeSearchTask` candidate feasibility scanning and its same-priority conflict metadata instead of selecting the first ordered candidate unconditionally.
- Rebuild `nextFactoryLaneId` from loaded `factory-N` lane IDs before scheduling new factory lanes.
- Added scheduler production-path coverage using a real item handler: two pending factory starts share ten inputs, coordinator callbacks install parallelism 8 and 2, and a second scheduler tick cannot enqueue duplicates while starts are pending.

### Verification

- `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`: `BUILD SUCCESSFUL`, 64 tests completed.
- `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeSearchTaskTest --no-daemon`: `BUILD SUCCESSFUL`, 6 tests completed.
