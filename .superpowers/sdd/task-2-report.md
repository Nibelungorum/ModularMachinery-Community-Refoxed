# Task 2 Report: Generate All Basic Port Kinds

## What changed

- Added default optional size accessors to `IOPortKind` for item bus, fluid hatch, and energy hatch sizes.
- Reworked `PortKinds` to generate all basic IO port variants from `ItemBusSize`, `FluidHatchSize`, and `EnergyHatchSize` in stable order.
- Preserved the six public normal-tier constants as aliases resolved by id.
- Added typed `ItemBusKind`, `FluidHatchKind`, and `EnergyHatchKind` records that expose their matching size object.
- Updated `PortKindsTest` to assert stable ids, IO type ordering, normal-tier aliases, and append behavior after defaults reset.

## Commands run and results

- `git rev-parse --git-dir && git rev-parse --git-common-dir && rtk git branch --show-current && git rev-parse --show-superproject-working-tree 2>/dev/null || true`
  - Result: confirmed linked worktree on `feat/port-me-interfaces-plan`.
- `rtk gradlew test --tests cn.howxu.mmcr.registry.PortKindsTest --no-daemon`
  - RED result: failed as expected with 4 failing tests because only six old kinds were registered.
- `rtk gradlew test --tests cn.howxu.mmcr.registry.PortKindsTest --no-daemon`
  - Intermediate result: compile failed because record component accessors conflicted with `Optional` interface methods.
- `rtk gradlew test --tests cn.howxu.mmcr.registry.PortKindsTest --no-daemon`
  - Intermediate result: tests compiled; append-size assertion failed because generated defaults count is 46, so append count is 47.
- `rtk gradlew test --tests cn.howxu.mmcr.registry.PortKindsTest --no-daemon`
  - GREEN result: `BUILD SUCCESSFUL in 11s`, 17 actionable tasks, 2 executed, 15 up-to-date.
- `rtk git diff --check`
  - Result: passed with no whitespace errors.
- `rtk gradlew compileJava --no-daemon`
  - Result: `BUILD SUCCESSFUL in 9s`, 14 actionable tasks up-to-date.

## TDD evidence

- RED: Updated `PortKindsTest` first with the expected generated id order, size alias assertions, and append count expectation. The targeted test failed because the production registry still contained only the original six simple kinds.
- GREEN: Implemented generated typed port kinds and reset defaults. The targeted registry test then passed.
- Correction during GREEN: Java records cannot override their canonical accessor with a different return type, so record components were named `size` while the interface methods remain `itemBusSize()`, `fluidHatchSize()`, and `energyHatchSize()`.

## Files changed

- `src/main/java/cn/howxu/mmcr/internal/port/IOPortKind.java`
- `src/main/java/cn/howxu/mmcr/registry/PortKinds.java`
- `src/test/java/cn/howxu/mmcr/registry/PortKindsTest.java`
- `.superpowers/sdd/task-2-report.md`

## Self-review

- Verified the generated order matches the explicit id list in the task brief.
- Verified normal-tier aliases point at the generated normal variants and expose the matching size object.
- Verified `clearForTesting()` restores generated defaults and `register()` still appends external kinds.
- Kept changes scoped to the requested interface, registry, test, and task report.
- Did not run the forbidden `./gradlew runClient --no-daemon` command.

## Concerns

- The task title/context says “48 basic IO port kinds,” but the explicit expected id list and Task 1 enum sizes produce 46 built-in kinds: 7 item input + 7 item output + 8 fluid input + 8 fluid output + 8 energy input + 8 energy output. I followed the explicit expected id list from the brief; `register_appends_new_kind()` therefore expects 47 after appending one custom kind.
- Worktree status showed a pre-existing modified `.superpowers/sdd/task-1-report.md`; it was not touched or staged for this task.
