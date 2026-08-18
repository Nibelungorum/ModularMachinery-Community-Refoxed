package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
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
        json.add("inputs", array(itemInput("minecraft:iron_ingot", 2)));
        json.add("outputs", array(itemOutput("minecraft:iron_block", 1)));
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

        var recipe = MachineRecipeJson.parse(id("complex"), json, registries);

        assertThat(recipe.inputs()).hasSize(2);
        assertThat(recipe.inputs().getLast()).isEqualTo(new MachineIngredient.EnergyIngredient(80));
        assertThat(recipe.fluidOutputs()).singleElement().satisfies(fluid -> assertThat(fluid.getAmount()).isEqualTo(1000));
        assertThat(recipe.modifiers()).singleElement().satisfies(modifierValue -> {
            assertThat(modifierValue.getOperation()).isEqualTo(RecipeModifier.Operation.MULTIPLY);
            assertThat(modifierValue.getModifier()).isEqualTo(2.0F);
        });
        assertThat(recipe.maxThreads()).isEqualTo(4);
        assertThat(recipe.isParallelized()).isTrue();
        assertThat(recipe.doesCancelRecipeOnPerTickFailure()).isTrue();
        assertThat(recipe.allowPartialOutputs()).isTrue();
        assertThat(recipe.requiredHostIds()).containsExactly(Identifier.parse("mmcr:factory_controller"));
        assertThat(recipe.requirements()).anyMatch(ItemRequirement.class::isInstance);
    }

    @Test
    void rejectsUnknownTypeAndInvalidMachine() {
        var unknownType = recipeJson();
        unknownType.addProperty("type", "mmcr:not_machine_recipe");
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("wrong_type"), unknownType, registries))
                .hasMessageContaining("wrong_type").hasMessageContaining("type");

        var invalidMachine = recipeJson();
        invalidMachine.addProperty("machine", "mmcr:missing_machine");
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("wrong_machine"), invalidMachine, registries))
                .hasMessageContaining("wrong_machine").hasMessageContaining("machine");
    }

    @Test
    void rejectsInvalidTickTimeAndMalformedIngredient() {
        var invalidTick = recipeJson();
        invalidTick.addProperty("tick_time", 0);
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("bad_tick"), invalidTick, registries))
                .hasMessageContaining("bad_tick").hasMessageContaining("tick_time");

        var malformed = recipeJson();
        malformed.add("inputs", array(new JsonObject()));
        assertThatThrownBy(() -> MachineRecipeJson.parse(id("bad_input"), malformed, registries))
                .hasMessageContaining("bad_input").hasMessageContaining("inputs");
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
