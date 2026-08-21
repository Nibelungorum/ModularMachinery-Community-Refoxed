# Task 1 Report

## Changed Files

- `src/main/java/cn/howxu/mmcr/internal/tile/FactorySchedulerBlockEntity.java`
  - Removed delayed 40-tick capacity polling and the block entity-owned capacity field.
  - Kept inventory persistence and `threadCount()` as the capacity query.
  - Added null-safe owner binding and immediate owner notification on inventory changes.
- `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
  - Aggregates all factory component capacities with integer saturation.
  - Binds and unbinds factory owners during component refresh and structure reset.
  - Refreshes scheduler capacity and menu/runtime state on owner notification.
  - Added the package-visible factory component helper and test callback seam.
- `src/test/java/cn/howxu/mmcr/internal/tile/FactorySchedulerBlockEntityTest.java`
  - Replaced delayed-polling assertions with aggregation and immediate insert/remove notification tests.

`PublicBuiltinDefinitions.java` was not modified. The two pre-existing example-script changes remain in the worktree and were not included in the implementation commit.

## Verification

- `./gradlew test --no-daemon --tests '*FactorySchedulerBlockEntityTest'`
  - `BUILD SUCCESSFUL`
  - `7 tests completed`
- `./gradlew test --no-daemon --tests '*MachineControllerBlockEntityTest'`
  - Existing tests failed because their test fixture still reflects the removed `FactorySchedulerBlockEntity.threadLimit` field; 18 tests fail during fixture allocation at `MachineControllerBlockEntityTest.java:2808`.
- `git diff --check`
  - Passed.

## Commit

- `d79d73e` `fix factory controller capacity aggregation`

## Concerns

- No known concerns remain for the two reviewer findings; both focused test classes pass after the fixture and expectation updates.

## Reviewer Fixes

- Updated `MachineControllerBlockEntityTest.factoryController` to configure the current `FactoryRecipeScheduler(4)` directly without reflecting the deleted `FactorySchedulerBlockEntity.threadLimit` field.
- Updated the factory aggregation assertion to compare against `first.threadCount() + second.threadCount()`.
- Updated the remaining legacy first-controller-only aggregation test and the delayed-polling expectation to match Task 1 semantics. The test `ServerLevel` stub now returns an empty player list for immediate menu synchronization.

## Reviewer-Fix Verification

- Command: `./gradlew test --no-daemon --tests '*FactorySchedulerBlockEntityTest'`
  - Exact result: `BUILD SUCCESSFUL in 12s`; `18 actionable tasks: 2 executed, 16 up-to-date`.
- Command: `./gradlew test --no-daemon --tests '*MachineControllerBlockEntityTest'`
  - Exact result: `BUILD SUCCESSFUL in 11s`; `18 actionable tasks: 1 executed, 17 up-to-date`.

Reviewer-fix commit: `cef35f2` (`test update factory capacity fixtures`).
