# Task 3 Report

## Changes

- Added one post-finish Start request phase to `SharedIoCoordinator.resolveDomain`.
- The phase takes only valid pending Start requests for the active domain and generation, sorts them by the existing lane ordering, resolves them through the existing start cursor once, and requeues unsuccessful requests.
- Tick requests created by Finish or Start callbacks are left pending for the following resolution cycle.
- Added a coordinator regression test covering a Finish callback that enqueues both a replacement Start and a Tick request.

## Tests

- RED: `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --no-daemon`
  - Failed as expected before implementation: `finishCommitStartsReplacementWithoutRunningItsTickRequest` observed `starts` as `0`.
- GREEN: `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --no-daemon`
  - Passed.
- Focused: `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`
  - Passed.

## Self Review

- The new Start phase runs exactly once after all Finish requests; it cannot rerun initial Tick or Finish requests.
- Only matching Start requests are removed from `pending`; replacement Tick requests remain pending.
- Failed replacement Start requests are returned to the unresolved list, preserving retry behavior.
- The requested Factory timing test cannot be added on this worktree: the stated task-1/2 prerequisite that has a Finish callback enqueue a restart Start is absent from baseline `4e7b014` and current sources. `FactoryRecipeThread.onFinished()` only resets the idle timer, so `thread.tick()` plus resolution cannot produce the specified replacement request without implementing prerequisite behavior outside this task.

## Commit

- `4c1f1fd feat: resolve shared recipe restarts after finish`

## Review Fix

- Fixed the pending lifecycle in `takePendingStartRequests`: it now first takes and removes every pending Start request for the active domain and generation, then filters the taken batch with `isStillValid()` for the post-finish start phase.
- Invalid Finish-spawned Start requests are now discarded instead of remaining pending and becoming eligible during a later resolve.
- Added `invalidStartSpawnedByFinishIsDiscardedBeforeTheNextResolve`, which makes the request valid only after its spawning resolve and confirms it is never committed.

## Review Tests

- RED: `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --no-daemon`
  - Failed as expected before the fix: `invalidStartSpawnedByFinishIsDiscardedBeforeTheNextResolve` observed `starts` as `1`.
- Focused: `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`
  - Passed.
- Full unit tests: `./gradlew test --no-daemon`
  - Passed.
- GameTest: `./gradlew runGameTestServer --no-daemon --quiet`
  - The server logged `All 28 required tests passed :)`, but the Gradle process exited non-zero without an exposed Gradle failure reason. The earlier non-quiet attempt also exited non-zero during startup because its 1 MiB output was truncated.

## Review Commit

- `268f4de fix: discard invalid finish-spawned starts`
