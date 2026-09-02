package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeJsonTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.registerRuntimeBuiltins();
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(id("test_level_type"), Component.literal("Test Level")));
        TestBootstrap.registerLevel(new MachineLevel(id("test_level"), id("test_level_type"), 0,
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                ItemStack.EMPTY, LevelModifier.IDENTITY));
        TestBootstrap.freezeRegistration();
        registries = VanillaRegistries.createLookup();
    }

    @Test
    void parsesBasicMachineRecipeJson() {
        var json = recipeJson();
        json.add("requirements", array(itemRequirement("input", "minecraft:iron_ingot", 2)));
        json.add("outputs", array(itemOutput("minecraft:iron_block", 1)));

        var recipe = MachineRecipeJson.parse(id("basic"), json, registries);

        assertThat(recipe.id()).isEqualTo(id("basic"));
        assertThat(recipe.machineId()).isEqualTo(id("test_cube"));
        assertThat(recipe.tickTime()).isEqualTo(20);
        assertThat(recipe.requirements()).singleElement().isInstanceOfSatisfying(ItemRequirement.class,
                input -> assertThat(input.count()).isEqualTo(2));
        assertThat(recipe.machineOutputs()).singleElement().isInstanceOfSatisfying(MachineOutput.ItemOutput.class, output -> {
            assertThat(output.stack().getItem()).isEqualTo(Items.IRON_BLOCK);
            assertThat(output.stack().getCount()).isEqualTo(1);
        });
    }

    @Test
    void appliesSchemaDefaultsWhenOptionalFieldsAreMissing() {
        var recipe = MachineRecipeJson.parse(id("defaults"), recipeJson(), registries);

        assertThat(recipe.requirements()).isEmpty();
        assertThat(recipe.maxThreads()).isEqualTo(1);
        assertThat(recipe.isParallelized()).isFalse();
        assertThat(recipe.doesCancelRecipeOnPerTickFailure()).isFalse();
        assertThat(recipe.allowPartialOutputs()).isFalse();
    }

    @Test
    void retains_registered_custom_outputs_from_outputs_json() {
        try (OutputRegistry.TestScope scope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(JsonOutput.TYPE);
            var json = recipeJson();
            var output = new JsonObject();
            output.addProperty("type", JsonOutput.TYPE.serializedId());
            json.add("outputs", array(output));
            var outputRequirement = new JsonObject();
            outputRequirement.addProperty("type", "neoforge:energy");
            outputRequirement.addProperty("io", "output");
            outputRequirement.addProperty("fe_per_tick", 7);
            json.add("requirements", array(outputRequirement));

            var recipe = MachineRecipeJson.parse(id("custom_output"), json, registries);

            assertThat(recipe.machineOutputs()).containsExactly(new JsonOutput(7, 1F));
            assertThat(recipe.requirements()).anyMatch(requirement -> requirement instanceof EnergyRequirement energy
                    && energy.io() == RecipeModifier.IOType.OUTPUT && energy.fePerTick() == 7);
        }
    }

    @Test
    void custom_output_requirement_is_not_duplicated_at_runtime() {
        try (OutputRegistry.TestScope scope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(JsonOutput.TYPE);
            MachineRecipe recipe = MachineRecipe.fromCanonical(id("custom_output_requirement"), id("test_cube"), 20,
                    List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 7, List.of())),
                    List.of(new JsonOutput(7, 1F)), List.of(), 0, 1, false, false,
                    List.of(), false, Set.of());

            assertThat(recipe.runtimeRequirements()).filteredOn(
                    requirement -> requirement.io() == RecipeModifier.IOType.OUTPUT).hasSize(1);
        }
    }

    @Test
    void parsesComplexInputsOutputsModifiersAndRequirements() {
        var json = recipeJson();
        var input = itemRequirement("input", "minecraft:iron_ingot", 2);
        input.add("components", componentJson());
        input.addProperty("consume_chance", 0.5F);
        var output = itemOutput("minecraft:iron_block", 1);
        var modifier = new JsonObject();
        modifier.addProperty("target", "minecraft:iron_ingot");
        modifier.addProperty("io", "input");
        modifier.addProperty("multiplier", 2.0F);
        modifier.addProperty("operation", 1);
        json.add("modifiers", array(modifier));
        json.addProperty("max_threads", 4);
        json.addProperty("parallelized", true);
        json.addProperty("cancelIfPerTickFails", true);
        json.addProperty("allow_partial_outputs", true);
        json.add("required_host_ids", arrayValue("mmcr:factory_controller"));
        json.add("level_requirements", array(levelRequirement()));
        var explicit = itemRequirement("input", "minecraft:iron_ingot", 1);
        explicit.add("tags", arrayValue("contract-tag"));
        explicit.add("components", componentJson());
        explicit.addProperty("consume_chance", 0.5F);
        var energyRequirement = new JsonObject();
        energyRequirement.addProperty("type", "neoforge:energy");
        energyRequirement.addProperty("io", "input");
        energyRequirement.addProperty("fe_per_tick", 80);
        var requirements = new JsonArray();
        requirements.add(explicit);
        requirements.add(energyRequirement);
        var fluidRequirement = new JsonObject();
        fluidRequirement.addProperty("type", "minecraft:fluid");
        fluidRequirement.addProperty("io", "output");
        fluidRequirement.add("stack", fluidStack("minecraft:water", 1000));
        fluidRequirement.addProperty("chance", 0.75F);
        requirements.add(fluidRequirement);
        json.add("requirements", requirements);
        json.add("outputs", array(output, fluidOutput("minecraft:water", 1000, 0.75F)));

        var recipe = MachineRecipeJson.parse(id("complex"), json, registries);

        assertThat(recipe.requirements()).filteredOn(requirement -> requirement.io() == RecipeModifier.IOType.INPUT)
                .hasSize(2);
        assertThat(recipe.requirements()).filteredOn(EnergyRequirement.class::isInstance).singleElement()
                .isInstanceOfSatisfying(EnergyRequirement.class, energy -> assertThat(energy.fePerTick()).isEqualTo(80));
        assertThat(recipe.machineOutputs()).filteredOn(MachineOutput.ItemOutput.class::isInstance).singleElement()
                .isInstanceOfSatisfying(MachineOutput.ItemOutput.class, outputValue -> {
            assertThat(outputValue.stack().getItem()).isEqualTo(Items.IRON_BLOCK);
            assertThat(outputValue.stack().getCount()).isEqualTo(1);
        });
        assertThat(recipe.machineOutputs()).filteredOn(MachineOutput.FluidOutput.class::isInstance).singleElement()
                .isInstanceOfSatisfying(MachineOutput.FluidOutput.class,
                        fluid -> assertThat(fluid.stack().getAmount()).isEqualTo(1000));
        assertThat(recipe.requirements()).anyMatch(requirement -> requirement instanceof FluidRequirement fluid
                && fluid.chance() == 0.75F);
        assertThat(recipe.modifiers()).singleElement().satisfies(modifierValue -> {
            assertThat(modifierValue.getOperation()).isEqualTo(RecipeModifier.Operation.MULTIPLY);
            assertThat(modifierValue.getModifier()).isEqualTo(2.0F);
        });
        assertThat(recipe.maxThreads()).isEqualTo(4);
        assertThat(recipe.isParallelized()).isTrue();
        assertThat(recipe.doesCancelRecipeOnPerTickFailure()).isTrue();
        assertThat(recipe.allowPartialOutputs()).isTrue();
        assertThat(recipe.requiredHostIds()).containsExactly(Identifier.parse("mmcr:factory_controller"));
        assertThat(recipe.levelRequirements()).singleElement().satisfies(level -> {
            assertThat(level.typeId()).isEqualTo(Identifier.parse("mmcr:test_level_type"));
            assertThat(level.levelId()).isEqualTo(Identifier.parse("mmcr:test_level"));
        });
        assertThat(recipe.requirements()).anyMatch(ItemRequirement.class::isInstance);
        assertThat(recipe.requirements().stream().filter(ItemRequirement.class::isInstance).findFirst().orElseThrow())
                .isInstanceOfSatisfying(ItemRequirement.class, requirement -> {
            assertThat(requirement.tags()).containsExactly("contract-tag");
            assertThat(requirement.count()).isEqualTo(1);
        });
        assertThat(recipe.requirements().stream().filter(ItemRequirement.class::isInstance).findFirst().orElseThrow())
                .isInstanceOfSatisfying(ItemRequirement.class, requirement -> {
            assertThat(requirement.components().isEmpty()).isFalse();
            assertThat(requirement.consumeChance()).isEqualTo(0.5F);
        });
    }

    @Test
    void machine_recipe_codec_round_trips_all_active_plan_requirements() {
        var recipe = RecipeTestSupport.create(id("codec_complete"), id("test_cube"), 40,
                List.of(new MachineIngredient.ItemIngredient(
                                net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_INGOT), 2),
                        new MachineIngredient.FluidIngredient(
                                net.neoforged.neoforge.fluids.crafting.FluidIngredient.of(
                                        net.minecraft.world.level.material.Fluids.WATER), 250),
                        new MachineIngredient.EnergyIngredient(80)),
                List.of(new ItemStack(Items.IRON_NUGGET, 3)),
                List.of(new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 1.5F,
                        RecipeModifier.Operation.MULTIPLY, true)),
                3, 2, true,
                List.of(new net.neoforged.neoforge.fluids.FluidStack(
                        net.minecraft.world.level.material.Fluids.WATER.builtInRegistryHolder(), 500)),
                List.of(), true, List.of(), true, Set.of(id("factory_controller")));

        var ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var encoded = MachineRecipe.CODEC.codec().encodeStart(ops, recipe).getOrThrow();
        var decoded = MachineRecipe.CODEC.codec().parse(ops, encoded).getOrThrow();

        assertThat(encoded.getAsJsonObject().has("requirements")).isTrue();
        assertThat(encoded.getAsJsonObject().has("inputs")).isFalse();
        assertThat(encoded.getAsJsonObject().has("outputs")).isFalse();
        assertThat(encoded.getAsJsonObject().has("fluid_outputs")).isFalse();
        assertThat(decoded.id()).isEqualTo(recipe.id());
        assertThat(decoded.machineId()).isEqualTo(recipe.machineId());
        assertThat(decoded.tickTime()).isEqualTo(recipe.tickTime());
        assertThat(decoded.requirements()).hasSize(5);
        assertThat(decoded.requirements()).anyMatch(requirement -> requirement instanceof ItemRequirement item
                && item.io() == RecipeModifier.IOType.INPUT && item.count() == 2);
        assertThat(decoded.requirements()).anyMatch(requirement -> requirement instanceof FluidRequirement fluid
                && fluid.io() == RecipeModifier.IOType.INPUT && fluid.amount() == 250);
        assertThat(decoded.requirements()).anyMatch(requirement -> requirement instanceof EnergyRequirement energy
                && energy.io() == RecipeModifier.IOType.INPUT && energy.fePerTick() == 80);
        assertThat(decoded.requirements()).anyMatch(requirement -> requirement instanceof ItemRequirement item
                && item.io() == RecipeModifier.IOType.OUTPUT && item.stack() != null
                && item.stack().is(Items.IRON_NUGGET) && item.stack().getCount() == 3);
        assertThat(decoded.requirements()).anyMatch(requirement -> requirement instanceof FluidRequirement fluid
                && fluid.io() == RecipeModifier.IOType.OUTPUT && fluid.stack().getAmount() == 500);
        assertThat(decoded.machineOutputs()).filteredOn(MachineOutput.ItemOutput.class::isInstance).singleElement()
                .satisfies(output -> assertThat(((MachineOutput.ItemOutput) output).stack().getCount()).isEqualTo(3));
        assertThat(decoded.machineOutputs()).filteredOn(MachineOutput.FluidOutput.class::isInstance).singleElement()
                .satisfies(output -> assertThat(((MachineOutput.FluidOutput) output).stack().getAmount()).isEqualTo(500));
        assertThat(decoded.modifiers()).singleElement().satisfies(modifier -> {
            assertThat(modifier.getTarget()).isEqualTo("item");
            assertThat(modifier.getModifier()).isEqualTo(1.5F);
            assertThat(modifier.affectsChance()).isTrue();
        });
        assertThat(decoded.priority()).isEqualTo(3);
        assertThat(decoded.maxThreads()).isEqualTo(2);
        assertThat(decoded.doesCancelRecipeOnPerTickFailure()).isTrue();
        assertThat(decoded.isParallelized()).isTrue();
        assertThat(decoded.allowPartialOutputs()).isTrue();
        assertThat(decoded.requiredHostIds()).containsExactly(id("factory_controller"));
        assertThat(decoded.machineOutputs()).hasSize(2);
        assertThat(decoded.modifiers()).as("modifiers").isEqualTo(recipe.modifiers());
        assertThat(decoded.levelRequirements()).as("level requirements").isEqualTo(recipe.levelRequirements());
        assertThat(decoded.requiredHostIds()).as("required host ids").isEqualTo(recipe.requiredHostIds());
    }

    @Test
    void rejectsUnknownTypeAndInvalidMachine() {
        var unknownType = recipeJson();
        unknownType.addProperty("type", "mmcr:not_machine_recipe");
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("wrong_type"), unknownType, registries))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(id("wrong_type"));
                    assertThat(error.path()).isEqualTo("type");
                    assertThat(error.getCause()).isNotNull();
                });

        var invalidMachine = recipeJson();
        invalidMachine.addProperty("machine", "mmcr:missing_machine");
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("wrong_machine"), invalidMachine, registries))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(id("wrong_machine"));
                    assertThat(error.path()).isEqualTo("machine");
                });
    }

    @Test
    void rejectsInvalidTickTimeAndMalformedCanonicalPayload() {
        var invalidTick = recipeJson();
        invalidTick.addProperty("tick_time", 0);
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("bad_tick"), invalidTick, registries))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(id("bad_tick"));
                    assertThat(error.path()).isEqualTo("tick_time");
                });

        var fractionalTick = recipeJson();
        fractionalTick.addProperty("tick_time", 1.5);
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("fractional_tick"), fractionalTick, registries))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(id("fractional_tick"));
                    assertThat(error.path()).isEqualTo("tick_time");
                    assertThat(error.getCause()).isNotNull();
                });

        var overflowingPriority = recipeJson();
        overflowingPriority.addProperty("priority", 2147483648L);
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("overflowing_priority"), overflowingPriority, registries))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(id("overflowing_priority"));
                    assertThat(error.path()).isEqualTo("priority");
                    assertThat(error.getCause()).isNotNull();
                });

        var malformed = recipeJson();
        malformed.add("requirements", array(new JsonObject()));
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("bad_requirement"), malformed, registries))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(id("bad_requirement"));
                    assertThat(error.path()).isEqualTo("requirements[0]");
                    assertThat(error.getCause()).isNotNull();
                });

        var primitive = new JsonObject();
        primitive.addProperty("type", "mmcr:machine_recipe");
        primitive.addProperty("machine", "mmcr:test_cube");
        primitive.addProperty("tick_time", 20);
        primitive.addProperty("requirements", "not-an-array");
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("primitive"), primitive, registries))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(id("primitive"));
                    assertThat(error.path()).isEqualTo("requirements");
                    assertThat(error.getCause()).isNotNull();
                });

        var negativeThreads = recipeJson();
        negativeThreads.addProperty("max_threads", -1);
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("negative_threads"), negativeThreads, registries))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error ->
                        assertThat(error.path()).isEqualTo("max_threads"));
    }

    private static JsonObject recipeJson() {
        var json = new JsonObject();
        json.addProperty("type", "mmcr:machine_recipe");
        json.addProperty("machine", "mmcr:test_cube");
        json.addProperty("tick_time", 20);
        json.add("requirements", new JsonArray());
        return json;
    }

    private record JsonOutput(int value, float chance) implements CustomOutput {
        private static final OutputType<JsonOutput> TYPE = new OutputType.Definition<>(
                id("json_output"), MapCodec.unit(() -> new JsonOutput(7, 1F)),
                (output, chance) -> new JsonOutput(output.value(), chance),
                (output, modifiers) -> output,
                output -> new JsonOutput(output.value(), output.chance()),
                OutputType.Presentation.defaults(id("json_output")), id("json_output").toString(),
                (output, tags) -> new EnergyRequirement(RecipeModifier.IOType.OUTPUT, output.value(), tags),
                requirement -> requirement instanceof EnergyRequirement energy
                        && energy.io() == RecipeModifier.IOType.OUTPUT);

        private JsonOutput {
            chance = MachineOutput.clampChance(chance);
        }

        @Override
        public OutputType<JsonOutput> outputType() {
            return TYPE;
        }
    }

    private static JsonObject itemInput(String item, int count) {
        var input = new JsonObject();
        input.addProperty("type", "minecraft:item");
        input.add("item", arrayValue(item));
        input.addProperty("count", count);
        return input;
    }

    private static JsonObject itemRequirement(String io, String item, int count) {
        var requirement = itemInput(item, count);
        requirement.addProperty("io", io);
        return requirement;
    }

    private static JsonObject componentJson() {
        var components = new JsonObject();
        components.addProperty("minecraft:repair_cost", 1);
        return components;
    }

    private static JsonObject levelRequirement() {
        var level = new JsonObject();
        level.addProperty("type", "mmcr:test_level_type");
        level.addProperty("level", "mmcr:test_level");
        return level;
    }

    private static JsonObject itemOutput(String item, int count) {
        var output = new JsonObject();
        output.addProperty("type", "mmcr:item");
        output.add("stack", stack(item, count));
        output.addProperty("chance", 1F);
        return output;
    }

    private static JsonObject fluidOutput(String fluid, int amount, float chance) {
        var output = new JsonObject();
        output.addProperty("type", "mmcr:fluid");
        output.add("stack", fluidStack(fluid, amount));
        output.addProperty("chance", chance);
        return output;
    }

    private static JsonObject fluidStack(String fluid, int amount) {
        var stack = new JsonObject();
        stack.addProperty("id", fluid);
        stack.addProperty("amount", amount);
        return stack;
    }

    private static JsonObject stack(String item, int count) {
        var stack = new JsonObject();
        stack.addProperty("id", item);
        stack.addProperty("count", count);
        return stack;
    }

    private static JsonArray array(JsonObject value) {
        var array = new JsonArray();
        array.add(value);
        return array;
    }

    private static JsonArray array(JsonObject... values) {
        var array = new JsonArray();
        for (JsonObject value : values) array.add(value);
        return array;
    }

    private static JsonArray arrayValue(String value) {
        var array = new JsonArray();
        array.add(value);
        return array;
    }

    private static Identifier id(String path) {
        return MMCR.id(path);
    }
}
