# Task 6 Report

## Changes

- Production startup now executes one `ContentRegistrationCoordinator` lifecycle from the mod constructor before any DeferredRegister is attached; all register attachments happen only after coordinator commit.
- Production built-ins, test bootstrap declarations, and GameTest declarations enter the same machine, structure, and recipe events.
- Removed the old definitions-only startup path and the duplicate CommonSetup startup commit.
- Removed TestBootstrap's direct default-level installation; default levels are collected by the shared structure event.
- Startup commit installs the complete structure snapshot into `MachineStructureRegistry` and projects it into `MachineRegistry`, including compiled stages.
- Kept the coordinator counter/snapshot seam and lifecycle coverage for shared bootstrap behavior.
- GameTest structure declarations now use deferred block predicates, so startup collection never calls an unbound DeferredHolder; matching, preview, and sync resolve the registered block at runtime.
- TestBootstrap bootstrap is now single-shot: repeated bootstrap calls and runtime builtin reloads no longer restore and recommit static machine definitions.
- Dynamic controller block, block entity, and item holders are declared in one explicit phase before the coordinator startup commit, while their DeferredRegisters remain unattached until commit succeeds.
- Optional-source tests remain isolated to the unit source set; the bootstrapped coordinator test covers the startup seam's commit-before-attachment phase.

## Verification

- `./gradlew compileJava --no-daemon`: passed.
- `./gradlew compileTestJava --no-daemon`: passed.
- `./gradlew compileGametestJava --no-daemon`: passed.
- `./gradlew test --tests cn.howxu.mmcr.OptionalGameTestSourceTest --tests cn.howxu.mmcr.registration.ContentRegistrationCoordinatorTest.production_startup_seam_commits_before_register_attachment --no-daemon`: passed.
- Full `./gradlew test --no-daemon`: failed with 49 of 1068 tests. Failures are the existing FML/test initialization cascade and existing machine definition/reload assertions; the new startup seam test passed.
- `./gradlew runGameTestServer --no-daemon`: passed mod construction beyond the former DeferredHolder binding failure, then failed during startup commit with the existing `stage 2 has conflicting predicate at -1, -1, 0` structure validation error.

## Remaining Risk

The full test suite and GameTest server were rerun after this focused change. The remaining risks are the existing test JVM initialization cascade and the independent GameTest startup structure conflict; the previous `DeferredHolder.get()` before binding failure is no longer the reported blocker.

## Task 6 Static Review

The production constructor now has an explicit boundary: startup content is collected, frozen, validated, and committed first; only then are the six DeferredRegisters attached to the mod event bus. Dynamic controller holders are still declared during collection, so they are present when the commit validates the startup model without depending on a mounted event bus. The unit test seam asserts the committed, unattached phase; GameTest source remains optional to unit tests.
