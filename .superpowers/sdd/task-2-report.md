# Task 2 Report

## Status

PASS. Task 2 client preview caching, distance culling, layer-aware visibility rebuilding, lifecycle cleanup, configuration, and focused tests are implemented.

## Commit

`ef1a29e perf: cache and cull multiblock previews`

## Files

- `src/main/java/cn/howxu/mmcr/client/MultiblockPreviewClientHandler.java`
  - Caches resolved `BlockModelRenderState` values by `BlockState`.
  - Rebuilds visible entries only when the selected layer, camera-cell bucket, or configured radius changes.
  - Applies selected-layer filtering before squared-distance culling.
  - Clears visibility and model caches when replacing or clearing a preview.
- `src/main/java/cn/howxu/mmcr/config/Config.java`
  - Adds `preview_render_radius`, defaulting to 64 blocks and bounded to 1..512.
- `src/test/java/cn/howxu/mmcr/client/MultiblockPreviewClientHandlerTest.java`
  - Covers radius culling and cache clearing on preview replacement.

## Tests

- `./gradlew test --no-daemon --tests '*MultiblockPreviewClientHandler*'` PASS, 5 tests.
- `./gradlew compileJava --no-daemon` PASS.
- Client was not launched; forbidden `runClient` was not run.

## Concerns

- The model cache stores `BlockModelRenderState` instances and assumes the existing `submitMultiLayer` API does not mutate them during submission. Cache clearing is performed on preview replacement, `clear()`, expiry, and client-level unload.
- The report is intentionally not part of the implementation commit because the approved Task 2 file list contains only the three source/test files above.

## Review Fix

- Replaced the cross-frame `BlockModelRenderState` cache with immutable cached model parts and translucency metadata derived from the 26.1.2 `BlockStateModel` API. Each submission now creates an independent `BlockModelRenderState`.
- Added focused lifecycle tests for `clear()`, expiry, and client-level unload, plus repeated same-state cache reuse and selected-layer-before-distance visibility coverage.
- Configuration bounds remain defined as 1..512 by `defineInRange`; direct boundary value testing is not possible in the existing pure bootstrap because the config value is unloaded and throws on access.

## Review Fix Commit

`778ec69 fix: address task 2 preview review findings`

## Review Fix Tests

- `./gradlew test --no-daemon --tests '*MultiblockPreviewClientHandler*'` PASS, 9 tests.
- `./gradlew compileJava --no-daemon` PASS.
- Client was not launched; forbidden `runClient` was not run.

## Review Fix Concerns

- `BlockStateModel.collectParts` is the public 26.1.2 geometry-resolution seam used here. Special-model-only renderers are not represented by `BlockStateModelPart`; if previews must support those blocks, the renderer needs a separate per-submit special-model path rather than sharing render state.
