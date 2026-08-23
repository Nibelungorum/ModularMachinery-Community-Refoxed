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
