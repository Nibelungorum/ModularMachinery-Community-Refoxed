# Task 2 Report

## Status

Implemented and committed.

## Commit

- `50bde94` `perf: scan large structures across ticks`
- `pending` report commit

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
