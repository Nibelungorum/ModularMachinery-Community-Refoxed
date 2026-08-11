# Data Component Input Matching Design

## Goal

Make component-constrained item inputs use the component's own codec with the
server world's registry access. This must support all registered data component
types without special matching code for enchantments.

The built-in sharpness input example must require only a diamond sword with
Sharpness II. It must not require `minecraft:repair_cost`.

## Scope

- Remove `minecraft:repair_cost` from the built-in non-consumable sharpness
  input recipe.
- Pass the server world's `RegistryOps<JsonElement>` from
  `RecipeCraftingContext` to item-component matching.
- Parse exact component predicates using the relevant `DataComponentType` codec
  and compare the decoded value with the stack component value.
- Retain the existing codec-encoded structural predicate behavior for non-exact
  predicates and exact values that cannot be decoded.
- Remove enchantment-specific matching behavior.
- Keep direct, context-free callers functional by retaining the existing
  `JsonOps` fallback.

## Data Flow

1. A recipe stores each component condition as a `ComponentPredicate`.
2. `RecipeCraftingContext` obtains `RegistryOps` from its controller level.
3. `ItemInputState` passes these ops into `DataComponentPredicateSet.matches`.
4. For an exact predicate, the predicate JSON is converted to those ops and
   decoded through the component type's codec. A successfully decoded value is
   compared directly to the component read from the candidate `ItemStack`.
5. If decoding is unavailable, the candidate component is encoded through its
   codec and evaluated by the existing structural predicate code.

## Caching

The item-match cache remains scoped to one recipe search. The cache key already
contains the ingredient and a one-count copy of the input stack. Since one
search belongs to one controller level, the registry ops are constant during
that cache's lifetime and need not be added to the key.

## Diagnostics

On a failed recipe search, include non-empty input slots and their
`DataComponentPatch` values in the existing warning. This is temporary
diagnostic output retained until the regression is verified in-game; it does
not participate in matching.

## Tests

- A built-in Sharpness II input accepts a diamond sword carrying Sharpness II
  without `repair_cost`.
- Sharpness I and an unrelated enchantment remain rejected.
- Exact component matching through `RegistryOps` works without a component
  type-specific branch.
- Existing custom-name and structured-predicate tests remain green.

## Non-Goals

- Change recipe JSON syntax.
- Add special handling for any individual component type.
- Alter item consumption or machine scheduling behavior.
