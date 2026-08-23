# Task 3 Report

## Status

FOCUSED PASS. Task 3 scheduling and compiled-pattern cache changes are implemented; the exact wildcard command remains red due to an existing MachineRegistry lifecycle failure.

## Commit

`3fec69a perf: avoid redundant multiblock checks`

## Files

- `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
  - Keeps `structureDirty` as the invalidation flag.
  - Detects facing and vertical roll changes before scheduling checks.
  - Removes forced structure checks while redstone-paused or resuming.
  - Resets the check counter only when `checkStructure()` starts.
  - Reuses the compiled matcher path when compiled replacements are present.
  - Avoids the duplicate area-loaded check after the controller has already checked it.
- `src/main/java/cn/howxu/mmcr/api/machine/StructureMatcher.java`
  - Adds compiled matching with explicit replacements.
  - Adds an already-loaded compiled matching path while preserving the public area check.
  - Preserves `stateSensitive` matching.
- `src/test/java/cn/howxu/mmcr/api/machine/StructureMatcherTest.java`
  - Covers compiled matching with modifier replacements.
- `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`
  - Covers clean-controller interval scheduling, duplicate dirty notifications, and rotation invalidation.

`StructureDirtyEvents.java` was not changed because its existing placement, break, fluid, and chunk-unload routes already delegate to the controller's bounded invalidation methods.

## Tests

- PASS: `./gradlew test --no-daemon --tests 'cn.howxu.mmcr.api.machine.StructureMatcherTest' --tests 'cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest'`
- PASS: Java/test compilation completed as part of the focused test run.
- The exact brief command `./gradlew test --no-daemon --tests '*Structure*' --tests '*MachineController*'` had one unrelated failure in `MachineRegistryTest.installingStructuresRebuildsMergedCompiledCache`: `MachineDefinitions.register` rejected registration because the registry phase was already closed. The two task-focused test classes passed independently.

## Concerns

- The broad wildcard test selection remains sensitive to registry lifecycle/test ordering; this is pre-existing and outside Task 3.
- No client run or forbidden `runClient` task was used.

## Review Follow-up

- Added a test-only structure-check callback seam and changed scheduling assertions to use `serverTick()`; a dirty controller invokes one check on that tick, while a clean controller only increments its interval counter before the interval.
- Added real tick-path coverage for stage transition and reset, vertical roll-facing invalidation, compiled-bounds inside/outside changes, and chunk unload invalidation.
- Preserved `stateSensitive`, port/tier validation, and compiled matcher behavior. No MachineRegistry lifecycle production logic was changed.
- Focused command: PASS, `StructureMatcherTest` and `MachineControllerBlockEntityTest`.
- Exact brief wildcard command: FAIL, `MachineRegistryTest.installingStructuresRebuildsMergedCompiledCache` remains an existing registry-lifecycle failure; the wildcard command also includes the separate `MachineRegistryTest` failure and must not be reported as PASS.

## Incremental Scan Follow-up

- The two new GameTests were first run unchanged and confirmed RED at the callback boundary: the build assertion observed tick 150 before the real block ticker formed the controller, and the invalidation assertion observed tick 12 before the fresh scan completed.
- The scan seam records one batch per real controller ticker tick through `scanBatchesPerTickForTesting`; the GameTests do not call `serverTick()` manually.
- The production root cause was the fixed timeout of `interval * 5`. With the configured sentinel budget, the nine-entry test pattern advances one cursor entry per batch and needs nine real batches; the fixed timeout repeatedly invalidated it before completion.
- `structureScanTimeoutTicks()` now derives the timeout from the active scan's batch size and effective cursor progress while retaining the configured minimum. Pending invalidation still discards the completed old scan and schedules a fresh scan.
- The invalidation test restores the original state of the mutated placement rather than copying the first placement's state. Its final observation is scheduled after both real scans, not by manually ticking the controller.
- Both new GameTests pass while retaining duplicate-submission rejection, per-tick batch limits, final block count, pending invalidation, fresh-scan count, and formed assertions.

## Follow-up Verification

- PASS: `./gradlew test --no-daemon`
- PASS: focused `StructureMatcherTest` and `MachineControllerBlockEntityTest`
- GameTest server completed 45 tests; the two new incremental tests passed.
- Remaining pre-existing GameTest failures: `block_array_match`, `controller_tick`, `e2e_distillation_tower_partial_outputs`, and `e2e_recipe_run`.
- No `runClient` task was run.
