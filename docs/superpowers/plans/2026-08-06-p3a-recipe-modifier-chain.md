# P3A Recipe Modifier Chain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Phase 3A by making recipe-local modifiers affect duration, item/fluid/energy inputs, item/fluid outputs, and output chance without mutating serialized raw recipe definitions.

**Architecture:** Keep `RecipeModifier` as the recipe-local modifier model and make all derived runtime values flow through explicit helper methods on `MachineRecipe` / `IntegrationTypeHelper`. Introduce a focused `MachineOutput` API so item and fluid outputs can carry raw chance values, then update `MachineRequirement` and `RecipeCraftingContext` to consume derived requirement copies at simulate/commit/tick time.

**Tech Stack:** Java 25, Minecraft 26.1.2, NeoForge 7.1.38 userdev, JUnit 5, AssertJ, Gradle.

## Global Constraints

- Implement only P3A recipe-local static modifiers.
- Do not implement `SingleBlockModifierReplacement`, `MultiBlockModifierReplacement`, or `DynamicModifierReplacement` in this plan.
- Do not mutate serialized raw `tick_time`, raw requirement counts, raw output stacks, or raw output chance when modifiers apply.
- Preserve existing legacy `inputs`, `outputs`, and `fluid_outputs` constructor/API compatibility.
- Prefer formulas/helper methods over cached derived state; active recipes may serialize derived `totalTick` as current runtime state.
- Modifier order must be deterministic: apply all ADD/SUBTRACT effects before MULTIPLY/DIVIDE for matching target/io/chance filters.
- Keep target strings stable: `duration`, `item`, `fluid`, `energy`.
- Do not add third-party mod dependencies.
- Keep code style consistent with existing `api.recipe` records and helper classes.

---

## File Structure

- Modify `src/main/java/cn/howxu/mmcr/api/recipe/modifier/RecipeModifier.java`: Add `SUBTRACT` and `DIVIDE`, update Codec/NBT ids, and centralize ordered modifier application.
- Modify `src/main/java/cn/howxu/mmcr/api/recipe/IntegrationTypeHelper.java`: Add derived helper methods for duration, item/fluid/energy amounts, and output chance.
- Create `src/main/java/cn/howxu/mmcr/api/recipe/MachineOutput.java`: Sealed output model with item/fluid records, raw stack, raw chance, Codec, and derived copy methods.
- Modify `src/main/java/cn/howxu/mmcr/api/recipe/requirement/ItemRequirement.java`: Add `chance` for output requirements and helpers returning raw and derived stacks.
- Modify `src/main/java/cn/howxu/mmcr/api/recipe/requirement/FluidRequirement.java`: Add `chance` for output requirements and helpers returning raw and derived stacks.
- Modify `src/main/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirement.java`: Derive FE/t through recipe modifiers during simulate/ioTick.
- Modify `src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java`: Encode/decode output chance and adapt legacy output factories.
- Modify `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java`: Store raw requirements, expose raw legacy output APIs, add `machineOutputs()` and `runtimeRequirements()`.
- Modify `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`: Use `recipe.runtimeRequirements()` for simulate, commit, and ioTick.
- Modify `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java`: Continue deriving total tick from modifiers through helper methods and verify raw `tick_time` remains unchanged.
- Modify `src/main/java/cn/howxu/mmcr/api/recipe/PreparedRecipe.java`: Preserve existing constructors and pass raw outputs/modifiers unchanged.
- Modify `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeSchema.java`: Add optional raw `modifiers` passthrough key if current KubeJS schema cannot emit modifiers yet.
- Modify tests in `src/test/java/cn/howxu/mmcr/api/recipe/RecipeApiSmokeTest.java`, `MachineRecipeTest.java`, and `RecipeCraftingContextTest.java`.
- Optionally add `src/test/java/cn/howxu/mmcr/api/recipe/MachineOutputTest.java` if output Codec tests become too large for `MachineRecipeTest`.
- Modify `docs/main-roadmap.md` after implementation to mark P3A completed or partially completed with exact remaining items.

---

