# Task 5 Report

## Result

Task 5 replaces the closed requirement codec and planner dispatch with an open
`RequirementType` and `RequirementHandler` registry contract. Built-in item,
fluid, energy, and smart-interface requirements now own their codecs and
handlers through `RequirementType.Definition`. Custom requirements can register
the same contract and are copied, encoded, decoded, planned, and committed
without changes to built-in planning code.

## Changes

- Added `CustomRequirement` as the public extension marker.
- Changed `MachineRequirement.CODEC` and custom copying to use registered type
  codecs rather than built-in type dispatch.
- Changed `RequirementType` to own its ID, `MapCodec`, handler, and presentation
  metadata.
- Changed `RequirementHandlerRegistry` to register and resolve types, with
  lazy built-in registration so custom tests do not load Minecraft registries
  before bootstrap.
- Kept planner reservation, parallelism, output simulation, and operation
  materialization behavior while routing planning through registered handlers.
- Replaced Factory energy failure matching with requirement type matching.
- Updated requirement, planner, capability-operation, and runtime test fixtures
  for the new type-owned handler contract.

## Verification

- `gradle test --no-daemon --tests 'cn.howxu.mmcr.api.recipe.requirement.CustomRequirementTest' --tests 'cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistryTest'`: passed.
- `gradle test --no-daemon --tests 'cn.howxu.mmcr.internal.recipe.RequirementPlannerTest'`: passed.
- `gradle test --no-daemon`: passed, 1601 tests completed, 1 skipped.
- `gradle runGameTestServer --no-daemon`: passed, all 62 required GameTests passed.

Known output consists of existing deprecation warnings and environment/mod
warnings; no test failures remain.

## Review Fix

- Moved built-in planning and failure wakeup logic into dedicated requirement
  handlers while keeping shared reservation mechanics in `RequirementHandlerSupport`.
- Made the registry resolve handlers only for the canonical type instance. A
  different type implementation with the same stable identifier is now rejected
  by handler lookup, codec encoding, and copying.
- Added tests for built-in preservation across test scopes and for rejecting
  equal-identifier substitute types.

The first affected-test run during this fix failed with 24 failures because the
default codec-based requirement copy used `JsonOps` for Minecraft item and
ingredient data. Built-in type-owned copiers were added for the affected
requirements; the affected suite then passed.

## Fix Verification

- `gradle test --no-daemon --tests 'cn.howxu.mmcr.api.recipe.requirement.CustomRequirementTest' --tests 'cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistryTest'`: passed.
- `gradle test --no-daemon --tests 'cn.howxu.mmcr.internal.recipe.RequirementPlannerTest'`: passed.
- `gradle test --no-daemon --tests 'cn.howxu.mmcr.internal.recipe.FactoryRecipeThreadTest'`: passed.
- Affected requirement, planner, factory, capability, and crafting-runtime test set: passed.
- Final registry focused test after review fix: passed.
- `gradle test --no-daemon`: passed.
- `gradle runGameTestServer --no-daemon`: passed, all 62 required GameTests passed.

Final output still contains existing deprecation, restricted-access, dependency,
and `spark` shutdown `CancellationException` warnings; no test failures remain.

## Renderer Review Fix

- Reviewed commit: `a347a7d3`.
- Reversed all six selection-mask quad windings so the cull-enabled exterior is visible.
- `gradle compileJava --no-daemon`: passed.
