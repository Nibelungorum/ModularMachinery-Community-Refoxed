# Task 4 Report: Fair Resource-Domain Request Resolution

## Changes

- Added `SharedIoCoordinator`, a per-`ServerLevel` coordinator that queues start, tick, and finish requests; validates domain ID, domain generation, and request validator; resolves each request at most once per level tick; and preserves independent round-robin cursors.
- Start callbacks receive their maximum parallelism and commit only positive partial/full grants. Tick and finish callbacks commit only after their transaction reports success. Failed requests remain pending for the next level tick.
- Added `SharedIoEvents`, registering end-of-level server ticks and server-level unload cleanup in `MMCR`.
- Added `StructureClaimRegistry.discard(ServerLevel)` so level unload removes both level-scoped registries.
- Added only the brief-specified coordinator tests for partial start grants, rotation, and stale generations.

## TDD Failure Evidence

The RED command was run before `SharedIoCoordinator` existed:

```text
./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --no-daemon
```

It failed at `:compileTestJava` with 11 expected errors, including:

```text
SharedIoCoordinatorTest.java:20: error: cannot find symbol
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        ^
  symbol:   class SharedIoCoordinator
```

The full RED output is retained at `/home/howxu/.local/share/rtk/tee/1786323496_gradlew_test.log`.

## Verification

Command:

```text
./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --no-daemon
```

Complete final output:

```text
BUILD SUCCESSFUL in 7s
17 actionable tasks: 17 up-to-date
```

## Follow-up Test Coverage

- Added a two-pass re-enqueue assertion proving start scheduling resumes after the last successful lane.
- Added an assertion proving start, tick, and finish retain independent round-robin cursors.

## Commit

`aa14cab feat: schedule shared multiblock IO fairly`

## Self-Check

- Start, tick, and finish use separate cursor maps.
- Current domain ID/generation and request validity are checked before callbacks.
- Each request is attempted no more than once during a resolver pass.
- Cursors advance only after a successful grant/transaction.
- Insufficient or failed requests remain queued until a later level tick.
- Callbacks execute only from `LevelTickEvent.Post`; no worker or block-entity tick invokes them.
- No Task 5 recipe integration was added.
- `git show --check HEAD` reported no whitespace errors.

## Concerns

- CodeGraph is indexed only for the parent worktree, not this worktree. Its event API context was used alongside direct worktree reads; source-specific checks were performed against this worktree.
- The targeted Gradle test task emits pre-existing deprecation warnings when it recompiles the full test source set; the final cached run had no warning output.
