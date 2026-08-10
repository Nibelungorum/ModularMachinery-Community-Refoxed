# Final Review Fix Report

## Changes

- `InputConsumptionPlan` persists one consumed-batch count per requirement. Input chance now rolls once per parallel batch, skips RNG at chances zero and one, and scales input routes/extraction from the persisted count. Legacy boolean-plan NBT is read as zero-or-one consumed batch for existing saves.
- `ComponentPredicate.ListValue` now backtracks candidate assignments, preserving the no-reuse rule while accepting feasible non-greedy matches.
- `MachineIngredient` and `MachineRequirement` now default `components` only when that field is absent. Invalid present component JSON remains a `DataResult` error.
- Added regression tests for persisted consumed batch counts and extraction, the `[range(1,2), exact(1)]` list counterexample against `[1,2]`, and invalid component JSON errors for both codecs.
- Removed only the requested tracked reports: `task-1-report.md`, `task-2-report.md`, `task-3-report.md`, and `task-5-report.md`.

## RED Evidence

Command:

```bash
rtk gradlew test --no-daemon --tests cn.howxu.mmcr.api.recipe.ActiveMachineRecipeTest --tests cn.howxu.mmcr.api.recipe.MachineIngredientCodecTest --tests cn.howxu.mmcr.api.recipe.component.ComponentPredicateTest
```

Result: `BUILD FAILED` at `:compileTestJava`.

- New batch-plan test failed as intended before implementation: `InputConsumptionPlan` accepted `List<Integer>`, with no `serialize()` or `consumedBatches(int)` API.
- The same pre-fix command was also blocked by existing unrelated test source: `MachineRecipeDisplayTest.java:58` references missing `MachineRecipeDisplay#itemInputCounts()`.

Full output: `/home/howxu/.local/share/rtk/tee/1786325645_gradlew_test.log`.

## GREEN Verification

Command:

```bash
rtk gradlew compileJava --no-daemon
```

Result: `BUILD SUCCESSFUL in 12s`; 14 actionable tasks, 1 executed and 13 up-to-date. The build emits the pre-existing 84 removal/unchecked warnings.

Command:

```bash
rtk git diff --check
```

Result: exit 0, no whitespace errors.

The focused test command cannot reach JUnit after the repair because Gradle compiles all test sources and the unrelated `MachineRecipeDisplayTest#itemInputCounts()` compile error remains. It was not changed to honor the no-unrelated-code constraint.

## Commands Run

```text
rtk git status --short && rtk git log --oneline -10 && git ls-files ...
rtk gradlew test --no-daemon --tests cn.howxu.mmcr.api.recipe.ActiveMachineRecipeTest --tests cn.howxu.mmcr.api.recipe.MachineIngredientCodecTest --tests cn.howxu.mmcr.api.recipe.component.ComponentPredicateTest
rtk gradlew compileJava --no-daemon
rtk gradlew compileJava --no-daemon
rtk git diff --check
```

## Concerns

- Focused JUnit GREEN evidence is unavailable until the pre-existing `MachineRecipeDisplayTest.java:58` compile error is resolved by its owning work.
- No untracked or uncommitted collaborator reports in `.superpowers/sdd` were deleted.
