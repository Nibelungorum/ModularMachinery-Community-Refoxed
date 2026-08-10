# Task 3 Report

## Status

Complete.

## Changes

- Added `ActiveMachineRecipe.InputConsumptionPlan`, including NBT and `ValueInput`/`ValueOutput` persistence.
- Generate probability decisions once at recipe start after parallel selection; `0` and `1` chances do not consume random values.
- Apply the persisted plan during input simulation, revalidation, and extraction. Keep inputs are matched and revalidated but never extracted.
- Preserve keep-input quantity while scaling parallel recipes, and retain conservative simulations for all consumable probability inputs.
- Keep item/tag matching as the first-stage `Ingredient` prefilter before component-predicate matching.

## Verification

- `./gradlew compileJava --no-daemon` passed.

## Notes

- Per instruction, no tests were created or run.
- Existing NeoForge deprecation warnings remain unchanged.
