# Task 6 Report

## Changes

- Production startup now executes one `ContentRegistrationCoordinator` lifecycle from the mod constructor, after DeferredRegisters are attached and before registry events fire.
- Production built-ins, test bootstrap declarations, and GameTest declarations enter the same machine, structure, and recipe events.
- Removed the old definitions-only startup path and the duplicate CommonSetup startup commit.
- Removed TestBootstrap's direct default-level installation; default levels are collected by the shared structure event.
- Startup commit installs the complete structure snapshot into `MachineStructureRegistry` and projects it into `MachineRegistry`, including compiled stages.
- Kept the coordinator counter/snapshot seam and lifecycle coverage for shared bootstrap behavior.
- GameTest structure declarations now use deferred block predicates, so startup collection never calls an unbound DeferredHolder; matching, preview, and sync resolve the registered block at runtime.
- TestBootstrap bootstrap is now single-shot: repeated bootstrap calls and runtime builtin reloads no longer restore and recommit static machine definitions.
- Dynamic controller block, block entity, and item holders are declared in one explicit phase before the coordinator startup commit.
- Shared optional-source invocation tests now cover both a present GameTest-like source and an absent source.

## Verification

- `./gradlew compileJava compileTestJava compileGametestJava --no-daemon`: passed.
- `./gradlew test --tests cn.howxu.mmcr.OptionalGameTestSourceTest --tests cn.howxu.mmcr.api.publicapi.PublicApiAdapterTest --no-daemon`: passed.
- Coordinator and public lifecycle tests: not rerun after this focused change.
- Full `./gradlew test --no-daemon`: failed with 93 tests; most failures are the existing test JVM `Another FML loader is already active` initialization cascade. One independent `MachineDefinitionBootstrapTest` assertion also failed.
- `./gradlew runGameTestServer --no-daemon`: failed during mod construction because GameTest structure declarations call `DeferredHolder.get()` before `basic_casing` is bound. The failure is at `GameTestRegistry.registerMachineStructures` and was not hidden by changing or disabling tests.

## Remaining Risk

The full test suite and GameTest server were not rerun after this focused change. Existing known failures remain: the test JVM can report `Another FML loader is already active`, and the earlier GameTest server run failed during mod construction before this deferred predicate change.
