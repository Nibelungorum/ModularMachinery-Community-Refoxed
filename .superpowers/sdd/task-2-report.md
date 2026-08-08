# Task 2 Report

## Files

- Updated `ModelGen` to skip controller and I/O port blockstate/item model output while retaining static casing/debug models, wrench/detector flat items, and language generation.
- Removed `MachineControllerVariants` and its obsolete test.
- Removed static controller/port overlay model assets.
- Moved overlay texture-name resolution into `DynamicOverlayBakedModel` and retained runtime coverage in `DynamicOverlayModelTest`.
- Updated `ModelGenTest` and `BasicIOVariantResourceTest` for the no-generated-model behavior.
- Removed stale ignored generated controller/port blockstate and item model files under `src/generated/resources`.

## Tests

- Initial target test run failed at test compilation because the new `generatedDynamicBlocks()` test interface was not implemented yet, confirming the test-first red phase.
- The next target run reached the new resource assertions and failed because stale generated controller/port assets were still present.
- Final command passed:

  `./gradlew test --no-daemon --tests cn.howxu.mmcr.datagen.ModelGenTest --tests cn.howxu.mmcr.resources.BasicIOVariantResourceTest --tests cn.howxu.mmcr.client.model.DynamicOverlayModelTest`

  Result: `BUILD SUCCESSFUL`, 7 tests completed.

- `git diff --check` passed.

## Commit

- Task 2 commit: `a289336502a041d4acb2dd9c8648312bbb2f0e33 refactor: remove dynamic machine models from datagen`

## Concerns

- The generated assets are ignored by Git, so their cleanup is local working-tree cleanup rather than a tracked diff.
- `cube_all_overlay.json` had no remaining static model references and is removed by the follow-up fix; dynamic runtime model assets, overlay textures, and language resources remain.

## Follow-up Review Fix

- Replaced the hardcoded `ModelGen.generatedDynamicBlocks()` empty list with a collector that excludes registered controller and I/O port names without dereferencing unbound test holders; `registerModels()` retains its block-type predicate.
- Added default controller blockstate/item model absence and translation-key assertions; port assertions remain unchanged.
- Removed the unreferenced `cube_all_overlay.json` resource.
- The first follow-up verification exposed that `ModelGenTest` needed the existing `TestBootstrap` before touching NeoForge registries; the test now performs that initialization.
- Verification: `./gradlew test --no-daemon --tests cn.howxu.mmcr.datagen.ModelGenTest --tests cn.howxu.mmcr.resources.BasicIOVariantResourceTest --tests cn.howxu.mmcr.client.model.DynamicOverlayModelTest` passed; `BUILD SUCCESSFUL`, 8 tests completed. `git diff --check` passed.
