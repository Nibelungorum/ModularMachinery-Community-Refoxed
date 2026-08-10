# Task 2 Report

## Changes

- Extended `MachineIngredient.ItemIngredient` with data component predicates and input-only `consumeChance`, retaining the two-argument constructor's empty-predicate, certain-consumption behavior.
- Added JSON codecs for data component predicate maps, keyed by registered data component identifiers. Item inputs emit `components` only when non-empty and `consume_chance` only when it differs from `1`.
- Propagated both fields through `MachineRequirement`, `ItemRequirement`, runtime modifiers, parallel scaling, and `MachineRecipe#inputs`.
- Applied component predicates while locating input item stacks. Input consumption is probabilistically skipped before routing; output `chance` remains separate.
- Made `ItemRequirement#maxInputParallelism` return its upper limit for zero consumption chance; component-constrained and port-tagged inputs retain the existing conservative calculation.

## Self-Review

- Legacy constructors, legacy JSON, and all default values retain empty component predicates and `consumeChance == 1F`.
- Derived display stacks are not serialized as recipe input data; the authoritative JSON representation remains `Ingredient` plus predicates.
- Item requirement reconstructions during modifier application and parallel scaling retain component predicates and consumption chance.
- Component conditions now affect actual input matching instead of only codec round-trips.

## Verification

Command:

```bash
./gradlew compileJava --no-daemon
```

Output summary:

```text
> Task :compileJava
BUILD SUCCESSFUL in 16s
14 actionable tasks: 1 executed, 13 up-to-date
```

No tests were added or run, as explicitly requested. The task brief listed focused codec and recipe tests, but the user instruction overrides it with compile-only verification.

## Commit

`f935a11e2103c675395ae09945b9a2dc6c2cd8f7` (`feat: enrich recipe item inputs`)

## Concerns

- The required JSON round-trip and runtime probability behavior are compile-verified only, because no tests were requested.
- The project reports 84 pre-existing removal/deprecation warnings during compilation; this task does not add warnings.
