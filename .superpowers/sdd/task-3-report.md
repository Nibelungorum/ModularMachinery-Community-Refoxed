# Task 3 Report

## Status

Completed and committed as `f6de162` (`feat: claim multiblock components during formation`).

## Changes

- Added server-level `StructureClaimRegistry.get(ServerLevel)` lookup and controller `resourceDomain()` API.
- Claim stateful structure participants after port validation and before formation state mutation. Claim conflicts leave the controller unformed and retain a diagnostic containing `component_claim_conflict`, component position, and owner position.
- Cache the formed controller's resource domain, release claims during reset and server-side removal, and preserve claims during chunk-unloaded pauses.
- Replaced broad port appearance resets with per-controller linking and unlinking so shared ports retain other controller links.
- Added conflict and shared-port reset coverage. Updated Unsafe-created port fixtures to initialize Task 2's owner-link map.

## TDD Evidence

1. Added `exclusive_component_claim_prevents_second_controller_from_forming` and `shared_port_remains_linked_when_one_of_its_controllers_resets` before production lifecycle changes.
2. RED command: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon` failed initially. The initial failure exposed the pre-existing Unsafe fixture omission for Task 2's `linkedControllers` state; after owner-specific unlinking replaced broad resets, the remaining fixture failures were corrected in the test-only constructors.
3. GREEN commands passed after the implementation and fixture initialization.

## Test Results

- `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`: PASS, 66 tests.
- `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --tests cn.howxu.mmcr.internal.tile.MachinePortAppearanceTest --no-daemon`: PASS.

## Self Review

- `tryFormMachine` claims only after all pattern and port validations, before `onStructureFormed` mutates formation fields.
- Claim/release only run against `ServerLevel`; client and LevelStub paths do not create registries.
- Chunk-unload pause continues to only pause work and does not release claims.
- Reset and `setRemoved` release claims and unlink only this controller's appearance link.
- `git diff --check` passed before commit.
- Only the three Task 3 source/test files were staged. Existing `.superpowers/sdd/task-1-report.md` worktree changes remain unstaged and untouched.

## Important Review Follow-up

- Replaced the direct-registry and manually linked controller tests with real two-controller formation scenarios in `MachineControllerBlockEntityTest` using a minimal `ServerLevel` fixture, because claims and resource domains are intentionally server-only.
- Exclusive coverage forms the first controller around a real parallel controller, verifies the second formation fails with `component_claim_conflict`, and checks that the failed controller remains unformed with no components while the registry retains only the first owner.
- Shared-port coverage forms two controllers around the same real item input bus, verifies merged resource-domain membership and both owners, then verifies reset retains the second controller/link and reduces ownership/domain correctly; `setRemoved` releases the remaining owner and clears its cached domain.
- No chunk-unload case was added: the existing unit-test harness cannot model a deterministic server chunk lifecycle, so a synthetic test would not validate production behavior.

## Review Follow-up Test Result

- `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`: PASS, 66 tests, 0 failures (fresh run; existing deprecation and Unsafe runtime warnings only).