### Task 1: Modifier Operation Semantics

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/modifier/RecipeModifier.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/IntegrationTypeHelper.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeApiSmokeTest.java`

**Interfaces:**
- Consumes: `RecipeModifier(String target, IOType ioTarget, float modifier, Operation operation, boolean affectsChance)`.
- Produces: `RecipeModifier.Operation.SUBTRACT`, `RecipeModifier.Operation.DIVIDE`, `RecipeModifier.applyModifiers(Collection<RecipeModifier>, String, IOType, float, boolean)`, `IntegrationTypeHelper.asInt(float)`.

- [ ] **Step 1: Add failing operation-order tests**

Add these tests to `RecipeApiSmokeTest` after `recipe_modifier_apply_modifiers_combines_add_and_multiply`:

```java
    @Test
    void recipe_modifier_applies_add_subtract_before_multiply_divide() {
        var mods = List.of(
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 4F, RecipeModifier.Operation.ADD, false),
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 3F, RecipeModifier.Operation.SUBTRACT, false),
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 6F, RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 2F, RecipeModifier.Operation.DIVIDE, false)
        );

        float result = RecipeModifier.applyModifiers(mods, "item", RecipeModifier.IOType.OUTPUT, 10F, false);

        assertThat(result).isEqualTo(((10F + 4F - 3F) * 6F) / 2F);
    }

    @Test
    void recipe_modifier_ignores_zero_divide_modifier() {
        var mods = List.of(
                new RecipeModifier("duration", RecipeModifier.IOType.INPUT, 0F, RecipeModifier.Operation.DIVIDE, false)
        );

        float result = RecipeModifier.applyModifiers(mods, "duration", RecipeModifier.IOType.INPUT, 80F, false);

        assertThat(result).isEqualTo(80F);
    }
```

- [ ] **Step 2: Run tests and verify they fail**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeApiSmokeTest --no-daemon`

Expected: FAIL because `SUBTRACT` and `DIVIDE` do not exist.

- [ ] **Step 3: Extend `RecipeModifier.Operation`**

Update the enum in `RecipeModifier.java`:

```java
    public enum Operation {
        ADD(0),
        MULTIPLY(1),
        SUBTRACT(2),
        DIVIDE(3);

        private final int id;

        Operation(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static Operation byId(int id) {
            return switch (id) {
                case 0 -> ADD;
                case 1 -> MULTIPLY;
                case 2 -> SUBTRACT;
                case 3 -> DIVIDE;
                default -> throw new IllegalArgumentException("Unknown modifier operation: " + id);
            };
        }
    }
```

- [ ] **Step 4: Update ordered application logic**

Replace `applyModifiers(...)` with:

```java
    public static float applyModifiers(Collection<RecipeModifier> modifiers, String target, IOType ioType, float value, boolean isChance) {
        if (modifiers == null || modifiers.isEmpty()) return value;
        float add = 0F;
        float mul = 1F;
        for (RecipeModifier mod : modifiers) {
            if (!mod.matches(target, ioType, isChance)) continue;
            switch (mod.operation) {
                case ADD -> add += mod.modifier;
                case SUBTRACT -> add -= mod.modifier;
                case MULTIPLY -> mul *= mod.modifier;
                case DIVIDE -> {
                    if (mod.modifier != 0F) mul /= mod.modifier;
                }
            }
        }
        return (value + add) * mul;
    }

    private boolean matches(String target, IOType ioType, boolean isChance) {
        if (this.target != null && !this.target.isEmpty() && !this.target.equals(target)) return false;
        if (ioType != null && this.ioTarget != ioType) return false;
        return this.affectsChance == isChance;
    }
```

- [ ] **Step 5: Update `applyValueToApplier` for legacy applier callers**

Replace `applyValueToApplier(...)` with:

```java
    public static void applyValueToApplier(ModifierApplier applier, RecipeModifier mod) {
        switch (mod.operation) {
            case ADD -> {
                if (mod.ioTarget == IOType.OUTPUT) applier.outputAdd += mod.modifier;
                else applier.inputAdd += mod.modifier;
            }
            case SUBTRACT -> {
                if (mod.ioTarget == IOType.OUTPUT) applier.outputAdd -= mod.modifier;
                else applier.inputAdd -= mod.modifier;
            }
            case MULTIPLY -> {
                if (mod.ioTarget == IOType.OUTPUT) applier.outputMul *= mod.modifier;
                else applier.inputMul *= mod.modifier;
            }
            case DIVIDE -> {
                if (mod.modifier == 0F) return;
                if (mod.ioTarget == IOType.OUTPUT) applier.outputMul /= mod.modifier;
                else applier.inputMul /= mod.modifier;
            }
        }
    }
```

- [ ] **Step 6: Run focused modifier tests**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeApiSmokeTest --no-daemon`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/modifier/RecipeModifier.java src/test/java/cn/howxu/mmcr/api/recipe/RecipeApiSmokeTest.java
git commit -m "feat(recipe): expand modifier operations"
```

---

