# Task 2 Report

## Status

Implemented Task 2: component-aware public item inputs and outputs.

## Changes

- Switched public `ItemInput` and `ItemRequirement` component fields to the Task 1 public DTO.
- Preserved the pre-existing core component input overload and converted its predicates into public DTOs at the builder boundary.
- Added component-bearing ordinary and chanced output overloads.
- Added public component storage to `ItemOutput` and propagated it through public `ItemRequirement`.
- Rejected output component sets containing any non-exact predicate.
- Added recursive public-to-core component conversion in `PublicRecipeAdapter` for exact, map, list, range, and text predicates.
- Retained and completed the pre-existing output component tests.

## Verification

- RED: `./gradlew test --tests cn.howxu.mmcr.api.publicapi.PublicRecipeBuilderTest --no-daemon` failed at test compilation for the missing public DTO overloads and output overloads.
- Focused GREEN: `./gradlew test --tests cn.howxu.mmcr.api.publicapi.PublicRecipeBuilderTest --no-daemon` passed, 9 tests completed.
- Full test: `./gradlew test --no-daemon` failed in two unrelated existing `PublicEventSubscribersTest` cases: `builtin_level_subscriber_does_not_duplicate_an_existing_type` and `builtin_level_subscriber_skips_development_levels_in_production`. The run completed 1128 tests with 2 failures.
- GameTest: `./gradlew runGameTestServer --no-daemon` passed.
- `git diff --check` passed.

## Concerns

- The full test suite remains red because of the unrelated builtin level subscriber failures in the shared worktree. Those files were not modified.
- The compatibility input overload converts existing core predicates to the public DTO before the adapter converts them back to core predicates; this is retained only to preserve existing callers while exposing the public DTO API.

## Review Fix

- Restored `MachineRecipeBuilder` behavior that uses explicit requirements exclusively when any are provided; automatic requirements remain the fallback when none are explicit.
- Kept the valid public component DTO, output component, and adapter work unchanged.
- Strengthened `retains_explicit_requirements_smart_interface_level_host_and_modifier_without_deriving_duplicates` with an item input so the regression is observable.

## Review Fix Verification

- RED: `./gradlew test --tests cn.howxu.mmcr.api.publicapi.PublicRecipeBuilderTest --no-daemon` failed with 1 test failure in `retains_explicit_requirements_smart_interface_level_host_and_modifier_without_deriving_duplicates` before the behavior fix.
- GREEN: `./gradlew test --tests cn.howxu.mmcr.api.publicapi.PublicRecipeBuilderTest --no-daemon` passed; build successful, 9 tests completed.
