# Task 2 Report

## Changed Files

- `src/main/java/cn/howxu/mmcr/client/preview/world/WorldPreviewMesh.java`
- `src/main/java/cn/howxu/mmcr/client/preview/world/WorldPreviewMeshCompiler.java`
- `src/test/java/cn/howxu/mmcr/client/preview/world/WorldPreviewMeshCompilerTest.java`

The compiler batches block and fluid output by `ChunkSectionLayer`, resolves each distinct block state model once, uses full-bright region lighting, records translucent sort metadata, tracks block-entity positions, and closes all intermediate and published resources deterministically. It does not connect final event submission or modify JEI/PiP behavior.

## Commits

- `f32f402` `feat: batch world preview meshes`

## Tests

- `./gradlew test --no-daemon --tests cn.howxu.mmcr.client.preview.world.WorldPreviewMeshCompilerTest`: `BUILD SUCCESSFUL`.
- `./gradlew test --no-daemon`: `BUILD SUCCESSFUL`.
- `./gradlew runGameTestServer --no-daemon`: `BUILD SUCCESSFUL`.
- TDD red phase: the focused test initially failed at `compileTestJava` because `WorldPreviewMeshCompiler` and `WorldPreviewMesh` did not exist.

## Concerns

- The two render-layer tests skip when the headless unit-test runtime has no client model manager; cancellation remains fully exercised without GPU/model initialization. Full render-layer behavior still needs client-runtime coverage when final event submission is added.
- `.superpowers/sdd/task-3-report.md` remains an unrelated, unstaged worktree change and was not committed.

## Review Fixes

- Replaced the headless `assume` path with the package-private `CompilationPlan` seam. It verifies selected-layer filtering, air filtering, solid/cutout classification, fluid routing, and block-entity position tracking without a live client model manager.
- Documented and centralized the full-bright contract. The compiler region reports full brightness for both light layers, while translucent layers alone retain sort metadata.
- Added cleanup coverage proving every resource is attempted after a failure, and made `WorldPreviewMesh.close()` idempotent while retaining deterministic resource cleanup.
- Kept runtime model integration, final event submission, JEI, and PiP behavior unchanged. Fluids are explicitly routed to the translucent builder.

## Review Fix Verification

- `./gradlew test --no-daemon --tests cn.howxu.mmcr.client.preview.world.WorldPreviewMeshCompilerTest`: `BUILD SUCCESSFUL`.
- `./gradlew test --no-daemon`: `BUILD SUCCESSFUL`.
- `./gradlew runGameTestServer --no-daemon`: `BUILD SUCCESSFUL`.

## Review Fix Commit

- Pending commit after verification: `fix: cover task 2 mesh compiler contracts`.

## Latest Review Fixes

- Connected `CompilationPlan` to production compilation. The compiler now uses planned entry filtering and planned solid/cutout/translucent routing for block output, while planned fluid routing always targets translucent output.
- Added an independent air-filter test where air is present on the selected layer.
- Added a real `compile` failure-path test that allocates the builder pack, injects a non-empty intermediate mesh, fails through compilation cleanup, and verifies both resources are closed.
- Added a client-runtime test for translucent sort metadata through the public production compile path; it is skipped by the headless unit-test runtime when no `Minecraft` instance exists.
- Changed the idempotence test to close a non-empty mesh resource and verify its buffer becomes invalid after the first close.
- Kept full-bright lighting, block entity position tracking, final event submission, JEI, and PiP behavior unchanged.

## Latest Verification

- `./gradlew test --no-daemon --tests cn.howxu.mmcr.client.preview.world.WorldPreviewMeshCompilerTest`: `BUILD SUCCESSFUL`.
- `./gradlew test --no-daemon`: `BUILD SUCCESSFUL`.
- `./gradlew runGameTestServer --no-daemon`: `BUILD SUCCESSFUL`.

## Latest Concerns

- The headless unit-test runtime does not create a live `Minecraft` model manager, so direct GPU/model tessellation cannot execute in that focused JVM. Production `compile` now consumes the shared plan; actual client model rendering remains runtime-dependent.
