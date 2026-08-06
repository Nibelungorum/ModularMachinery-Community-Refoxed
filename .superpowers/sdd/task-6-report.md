# Task 6 Report: KubeJS Schema and PreparedRecipe Pass-through

Status: DONE

## Changes

- Extended `prepared_recipe_converts_to_machine_recipe` to assert `PreparedRecipe.toMachineRecipe()` preserves the raw modifier list via `recipe.modifiers().equals(prepared.getModifiers())`.
- Changed that PreparedRecipe smoke case to use a duration multiplier modifier so `recipe.getRecipeTotalTickTime()` proves runtime-derived duration is 50 while raw `tickTime` remains 50.
- Added the `modifiers` KubeJS schema key as a raw JSON list passthrough and included it in the machine recipe schema constructor.

## MachineRecipeSchema.java

Changed: yes.

Reason: `Plugin.registerRecipeSchemas()` registers `MachineRecipeSchema` and `Plugin.registerRecipeFactories()` registers `MachineRecipeFactory.INSTANCE`, so the current KubeJS authoring path exposes machine recipes through this schema/factory registration. `MachineRecipeFactory` uses the generic `KubeRecipe` factory and does not add custom builder decoding logic, so adding the raw `modifiers` schema key is the minimal pass-through needed for recipe scripts without inventing fake builder behavior.

## PreparedRecipe.java

Changed: no.

Reason: `PreparedRecipe.toMachineRecipe()` already passes `modifiers` and `fluidOutputs` into the full `MachineRecipe` constructor, so only test coverage was needed.

## Verification

Command: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeApiSmokeTest --no-daemon`

Result: PASS (`BUILD SUCCESSFUL in 13s`, 17 actionable tasks: 3 executed, 14 up-to-date).

## Concerns

- CodeGraph was indexed from the main worktree rather than this linked worktree, so current worktree files were confirmed with direct reads before editing.
