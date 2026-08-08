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

- Task 2 commit: `a289336 refactor: remove dynamic machine models from datagen`

## Concerns

- The generated assets are ignored by Git, so their cleanup is local working-tree cleanup rather than a tracked diff.
- `cube_all_overlay.json`, dynamic runtime model assets, overlay textures, and language resources were retained.
