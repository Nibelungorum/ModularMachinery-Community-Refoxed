# Task 2 Report: Persist Single-Controller Paused State

## Review Fix

- Root cause: `MachineControllerBlockEntity` serialized only `ActiveMachineRecipe`; loading constructed an empty `RecipeCraftingContext`, losing its runtime failure state. The existing regression manually rearranged private fields rather than testing the redstone branch in `serverTick()`.
- `RecipeCraftingContext` now has minimal `ValueOutput`/`ValueInput` support for `lastFailureUnloc`, the only runtime state needed to preserve the active/paused controller pair. Transient I/O routes remain unsaved because they are recalculated on the next recipe tick.
- Controller save/load writes and restores the matching context under `active_context` for either the active or paused recipe state.
- `LevelStub` now supports configurable direct redstone signal strength. The single-controller regression drives a real powered `serverTick()`, proves consecutive powered ticks retain the identical paused recipe/context pair and tick, then removes the signal and verifies `serverTick()` resumes progression.

## Implementation

- Redstone power moves the single-controller active recipe/context pair to the paused slot only when `active` is present. Later powered ticks return before recipe work, leaving the paused pair unchanged.
- Saved controller data now records `recipe_state` as `active` or `paused`, serializes the populated `ActiveMachineRecipe` through its existing `ValueOutput` API, and persists a non-null `last_failure_unloc`.
- Loading reconstructs the matching runtime slot and its `RecipeCraftingContext`. Invalid recipe registry entries are logged and discard only the saved pair. Legacy `has_active` data is still read as active state.
- Normal single-controller recipe progression and completion no longer clear `lastFailureUnloc`. It is cleared by successful starts in `applySearchResult` and `tryRestartLastRecipe`.

## Verification

- Red phase: `./gradlew test --no-daemon --tests '*MachineControllerBlockEntityTest'` failed as expected: restored paused context expected `test.pause.failure` but was `null` at `MachineControllerBlockEntityTest.java:458`.
- Green phase: `./gradlew test --no-daemon --tests '*MachineControllerBlockEntityTest'` passed after the implementation: `BUILD SUCCESSFUL`.
- Formatting: `git diff --check` passed.
- GameTest: `./gradlew runGameTest --no-daemon --tests '*ControllerTickGameTest'` could not run because NeoGradle reports `The run type 'gameTest' was not found`. The build configuration was intentionally not changed.

## Follow-up Review Fix

- Root cause: `RecipeCraftingContext.serialize/from` preserved only the failure key. A recipe that had already consumed inputs retained no route identity after a paused save/load, so its item/fluid output routes could not be recovered from the controller components.
- Context persistence now records route entries by requirement index. Each entry stores the stable component `BlockPos`, slot or tank index, and the transfer payload needed at completion. Loading resolves that identity only against the current controller components with matching I/O direction; an unresolved component or invalid slot/tank discards only that route.
- Structure modifiers are persisted with their existing `RecipeModifier.CODEC`, preserving runtime requirement scaling for a paused recipe.
- `active/context` and `pausedActive/pausedContext` are treated as indivisible pairs. Redstone transfer, save, normal resume, structure-check resume, and load reject isolated members. Save records `has_recipe_context`; load discards legacy or corrupt recipe state that lacks it.
- Regression coverage now proves a started recipe consumes its input, pauses, saves, loads, restores both routes, resumes, completes, and emits the planned output. Separate tests cover orphaned in-memory and serialized recipe/context pairs.

## Follow-up Verification

- Red phase: `./gradlew test --no-daemon --tests '*MachineControllerBlockEntityTest.paused_recipe_save_load_keeps_consumed_input_route_and_commits_output'` failed as expected because the restored context had no item input route.
- Green phase: `./gradlew test --no-daemon --tests '*MachineControllerBlockEntityTest.paused_recipe_save_load_keeps_consumed_input_route_and_commits_output'` passed after route persistence was added: `BUILD SUCCESSFUL`.
- `./gradlew test --no-daemon --tests '*MachineControllerBlockEntityTest' --tests '*RecipeCraftingContextTest'`: `BUILD SUCCESSFUL`.
- Formatting: `git diff --check` passed.

## Residual Risk

- The GameTest runtime remains unexecuted until a `gameTest` run type is configured in a later task.
