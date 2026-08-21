# Task 1 Report: Public Recipe Component Declarations

## Scope

Created standalone public declaration DTOs under `cn.howxu.mmcr.api.publicapi.recipe.component` without changing the canonical runtime model in `cn.howxu.mmcr.api.recipe.component`.

## Implementation

- Added sealed `ComponentPredicate` declarations for exact JSON, map, list, range, and text forms.
- Exact JSON values are deep-copied at construction and when exposed, preventing callers from mutating the declaration through a retained or returned `JsonElement`.
- Map and list forms defensively copy into unmodifiable collections.
- Added `DataComponentPredicateSet`, keyed by `Identifier`, with an immutable map and `hasNonExactValues()` exact-form detection.
- No adapter conversion was added; that remains Task 2 work.

## Tests

- Added focused tests for structural equality of all declaration forms.
- Added tests proving exact JSON, maps, lists, and component maps cannot be mutated through source collections or exposed values.
- Added tests for predicate and set exact-form detection.

## Verification

- `./gradlew compileJava --no-daemon`: passed.
- `./gradlew test --tests cn.howxu.mmcr.api.publicapi.recipe.component.ComponentPredicateTest --no-daemon`: blocked during `compileTestJava` by the pre-existing user change in `src/test/java/cn/howxu/mmcr/api/publicapi/PublicRecipeBuilderTest.java:172`, where `outputItem(new ItemStack(...), components())` has no matching overload.
- `./gradlew test --no-daemon`: blocked by the same unrelated `compileTestJava` error.
- `./gradlew runGameTestServer --no-daemon`: passed.

## Review

- Reviewed the task-file diff and ran `git diff --check`; no whitespace errors were reported.
- The report is intentionally not staged because `.superpowers/` files must not be committed.

## Commit

- `444d58c feat: add public component declarations`
