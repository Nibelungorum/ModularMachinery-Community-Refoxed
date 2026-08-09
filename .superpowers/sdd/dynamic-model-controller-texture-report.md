# Dynamic Controller Item Texture Fix Report

## Modification

- `DynamicOverlayItemModel.describeBlock` continues to obtain the stable runtime definition from `RuntimeMachineModelRegistry`.
- When that explicit definition declares a controller, its declared `machineId` is passed back to `Description.controller(machineId)`. This resolves the current `MachineAppearanceCache` base texture and `ControllerSpecCache` front-overlay texture.
- PORT and port-style declarations are returned directly. Unknown blocks still use `Description.staticItem()`.
- Added `DynamicOverlayItemModelTest.controller_item_resolves_textures_from_current_snapshots_after_definition_is_cached`, covering both `describeItem` and `describeBlock` after the definition cache has been populated and controller textures change.

## Snapshot Restore

- The regression test captures `MachineAppearanceCache.snapshot()` and `ControllerSpecCache.snapshot()` before replacing either snapshot.
- Both original maps are restored in a `finally` block, including when an assertion fails, so the test does not leak cache state to later tests.

## Verification

The test was first run before the production change and failed as expected:

```text
DynamicOverlayItemModelTest > controller_item_resolves_textures_from_current_snapshots_after_definition_is_cached() FAILED
7 tests completed, 1 failed
BUILD FAILED in 9s
```

After the production change:

```text
rtk gradlew test --tests cn.howxu.mmcr.client.model.DynamicOverlayItemModelTest --no-daemon
BUILD SUCCESSFUL in 10s
17 actionable tasks: 2 executed, 15 up-to-date
```

Specified affected tests:

```text
rtk gradlew test --tests cn.howxu.mmcr.client.model.DynamicOverlayItemModelTest --tests cn.howxu.mmcr.client.model.RuntimeMachineModelRegistryTest --tests cn.howxu.mmcr.client.model.RuntimeMachineResourcePackTest --no-daemon
BUILD SUCCESSFUL in 13s
17 actionable tasks: 1 executed, 16 up-to-date
```

Compilation:

```text
rtk gradlew compileJava --no-daemon
BUILD SUCCESSFUL in 5s
14 actionable tasks: 14 up-to-date
```

## Self Review

- The definition remains the stable source for deciding dynamic eligibility and the controller machine identity.
- Only controller items dynamically resolve textures; PORT/port-style declarations do not regain block/name/type inference.
- The static fallback remains unchanged.
- No client run task was used.
