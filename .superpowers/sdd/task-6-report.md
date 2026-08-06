# Task 6 Report: 合并 recipe-local 与 structure modifiers

## Summary

- Added runtime modifier overloads to `MachineRecipe` for requirements and outputs, preserving raw recipe requirements/modifiers.
- Added `RecipeCraftingContext` structure modifier snapshot APIs and routed simulate/commit/ioTick through runtime requirements with the snapshot.
- Made `ActiveMachineRecipe.start(context)` refresh duration from the same context-effective modifier list used by runtime I/O.
- Injected `MachineControllerBlockEntity.foundModifierList()` snapshots through `RecipeCraftingContextPool.borrow`.
- Added lightweight `modifierSnapshotVersion` on the controller so active contexts invalidate when found modifiers change without a structure version change.
- Refreshed replacement contexts in active ticking with the current structure modifier snapshot.

## RED Evidence

Command:

```bash
./gradlew test --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon
```

Result: `BUILD FAILED` during compilation because `MachineRecipe.runtimeRequirements(List<RecipeModifier>)`, `RecipeCraftingContext.structureModifiers()`, and `RecipeCraftingContext.effectiveModifiers(MachineRecipe)` did not exist.

## GREEN Evidence

Command:

```bash
./gradlew test --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.api.recipe.RecipeSearchTaskTest --no-daemon
```

Result: `BUILD SUCCESSFUL` with 49 focused tests completed.

## Notes

- The brief's first new test expected count `6` for base `2`, recipe modifier `MULTIPLY 1`, and structure modifier `ADD 2`. Existing project semantics define modifier application as `(value + add) * mul`, so the correct runtime count under current semantics is `4`. I preserved existing `RecipeModifier.applyModifiers` behavior and adjusted only that assertion.
- `RecipeSearchTask.java` did not need direct code changes; it consumes the snapshot through `RecipeCraftingContextPool.borrow` as intended.
- No unrelated `org/nibelungorum`, related tests, or `TestBootstrap` changes were touched.

## P3B Task 6 Review Fix

Command:

```bash
./gradlew test --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.api.recipe.RecipeSearchTaskTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon
```

Result: `BUILD SUCCESSFUL` after adding regression coverage for modifier-only snapshot refresh preserving the active `RecipeCraftingContext` instance.
P3B Task 6 review fix - modifier-only active duration refresh
- Command: `./gradlew test --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.api.recipe.RecipeSearchTaskTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`
- Result: PASS (`BUILD SUCCESSFUL in 13s`, 83 tests completed)
