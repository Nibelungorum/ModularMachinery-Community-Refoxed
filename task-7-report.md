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

## Follow-up Review Fixes

- Client runtime snapshot state now resets on both client login and logout, so a new server may start at any content version.
- Server snapshot creation is owned by the synchronized runtime content coordinator, which provides one immutable registry read boundary.
- Recipe requirement decoding now bounds item counts, fluid amounts, and energy rates and rejects negative or oversized values.
- Test bootstrap restores startup registrations when another test has cleared them while leaving the coordinator marked committed.
- Complete snapshot coverage now round-trips both a Level slot and a Modifier replacement.

## Follow-up Verification

- `./gradlew compileJava compileTestJava --no-daemon`: passed.
- `./gradlew test --no-daemon --tests '*RuntimeContentSnapshotTest' --tests '*RuntimeContentSyncTest' --tests '*JeiRuntimeReloaderTest'`: passed.

## Final Important Fixes

- Structure and recipe mutations and coordinator snapshot creation now share `RuntimeContentVersion.lock()`, making the effective snapshot read and commit transaction use the same lock boundary.
- FluidStack output amounts are bounded on both recipe codec encode and decode paths.
- Client connection reset now clears effective structures, recipes, controller and appearance caches, preview/model caches, and crafting context state before accepting the next server snapshot.
- Controller spec validation now checks the machine map key against the canonical controller spec ID for that machine.
- Startup commit, dynamic commit, data-pack replacement, and snapshot reads now share the same content lock; no snapshot can observe the interval between structure and recipe installation.

## Final Verification

- `./gradlew compileJava compileTestJava --no-daemon`: passed.
- `./gradlew test --no-daemon --tests '*RuntimeContentSnapshotTest' --tests '*RuntimeContentSyncTest' --tests '*JeiRuntimeReloaderTest'`: passed.