### Task 2: MachineOutput API and Output Chance Codec

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/recipe/MachineOutput.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/IntegrationTypeHelper.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/requirement/ItemRequirement.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/requirement/FluidRequirement.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/requirement/MachineRequirement.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/MachineRecipeTest.java`

**Interfaces:**
- Consumes: `ItemRequirement(io, item, count, stack, tags)`, `FluidRequirement(io, fluid, amount, stack, tags)`.
- Produces: `MachineOutput.ItemOutput(ItemStack stack, float chance)`, `MachineOutput.FluidOutput(FluidStack stack, float chance)`, `ItemRequirement.chance()`, `FluidRequirement.chance()`, output requirement Codec fields `chance`.

- [ ] **Step 1: Add failing output chance roundtrip test**

Add to `MachineRecipeTest`:

```java
    @Test
    void output_requirement_chance_roundtrips() {
        bindItemComponents(Items.IRON_NUGGET);
        bindFluidComponents(Fluids.WATER);
        var root = new JsonObject();
        root.addProperty("id", "mmcr:chance_outputs");
        root.addProperty("machine", "mmcr:machine");
        root.addProperty("tick_time", 20);
        root.add("requirements", requirements(
                itemOutputRequirement(itemId(Items.IRON_NUGGET), 3, 0.25F),
                fluidOutputRequirement("minecraft:water", 500, 0.75F)
        ));

        var recipe = MachineRecipe.CODEC.codec().parse(jsonOps(), root).getOrThrow();

        assertThat(recipe.machineOutputs()).hasSize(2);
        assertThat(recipe.machineOutputs().get(0)).isInstanceOfSatisfying(MachineOutput.ItemOutput.class, output -> {
            assertThat(output.stack().getItem()).isEqualTo(Items.IRON_NUGGET);
            assertThat(output.stack().getCount()).isEqualTo(3);
            assertThat(output.chance()).isEqualTo(0.25F);
        });
        assertThat(recipe.machineOutputs().get(1)).isInstanceOfSatisfying(MachineOutput.FluidOutput.class, output -> {
            assertThat(output.stack().getFluid()).isEqualTo(Fluids.WATER);
            assertThat(output.stack().getAmount()).isEqualTo(500);
            assertThat(output.chance()).isEqualTo(0.75F);
        });

        var encoded = MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow().getAsJsonObject();
        assertThat(encoded.getAsJsonArray("requirements").get(0).getAsJsonObject().get("chance").getAsFloat()).isEqualTo(0.25F);
        assertThat(encoded.getAsJsonArray("requirements").get(1).getAsJsonObject().get("chance").getAsFloat()).isEqualTo(0.75F);
    }
```

Add helper overloads near existing helper methods:

```java
    private static JsonObject itemOutputRequirement(String itemId, int count, float chance) {
        var output = itemOutputRequirement(itemId, count);
        output.addProperty("chance", chance);
        return output;
    }

    private static JsonObject fluidOutputRequirement(String fluidId, int amount, float chance) {
        var output = new JsonObject();
        output.addProperty("type", "fluid");
        output.addProperty("io", "output");
        var stack = new JsonObject();
        stack.addProperty("id", fluidId);
        stack.addProperty("amount", amount);
        output.add("stack", stack);
        output.addProperty("chance", chance);
        return output;
    }
```

- [ ] **Step 2: Run test and verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --no-daemon`

Expected: FAIL because `MachineOutput` and `machineOutputs()` do not exist.

- [ ] **Step 3: Create `MachineOutput`**

Create `src/main/java/cn/howxu/mmcr/api/recipe/MachineOutput.java`:

```java
package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * @author howxu <dev@howxu.cn>
 */
public sealed interface MachineOutput permits MachineOutput.ItemOutput, MachineOutput.FluidOutput {

    Codec<MachineOutput> CODEC = Codec.of(MachineOutput::encode, MachineOutput::decode);

    String type();

    float chance();

    MachineOutput withChance(float chance);

    MachineOutput applyModifiers(java.util.List<RecipeModifier> modifiers);

    private static <T> DataResult<T> encode(MachineOutput output, DynamicOps<T> ops, T prefix) {
        var builder = ops.mapBuilder()
                .add("type", ops.createString(output.type()))
                .add("chance", ops.createFloat(output.chance()));
        if (output instanceof ItemOutput item) {
            return builder.add("stack", item.stack(), ItemStack.CODEC).build(prefix);
        }
        if (output instanceof FluidOutput fluid) {
            return builder.add("stack", fluid.stack(), FluidStack.CODEC).build(prefix);
        }
        return DataResult.error(() -> "Unknown machine output: " + output);
    }

    private static <T> DataResult<Pair<MachineOutput, T>> decode(DynamicOps<T> ops, T input) {
        return ops.get(input, "type")
                .flatMap(ops::getStringValue)
                .flatMap(type -> decodeByType(type, ops, input))
                .map(output -> Pair.of(output, input));
    }

    private static <T> DataResult<MachineOutput> decodeByType(String type, DynamicOps<T> ops, T input) {
        float chance = decodeChance(ops, input);
        return switch (type) {
            case "item" -> ops.get(input, "stack")
                    .flatMap(value -> ItemStack.CODEC.parse(ops, value))
                    .map(stack -> new ItemOutput(stack, chance));
            case "fluid" -> ops.get(input, "stack")
                    .flatMap(value -> FluidStack.CODEC.parse(ops, value))
                    .map(stack -> new FluidOutput(stack, chance));
            default -> DataResult.error(() -> "Unknown output type: " + type);
        };
    }

    private static <T> float decodeChance(DynamicOps<T> ops, T input) {
        return ops.get(input, "chance")
                .flatMap(ops::getNumberValue)
                .map(Number::floatValue)
                .result()
                .orElse(1F);
    }

    record ItemOutput(ItemStack stack, float chance) implements MachineOutput {
        public ItemOutput {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            chance = clampChance(chance);
        }

        @Override public String type() { return "item"; }

        @Override public ItemOutput withChance(float chance) {
            return new ItemOutput(stack, chance);
        }

        @Override public ItemOutput applyModifiers(java.util.List<RecipeModifier> modifiers) {
            ItemStack derived = stack.copy();
            derived.setCount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyItemOutput(modifiers, stack.getCount())));
            return new ItemOutput(derived, IntegrationTypeHelper.applyItemOutputChance(modifiers, chance));
        }
    }

    record FluidOutput(FluidStack stack, float chance) implements MachineOutput {
        public FluidOutput {
            stack = stack == null ? FluidStack.EMPTY : stack.copy();
            chance = clampChance(chance);
        }

        @Override public String type() { return "fluid"; }

        @Override public FluidOutput withChance(float chance) {
            return new FluidOutput(stack, chance);
        }

        @Override public FluidOutput applyModifiers(java.util.List<RecipeModifier> modifiers) {
            FluidStack derived = stack.copy();
            derived.setAmount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidOutput(modifiers, stack.getAmount())));
            return new FluidOutput(derived, IntegrationTypeHelper.applyFluidOutputChance(modifiers, chance));
        }
    }

    static float clampChance(float chance) {
        if (Float.isNaN(chance)) return 1F;
        if (chance < 0F) return 0F;
        if (chance > 1F) return 1F;
        return chance;
    }
}
```

