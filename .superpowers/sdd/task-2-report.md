# Task 2 Report

## Status

Implemented and committed as `afd13cb` (`move factory scheduling to machine controller`).

## Files Changed

- `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
  - Added controller-owned `FactoryRecipeScheduler`.
  - Moved factory ticking, pause/resume, lock access, snapshots, menu sync, and persistence to the controller.
  - Rebinds/adopts the scheduler across factory component transitions and resets it through `stopAll`.
  - Recomputes the aggregate factory thread limit before ticking.
  - Accumulates parallelism and level bonuses as `long`, then clamps to `Integer.MAX_VALUE` and machine limit.
- `src/main/java/cn/howxu/mmcr/internal/tile/FactorySchedulerBlockEntity.java`
  - Keeps factory capacity inventory and owner invalidation.
  - Removed scheduler save/load from factory capacity component persistence.
- `src/main/java/cn/howxu/mmcr/internal/menu/FactoryControllerMenu.java`
  - Reads snapshots and sends updates through the main controller.
- `src/main/java/cn/howxu/mmcr/internal/menu/MachineControllerMenu.java`
  - Reads factory thread state through controller accessors.
- `src/main/java/cn/howxu/mmcr/internal/menu/ControllerMenuState.java`
  - Reads recipe locks through the controller scheduler.
- `src/main/java/cn/howxu/mmcr/internal/network/PktRecipeLockPayload.java`
  - Sends lock updates from the controller.
- `src/main/java/cn/howxu/mmcr/compat/jade/MachineControllerDataProvider.java`
  - Reports controller-owned factory runtime state.
- `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`
  - Added aggregate-capacity/immediate-limit coverage and parallelism overflow boundary coverage.
- `src/test/java/cn/howxu/mmcr/internal/tile/FactorySchedulerBlockEntityTest.java`
  - Updated persistence coverage to assert factory capacity entities persist inventory only.

## Verification

- `./gradlew test --no-daemon --tests '*FactoryRecipeSchedulerTest' --tests '*FactoryControllerMenuTest' --tests '*MachineControllerBlockEntityTest.controller_scheduler_aggregates_factory_capacity_and_updates_immediately' --tests '*MachineControllerBlockEntityTest.max_parallelism_clamps_long_sum_at_integer_max'`
  - `BUILD SUCCESSFUL`
- `./gradlew test --no-daemon`
  - `BUILD SUCCESSFUL`, 1132 tests completed.
- `./gradlew runGameTestServer --no-daemon`
  - `BUILD SUCCESSFUL`.
- `git diff --check`
  - Passed.

## Concerns

- `FactorySchedulerBlockEntity` retains compatibility scheduler methods used by existing tests and legacy callers, but the controller runtime path no longer uses them and factory persistence no longer serializes scheduler state.
- Existing unrelated worktree changes were preserved and not included in the commit.

## Review Fixes

- Removed the scheduler field and all runtime scheduler operations from `FactorySchedulerBlockEntity`; it now owns only the disperser inventory, capacity notification, and item handling.
- Removed first-factory scheduler adoption. The controller creates and retains the single scheduler independently, while structure transitions bind only the capacity entity owner and stop the controller scheduler when machine definition or structure version changes.
- Migrated tests and fixtures to controller scheduler accessors. Added structure-transition ownership/reset and controller snapshot/lock behavior coverage. Capacity assertions now derive expected values from component counts.

## Review Verification

- Command: `./gradlew test --no-daemon --tests '*FactoryRecipeSchedulerTest' --tests '*FactoryControllerMenuTest' --tests '*MachineControllerBlockEntityTest'`
  - Exact result: `BUILD SUCCESSFUL`; `174 tests completed, 0 failed`.
- Command: `./gradlew runGameTestServer --no-daemon`
  - Exact result: `BUILD SUCCESSFUL`.
- Command: `git diff --check`
  - Exact result: no output; exit code `0`.

## Updated Concerns

- No second runtime scheduler remains on `FactorySchedulerBlockEntity`.
- Existing unrelated worktree changes remain preserved and uncommitted.

## Final Review Verification

- `./gradlew test --no-daemon --tests '*FactoryRecipeSchedulerTest' --tests '*FactoryControllerMenuTest' --tests '*MachineControllerBlockEntityTest'`: `BUILD SUCCESSFUL`.
- `./gradlew runGameTestServer --no-daemon`: `BUILD SUCCESSFUL`.
- `git diff --check`: passed with exit code `0`.

## Minor Review Fix

- Guarded factory scheduler accessors so non-factory machines return zero/idle snapshots without creating a scheduler.
- Controller scheduler persistence now occurs only when a factory controller is present; loaded scheduler data is deferred until a formed factory scheduler is requested.
- Added `non_factory_accessors_do_not_create_a_factory_scheduler` regression coverage.

## Minor Review Verification

- Command: `./gradlew test --no-daemon --tests '*FactoryControllerMenuTest' --tests '*MachineControllerBlockEntityTest'`
  - Exact result: `BUILD SUCCESSFUL`; `125 tests completed, 0 failed`.
