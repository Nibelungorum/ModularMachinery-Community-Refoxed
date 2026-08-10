# Task 2 Report: KubeJS Component-Bearing Item Outputs

## Changed Files

- `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeBuilderJS.java`
  - Added `itemOutputWithComponents(String itemId, int count, JsonElement components)`.
  - The method constructs the native `ItemStack` JSON object with `id`, `count`, and a deep copy of `components`, then parses it with `ItemStack.CODEC` and `JsonOps.INSTANCE`.
  - `getOrThrow()` preserves codec failures: invalid component values raise `IllegalStateException` rather than producing a plain item stack.
  - The existing concise `itemOutput(String, int)` method is unchanged.
- `src/test/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeSchemaTest.java`
  - Added a builder-level test which verifies native `minecraft:custom_name` component preservation and rejects malformed component JSON.
  - Added the existing project test bootstrap required for Minecraft item/component codecs.

## RED Evidence

Command:

```bash
rtk gradlew test --tests cn.howxu.mmcr.compat.kubejs.MachineRecipeSchemaTest --no-daemon
```

Result: `BUILD FAILED in 14s` during `:compileTestJava`, before production code was added.

Relevant output:

```text
MachineRecipeSchemaTest.java:63: error: cannot find symbol
builder.itemOutputWithComponents("minecraft:diamond_sword", 1, json(...))
       ^
symbol: method itemOutputWithComponents(String,int,JsonElement)
location: variable builder of type MachineRecipeBuilderJS
```

The same missing-method error appeared in the malformed-component assertion. This confirmed the test failed for the intended absent API.

## Codec Investigation

The first GREEN candidate followed the specified `ItemStack.CODEC.parse(JsonOps.INSTANCE, stack)` implementation, but the task-brief's enchantment example failed under the unit-test fixture. The codec error showed two fixture constraints:

```text
Can't access registry ResourceKey[minecraft:root / minecraft:enchantment]
Item minecraft:diamond_sword does not have components yet
```

`JsonOps.INSTANCE` is required by the task and has no enchantment registry lookup. The test now binds the diamond sword's component map and uses the registry-independent native `minecraft:custom_name` component. It still proves that a component is parsed and preserved by the full `ItemStack.CODEC`; the malformed `custom_name: 42` assertion verifies errors are not silently downgraded.

## GREEN Evidence

Command:

```bash
rtk gradlew test --tests cn.howxu.mmcr.compat.kubejs.MachineRecipeSchemaTest --no-daemon
```

Output:

```text
BUILD SUCCESSFUL in 14s
17 actionable tasks: 2 executed, 15 up-to-date
```

The focused class executed three tests, including `builder_creates_component_bearing_item_output`.

## Compile Evidence

Command:

```bash
rtk gradlew compileJava --no-daemon
```

Output:

```text
> Task :compileJava UP-TO-DATE

BUILD SUCCESSFUL in 7s
14 actionable tasks: 14 up-to-date
Configuration cache entry reused.
```

## Commit Scope

The task commit contains only:

- `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeBuilderJS.java`
- `src/test/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeSchemaTest.java`
- `.superpowers/sdd/output-task-2-report.md`

It excludes the pre-existing `.superpowers/sdd/task-4-report.md` modification.

## Concerns

- This API deliberately uses `JsonOps.INSTANCE` as required. Components requiring dynamic registry resolution, such as a non-empty enchantment level map, cannot be parsed by this entry point without changing that required ops choice to `RegistryOps`.
- No client was started; `runClient` was not run.
