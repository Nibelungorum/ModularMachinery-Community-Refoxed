# Task 5 Report

## Implementation

- Added `LevelInsufficientFailure`, carried by `RecipeSearchResult` and exposed by `MachineControllerBlockEntity.getRecipeFailure()`.
- `RecipeSearchTask` now checks levels after a candidate has compatible inputs. An insufficient first candidate stops the priority scan instead of allowing fallback recipes.
- The controller maps this result to the existing failure key `gui.mmcr.controller.failure.level_insufficient` and preserves the structured failure for synchronized state consumers.
- Level modifiers are collected in stable level-type identifier order. Duration, energy, and outputs apply level multipliers before existing ordinary modifiers; item and fluid output multiplication floors and retains one unit when the unmodified output was positive.
- Parallel and factory-thread bonuses are applied through their existing controller caps. Ordinary modifier collection, replacement, and stack order were not changed.

## Tests

- `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeSearchTaskTest --tests cn.howxu.mmcr.api.recipe.ActiveMachineRecipeTest --no-daemon`
- `./gradlew compileJava --no-daemon`

Both commands passed.

## Scope Note

- Focused tests were added to the existing recipe-search and active-recipe test classes rather than creating the brief's suggested files, because those classes already own the directly testable paths.
