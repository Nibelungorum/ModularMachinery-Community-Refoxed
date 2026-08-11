# Default Machine Data Component Recipe Tests

## Goal

Add Java API integration tests under `src/test` that execute component-aware item recipes on every currently registered default machine:

- `blast_furnace`
- `alloy_furnace`
- `cracker`
- `reactor`
- `thermal_smelting_furnace`

The tests validate matching, input consumption, non-consumption, and component-preserving item output insertion through the normal recipe crafting path.

## Test Organization

Create one parameterized integration test class alongside the existing recipe crafting tests. A machine case supplies its default machine ID and the existing fixture needed to form and run that machine. A scenario supplies the Java API recipe ingredients, initial input stacks, expected remaining inputs, and expected output stacks.

Each machine executes every scenario. The test names include both the machine ID and scenario name to make failures directly identifiable.

## Scenarios

1. Chanced input consumption: an item input with a component predicate and a deterministic consuming roll is removed.
2. Non-consumable input: an item input with a component predicate and zero consume chance remains unchanged.
3. Component input to plain output: a matching component-bearing input is consumed and produces an output without that component.
4. Plain input to component output: a plain input is consumed and output contains the exact configured component value.
5. Component input to component output: both matching and output component serialization are verified independently.
6. Mixed component inputs: a recipe requires one component-bearing input and one plain input; each must match its intended requirement and both are consumed.
7. Mixed component outputs: a recipe emits one component-bearing stack and one plain stack, preserving the component only on the configured output.

The chance scenario uses a deterministic source available to the existing test fixture. No assertion depends on an uncontrolled random roll.

## Recipe Construction

Every scenario constructs `MachineRecipe` and `MachineIngredient.ItemIngredient` directly in Java. Input component matching uses `DataComponentPredicateSet` and exact predicates. Output stacks set their native `DataComponentMap` values before recipe construction.

The test registers each generated recipe with a unique ID, avoiding collisions with default recipes. It does not use KubeJS, JSON decoding, or JEI-only display paths.

## Execution And Assertions

For each machine/scenario pair, the test:

1. Forms the standard machine using the existing default-machine test fixture.
2. Installs the scenario input stacks into its item input bus and provides the normal energy/fluid requirements needed by the fixture.
3. Runs the controller through recipe start and completion using the production crafting path.
4. Asserts the selected recipe started, input bus contents equal the expected remaining stacks, and output bus contents equal the expected outputs.
5. Asserts component presence and value explicitly, as well as absence on plain expected stacks.

Thermal smelting uses an existing valid default-level fixture so the test verifies recipe behavior rather than failing level gating.

## Boundaries

- This adds tests only. Production recipe, component, random, and machine code remain unchanged unless a test reveals a defect.
- The suite exercises default machines but does not modify `DefaultRecipes`; generated recipes are test-local Java API recipes.
- Component codec and KubeJS builder coverage remain in their current focused unit tests.

## Verification

Run the new test class directly, then run `./gradlew test --no-daemon`. The full suite must pass.
