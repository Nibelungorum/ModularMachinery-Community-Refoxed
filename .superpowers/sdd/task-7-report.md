# Task 7 Report

Commit: `9e8856b test: cover shared multiblock IO`

## Changed Files

- `src/gametest/java/cn/howxu/mmcr/SharedMultiblockIoGameTest.java`: added executable server-world regressions for shared-port formation and one-controller teardown, ten-item partial parallel starts, and alternating finite-energy tick grants.
- `src/gametest/java/cn/howxu/mmcr/GameTestRegistry.java`: registered the three shared multiblock IO GameTests through the project's NeoForge 26.1.2 custom registry.
- `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`: verifies the remaining controller retains its shared port component after the first controller resets.

## Commands And Output

1. `./gradlew compileTestJava compileGameTestJava --no-daemon`
   - Initial baseline: `BUILD SUCCESSFUL in 17s`.
   - First run after test edit: `compileGametestJava` succeeded; `compileTestJava` failed because the new assertion passed `IOPortKind` where `ProcessingComponent` expects `MachineComponent`.
   - Corrected the assertion to extract component containers.
   - Final run: `BUILD SUCCESSFUL in 14s`, `17 actionable tasks: 1 executed, 16 up-to-date`.

2. `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.StructureClaimRegistryTest --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --tests cn.howxu.mmcr.internal.tile.MachinePortAppearanceTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon`
   - `BUILD SUCCESSFUL in 22s`, `17 actionable tasks: 1 executed, 16 up-to-date`.

3. `./gradlew build --no-daemon`
   - `BUILD FAILED in 26s`.
   - `568 tests completed, 1 failed`.
   - Unrelated existing failure: `cn.howxu.mmcr.client.gui.FactoryControllerScreenTest.progress_overlay_aligns_with_the_thread_element_without_extra_bottom_pixel()` at `FactoryControllerScreenTest.java:100`, expected `32`, actual `31`.
   - No `runClient` task was run.

4. `git diff --check`
   - No output; no whitespace errors.

5. `git status --short`
   - Before commit: pre-existing modified `.superpowers/sdd/task-1-report.md` and `.superpowers/sdd/task-4-report.md`; Task 7 source files only were staged.
   - Commit result: `9e8856b`.

## Concerns

- Full build remains red because of the pre-existing client UI assertion above. The targeted Task 7 test set passes.

## Important Review Follow-up

- `finiteSharedEnergyRotatesTickGrantsBetweenLanes` now injects the second 15 FE grant, enqueues both lanes, and resolves the coordinator at `runAtTickTime(5)`, after the first finite grant at tick 4. Each tick asserts exactly one grant, while the second tick proves round-robin advances the other lane. The prior same-callback double resolve cannot satisfy the tick-4 assertion.
- Pre-existing build-failure evidence was checked against commit `e632eab` without modifying the current worktree: `FactoryControllerScreenTest` expects `progressOverlayBottom(20)` to equal `52`, while the same commit's `FactoryControllerScreen` computes `20 + (THREAD_ROW_HEIGHT - 1)`, or `51`. Running the current isolated class likewise produced one failure out of 12, in that exact test. This failure therefore predates the Task 7 follow-up.

Verification:

- `./gradlew compileGameTestJava --no-daemon`
  - `BUILD SUCCESSFUL in 1m 25s` (16 actionable tasks: 1 executed, 15 up-to-date).
- `./gradlew test --tests cn.howxu.mmcr.client.gui.FactoryControllerScreenTest --no-daemon`
  - `BUILD FAILED in 1m 28s`; 12 tests completed, 1 failed: the pre-existing `progress_overlay_aligns_with_the_thread_element_without_extra_bottom_pixel` mismatch above.
- `./gradlew runGameTestServer --no-daemon`
  - `BUILD FAILED in 34s` after compiling the GameTest source set and starting the dedicated server. The Gradle output did not report a GameTest assertion or a specific server exception, so this run is not attributed to the Task 7 change.
- `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.StructureClaimRegistryTest --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --tests cn.howxu.mmcr.internal.tile.MachinePortAppearanceTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon`
  - `BUILD SUCCESSFUL in 7s` (17 actionable tasks: 1 from cache, 16 up-to-date).
