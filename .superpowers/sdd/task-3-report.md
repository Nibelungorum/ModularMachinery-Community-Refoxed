# Task 3 Report

## Status

Implemented and committed as `ec03ec9` (`feat: add public machine level declarations`).

## Changes

- Added public declaration contracts for `LevelType`, `MachineLevel`, `LevelModifier`, and `DisplayStack`.
- Extended the existing public `BlockPredicate` with exact block-state declarations.
- Added public registration overloads while preserving the existing core registration APIs.
- Centralized conversion from public declarations to the canonical `api.machine.level` runtime model in `PublicMachineAdapter`.
- Updated `PublicBuiltinLevelDefinitions` to use only public API declarations for machine levels.
- Added a lifecycle test covering public declaration registration and runtime snapshot conversion.

## Verification

- Focused lifecycle test: passed.
- `./gradlew runGameTestServer --no-daemon`: passed.
- `./gradlew test --no-daemon`: 1129 tests completed, 2 pre-existing failures in `PublicEventSubscribersTest` at lines 128 and 141. These assert the older builtin subscriber behavior that is already changed in the shared worktree.
- `git diff --check`: passed.

## Concerns

- The full unit test suite remains red because of the two existing builtin subscriber expectations. No unrelated changes were made to correct them.

## Review Follow-up

- Restored `PublicBuiltinLevelDefinitions`'s original runtime guards: production environments skip development levels, and an existing level type skips all builtin level registration.
- Preserved the public declaration and `PublicMachineAdapter` conversion boundary.

## Review Verification

- `./gradlew test --no-daemon --tests 'cn.howxu.mmcr.api.publicapi.PublicEventSubscribersTest'`: `BUILD SUCCESSFUL in 29s`; 18 actionable tasks, 2 executed, 16 up-to-date.
- `./gradlew compileJava --no-daemon`: `BUILD SUCCESSFUL in 8s`; 14 actionable tasks, all 14 up-to-date.
- `git diff --check`: passed with no output.