- [ ] **Step 4: Add chance helpers to `IntegrationTypeHelper`**

Add methods after output amount helpers:

```java
    public static float applyItemOutputChance(List<RecipeModifier> modifiers, float chance) {
        if (modifiers == null || modifiers.isEmpty()) return MachineOutput.clampChance(chance);
        return MachineOutput.clampChance(RecipeModifier.applyModifiers(modifiers, TARGET_ITEM, RecipeModifier.IOType.OUTPUT, chance, true));
    }

    public static float applyFluidOutputChance(List<RecipeModifier> modifiers, float chance) {
        if (modifiers == null || modifiers.isEmpty()) return MachineOutput.clampChance(chance);
        return MachineOutput.clampChance(RecipeModifier.applyModifiers(modifiers, TARGET_FLUID, RecipeModifier.IOType.OUTPUT, chance, true));
    }
```

- [ ] **Step 5: Extend output requirements with chance**

Change `ItemRequirement` signature to:

```java
public record ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack, float chance, List<String> tags) implements MachineRequirement {

    public ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack) {
        this(io, item, count, stack, 1F, List.of());
    }

    public ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack, List<String> tags) {
        this(io, item, count, stack, 1F, tags);
    }

    public ItemRequirement {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
        chance = MachineOutput.clampChance(chance);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
```

Change `FluidRequirement` the same way:

```java
public record FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack, float chance, List<String> tags) implements MachineRequirement {

    public FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack) {
        this(io, fluid, amount, stack, 1F, List.of());
    }

    public FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack, List<String> tags) {
        this(io, fluid, amount, stack, 1F, tags);
    }

    public FluidRequirement {
        stack = stack == null ? FluidStack.EMPTY : stack.copy();
        chance = MachineOutput.clampChance(chance);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
```

- [ ] **Step 6: Encode/decode chance in `MachineRequirement`**

In output encoding branches, add chance only when not default:

```java
            var itemBuilder = builder.add("stack", item.stack(), ItemStack.CODEC);
            if (item.chance() != 1F) itemBuilder = itemBuilder.add("chance", ops.createFloat(item.chance()));
            return itemBuilder.build(prefix);
```

```java
            var fluidBuilder = builder.add("stack", fluid.stack(), FluidStack.CODEC);
            if (fluid.chance() != 1F) fluidBuilder = fluidBuilder.add("chance", ops.createFloat(fluid.chance()));
            return fluidBuilder.build(prefix);
```

Decode chance in output branches:

```java
                        .map(stack -> new ItemRequirement(io, null, 0, stack, decodeChance(ops, input), tags));
```

```java
                        .map(stack -> new FluidRequirement(io, null, 0, stack, decodeChance(ops, input), tags));
```

Add helper:

```java
    private static <T> float decodeChance(DynamicOps<T> ops, T input) {
        return ops.get(input, "chance")
                .flatMap(ops::getNumberValue)
                .map(Number::floatValue)
                .result()
                .orElse(1F);
    }
```

- [ ] **Step 7: Add `machineOutputs()` to `MachineRecipe`**

Add:

