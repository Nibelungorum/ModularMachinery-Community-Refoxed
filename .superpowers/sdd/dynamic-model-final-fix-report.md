# Dynamic Model Final Fix Report

## Scope

Fixed the two Important findings from the final branch review and the related
Minor mutable item-description issue. The implementation is limited to the
runtime dynamic-model registry, its explicit definition value, the obsolete
descriptor removal, and regression-test bootstrap coverage.

## Changes

- `RuntimeMachineModelRegistry` now builds a `LinkedHashMap` of explicit
  `RuntimeBlockModelDefinition` values once and retains it as an unmodifiable
  map. `definition()` is therefore a direct lookup, while `definitions()`
  streams the same stored definitions rather than reconstructing them.
- Construction remains deferred. Before all `ModBlocks.BLOCKS` holders report
  bound, a call creates only a temporary partial map and does not cache it.
  Once all holders are bound, the ordered map is safely published through the
  volatile field after synchronized construction.
- Removed `RuntimeBlockModelDescriptor`, `describe(...)`, and the descriptor
  overload of `dynamicBlockState(...)`. The remaining blockstate helper looks
  up an explicit `RuntimeBlockModelDefinition` and returns its stored
  blockstate definition.
- `RuntimeBlockModelDefinition.itemDescription()` now returns a new
  `DynamicOverlayItemModel.Description` with a copied `EnumSet`, so mutation
  by a renderer caller cannot alter the cached definition.
- The test bootstrap now binds the three existing debug-source block holders.
  They are intentionally non-dynamic, but all deferred holders must be bound
  for tests to exercise the same cache-ready condition as runtime.

## Regression Coverage

- Verifies repeated definition lookups use the same stored definition and that
  repeated definition streams contain the same ordered definition objects.
- Verifies `dynamicBlockState(Block)` returns the stored explicit definition
  and rejects non-dynamic blocks.
- Verifies mutating an item description's overlay face set cannot affect a
  subsequent description read.
- Existing registry and resource-pack tests continue to cover generated
  blockstate JSON, loader IDs, dynamic item JSON, factory controller resources,
  and parallel/factory overlay textures.

## Verification

Passed:

```text
rtk gradlew test --tests cn.howxu.mmcr.client.model.RuntimeMachineModelRegistryTest --tests cn.howxu.mmcr.client.model.RuntimeMachineResourcePackTest --no-daemon
BUILD SUCCESSFUL in 11s

rtk gradlew compileJava --no-daemon
BUILD SUCCESSFUL in 9s
```

Also passed `git diff --check`. A source search found no remaining production
references to `RuntimeBlockModelDescriptor`, `RuntimeMachineModelRegistry.describe`,
or its descriptor-based state helper.

## Self-Review

- Initialization timing: static construction was rejected because deferred
  holders may be unbound when client/model code first loads. The selected
  all-bound, cache-once path avoids permanently caching an empty or partial
  registry and preserves existing pre-bind behavior.
- Ordering: `LinkedHashMap` preserves the `ModBlocks.BLOCKS` registration
  order, and the cached value is wrapped with `Collections.unmodifiableMap`.
- Output compatibility: definitions retain the same `blockName`, model kind,
  loader identifiers, item-description textures, and blockstate variants; the
  resource pack continues to serialize those stored definitions unchanged.
- Remaining concern: before all deferred holders bind, repeated calls still
  rebuild a temporary partial map by design. This is necessary to avoid caching
  incomplete registration state; normal rendering occurs after holder binding.
