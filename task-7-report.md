# Task 7 Report

## Completed

- Runtime snapshots now use effective structure and recipe registries.
- Added one monotonic runtime content version shared by structure and recipe mutations.
- Renamed the payload version field to `contentVersion` and added non-negative validation.
- Client application now replaces the complete structure/recipe snapshot, rejects older versions before mutation, and refreshes client caches after installation.
- Preserved bounded structure, recipe, and payload codec checks.
- Hardened controller discovery against unbound registry holders during test/common bootstrap.
- Added effective-layer and stale-snapshot coverage.

## Verification

- `./gradlew compileJava compileTestJava --no-daemon`: passed.
- `./gradlew test --no-daemon --tests '*RuntimeContentSyncTest' --tests '*JeiRuntimeReloaderTest'`: passed.
- Full focused command including `RuntimeContentSnapshotTest`: blocked by existing test bootstrap failure before test bodies run.

## Known Failure

`RuntimeContentSnapshotTest` fails in `TestBootstrap.registerRuntimeBuiltins()` because the dynamic bootstrap attempts to validate `mmcr:purpur_furnace` without a corresponding startup machine registration. This is a pre-test lifecycle failure, not a snapshot assertion failure.

## Static Review Fixes

- Client snapshot validation and cache preparation now complete before any structure or recipe replacement; invalid snapshots leave the previous client state unchanged.
- Stale snapshots return without applying and therefore do not trigger JEI reload.
- Successful application clears structure preview compilations and runtime machine model definitions; controller and appearance cache listeners invalidate dependent controller models.
- Server snapshot creation retries when the shared content version changes while collecting registries, preventing cross-version map composition.
- Client-only replacement no longer advances the shared runtime content version.
- Payload decode now rejects duplicate map keys, structure/recipe/controller key and ID mismatches, invalid stack counts, negative versions, and oversized tooltip lists.

## Additional Verification

- `./gradlew compileJava compileTestJava --no-daemon`: passed.
- `./gradlew test --no-daemon --tests '*RuntimeContentSnapshotTest.invalidSnapshotDoesNotPartiallyReplaceClientContent' --tests '*RuntimeContentSnapshotTest.applyingClientSnapshotDoesNotAdvanceServerContentVersion' --tests '*RuntimeContentSyncTest' --tests '*JeiRuntimeReloaderTest'`: passed.
- Duplicate-key focused tests remain blocked by the same pre-test bootstrap registration failure described above.