```java
    public List<MachineOutput> machineOutputs() {
        List<MachineOutput> outputs = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT) {
                outputs.add(new MachineOutput.ItemOutput(item.stack(), item.chance()));
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT) {
                outputs.add(new MachineOutput.FluidOutput(fluid.stack(), fluid.chance()));
            }
        }
        return List.copyOf(outputs);
    }
```

- [ ] **Step 8: Run focused tests**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --no-daemon`

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe src/test/java/cn/howxu/mmcr/api/recipe/MachineRecipeTest.java
git commit -m "feat(recipe): add machine outputs with chance"
```

---

### Task 3: Runtime Derived Requirements

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/requirement/EnergyRequirement.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeApiSmokeTest.java`

**Interfaces:**
- Consumes: raw `MachineRecipe.requirements()` and `MachineRecipe.modifiers()`.
- Produces: `MachineRecipe.runtimeRequirements()`, `MachineRecipe.runtimeMachineOutputs()`, runtime item/fluid/energy quantities after modifiers.

- [ ] **Step 1: Add failing derived runtime tests**

Add to `RecipeCraftingContextTest`:

```java
    @Test
    void item_input_modifier_changes_runtime_consumption_without_mutating_recipe() {
        bindItemComponents(Items.IRON_INGOT);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(4));
        MachineControllerBlockEntity controller = controllerWithComponents(input);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "modified_item_input"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
                List.of(),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false)),
                0,
                1
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe)).isTrue();
        assertThat(context.commitInputs(recipe)).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(recipe.inputs().getFirst()).isEqualTo(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2));
    }

    @Test
    void item_output_modifier_changes_runtime_output_without_mutating_recipe() {
        bindItemComponents(Items.IRON_NUGGET);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "modified_item_output"),
                Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
                20,
                List.of(),
                List.of(Items.IRON_NUGGET.getDefaultInstance().copyWithCount(2)),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.OUTPUT, 3F, RecipeModifier.Operation.MULTIPLY, false)),
                0,
                1
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitOutputs(recipe)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(6);
        assertThat(recipe.outputs().getFirst().getCount()).isEqualTo(2);
    }
```

Add to `RecipeApiSmokeTest`:

```java
    @Test
    void runtime_requirements_apply_all_supported_modifier_targets() {
        bindFluidComponents(Fluids.WATER);
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "runtime_modifiers"),
                Identifier.fromNamespaceAndPath("mmcr", "runtime_machine"),
                100,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2),
                        new MachineIngredient.FluidIngredient(net.neoforged.neoforge.fluids.crafting.FluidIngredient.of(Fluids.WATER), 250),
                        new MachineIngredient.EnergyIngredient(40)
                ),
                List.of(Items.IRON_NUGGET.getDefaultInstance().copyWithCount(1)),
                List.of(
                        new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false),
                        new RecipeModifier(IntegrationTypeHelper.TARGET_FLUID, RecipeModifier.IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false),
                        new RecipeModifier(IntegrationTypeHelper.TARGET_ENERGY, RecipeModifier.IOType.INPUT, 2F, RecipeModifier.Operation.MULTIPLY, false),
                        new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.OUTPUT, 3F, RecipeModifier.Operation.MULTIPLY, false)
                ),
                0,
                1
        );

        assertThat(recipe.runtimeRequirements()).satisfies(requirements -> {
            assertThat(((cn.howxu.mmcr.api.recipe.requirement.ItemRequirement) requirements.get(0)).count()).isEqualTo(4);
            assertThat(((cn.howxu.mmcr.api.recipe.requirement.FluidRequirement) requirements.get(1)).amount()).isEqualTo(500);
            assertThat(((cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement) requirements.get(2)).fePerTick()).isEqualTo(80);
            assertThat(((cn.howxu.mmcr.api.recipe.requirement.ItemRequirement) requirements.get(3)).stack().getCount()).isEqualTo(3);
        });
        assertThat(recipe.inputs()).contains(new MachineIngredient.EnergyIngredient(40));
        assertThat(recipe.outputs().getFirst().getCount()).isEqualTo(1);
    }
```

- [ ] **Step 2: Run tests and verify they fail**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.api.recipe.RecipeApiSmokeTest --no-daemon`

Expected: FAIL because runtime requirement derivation is not implemented.

- [ ] **Step 3: Add derived helper methods in `MachineRecipe`**

Add imports if needed and add:

