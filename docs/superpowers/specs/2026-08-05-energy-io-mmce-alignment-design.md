# Energy IO MMCE Alignment Design

## Goal

Align the current controller energy tick behavior with MMCE's core `RequirementEnergy` semantics without rebuilding the whole recipe requirement system.

The selected approach is to introduce an internal energy IO helper used by `RecipeCraftingContext`, so energy behavior is centralized and can later grow toward MMCE parallelism and modifier support.

## Current Behavior

- `MachineControllerBlockEntity.serverTick()` advances the active recipe once per server tick when the machine is formed.
- `RecipeCraftingContext.ioTick()` handles energy inputs inline.
- Current energy input handling finds one `EnergyInputHatchBlockEntity` with at least `fePerTick` stored, then immediately calls `extractEnergy(fePerTick, false)`.
- This does not aggregate multiple hatches and does not perform a full simulate-before-commit pass like MMCE.

## Target Behavior

- Energy input is evaluated across all live energy input hatches in the formed structure.
- The total required energy for the tick must be available before any hatch is mutated.
- If total available energy is insufficient, the tick fails and all hatch energy remains unchanged.
- If total energy is sufficient, energy is extracted from hatches in controller component order until the tick requirement is satisfied.
- Existing recipe state-machine behavior remains unchanged; this work only changes energy IO semantics.

## Architecture

Add a focused internal helper, tentatively `EnergyRecipeIo`, under the recipe or internal IO package.

Responsibilities:

- Accept an ordered list of energy hatches, an IO direction, and required FE for the current tick.
- Simulate aggregate capacity/availability first.
- Commit only after simulation succeeds.
- Keep current `RecipeCraftingContext` responsible for discovering live hatches from controller components.

`RecipeCraftingContext.ioTick()` becomes a thin orchestration layer:

- For each `MachineIngredient.EnergyIngredient`, collect live input hatches.
- Ask the helper to consume the required FE.
- Return false if the helper reports failure.

## MMCE Parity Scope

This step intentionally matches MMCE's important energy IO behavior, not its full requirement abstraction:

- Included: multi-hatch aggregation, simulate-before-commit, no partial mutation on failure.
- Included: helper API shaped to accept an effective multiplier later.
- Deferred: full `ComponentRequirement` hierarchy.
- Deferred: general recipe parallelism implementation if not already present.
- Deferred: energy output requirements unless the current recipe model already exposes output energy ingredients.

## Error Handling

- Energy shortage returns failure from `ioTick()` and lets the existing controller recipe failure path decide whether to pause, retry, or cancel.
- The helper must not throw for normal shortage conditions.
- Null or empty hatch lists are treated as insufficient energy.

## Tests

Add focused coverage around the helper and/or context behavior:

- Single input hatch with enough FE consumes exactly `fePerTick`.
- Multiple input hatches whose combined FE is enough consume across hatches and succeed.
- Multiple hatches whose combined FE is insufficient fail with no energy changed.
- A hatch transfer limit lower than the requirement does not cause partial mutation when aggregate commit cannot satisfy the tick.

## Out Of Scope

- Rewriting item or fluid IO around MMCE requirements.
- Changing recipe codec shape unless needed by existing energy ingredient support.
- Changing controller tick scheduling.
- Adding unrelated performance or logging changes.
