# Task 1 Report

## Status

Implemented and verified.

## Changes

- Added `org.nibelungorum.builtin.PublicBuiltinLevelDefinitions` as the canonical `MMCRMachineStructuresEvent` PublicAPI subscriber.
- Preserved the development-only production guard, `thermal_smelting_coil` duplicate guard, four coil levels, level type, and modifier declarations.
- Removed `DefaultMachineLevels` and the corresponding level registration/reflection helper path from `MMCR`.
- Migrated production test fixtures and all current test references to `PublicBuiltinLevelDefinitions`.
- Kept the existing machine and recipe builtin subscriber paths unchanged.
- Added real event-bus coverage for the level subscriber, including complete LevelType/level/Modifier declarations.
- Added duplicate-type and production-environment coverage for the level subscriber.

## Verification

- `./gradlew test --no-daemon --tests '*MachineLevel*' --tests '*Modifier*' --tests '*PublicApi*'`: passed.
- `./gradlew test --no-daemon`: passed.
- `./gradlew runGameTestServer --no-daemon`: passed.
- Focused tests now explicitly cover event-bus registration, duplicate protection, and production suppression.
- `./gradlew runClient`: not run, as required.
- `docs` files were not modified.

## Concerns

- Existing deprecation and `Unsafe` warnings remain; they are unrelated to this task.