```java
    public List<MachineRequirement> runtimeRequirements() {
        if (modifiers.isEmpty()) return requirements;
        List<MachineRequirement> derived = new ArrayList<>(requirements.size());
        for (MachineRequirement requirement : requirements) {
            derived.add(applyModifiers(requirement));
        }
        return List.copyOf(derived);
    }

    public List<MachineOutput> runtimeMachineOutputs() {
        if (modifiers.isEmpty()) return machineOutputs();
        return machineOutputs().stream()
                .map(output -> output.applyModifiers(modifiers))
                .toList();
    }

    private MachineRequirement applyModifiers(MachineRequirement requirement) {
        if (requirement instanceof ItemRequirement item) {
            if (item.io() == RecipeModifier.IOType.INPUT) {
                int count = IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyItemInput(modifiers, item.count()));
                return new ItemRequirement(item.io(), item.item(), count, item.stack(), item.chance(), item.tags());
            }
            ItemStack stack = item.stack().copy();
            stack.setCount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyItemOutput(modifiers, stack.getCount())));
            float chance = IntegrationTypeHelper.applyItemOutputChance(modifiers, item.chance());
            return new ItemRequirement(item.io(), item.item(), item.count(), stack, chance, item.tags());
        }
        if (requirement instanceof FluidRequirement fluid) {
            if (fluid.io() == RecipeModifier.IOType.INPUT) {
                int amount = IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidInput(modifiers, fluid.amount()));
                return new FluidRequirement(fluid.io(), fluid.fluid(), amount, fluid.stack(), fluid.chance(), fluid.tags());
            }
            FluidStack stack = fluid.stack().copy();
            stack.setAmount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidOutput(modifiers, stack.getAmount())));
            float chance = IntegrationTypeHelper.applyFluidOutputChance(modifiers, fluid.chance());
            return new FluidRequirement(fluid.io(), fluid.fluid(), fluid.amount(), stack, chance, fluid.tags());
        }
        if (requirement instanceof EnergyRequirement energy) {
            int fePerTick = IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyEnergy(modifiers, energy.fePerTick()));
            return new EnergyRequirement(fePerTick, energy.tags());
        }
        return requirement;
    }
```

- [ ] **Step 4: Update `RecipeCraftingContext` to use runtime requirements**

In `ioTick`, `simulateInputs`, `simulateOutputs`, `commitInputs`, and `commitOutputs`, replace:

```java
        List<MachineRequirement> requirements = recipe.requirements();
```

with:

```java
        List<MachineRequirement> requirements = recipe.runtimeRequirements();
```

- [ ] **Step 5: Keep `EnergyRequirement` simple**

Do not make `EnergyRequirement` read recipe modifiers directly. It should keep consuming its own `fePerTick`; derivation belongs to `MachineRecipe.runtimeRequirements()`.

- [ ] **Step 6: Run focused tests**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.api.recipe.RecipeApiSmokeTest --no-daemon`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java src/test/java/cn/howxu/mmcr/api/recipe/RecipeApiSmokeTest.java
git commit -m "feat(recipe): apply modifiers to runtime requirements"
```

---

### Task 4: Codec Shape and Raw-vs-Derived Guarantees

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/MachineRecipeTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeApiSmokeTest.java`

**Interfaces:**
- Consumes: `MachineRecipe.CODEC`, `MachineRecipe.runtimeRequirements()`, `ActiveMachineRecipe.getTotalTick()`.
- Produces: tests proving encoded JSON preserves raw `tick_time`, raw requirements, raw output stacks, and raw chance.

- [ ] **Step 1: Add raw preservation tests**

Add to `MachineRecipeTest`:

```java
    @Test
    void codec_preserves_raw_values_when_runtime_modifiers_change_derived_values() {
        bindItemComponents(Items.IRON_NUGGET);
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "raw_preserved"),
                Identifier.fromNamespaceAndPath("mmcr", "machine"),
                100,
                List.of(new MachineIngredient.ItemIngredient(net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_INGOT), 2)),
                List.of(Items.IRON_NUGGET.getDefaultInstance().copyWithCount(1)),
                List.of(
                        new RecipeModifier(IntegrationTypeHelper.TARGET_DURATION, RecipeModifier.IOType.INPUT, 0.5F, RecipeModifier.Operation.MULTIPLY, false),
                        new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.INPUT, 3F, RecipeModifier.Operation.MULTIPLY, false),
                        new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.OUTPUT, 4F, RecipeModifier.Operation.MULTIPLY, false)
                ),
                0,
                1
        );

        var encoded = MachineRecipe.CODEC.codec().encodeStart(jsonOps(), recipe).getOrThrow().getAsJsonObject();
        var back = MachineRecipe.CODEC.codec().parse(jsonOps(), encoded).getOrThrow();

        assertThat(encoded.get("tick_time").getAsInt()).isEqualTo(100);
        assertThat(back.inputs().getFirst()).isEqualTo(recipe.inputs().getFirst());
        assertThat(back.outputs().getFirst().getCount()).isEqualTo(1);
        assertThat(((cn.howxu.mmcr.api.recipe.requirement.ItemRequirement) back.runtimeRequirements().get(0)).count()).isEqualTo(6);
        assertThat(((cn.howxu.mmcr.api.recipe.requirement.ItemRequirement) back.runtimeRequirements().get(1)).stack().getCount()).isEqualTo(4);
    }
