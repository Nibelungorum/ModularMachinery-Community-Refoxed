# Task 3 Report: Default Machine Recipe Coverage

## Scope

- Modified `src/main/java/org/nibelungorum/DefaultRecipes.java`.
- Modified `src/test/java/org/nibelungorum/DefaultRecipesTest.java`.
- Modified `src/test/java/cn/howxu/mmcr/BuiltinRecipeBootstrapTest.java`.
- Did not modify, restore, or rewrite `.gitignore`.

## Implementation

- Replaced the four ad hoc registrations with declarative `Definition` entries.
- Registered ten definitions for each of `blast_furnace`, `alloy_furnace`, `cracker`, and `reactor`.
- `ensureRegistered()` checks `RecipeRegistry.getRecipe(id) == null` before every registration, preserving externally registered recipes and repeat-call idempotence.
- All recipes use vanilla items and fluids; registrations use priority `0`, max threads `1`, and cancel-on-per-tick-failure `true`.
- Preserved the existing semantics of `alloy_furnace_netherite`, `cracker_coal_lapis`, and `reactor_diamond_water` as required by the legacy callers and tests.

## Compatibility Note

The brief simultaneously assigns each legacy ID to the `iron_to_nugget` scenario and requires its pre-existing semantics to remain unchanged. These requirements conflict for the three legacy IDs. This implementation gives precedence to preserving their established semantics while retaining exactly ten recipes per machine; the remaining nine entries per machine follow the shared scenario matrix.

## Test-First Evidence

1. Added recipe-count and coverage assertions.
2. Ran `rtk gradlew test --tests org.nibelungorum.DefaultRecipesTest --no-daemon` before implementation.
3. Result: `BUILD FAILED`, 6 tests completed, 2 failures at the new ten-recipe assertions in `ensureRegistered_publishes_builtin_blast_furnace_iron_to_nugget_recipe` and `ensureRegistered_is_idempotent`.

## Final Verification

| Command | Result |
| --- | --- |
| `rtk gradlew test --tests org.nibelungorum.DefaultRecipesTest --tests cn.howxu.mmcr.BuiltinRecipeBootstrapTest --no-daemon` | `BUILD SUCCESSFUL`; 7 tests completed, 0 failures. |
| `rtk gradlew compileJava --no-daemon` | `BUILD SUCCESSFUL`. |
| `rtk git diff --check` | Exit 0; no whitespace errors. |

## Worktree Note

`reference/gtceu/` was already an untracked, unrelated directory. It is excluded from the task commit.
