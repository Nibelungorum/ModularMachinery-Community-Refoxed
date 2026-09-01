# Task 15 Report

## Implemented

- Added registry-independent resource, scalar, exchange, and network participant facet fixtures under `cn.howxu.mmcr.test.capability`.
- Added reusable capability contract assertions and `CapabilityFacetContractTest` for identity matching, transactional simulation/commit/rollback, scalar direction and reverse extraction, signed exchange capacity failures with rollback, facet discovery, and topology snapshot behavior.
- Added `CustomRecipeContractTest` for test-only custom requirement/output registration through `RecipeApi.custom`, canonical codecs, handler dispatch, and public-to-internal recipe adaptation.
- Existing `CapabilityTickContractTest` already covers fixed phase ordering, snapshot order, rollback, and idle behavior.
- Existing `CapabilityPersistenceTest` covers persistence and sync round trips without a Minecraft world, so no GameTest conversion was required.

## Verification

- Static review confirmed that recipe IO direction uses the current `RecipeIo.isInput()` API; no `util.IOType.isInput()` call remains in `CustomRecipeContractTest`.
- Static review confirmed all four capability fixtures are discovered through `MachineCapability.facet()` and `CapabilitySnapshot.facets()`, and network snapshots change membership across attach/detach.
- Ran `git diff --check` successfully before the report update.
- Not run, per task instruction: Gradle compilation, `gradle test --no-daemon`, the Task15 contract test selection, `gradle runGameTestServer --no-daemon`, and all other unit/GameTests.
- Contract test/fixture review commit: `080f3a4b` (`test: tighten Task15 capability contracts`).

## Scope

- No production code, wiki files, Task16+ work, or final-issue cleanup was changed.