```

Add to `RecipeApiSmokeTest`:

```java
    @Test
    void active_recipe_uses_derived_duration_but_recipe_tick_time_stays_raw() {
        var recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "duration_runtime"),
                Identifier.fromNamespaceAndPath("mmcr", "duration_machine"),
                100,
                List.of(),
                List.of(),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_DURATION, RecipeModifier.IOType.INPUT, 0.5F, RecipeModifier.Operation.MULTIPLY, false)),
                0,
                1
        );

        var active = new ActiveMachineRecipe(recipe);

        assertThat(active.getTotalTick()).isEqualTo(50);
        assertThat(recipe.getRecipeTotalTickTime()).isEqualTo(100);
    }
```

- [ ] **Step 2: Run tests and verify result**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --tests cn.howxu.mmcr.api.recipe.RecipeApiSmokeTest --no-daemon`

Expected: PASS if Tasks 1-3 are complete. If FAIL, fix only raw-vs-derived boundary issues in `MachineRecipe` / `ActiveMachineRecipe`.

- [ ] **Step 3: Ensure Codec still omits legacy fields when requirements exist**

Re-run existing `recipe_codec_prefers_requirements_and_encodes_stable_shape` test. Do not reintroduce serialized `inputs`, `outputs`, or `fluid_outputs` when `requirements` is present.

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest.recipe_codec_prefers_requirements_and_encodes_stable_shape --no-daemon`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe src/test/java/cn/howxu/mmcr/api/recipe/MachineRecipeTest.java src/test/java/cn/howxu/mmcr/api/recipe/RecipeApiSmokeTest.java
git commit -m "test(recipe): lock raw modifier serialization"
```

---

### Task 5: Chance Application at Finish Time

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/MachineOutput.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`

**Interfaces:**
- Consumes: `ItemRequirement.chance()`, `FluidRequirement.chance()`, derived output requirements from `runtimeRequirements()`.
- Produces: deterministic 0% and 100% chance behavior. Non-100% chance is sampled only at finish/commit time, not during output capacity simulation.

- [ ] **Step 1: Add deterministic chance tests**

Add to `RecipeCraftingContextTest`:

```java
    @Test
    void zero_chance_item_output_does_not_insert_at_finish() {
        bindItemComponents(Items.IRON_NUGGET);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = explicitItemRecipe(
                "zero_chance_item_output",
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_NUGGET.getDefaultInstance().copyWithCount(3), 0F, List.of()))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitOutputs(recipe)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void hundred_percent_item_output_inserts_at_finish() {
        bindItemComponents(Items.IRON_NUGGET);
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = controllerWithComponents(output);
        MachineRecipe recipe = explicitItemRecipe(
                "full_chance_item_output",
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_NUGGET.getDefaultInstance().copyWithCount(3), 1F, List.of()))
        );
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(context.simulateOutputs(recipe)).isTrue();
        assertThat(context.commitOutputs(recipe)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(3);
    }
```

- [ ] **Step 2: Run tests and verify zero chance currently inserts**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon`

Expected: FAIL because output chance is not applied.

- [ ] **Step 3: Apply chance during transfer collection**

In `RecipeCraftingContext.commitOutputs`, when collecting item/fluid output routes, skip transfers if chance is `0F`, and always include if `1F`:

```java
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT) {
                if (item.chance() <= 0F) continue;
                ItemOutputRoute route = itemOutputRoutes.get(requirementIndex);
                itemTransfers.addAll(route.transfers());
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT) {
                if (fluid.chance() <= 0F) continue;
                FluidOutputRoute route = fluidOutputRoutes.get(requirementIndex);
                fluidTransfers.addAll(route.transfers());
            }
```

For non-deterministic chance, add a private helper using the controller level random when available:

```java
    private boolean shouldProduce(float chance) {
        if (chance >= 1F) return true;
        if (chance <= 0F) return false;
        var level = controller.getLevel();
        return (level == null ? Math.random() : level.random.nextFloat()) < chance;
    }
```

Then use `if (!shouldProduce(item.chance())) continue;` and `if (!shouldProduce(fluid.chance())) continue;`.

- [ ] **Step 4: Keep simulation conservative**

Do not skip `simulateOutputs` for chance below 1. It should still reserve/check full possible output so a successful recipe never voids an output because the chance roll hit and storage was full.

- [ ] **Step 5: Run focused tests**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java
git commit -m "feat(recipe): apply output chance on finish"
```

---

### Task 6: KubeJS Schema and PreparedRecipe Pass-through

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeSchema.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/PreparedRecipe.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeApiSmokeTest.java`

**Interfaces:**
- Consumes: `PreparedRecipe.getModifiers()`, existing KubeJS `inputs`, `outputs`, `energy_per_tick` schema.
- Produces: KubeJS schema key `modifiers` as raw JSON list passthrough if the factory path supports it, and `PreparedRecipe.toMachineRecipe()` preserving modifiers/fluid outputs.

