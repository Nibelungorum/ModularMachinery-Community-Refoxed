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

## Residual Risk

- The GameTest runtime remains unexecuted until a `gameTest` run type is configured in a later task.
