# Task 3 Report

## Status

Implemented and committed as `07dab8f` (`allow multiple factory controllers`). The pre-existing user changes were preserved, and `PublicBuiltinDefinitions.java` was not modified.

## Changes

- `ComponentClaimPolicy` now has an explicit `SHARED_CAPACITY` category.
- Factory scheduler components use `SHARED_CAPACITY`; parallel controllers and other exclusive stateful components remain `EXCLUSIVE`.
- Factory machines accept any positive number of factory controllers. Non-factory machines reject factory controllers.
- Added registry coverage for shared-capacity ownership and release cleanup.
- Updated controller formation coverage to verify two capacity providers and aggregate thread capacity.
- Added a focused GameTest covering two-controller formation, removal cleanup, and reformation.

## Verification

- Focused unit tests: `./gradlew test --no-daemon --tests '*StructureClaimRegistryTest' --tests '*MachineControllerBlockEntityTest'` passed.
- Focused GameTest: `./gradlew runGameTestServer --no-daemon` passed.
- `git diff --check` passed.
- Full unit suite: 1135 tests executed, 2 unrelated pre-existing failures remained in `MachineControllerMenuTest.server_menu_syncs_factory_base_thread_progress_when_controller_has_no_local_active_recipe` (`NoSuchFieldException`) and `FactorySchedulerBlockEntityTest.boundControllerIsNotifiedImmediatelyWhenInventoryChanges` (`IllegalStateException`). Both reproduce when run individually and do not involve the changed files.

## Concerns

- `SHARED_CAPACITY` intentionally allows multiple claimants for the same factory capacity component, matching the explicit shareable-capacity policy. Exclusive component conflict behavior remains unchanged.

## Review Follow-Up

- Updated `MachineControllerMenuTest.server_menu_syncs_factory_base_thread_progress_when_controller_has_no_local_active_recipe` to configure the active base thread through `MachineControllerBlockEntity.factoryScheduler()` instead of reflecting the removed factory-component scheduler.
- Updated `FactorySchedulerBlockEntityTest.boundControllerIsNotifiedImmediatelyWhenInventoryChanges` to construct a factory-capable controller before binding the capacity component, matching the current owner callback lifecycle without adding a component-owned scheduler.

## Review Verification

- Command: `./gradlew test --no-daemon --tests '*StructureClaimRegistryTest' --tests '*MachineControllerBlockEntityTest' --tests '*MachineControllerMenuTest' --tests '*FactorySchedulerBlockEntityTest'`
  - Exact result: `BUILD SUCCESSFUL`; `18 actionable tasks: 1 executed, 17 up-to-date`.
- Command: `./gradlew runGameTestServer --no-daemon`
  - Exact result: `BUILD SUCCESSFUL`; `20 actionable tasks: 1 executed, 19 up-to-date`.