- [ ] **Step 1: Add/confirm PreparedRecipe modifier pass-through test**

Extend existing `prepared_recipe_converts_to_machine_recipe` in `RecipeApiSmokeTest` to assert modifiers remain raw:

```java
        assertThat(recipe.modifiers()).isEqualTo(prepared.getModifiers());
        assertThat(recipe.getRecipeTotalTickTime()).isEqualTo(50);
```

If that test does not construct modifiers, change the `PreparedRecipe` construction to pass:

```java
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_DURATION, RecipeModifier.IOType.INPUT, 0.5F, RecipeModifier.Operation.MULTIPLY, false)),
                0,
                1
```

- [ ] **Step 2: Inspect whether KubeJS factory consumes schema keys**

Check `MachineRecipeFactory` and current KubeJS integration. If the current `KubeRecipeFactory` path only stores raw KubeJS JSON and does not call `MachineRecipe.CODEC` yet, do not add fake builder logic.

- [ ] **Step 3: Add schema key only if needed for KubeJS authoring**

If KubeJS recipe scripts currently define machine recipes through `MachineRecipeSchema`, add:

```java
    public static final RecipeKey<List<JsonElement>> MODIFIERS =
            new RecipeKey<>(ListRecipeComponent.create(JSON_ELEMENT, true, false), "modifiers", ComponentRole.OTHER)
                    .optional(List.of()).exclude();
```

and include it in the schema constructor:

```java
    public static final RecipeSchema SCHEMA = new RecipeSchema(MACHINE, TICK_TIME, INPUTS, ENERGY_PER_TICK, OUTPUTS, MODIFIERS, CANCEL_IF_PER_TICK_FAILS)
            .factory(MachineRecipeFactory.INSTANCE);
```

If the schema is not yet used for MachineRecipe decoding, skip this edit and record the reason in the final task notes.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeApiSmokeTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/PreparedRecipe.java src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeSchema.java src/test/java/cn/howxu/mmcr/api/recipe/RecipeApiSmokeTest.java
git commit -m "feat(recipe): expose modifiers through recipe authoring"
```

If `MachineRecipeSchema.java` was not modified, omit it from `git add` and use commit message:

```bash
git add src/test/java/cn/howxu/mmcr/api/recipe/RecipeApiSmokeTest.java
git commit -m "test(recipe): preserve prepared recipe modifiers"
```

---

### Task 7: Final Verification and Roadmap Update

**Files:**
- Modify: `docs/main-roadmap.md`
- Optional modify: `docs/MMCE.md` only if the implementation reveals an incorrect MMCE mapping.

**Interfaces:**
- Consumes: All completed P3A code and tests.
- Produces: Updated roadmap status and a verified P3A completion boundary.

- [ ] **Step 1: Run compile**

Run: `./gradlew compileJava --no-daemon`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run focused recipe test suite**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.* --no-daemon`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run full test suite**

Run: `./gradlew test --no-daemon`

Expected: BUILD SUCCESSFUL. If unrelated pre-existing failures appear, record exact failing tests and run the focused recipe suite again to prove P3A scope is green.

- [ ] **Step 4: Update roadmap P3A status**

In `docs/main-roadmap.md`, under `### 4.1 P3A：Recipe 内静态 modifier`, add a status line only after verification passes:

```markdown
**状态**：已完成 recipe-local static modifier runtime chain；P3B pattern position modifier 与 runtime hook 仍按后续阶段执行。
```

If some acceptance criteria remain incomplete, write the exact remaining item instead:

```markdown
**状态**：部分完成；剩余：<specific item>。
```

- [ ] **Step 5: Inspect final diff**

Run: `rtk git diff --stat`

Expected: Only P3A source, tests, and docs changed.

Run: `rtk git diff -- docs/main-roadmap.md src/main/java/cn/howxu/mmcr/api/recipe src/test/java/cn/howxu/mmcr/api/recipe`

Expected: No structure modifier, upgrade, JEI, or unrelated refactor changes.

- [ ] **Step 6: Commit final docs if needed**

```bash
git add docs/main-roadmap.md
git commit -m "docs: update p3a modifier roadmap status"
```

If docs were already included in a previous commit, skip this commit.

---

## Self-Review

- Spec coverage: P3A covers modifier operation/target semantics, raw-vs-derived duration, item/fluid/energy input, item/fluid output, output chance, Codec roundtrip, and roadmap update.
- Deliberate exclusions: `SingleBlockModifierReplacement`, `MultiBlockModifierReplacement`, and `DynamicModifierReplacement` remain out of scope and are documented in `docs/main-roadmap.md`.
- Type consistency: All runtime derivation goes through `MachineRecipe.runtimeRequirements()` and existing context methods consume `MachineRequirement` records.
- Validation coverage: Each task includes focused tests plus final compile, recipe tests, and full test suite.
