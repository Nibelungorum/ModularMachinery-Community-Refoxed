# Task 4 Report: Menus, Titles, And Existing Block Compatibility

## What Changed

- Added `IOPortBlock.PortMenuKind` and package-private `IOPortBlock.menuKindFor(String)` to classify generated port ids by category prefix.
- Updated `IOPortBlock.openServerMenu` to route item, fluid, and energy generated variants to the existing `ItemBusMenu`, `FluidHatchMenu`, and `EnergyHatchMenu` constructors.
- Updated `IOPortBlock.titleFor(String)` to always use deterministic `container.mmcr.<kind>` translation keys.
- Added `IOPortBlockTest` coverage for generated menu routing and deterministic generated translation keys.

## Commands Run And Results

- `rtk git status --short --branch` -> current branch `feat/port-me-interfaces-plan`; pre-existing modified `.superpowers/sdd/task-1-report.md` observed and left untouched.
- `rtk gradlew test --tests cn.howxu.mmcr.internal.block.IOPortBlockTest --no-daemon` after RED test -> `BUILD FAILED`, 1 test failed as expected for generated id prefix routing.
- `rtk gradlew test --tests cn.howxu.mmcr.internal.block.IOPortBlockTest --no-daemon` after implementation -> `BUILD SUCCESSFUL`.
- `rtk gradlew compileJava --no-daemon` -> `BUILD SUCCESSFUL`; existing deprecation/removal warnings emitted by unrelated NeoForge capability usage.
- A parallel run of `test` and `compileJava` produced a transient `compileTestJava` failure with missing main-class symbols; re-running the target test alone immediately passed, so final verification was run sequentially/fresh.
- Final `rtk gradlew test --tests cn.howxu.mmcr.internal.block.IOPortBlockTest --no-daemon` -> `BUILD SUCCESSFUL`.
- Final `rtk gradlew compileJava --no-daemon` -> `BUILD SUCCESSFUL`.

## TDD Evidence

- RED: Added `generated_port_ids_route_to_menu_by_category_prefix`; the test failed because `menuKindFor` initially only matched exact six base ids and returned `NONE` for generated suffix ids.
- GREEN: Changed routing to `startsWith` prefix classification and switched `openServerMenu` to `menuKindFor`; the targeted test then passed.
- Additional coverage: Added `generated_port_ids_use_direct_container_translation_key` after making `titleFor` package-private and deterministic; final targeted test run passed.

## Files Changed

- `src/main/java/cn/howxu/mmcr/internal/block/IOPortBlock.java`
- `src/test/java/cn/howxu/mmcr/internal/block/IOPortBlockTest.java`
- `.superpowers/sdd/task-4-report.md`

## Self-Review

- Scope stayed limited to `IOPortBlock` and the new focused test file.
- Existing menu constructors and block entity checks were preserved.
- Existing six normal ids still resolve to the same `container.mmcr.<id>` translation keys.
- Unknown ids now get deterministic untranslated `container.mmcr.<id>` keys instead of literal text, matching the brief.
- Did not modify the pre-existing `.superpowers/sdd/task-1-report.md` worktree change.

## Concerns

- `compileJava` emits many existing deprecation/removal warnings from NeoForge capability APIs; not introduced by this task.
- One parallel Gradle invocation showed a transient test compilation failure while `compileJava` ran concurrently; sequential final verification passed.

## Review Fix: IO Port Menu Prefix Boundaries

- Tightened menu kind matching so an id routes only when it exactly equals a base port id or starts with that base id followed by `_`.
- Added focused tests proving the six normal base ids still route to ITEM, FLUID, and ENERGY menu kinds.
- Added focused tests proving near-prefix ids `item_input_busbar`, `fluid_output_hatchery`, and `energy_input_hatchling` return `NONE`.

## Review Fix Commands Run And Results

- `rtk gradlew test --tests cn.howxu.mmcr.internal.block.IOPortBlockTest --no-daemon` after adding review regression tests -> `BUILD FAILED`; `near_prefix_port_ids_do_not_route_to_menus()` failed as expected before tightening matching.
- `rtk gradlew test --tests cn.howxu.mmcr.internal.block.IOPortBlockTest --no-daemon` after implementation, run in parallel with `compileJava` -> `BUILD FAILED` at `compileTestJava` with missing main-class symbols, matching the previously observed concurrent Gradle interference.
- `rtk gradlew compileJava --no-daemon` in that parallel run -> `BUILD SUCCESSFUL` with existing deprecation/removal warnings.
- Final sequential `rtk gradlew test --tests cn.howxu.mmcr.internal.block.IOPortBlockTest --no-daemon` -> `BUILD SUCCESSFUL` in 7s; 17 actionable tasks: 1 executed, 16 up-to-date.
- Final sequential `rtk gradlew compileJava --no-daemon` -> `BUILD SUCCESSFUL` in 6s; 14 actionable tasks: 14 up-to-date.
