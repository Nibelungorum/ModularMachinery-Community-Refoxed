# Critical Fix Report

## Changes

- `MMCR.java`: runtime recipes are still published from `DefaultDataComponentsBoundEvent`, after common setup has completed machine/structure installation; removed the earlier common-setup recipe publication path. Runtime builtin setup now also makes the public lifecycle idempotently complete before rebuilding runtime content.
- `RegisterMachineStructuresEvent.java`: added collection and immutable snapshots for `LevelType` and `MachineLevel`; the level modifier remains part of the collected `MachineLevel`.
- `DefaultMachineLevels.java`, `LevelTypeBuilderJS.java`, `MachineLevelBuilderJS.java`, `Plugin.java`: production registration paths now collect through the structure event instead of independently driving the level registry lifecycle.
- `MachineRegistration.java`, `MachineBuilderJS.java`: sound IDs are validated in the canonical record constructor and immediately in KubeJS Identifier overloads with `ApiRegistrationException`.
- Tests were updated for event-backed level installation and added immediate/canonical sound validation coverage.

## Verification

- `./gradlew compileJava --no-daemon`: passed; existing NeoForge deprecation warnings only.
- Targeted coverage command:
  `./gradlew test --no-daemon --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest --tests cn.howxu.mmcr.compat.kubejs.MachineLevelBuilderJSTest --tests cn.howxu.mmcr.api.publicapi.PublicApiLifecycleTest --tests cn.howxu.mmcr.api.machine.BlockPredicateTest --tests cn.howxu.mmcr.api.publicapi.PublicRecipeBuilderTest --tests cn.howxu.mmcr.compat.kubejs.KubeJSApiTest --tests cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactoryTest`
  passed.
- `./gradlew test --no-daemon`: failed with 3 existing unrelated failures: `ReloadCommandTest.reloadCommandClearsDynamicStructures`, `DynamicModuleReloadValidationTest.invalid_candidate_role_coupler_or_module_reference_retains_previous_snapshot`, and `RuntimeContentSnapshotTest.applyClientRemovesDynamicContentOmittedFromSnapshot`. Summary: `1050 tests completed, 3 failed`.
- `./gradlew runGameTestServer --no-daemon`: failed with 14 required GameTests, all existing runtime/timing or terminal-assembly failures (`block_array_match`, `controller_tick`, `e2e_recipe_run`, datapack recipe run, distillation partial outputs, expandable/terminal tests). The server reached test execution and no `Components not bound yet` startup exception remained after the lifecycle correction.

## Residual Risk

- The full suite and GameTest suite retain the unrelated failures listed above. No `runClient` task was run.
