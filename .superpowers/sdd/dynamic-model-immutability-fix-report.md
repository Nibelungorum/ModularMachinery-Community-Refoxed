# Dynamic Model Immutability Fix Report

## Scope

Final-review important issue: cached runtime block model definitions exposed a mutable `variants` list through `RuntimeBlockStateDefinition`. Controller definitions construct their variants with an `ArrayList`, so callers could mutate cached state and alter later runtime resource-pack JSON output.

## Change

- Added a compact constructor to `RuntimeMachineModelRegistry.RuntimeBlockStateDefinition` that assigns `variants = List.copyOf(variants)`.
- Added `cached_block_state_variants_cannot_be_mutated` to `RuntimeMachineModelRegistryTest`.
- The regression test obtains a cached controller definition, captures generated blockstate JSON, asserts that clearing the accessor list throws `UnsupportedOperationException`, then asserts that the generated JSON is unchanged.

## Test-First Evidence

Before the production change, the new regression test failed because `definition.blockStateDefinition().variants().clear()` completed normally. This confirmed that the cached controller definition exposed the mutable `ArrayList` created by `controllerDefinition`.

After adding the defensive snapshot, the focused regression test passed.

## Verification

- `rtk gradlew test --tests cn.howxu.mmcr.client.model.RuntimeMachineModelRegistryTest.cached_block_state_variants_cannot_be_mutated --no-daemon`
  - Failed before the production change as expected.
  - Passed after the production change.
- `rtk gradlew test --tests cn.howxu.mmcr.client.model.RuntimeMachineModelRegistryTest --tests cn.howxu.mmcr.client.model.RuntimeMachineResourcePackTest --tests cn.howxu.mmcr.client.model.DynamicOverlayModelTest --no-daemon`
  - Passed.
- `rtk gradlew compileJava --no-daemon`
  - Passed.

## Risk

`List.copyOf` rejects null list elements. Runtime variants are internally constructed and never permit null entries, so this is appropriate validation at the immutable definition boundary. No API shape or resource JSON content changed.
