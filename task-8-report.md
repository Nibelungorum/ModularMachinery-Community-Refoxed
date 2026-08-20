# Task 8 Report

## Completed

- Client preview compilation entries now include the effective content version in their cache key.
- Controller spec and machine appearance caches accept the synchronized `contentVersion`; legacy local replacement calls retain their existing behavior.
- Runtime client snapshot application passes the payload version into both client caches.
- Controller blocks, tooltips, controller block entities, and controller/spec synchronization use explicit effective machine snapshots.
- Controller recipe candidate invalidation now follows `RuntimeContentVersion` instead of the recipe-only registry counter.
- Added focused cache invalidation coverage for version changes.

## Verification

- `./gradlew compileJava compileTestJava --no-daemon`: passed.
- `./gradlew test --no-daemon --tests '*StructurePreviewCompilationCacheTest' --tests '*MachineAppearanceCacheTest' --tests '*ControllerSpecCacheTest' --tests '*MachineControllerBlockEntityTest'`: passed.
- `./gradlew test --no-daemon`: failed with 57 failures across existing bootstrap/lifecycle-sensitive tests, including `PluginBindingTest`, `RuntimeContentSnapshotTest`, and `MachineControllerBlockEntityTest`.
- `./gradlew runGameTestServer --no-daemon`: failed during mod loading before GameTests ran because `stage 2 has conflicting predicate at -1, -1, 0`.

The focused Task 8 tests pass; the full-suite and GameTest failures are recorded without weakening or disabling tests.
