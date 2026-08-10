# Task 2 Registry Context Fix Report

## Root Cause

`MachineRecipeBuilderJS.itemOutputWithComponents` built an `ItemStack` JSON object and decoded it immediately with `ItemStack.CODEC.parse(JsonOps.INSTANCE, stack)`. `JsonOps.INSTANCE` does not provide a lookup for data components that refer to dynamic registries. A non-empty `minecraft:enchantments` map therefore fails while `minecraft:custom_name` succeeds.

## API Evidence

- KubeJS `RecipesKubeEvent` owns a `RegistryAccessContainer` from `ServerScriptManager.getRegistries()` and constructs its `RegistryOpsContainer` with a `KubeRecipeEventOps` wrapping `registries.json()`.
- KubeJS `RegistryAccessContainer(RegistryAccess.Frozen)` calls `Frozen.createSerializationContext(JsonOps.INSTANCE)` to create that `json()` ops. `RegistryAccessContainer.current` is initialized to `BUILTIN`, so the existing public builder constructors can always obtain a registry-aware JSON ops without a new script API.
- AE2 follows the same lifecycle boundary: `AEBaseBlockEntity.debugExport` calls `registries.createSerializationContext(JsonOps.INSTANCE)` when it already has a `HolderLookup.Provider`; test utility `StackUtil` uses the same method for `ItemStack` codecs.

## Fix

- `MachineRecipeBuilderJS` now obtains `RegistryAccessContainer.current.json()` in its normal constructor and uses it for `ItemStack.CODEC` parsing.
- The public `MachineRecipeBuilderJS(String)` and `MachineRecipeBuilderJS(Identifier)` APIs remain unchanged. Plain `itemOutput` is unchanged.
- A package-private constructor accepts `DynamicOps<JsonElement>` only to make the registry-aware codec behavior directly testable without fabricating a KubeJS server event.

## RED

Added `builder_creates_sharpness_four_named_item_output` with both:

```json
{
  "minecraft:custom_name": { "text": "Sharp Sword" },
  "minecraft:enchantments": { "minecraft:sharpness": 4 }
}
```

Before the fix:

```text
MachineRecipeSchemaTest > builder_creates_sharpness_four_named_item_output() FAILED
java.lang.IllegalStateException at MachineRecipeSchemaTest.java:88
```

This is the `ItemStack.CODEC.parse(JsonOps.INSTANCE, ...)` call; the companion `MachineRecipeTest.outputs_roundtrip_native_component_stack` passes with `RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup())`, isolating the missing registry lookup as the cause.

## GREEN

```text
rtk gradlew test --tests cn.howxu.mmcr.compat.kubejs.MachineRecipeSchemaTest --no-daemon
BUILD SUCCESSFUL in 16s
17 actionable tasks: 3 executed, 14 up-to-date
```

The focused class now verifies that the output retains both `custom_name` and Sharpness IV.

## Final Verification

```text
rtk gradlew test --tests cn.howxu.mmcr.compat.kubejs.MachineRecipeSchemaTest --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --no-daemon
BUILD SUCCESSFUL in 6s
17 actionable tasks: 17 up-to-date

rtk gradlew compileJava --no-daemon
BUILD SUCCESSFUL in 8s
14 actionable tasks: 14 up-to-date
```

`git diff --check` also passed. No client was started and `runClient` was not run.
