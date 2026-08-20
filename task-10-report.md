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
- `MachineLevelRegistry.installSnapshot` and `ModifierRegistry.installSnapshot`: intentional coordinator snapshot installation; retained.
- `MachineLevelRegistryBridge` and `ModifierRegistryBridge`: internal forwarding only; deleted. Tests now use the public snapshot installation entry through the test fixture.
- `MachineDefinitions.register` and `RecipeRegistry.register`: intentional runtime/test registration operations; retained where consumers require them.
- Deprecated external event aliases: retained; no external compatibility bridge was removed.

## Changes

- Routed startup event collection directly to `ContentRegistrationCoordinator`.
- Routed KubeJS startup machine declarations into coordinator collection.
- Migrated tests away from deleted production bridges and obsolete bootstrap installation methods.
- Added this inventory and implementation report.

## Verification

- `./gradlew compileJava --no-daemon`: passed.
- `./gradlew compileTestJava --no-daemon`: passed.
- Focused API/registration tests: passed (33 tests).
- Full `./gradlew test --no-daemon`: failed in the shared Minecraft test bootstrap with `Another FML loader is already active`; 98 initialization/class-loading failures followed, plus machine bootstrap failures.
- `./gradlew runGameTestServer --no-daemon`: failed during mod construction with a structure predicate conflict at `MachineStructureFamily.java:145`.
