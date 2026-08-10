# Task 1 Report

## Result

Added native ItemStack component output coverage without production code changes.

- `MachineRecipeTest.outputs_roundtrip_native_component_stack` decodes an output JSON stack with `minecraft:custom_name` and Sharpness IV, then verifies both components are retained.
- `MachineRecipeDisplayTest.displayPreservesOutputComponents` verifies JEI receives a copied named, enchanted sword with the same enchantment component.
- Existing production paths already use `ItemStack.copy()` for recipe outputs and JEI output stacks, so no component-loss defect was exposed.

## TDD Evidence

Initial focused test execution failed before assertions due to test API/fixture setup: the built-in registry access lacks the data-driven enchantment registry and the JEI fixture had unbound item components. The tests were adjusted to use `VanillaRegistries.createLookup()` for enchantments and bind the required test item components. The focused tests then passed without production changes.

## Verification

- `rtk gradlew test --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --tests cn.howxu.mmcr.compat.jei.MachineRecipeDisplayTest --no-daemon` passed.
- `rtk gradlew compileJava --no-daemon` passed.
