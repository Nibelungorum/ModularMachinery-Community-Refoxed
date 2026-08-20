# Task 10 Report

## Obsolete API Inventory

Inventory command executed:

```bash
rg -n "PublicRegistryBridge|PublicBuiltinRuntime|registerDefinitions|composeMachineRegistrations|freezeAndInstallMachines|installRecipes|dynamicSnapshot|staticSnapshot|replaceDynamic|MachineDefinitions\.register\(|RecipeRegistry\.register\(|MachineLevelRegistryBridge|ModifierRegistryBridge|RecipeModifierValue|ModifierDefinition|registerLevelType|registerLevel|registerModifier" src
```

Classifications:

- `ContentRegistrationCoordinator`: canonical coordinator implementation; owns startup validation and installation.
- `PublicRegistryBridge`: obsolete machine/recipe forwarding; deleted.
- `PublicApiBootstrap.registerDefinitions`, `composeMachineRegistrations`, `registerRecipes`, `registerMachine`, `registerRecipe`, `freezeAndInstallMachines`, `installRecipes`: obsolete duplicate startup path; deleted.
- `PublicBuiltinRuntime.registerStructures`: intentional runtime-layer dynamic reload operation; retained.
- `PublicBuiltinRuntime.registerRecipes`: no shipped caller; deleted.
- `MachineLevelRegistry.installSnapshot` and `ModifierRegistry.installSnapshot`: canonical coordinator snapshot installation; retained.
- `MachineLevelRegistry.install(...)` and `ModifierRegistry.install(...)`: uncalled dead aliases; deleted. Internal coordinator calls use the canonical snapshot API.
- `MachineLevelRegistryBridge` and `ModifierRegistryBridge`: deprecated public compatibility bridges; restored with their historical signatures and forwarding-only behavior. They are not coordinator owners and add no startup behavior.
- `MachineDefinitions.register` and `RecipeRegistry.register`: intentional runtime/test registration operations; retained where consumers require them.
- Deprecated external event aliases: retained; no external compatibility bridge was removed.

## Changes

- Routed startup event collection directly to `ContentRegistrationCoordinator`.
- Routed KubeJS startup machine declarations into coordinator collection.
- Kept public compatibility bridges available for external consumers while migrating internal installation to canonical snapshot methods.
- Added API compatibility coverage for bridge visibility, deprecation, and snapshot forwarding.
- Corrected the `PublicApiBootstrap` class-level Javadoc to describe startup installation rather than the removed bootstrap declarations.
- Added this inventory and implementation report.

## Verification

- `./gradlew compileJava --no-daemon`: passed.
- `./gradlew compileTestJava --no-daemon`: passed.
- Focused API/registration tests: passed (`PublicApiInventoryTest`, `PublicApiLifecycleTest`).
- Full `./gradlew test --no-daemon`: failed with 250 tests completed and 98 initialization/bootstrap failures; the failures are outside this compatibility change.
- `./gradlew runGameTestServer --no-daemon`: failed during mod construction with `stage 2 has conflicting predicate at -1, -1, 0` from `MachineStructureFamily.java:145`.
