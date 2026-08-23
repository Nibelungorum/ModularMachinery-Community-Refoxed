# Task 2 Report

## Status

Implemented and committed.

## Commit

- `50bde94` `perf: scan large structures across ticks`
- `005a926` `docs: report incremental task 2`

## Files

- `src/main/java/cn/howxu/mmcr/api/machine/StructureMatcher.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/config/Config.java`
- `src/test/java/cn/howxu/mmcr/api/machine/StructureMatcherTest.java`
- `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`

## Red/Green Evidence

- RED: focused matcher test compilation failed because `ScanState`, `ScanOptions`, `ScanResult`, `ScanStatus`, and `InvalidationReason` did not exist.
- GREEN: focused matcher and controller tests passed after implementation.

## Tests

- `./gradlew test --no-daemon --tests 'cn.howxu.mmcr.api.machine.StructureMatcherTest' --tests 'cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest'` passed.
- `./gradlew test --no-daemon` passed.
- `./gradlew runGameTestServer --no-daemon` executed serially but failed 5 existing timing assertions that expect same-tick structure formation; the failures are incompatible with the new bounded cross-tick scan timing.

## Concerns

- GameTest expectations for `block_array_match`, `controller_tick`, `e2e_recipe_run`, `e2e_distillation_tower_partial_outputs`, and `terminal_build_across_ticks_duplicate` still assume immediate formation and need a follow-up timing update.
- Right-click wiring was not implemented.

## Critical/Important Review Fixes

- `50bde94` left the scan timeout at one 40-tick check interval. The timeout now allows the configured number of batches at the configured interval, while still invalidating a scan that exceeds that bounded window.
- Each batch and final `VALID` acceptance now validate version, facing, roll, stage, pattern identity, loaded area, and removal state. Normal block-change pending remains deferred until final acceptance.
- `Mismatch` now carries the complete scan identity. A mismatch from another version, orientation, roll, stage, or pattern is not prioritized.
- `Air` matching now checks configured replacements before applying the `isAir()` fast path.

## Review-Fix Verification

- RED: the new matcher tests failed to compile because `Mismatch.structureVersion()` and `Mismatch.patternIdentity()` did not exist.
- GREEN: focused matcher/controller tests passed.
- `./gradlew test --no-daemon` passed.
- `./gradlew runGameTestServer --no-daemon` was run serially and failed with the known five timing assertions: `block_array_match`, `controller_tick`, `e2e_recipe_run`, `e2e_distillation_tower_partial_outputs`, and `terminal_build_across_ticks_duplicate`. These are Task 3/GameTest timing expectations and were not changed.
