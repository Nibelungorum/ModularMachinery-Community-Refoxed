# Task 1 Report

## Status

Implemented; focused tests verified. Full suite retains unrelated failures noted below.

## Changes

- Added `org.nibelungorum.builtin.PublicBuiltinLevelDefinitions` as the canonical `MMCRMachineStructuresEvent` PublicAPI subscriber.
- Preserved the development-only production guard, `thermal_smelting_coil` duplicate guard, four coil levels, level type, and modifier declarations.
- Removed `DefaultMachineLevels` and the corresponding level registration/reflection helper path from `MMCR`.
- Migrated production test fixtures and all current test references to `PublicBuiltinLevelDefinitions`.
- Kept the existing machine and recipe builtin subscriber paths unchanged.
- Added real canonical event coverage for the level subscriber, including complete LevelType/level/Modifier declarations.
- Added duplicate-type and production-environment coverage for the level subscriber.
- Corrected the tests to invoke the provider through the project's direct event source instead of assuming manual NeoForge bus posts scan `@EventBusSubscriber` classes.
- Corrected the production fixture to snapshot and restore the original active FML loader, its `LoadingModList`, and the active reference; it also asserts the replacement loader is actually in production before checking suppression.
- Added `@AfterEach` cleanup gates for manual lifecycle listeners so they become inert after the test and cannot mutate later events.
- Added behavior assertions for the LevelType display name, all four priorities, block-state predicates, and modifier parameters.

## Verification

- `./gradlew test --no-daemon --tests '*MachineLevel*' --tests '*Modifier*' --tests '*PublicApi*'`: passed.
- `./gradlew test --no-daemon`: two existing `ContentRegistrationCoordinatorTest` production lifecycle assertions failed at lines 246 and 269; running that class alone reproduces the failures.
- `./gradlew runGameTestServer --no-daemon`: passed.
- Focused tests now explicitly cover the direct provider event path, duplicate protection, and production suppression.
- `./gradlew test --no-daemon --tests 'cn.howxu.mmcr.api.publicapi.PublicEventSubscribersTest.builtin_level_subscriber*'`: passed.
- `./gradlew test --no-daemon --tests 'cn.howxu.mmcr.api.publicapi.PublicEventSubscribersTest'`: passed.
- `./gradlew runClient`: not run, as required.
- `docs` files were not modified.

## Concerns

- Existing deprecation and `Unsafe` warnings remain; they are unrelated to this task.
- The full-test failures are unrelated to the modified test file and reproduce in `ContentRegistrationCoordinatorTest` alone.
