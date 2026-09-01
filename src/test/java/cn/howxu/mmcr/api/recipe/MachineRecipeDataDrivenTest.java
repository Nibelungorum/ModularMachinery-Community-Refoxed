package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.CustomRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the canonical data-driven machine recipe contract.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeDataDrivenTest {
    private static final Identifier REQUIREMENT_ID = Identifier.parse("mmcr_test:scalar_requirement");
    private static final Identifier OUTPUT_ID = Identifier.parse("mmcr_test:scalar_output");
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        registries = VanillaRegistries.createLookup();
    }

    @AfterEach
    void clearRegistries() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void canonical_json_dispatches_custom_requirement_and_output_payloads() {
        try (var requirementScope = cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry.openTestScope();
             var outputScope = OutputRegistry.openTestScope()) {
            cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry.register(TEST_REQUIREMENT_TYPE);
            OutputRegistry.register(TEST_OUTPUT_TYPE);

            JsonObject json = recipeJson();
            JsonObject requirement = new JsonObject();
            requirement.addProperty("type", REQUIREMENT_ID.toString());
            requirement.addProperty("io", "input");
            requirement.addProperty("amount", 17);
            json.add("requirements", array(requirement));

            JsonObject output = new JsonObject();
            output.addProperty("type", OUTPUT_ID.toString());
            output.addProperty("value", 23);
            output.addProperty("chance", 0.5F);
            json.add("outputs", array(output));

            MachineRecipe recipe = MachineRecipeJson.parse(Identifier.parse("mmcr_test:canonical"), json,
                    registries, ignored -> true);

            assertThat(recipe.requirements()).singleElement().isEqualTo(new TestRequirement(17));
            assertThat(recipe.machineOutputs()).singleElement().isEqualTo(new TestOutput(23, 0.5F));
        }
    }

    @Test
    void legacy_fields_normalize_to_canonical_requirements_and_outputs() {
        JsonObject json = recipeJson();
        JsonObject input = new JsonObject();
        input.addProperty("type", "item");
        JsonArray ingredient = new JsonArray();
        ingredient.add("minecraft:iron_ingot");
        input.add("item", ingredient);
        input.addProperty("count", 2);
        json.add("inputs", array(input));
        json.add("outputs", array(stack(Items.IRON_NUGGET, 3)));
        json.add("fluid_outputs", array(fluidStack("minecraft:water", 250)));
        json.addProperty("energy_per_tick", 40);

        MachineRecipe recipe = MachineRecipeJson.parse(Identifier.parse("mmcr_test:legacy"), json,
                registries, ignored -> true);

        assertThat(recipe.requirements()).hasSize(4);
        assertThat(recipe.requirements()).anyMatch(requirement -> requirement instanceof EnergyRequirement energy
                && energy.fePerTick() == 40);
        assertThat(recipe.machineOutputs()).hasSize(2);
        assertThat(recipe.outputs()).singleElement().satisfies(output -> {
            assertThat(output.getItem()).isEqualTo(Items.IRON_NUGGET);
            assertThat(output.getCount()).isEqualTo(3);
        });
        assertThat(recipe.fluidOutputs()).singleElement()
                .satisfies(output -> assertThat(output.getAmount()).isEqualTo(250));
    }

    @Test
    void empty_canonical_requirements_preserve_legacy_recipe_fields() {
        JsonObject json = recipeJson();
        JsonObject input = new JsonObject();
        input.addProperty("type", "item");
        input.add("item", new JsonPrimitive("minecraft:iron_ingot"));
        input.addProperty("count", 2);
        json.add("inputs", array(input));
        json.add("requirements", new JsonArray());
        json.add("outputs", array(stack(Items.IRON_NUGGET, 3)));
        json.add("fluid_outputs", array(fluidStack("minecraft:water", 250)));
        json.addProperty("energy_per_tick", 40);

        MachineRecipe recipe = MachineRecipeJson.parse(Identifier.parse("mmcr_test:empty_requirements"), json,
                registries, ignored -> true);

        assertThat(recipe.inputs()).hasSize(2);
        assertThat(recipe.outputs()).singleElement().satisfies(output -> {
            assertThat(output.getItem()).isEqualTo(Items.IRON_NUGGET);
            assertThat(output.getCount()).isEqualTo(3);
        });
        assertThat(recipe.fluidOutputs()).singleElement()
                .satisfies(output -> assertThat(output.getAmount()).isEqualTo(250));
        assertThat(recipe.requirements()).hasSize(4);
        assertThat(recipe.requirements()).anyMatch(requirement -> requirement instanceof EnergyRequirement energy
                && energy.fePerTick() == 40);
    }

    @Test
    void canonical_outputs_merge_legacy_fluid_outputs_without_loss() {
        JsonObject json = recipeJson();
        JsonObject canonicalOutput = new JsonObject();
        canonicalOutput.addProperty("type", "mmcr:item");
        canonicalOutput.add("stack", stack(Items.IRON_NUGGET, 3));
        canonicalOutput.addProperty("chance", 1F);
        json.add("outputs", array(canonicalOutput));
        json.add("fluid_outputs", array(fluidStack("minecraft:water", 250)));

        MachineRecipe recipe = MachineRecipeJson.parse(Identifier.parse("mmcr_test:canonical_and_fluid"), json,
                registries, ignored -> true);

        assertThat(recipe.machineOutputs()).hasSize(2);
        assertThat(recipe.outputs()).singleElement().satisfies(output ->
                assertThat(output.getCount()).isEqualTo(3));
        assertThat(recipe.fluidOutputs()).singleElement()
                .satisfies(output -> assertThat(output.getAmount()).isEqualTo(250));
    }

    @Test
    void codec_and_serializer_retain_legacy_fluid_outputs_with_canonical_outputs() {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        JsonObject json = recipeJson();
        JsonArray outputs = new JsonArray();
        outputs.add(MachineOutput.CODEC.encodeStart(ops,
                new MachineOutput.ItemOutput(new ItemStack(Items.IRON_NUGGET, 3), 1F)).getOrThrow());
        json.add("outputs", outputs);
        json.add("fluid_outputs", array(fluidStack("minecraft:water", 250)));
        json.add("requirements", new JsonArray());

        MachineRecipe decoded = MachineRecipe.CODEC.codec().parse(ops, json).getOrThrow();
        JsonElement serialized = MachineRecipeSerializer.INSTANCE.codec().codec()
                .encodeStart(ops, decoded).getOrThrow();
        MachineRecipe roundTrip = MachineRecipeSerializer.INSTANCE.codec().codec()
                .parse(ops, serialized).getOrThrow();

        assertThat(decoded.machineOutputs()).hasSize(2);
        assertThat(roundTrip.machineOutputs()).hasSize(2);
        assertThat(roundTrip.fluidOutputs()).singleElement()
                .satisfies(output -> assertThat(output.getAmount()).isEqualTo(250));
        assertThat(serialized.getAsJsonObject().has("fluid_outputs")).isFalse();
    }

    @Test
    void transitional_machine_outputs_does_not_duplicate_canonical_builtin_output() {
        JsonObject json = recipeJson();
        JsonObject output = new JsonObject();
        output.addProperty("type", "mmcr:item");
        output.add("stack", stack(Items.IRON_NUGGET, 3));
        json.add("outputs", array(output));
        json.add("machine_outputs", array(output.deepCopy()));

        MachineRecipe recipe = MachineRecipeJson.parse(Identifier.parse("mmcr_test:deduplicated"), json,
                registries, ignored -> true);

        assertThat(recipe.machineOutputs()).hasSize(1);
    }

    @Test
    void unknown_canonical_type_reports_recipe_id_and_json_path() {
        JsonObject json = recipeJson();
        JsonObject requirement = new JsonObject();
        requirement.addProperty("type", "mmcr_test:missing_requirement");
        json.add("requirements", array(requirement));

        assertThatThrownBy(() -> MachineRecipeJson.parse(Identifier.parse("mmcr_test:bad_type"), json,
                registries, ignored -> true))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(Identifier.parse("mmcr_test:bad_type"));
                    assertThat(error.path()).isEqualTo("requirements[0]");
                    assertThat(error.getMessage()).contains("Unknown requirement type");
                });
    }

    @Test
    void unknown_canonical_output_reports_recipe_id_and_json_path() {
        JsonObject json = recipeJson();
        JsonObject output = new JsonObject();
        output.addProperty("type", "mmcr_test:missing_output");
        json.add("outputs", array(output));

        assertThatThrownBy(() -> MachineRecipeJson.parse(Identifier.parse("mmcr_test:bad_output_type"), json,
                registries, ignored -> true))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(Identifier.parse("mmcr_test:bad_output_type"));
                    assertThat(error.path()).isEqualTo("outputs[0]");
                    assertThat(error.getMessage()).contains("Unknown output type");
                });
    }

    @Test
    void rejects_oversized_lists_before_decoding_children() {
        JsonObject json = recipeJson();
        JsonArray requirements = new JsonArray();
        for (int index = 0; index <= 4096; index++) requirements.add(new JsonObject());
        json.add("requirements", requirements);

        assertThatThrownBy(() -> MachineRecipeJson.parse(Identifier.parse("mmcr_test:too_many_requirements"), json,
                registries, ignored -> true))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(Identifier.parse("mmcr_test:too_many_requirements"));
                    assertThat(error.path()).isEqualTo("requirements");
                    assertThat(error.getMessage()).contains("too many entries");
                });
    }

    @Test
    void rejects_oversized_child_payloads_before_decoding_children() {
        JsonObject json = recipeJson();
        JsonObject requirement = new JsonObject();
        requirement.addProperty("type", REQUIREMENT_ID.toString());
        requirement.addProperty("io", "input");
        requirement.addProperty("amount", 17);
        requirement.addProperty("payload", "x".repeat(1_000_001));
        json.add("requirements", array(requirement));

        assertThatThrownBy(() -> MachineRecipeJson.parse(Identifier.parse("mmcr_test:large_requirement"), json,
                registries, ignored -> true))
                .isInstanceOfSatisfying(MachineRecipeJson.RecipeJsonException.class, error -> {
                    assertThat(error.recipeId()).isEqualTo(Identifier.parse("mmcr_test:large_requirement"));
                    assertThat(error.path()).isEqualTo("requirements[0]");
                    assertThat(error.getMessage()).contains("payload exceeds limit");
                });
    }

    @Test
    void custom_output_survives_with_id_equality_hash_code_and_serializer() {
        try (var outputScope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(TEST_OUTPUT_TYPE);
            Identifier recipeId = Identifier.parse("mmcr_test:custom_output_round_trip");
            MachineRecipe base = MachineRecipe.fromCanonical(recipeId, Identifier.parse("mmcr:test_machine_name"),
                    20, List.of(), List.of(), List.of(), 0, 1, false, false, List.of(), false, Set.of());
            MachineRecipe recipe = MachineRecipe.withAdditionalOutputs(base, List.of(new TestOutput(23, 0.5F)));
            MachineRecipe equalRecipe = MachineRecipe.fromCanonical(recipeId, recipe.machineId(), recipe.tickTime(),
                    List.of(), List.of(new TestOutput(23, 0.5F)), List.of(), 0, 1, false, false,
                    List.of(), false, Set.of());

            assertThat(recipe).isEqualTo(equalRecipe);
            assertThat(recipe.hashCode()).isEqualTo(equalRecipe.hashCode());
            assertThat(recipe.withId(Identifier.parse("mmcr_test:renamed")).machineOutputs())
                    .containsExactly(new TestOutput(23, 0.5F));

            JsonElement encoded = MachineRecipeSerializer.INSTANCE.codec().codec()
                    .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), recipe).getOrThrow();
            MachineRecipe decoded = MachineRecipeSerializer.INSTANCE.codec().codec()
                    .parse(registries.createSerializationContext(JsonOps.INSTANCE), encoded).getOrThrow();

            assertThat(decoded.machineOutputs()).containsExactly(new TestOutput(23, 0.5F));
            assertThat(encoded.getAsJsonObject().getAsJsonArray("outputs")).hasSize(1);
            assertThat(encoded.getAsJsonObject().has("machine_outputs")).isFalse();
        }
    }

    @Test
    void canonical_builtin_output_is_not_added_twice_to_runtime_requirements() {
        ItemStack stack = new ItemStack(Items.IRON_NUGGET, 3);
        MachineRecipe recipe = MachineRecipe.fromCanonical(
                Identifier.parse("mmcr_test:output_requirement_once"), Identifier.parse("mmcr:test_machine_name"), 20,
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack, 1F,
                        List.of("output-tag"))),
                List.of(new MachineOutput.ItemOutput(stack, 1F)), List.of(), 0, 1, false, false,
                List.of(), false, Set.of());

        assertThat(recipe.runtimeRequirements()).filteredOn(
                requirement -> requirement.io() == RecipeModifier.IOType.OUTPUT).hasSize(1);
    }

    @Test
    void different_canonical_builtin_outputs_remain_distinct_runtime_requirements() {
        MachineRecipe recipe = MachineRecipe.fromCanonical(
                Identifier.parse("mmcr_test:distinct_output_requirements"),
                Identifier.parse("mmcr:test_machine_name"), 20,
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        new ItemStack(Items.IRON_NUGGET, 1), 1F, List.of())),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET, 1), 1F)),
                List.of(), 0, 1, false, false, List.of(), false, Set.of());

        assertThat(recipe.runtimeRequirements()).filteredOn(
                requirement -> requirement.io() == RecipeModifier.IOType.OUTPUT).hasSize(2);
    }

    private static final RequirementHandler<TestRequirement> TEST_REQUIREMENT_HANDLER =
            (requirement, capabilities, context) -> null;
    private static final RequirementType<TestRequirement> TEST_REQUIREMENT_TYPE = new RequirementType.Definition<>(
            REQUIREMENT_ID,
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("type").forGetter(ignored -> REQUIREMENT_ID.toString()),
                    RecipeModifier.IO_TYPE_CODEC.fieldOf("io").forGetter(ignored -> RecipeModifier.IOType.INPUT),
                    Codec.INT.fieldOf("amount").forGetter(TestRequirement::amount)
            ).apply(instance, (ignored, io, amount) -> new TestRequirement(amount))),
            TEST_REQUIREMENT_HANDLER);
    private static final OutputType<TestOutput> TEST_OUTPUT_TYPE = new OutputType.Definition<>(
            OUTPUT_ID,
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("type").forGetter(ignored -> OUTPUT_ID.toString()),
                    Codec.INT.fieldOf("value").forGetter(TestOutput::value),
                    Codec.FLOAT.fieldOf("chance").forGetter(TestOutput::chance)
            ).apply(instance, (ignored, value, chance) -> new TestOutput(value, chance))),
            (output, chance) -> new TestOutput(output.value(), chance),
            (output, modifiers) -> output,
            output -> new TestOutput(output.value(), output.chance()));

    private record TestRequirement(int amount) implements CustomRequirement {
        @Override
        public RecipeModifier.IOType io() {
            return RecipeModifier.IOType.INPUT;
        }

        @Override
        public RequirementType<TestRequirement> type() {
            return TEST_REQUIREMENT_TYPE;
        }
    }

    private record TestOutput(int value, float chance) implements CustomOutput {
        @Override
        public OutputType<TestOutput> outputType() {
            return TEST_OUTPUT_TYPE;
        }
    }

    private static JsonObject recipeJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "mmcr:machine_recipe");
        json.addProperty("machine", "mmcr:test_machine_name");
        json.addProperty("tick_time", 20);
        return json;
    }

    private static JsonArray array(JsonObject value) {
        JsonArray values = new JsonArray();
        values.add(value);
        return values;
    }

    private static JsonObject stack(net.minecraft.world.item.Item item, int count) {
        JsonObject stack = new JsonObject();
        stack.addProperty("id", BuiltInRegistries.ITEM.getKey(item).toString());
        stack.addProperty("count", count);
        return stack;
    }

    private static JsonObject fluidStack(String id, int amount) {
        JsonObject stack = new JsonObject();
        stack.addProperty("id", id);
        stack.addProperty("amount", amount);
        return stack;
    }
}
