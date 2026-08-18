package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import org.nibelungorum.DefaultMachineLevels;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        registries = VanillaRegistries.createLookup();
    }

    @Test
    void parsesBasicMachineRecipeJson() {
        var json = recipeJson();
        json.add("inputs", array(itemInput("minecraft:iron_ingot", 2)));
        json.add("outputs", array(itemOutput("minecraft:iron_block", 1)));

        var recipe = MachineRecipeJson.parse(id("basic"), json, registries);

        assertThat(recipe.id()).isEqualTo(id("basic"));
        assertThat(recipe.machineId()).isEqualTo(id("alloy_furnace"));
        assertThat(recipe.tickTime()).isEqualTo(20);
        assertThat(recipe.inputs()).singleElement().isInstanceOfSatisfying(MachineIngredient.ItemIngredient.class,
                input -> assertThat(input.count()).isEqualTo(2));
        assertThat(recipe.outputs()).singleElement().satisfies(output -> {
            assertThat(output.getItem()).isEqualTo(net.minecraft.world.item.Items.IRON_BLOCK);
            assertThat(output.getCount()).isEqualTo(1);
        });
    }

    @Test
    void appliesSchemaDefaultsWhenOptionalFieldsAreMissing() {
        var recipe = MachineRecipeJson.parse(id("defaults"), recipeJson(), registries);

        assertThat(recipe.inputs()).isEmpty();
        assertThat(recipe.maxThreads()).isEqualTo(1);
        assertThat(recipe.isParallelized()).isFalse();
        assertThat(recipe.doesCancelRecipeOnPerTickFailure()).isFalse();
        assertThat(recipe.allowPartialOutputs()).isFalse();
    }

    @Test
    void parsesComplexInputsOutputsModifiersAndRequirements() {
        var json = recipeJson();
        var input = itemInput("minecraft:iron_ingot", 2);
        input.add("components", componentJson());
        input.addProperty("consume_chance", 0.5F);
        input.add("item", arrayValue("minecraft:iron_ingot"));
        json.add("inputs", array(input));
        var output = itemOutput("minecraft:iron_block", 1);
        json.add("outputs", array(output));
        json.addProperty("energy_per_tick", 80);
        json.add("fluid_outputs", array(fluidStack("minecraft:water", 1000)));
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
        energyRequirement.addProperty("type", "energy");
        energyRequirement.addProperty("io", "input");
        energyRequirement.addProperty("fe_per_tick", 80);
        var requirements = new JsonArray();
        requirements.add(explicit);
        requirements.add(energyRequirement);
        var fluidRequirement = new JsonObject();
        fluidRequirement.addProperty("type", "fluid");
        fluidRequirement.addProperty("io", "output");
        fluidRequirement.add("stack", fluidStack("minecraft:water", 1000));
        fluidRequirement.addProperty("chance", 0.75F);
        requirements.add(fluidRequirement);
        json.add("requirements", requirements);

        var recipe = MachineRecipeJson.parse(id("complex"), json, registries);

        assertThat(recipe.inputs()).hasSize(2);
        assertThat(recipe.inputs().getLast()).isEqualTo(new MachineIngredient.EnergyIngredient(80));
        assertThat(recipe.fluidOutputs()).singleElement().satisfies(fluid -> assertThat(fluid.getAmount()).isEqualTo(1000));
        assertThat(recipe.requirements()).anyMatch(requirement -> requirement instanceof cn.howxu.mmcr.api.recipe.requirement.FluidRequirement fluid
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
            assertThat(level.typeId()).isEqualTo(DefaultMachineLevels.THERMAL_SMELTING_COIL_TYPE);
            assertThat(level.levelId()).isEqualTo(DefaultMachineLevels.COPPER_COIL);
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
    void rejectsInvalidTickTimeAndMalformedIngredient() {
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

        var malformed = recipeJson();
        malformed.add("inputs", array(new JsonObject()));
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("bad_input"), malformed, registries))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(id("bad_input"));
                    assertThat(error.path()).isEqualTo("inputs[0]");
                    assertThat(error.getCause()).isNotNull();
                });

        var primitive = new JsonObject();
        primitive.addProperty("type", "mmcr:machine_recipe");
        primitive.addProperty("machine", "mmcr:alloy_furnace");
        primitive.addProperty("tick_time", 20);
        primitive.addProperty("inputs", "not-an-array");
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("primitive"), primitive, registries))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(id("primitive"));
                    assertThat(error.path()).isEqualTo("inputs");
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
        json.addProperty("machine", "mmcr:alloy_furnace");
        json.addProperty("tick_time", 20);
        return json;
    }

    private static JsonObject itemInput(String item, int count) {
        var input = new JsonObject();
        input.addProperty("type", "item");
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
        level.addProperty("type", DefaultMachineLevels.THERMAL_SMELTING_COIL_TYPE.toString());
        level.addProperty("level", DefaultMachineLevels.COPPER_COIL.toString());
        return level;
    }

    private static JsonObject itemOutput(String item, int count) {
        var output = new JsonObject();
        output.addProperty("id", item);
        output.addProperty("count", count);
        return output;
    }

    private static JsonObject fluidStack(String fluid, int amount) {
        var stack = new JsonObject();
        stack.addProperty("id", fluid);
        stack.addProperty("amount", amount);
        return stack;
    }

    private static JsonArray array(JsonObject value) {
        var array = new JsonArray();
        array.add(value);
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
