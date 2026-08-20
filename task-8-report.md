# Task 8 Report

## Completed

- Client preview compilation entries now include the effective content version in their cache key.
- Controller spec and machine appearance caches accept the synchronized `contentVersion`; legacy local replacement calls retain their existing behavior.
- Runtime client snapshot application passes the payload version into both client caches.
- Controller blocks, tooltips, controller block entities, and controller/spec synchronization use explicit effective machine snapshots.
- Controller recipe candidate invalidation now follows `RuntimeContentVersion` instead of the recipe-only registry counter.
- Added focused cache invalidation coverage for version changes.

## Changes

- `StructurePreviewCompilationCache` now uses the client server-applied `contentVersion` for default acquisition and `has(machineId)` queries.
- Applying a runtime snapshot records its version and clears preview compilations, so historical entries cannot be returned after a version change.
- Machine-controller preview and mismatch diagnosis now iterate all effective compiled candidate stages instead of only stage 1.

## Regression Coverage

- Old-version entries are not reported by `has(machineId)`.
- The same applied version returns the same lazy compilation instance.
- Applying a new version rebuilds the compilation.
- A staged machine preview uses the highest effective complete stage.
- A staged machine preview falls back when the highest stage has no preview state.
- A staged machine preview falls back when the highest stage cannot form at its current positions.
- A staged machine preview selects a valid highest stage over lower stages.

## Verification

- `./gradlew compileJava --no-daemon`: passed.
- Focused tests: passed.
  - `./gradlew test --no-daemon --tests '*StructurePreviewCompilationCacheTest'`
  - `./gradlew test --no-daemon --tests '*MachineAppearanceCacheTest'`
  - `./gradlew test --no-daemon --tests '*ControllerSpecCacheTest'`
  - `./gradlew test --no-daemon --tests '*MachineControllerBlockEntityTest'`
- Full unit tests: failed in the existing global registration/test-isolation suite, 61 failures out of 1092 tests. Representative failures include duplicate machine levels, empty recipe snapshots, and startup registration state leakage.
  - `./gradlew test --no-daemon`
- GameTest server: failed during mod startup before tests ran because existing startup data contains a conflicting stage predicate at `-1, -1, 0`.
  - `./gradlew runGameTestServer --no-daemon`

No `docs` files, dependencies, or `runClient` were changed.

The focused Task 8 tests pass; the full-suite and GameTest failures are recorded without weakening or disabling tests.
