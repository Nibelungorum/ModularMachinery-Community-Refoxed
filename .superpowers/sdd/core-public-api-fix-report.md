# Core Public API Fix Report

## Changes

- `RegisterMachineStructuresEvent` now collects `ModifierDefinition`, validates duplicate, unknown, frozen, and cross-reference writes with `ApiRegistrationException`, and returns an immutable `Snapshot` from `freeze()`.
- Public structure requirements and recipe definitions retain modifier and level identifiers rather than runtime modifier/level instances. `MachineRecipeBuilder.modifier(Identifier)` is the only public inline modifier entry; `RecipeModifierValue` was removed.
- `PublicRecipeAdapter` resolves modifier and level identifiers from the installed structure snapshot and rejects unknown identifiers before recipe installation.
- Modifier definitions now have snapshot installation/query storage, and KubeJS machine registration copies sounds, modifier/concurrency flags, expandable structure, and smart-interface settings into the final startup registration.
- Added direct lifecycle, adapter, and KubeJS registration coverage.

## Verification

- Focused tests: passed (`PublicApiLifecycleTest`, `PublicRecipeBuilderTest`, `PublicMachineBuilderTest`, `MachineBuilderJSTest`; 25 tests).
- `./gradlew test --no-daemon`: failed with 3 unrelated existing failures: `ReloadCommandTest.reloadCommandClearsDynamicStructures`, `DynamicModuleReloadValidationTest.invalid_candidate_role_coupler_or_module_reference_retains_previous_snapshot`, and `RuntimeContentSnapshotTest.applyClientRemovesDynamicContentOmittedFromSnapshot`. 1053 tests completed, 3 failed.
- `./gradlew runGameTestServer --no-daemon`: failed with 14 existing dynamic/timing/terminal GameTest failures, including structure timing, expandable machine lookup, recipe timing, and terminal assembly lookup. The server started and completed all 46 tests; no startup failure from this change occurred.
- No `runClient` task was run.

## Residual Issues

- The three unrelated unit failures and fourteen allowed dynamic/GameTest failures remain as listed above.
- `MachineLevelRegistry` legacy registration methods remain public because the existing unit-test consumers still use them; production lifecycle installation uses the structure-event snapshot path.
