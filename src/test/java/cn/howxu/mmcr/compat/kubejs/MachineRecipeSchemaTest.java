package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.CustomOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.recipe.requirement.CustomRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.ListRecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.StringComponent;
import dev.latvian.mods.kubejs.util.ErrorStack;
import dev.latvian.mods.rhino.Context;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeSchemaTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
        MachineRegistry.clearForTesting();
    }


    @Test
    void schema_exposes_modifiers_as_excluded_optional_raw_json_list() {
        var modifiers = MachineRecipeSchema.MODIFIERS;

        assertThat(MachineRecipeSchema.SCHEMA.keys).contains(modifiers);
        assertThat(MachineRecipeSchema.SCHEMA.includedKeys).doesNotContain(modifiers);
        assertThat(modifiers.name).isEqualTo("modifiers");
        assertThat(modifiers.excluded).isTrue();
        assertThat(modifiers.optional()).isTrue();
        assertThat(modifiers.optional.getInformativeValue()).isEqualTo(List.of());
        assertThat(modifiers.component).isInstanceOfSatisfying(ListRecipeComponent.class, component -> {
            assertThat(component.component()).isSameAs(MachineRecipeSchema.JSON_ELEMENT);
            assertThat(component.codec().parse(JsonOps.INSTANCE, new JsonArray()).getOrThrow())
                    .isInstanceOf(List.class);
        });
        assertThat(MachineRecipeSchema.JSON_ELEMENT.typeInfo().asClass()).isEqualTo(JsonElement.class);
    }

    @Test
    void schema_preserves_raw_requirements_and_level_requirements_for_custom_recipes() {
        assertThat(MachineRecipeSchema.SCHEMA.keys).contains(
                MachineRecipeSchema.REQUIREMENTS, MachineRecipeSchema.LEVEL_REQUIREMENTS);
        assertThat(MachineRecipeSchema.SCHEMA.includedKeys).doesNotContain(
                MachineRecipeSchema.REQUIREMENTS, MachineRecipeSchema.LEVEL_REQUIREMENTS);
        assertThat(MachineRecipeSchema.REQUIREMENTS.excluded).isTrue();
        assertThat(MachineRecipeSchema.LEVEL_REQUIREMENTS.excluded).isTrue();
    }

    @Test
    void schema_allows_empty_recipe_lists() {
        assertThat(((ListRecipeComponent<?>) MachineRecipeSchema.INPUTS.component).allowEmpty()).isTrue();
        assertThat(((ListRecipeComponent<?>) MachineRecipeSchema.OUTPUTS.component).allowEmpty()).isTrue();
        assertThat(((ListRecipeComponent<?>) MachineRecipeSchema.MODIFIERS.component).allowEmpty()).isTrue();
    }

    @Test
    void schema_exposes_parallel_opt_in_keys() {
        assertThat(MachineRecipeSchema.SCHEMA.keys).contains(
                MachineRecipeSchema.PARALLELIZED,
                MachineRecipeSchema.MAX_THREADS);
        assertThat(MachineRecipeSchema.PARALLELIZED.name).isEqualTo("parallelized");
        assertThat(MachineRecipeSchema.PARALLELIZED.optional()).isTrue();
        assertThat(MachineRecipeSchema.PARALLELIZED.optional.getInformativeValue()).isEqualTo(false);
        assertThat(MachineRecipeSchema.MAX_THREADS.name).isEqualTo("max_threads");
        assertThat(MachineRecipeSchema.MAX_THREADS.optional()).isTrue();
        assertThat(MachineRecipeSchema.MAX_THREADS.optional.getInformativeValue()).isEqualTo(1);
    }

    @Test
    void schema_exposes_partial_output_key_and_zero_arg_function() {
        assertThat(MachineRecipeSchema.SCHEMA.keys).contains(MachineRecipeSchema.ALLOW_PARTIAL_OUTPUTS);
        assertThat(MachineRecipeSchema.ALLOW_PARTIAL_OUTPUTS.name).isEqualTo("allow_partial_outputs");
        assertThat(MachineRecipeSchema.ALLOW_PARTIAL_OUTPUTS.optional()).isTrue();
        assertThat(MachineRecipeSchema.ALLOW_PARTIAL_OUTPUTS.optional.getInformativeValue()).isEqualTo(false);
        assertThat(MachineRecipeSchema.SCHEMA.functions.get("allowPartialOutputs").arguments()).isEmpty();
    }

    @Test
    void schema_allow_partial_outputs_function_writes_factory_readable_json() {
        var recipe = new KubeRecipe();
        recipe.json = new JsonObject();

        MachineRecipeSchema.SCHEMA.functions.get("allowPartialOutputs").function()
                .execute(new TestRecipeContext(recipe), List.of());

        assertThat(recipe.json.get("allow_partial_outputs").getAsBoolean()).isTrue();
        assertThat(MachineRecipeFactory.allowPartialOutputs(recipe)).isTrue();
    }

    @Test
    void builder_sets_partial_output_flag_on_registered_recipe() {
        var machineId = MMCR.id("partial_output_machine");
        MachineRegistry.register(new DynamicMachine(machineId, "Partial Output Machine", new BlockArray(Map.of())));
        var builder = new MachineRecipeBuilderJS(MMCR.id("partial_output_recipe"));

        assertThat(builder.allowPartialOutputs()).isSameAs(builder);
        builder.machine(machineId.toString()).build();

        assertThat(RecipeRegistry.getRecipe(MMCR.id("partial_output_recipe")).allowPartialOutputs()).isTrue();
    }

    @Test
    void schema_optional_partial_output_key_decodes_omitted_false() {
        var recipe = new KubeRecipe();
        recipe.json = new JsonObject();

        assertThat(MachineRecipeSchema.ALLOW_PARTIAL_OUTPUTS.optional.getInformativeValue()).isEqualTo(false);
        assertThat(MachineRecipeFactory.allowPartialOutputs(recipe)).isFalse();
    }

    @Test
    void builder_rejects_level_outside_declared_type() {
        var coilType = Identifier.parse("test:coil");
        var laserType = Identifier.parse("test:laser");
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(coilType, Component.literal("Coils")));
        TestBootstrap.registerType(new LevelType(laserType, Component.literal("Lasers")));
        TestBootstrap.registerLevel(new MachineLevel(Identifier.parse("test:laser"), laserType, 1,
                new BlockPredicate.OfBlockState(Blocks.GOLD_BLOCK.defaultBlockState()), ItemStack.EMPTY, LevelModifier.IDENTITY));

        assertThatIllegalArgumentException().isThrownBy(
                () -> new MachineRecipeBuilderJS("test:recipe").requiresLevel("test:coil", "test:laser"));
    }

    @Test
    void schema_exposes_requires_level_function_with_two_string_arguments() {
        var function = MachineRecipeSchema.SCHEMA.functions.get("requiresLevel");

        assertThat(function.arguments()).containsExactly(StringComponent.ID, StringComponent.ID);
    }

    void builder_creates_component_bearing_item_output() {
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS(MMCR.id("better_sword"));

        builder.itemOutputWithComponents("minecraft:diamond_sword", 1, json("""
                {
                  'minecraft:custom_name': { text: 'Better钻石剑' }
                }
                """));

        assertThat(builder.outputs).isEmpty();
    }

    @Test
    void public_identifier_builder_defers_sharpness_four_named_output_until_recipe_context_is_available() {
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS(MMCR.id("sharp_sword"));

        builder.itemOutputWithComponents("minecraft:diamond_sword", 1, json("""
                {
                  'minecraft:custom_name': { text: 'Sharp Sword' },
                  'minecraft:enchantments': { 'minecraft:sharpness': 4 }
                }
                """));

        assertThat(builder.outputs).isEmpty();
    }

    @Test
    void builder_keeps_plain_item_outputs_immediate() {
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS(MMCR.id("plain_output"));

        builder.itemOutput("minecraft:diamond_sword", 2);

        assertThat(builder.outputs).singleElement().satisfies(output -> {
            assertThat(output.getItem()).isSameAs(Items.DIAMOND_SWORD);
            assertThat(output.getCount()).isEqualTo(2);
        });
    }

    @Test
    void builder_exposes_smart_interface_input_and_output_functions() {
        var builder = new MachineRecipeBuilderJS(MMCR.id("smart_interface"));

        assertThat(builder.smartInterfaceInput("mode", 1F)).isSameAs(builder);
        assertThat(builder.smartInterfaceInput("mode", 1F, 2F)).isSameAs(builder);
        assertThat(builder.smartInterfaceOutput("mode", 9F)).isSameAs(builder);
        assertThat(builder.requirements).containsExactly(
                SmartInterfaceRequirement.input("mode", 1F),
                SmartInterfaceRequirement.input("mode", 1F, 2F),
                SmartInterfaceRequirement.output("mode", 9F));
    }

    @Test
    void schema_exposes_script_usable_smart_interface_factory_functions() {
        var input = new KubeRecipe();
        input.json = new JsonObject();
        var output = new KubeRecipe();
        output.json = new JsonObject();

        MachineRecipeSchema.SCHEMA.functions.get("smartInterfaceInput").function()
                .execute(new TestRecipeContext(input), List.of("mode", 1F));
        MachineRecipeSchema.SCHEMA.functions.get("smartInterfaceInputRange").function()
                .execute(new TestRecipeContext(input), List.of("temperature", 1F, 3F));
        MachineRecipeSchema.SCHEMA.functions.get("smartInterfaceOutput").function()
                .execute(new TestRecipeContext(output), List.of("mode", 2F));

        assertThat(input.json.getAsJsonArray("requirements")).hasSize(2);
        assertThat(output.json.getAsJsonArray("requirements")).hasSize(1);
        assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                input.json.getAsJsonArray("requirements").get(1)).getOrThrow())
                .isEqualTo(SmartInterfaceRequirement.input("temperature", 1F, 3F));
        assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                output.json.getAsJsonArray("requirements").get(0)).getOrThrow())
                .isEqualTo(SmartInterfaceRequirement.output("mode", 2F));
    }

    @Test
    void generic_schema_and_builder_paths_decode_registered_requirement_and_output() {
        var input = new cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement(
                cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.INPUT, 12);
        var output = new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F);
        var inputPayload = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, input).getOrThrow();
        var outputPayload = MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, output).getOrThrow();
        var schemaRecipe = new KubeRecipe();
        schemaRecipe.json = new JsonObject();

        MachineRecipeSchema.SCHEMA.functions.get("custom").function().execute(new TestRecipeContext(schemaRecipe),
                List.of(input.type().id().toString(), "input", inputPayload));
        MachineRecipeSchema.SCHEMA.functions.get("custom").function().execute(new TestRecipeContext(schemaRecipe),
                List.of(output.outputType().id().toString(), "output", outputPayload));
        var builder = new MachineRecipeBuilderJS(MMCR.id("generic_builder"))
                .custom(input.type().id().toString(), RecipeIo.INPUT, inputPayload)
                .custom(output.outputType().id().toString(), RecipeIo.OUTPUT, outputPayload);

        assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                schemaRecipe.json.getAsJsonArray("requirements").get(0)).getOrThrow()).isEqualTo(input);
        assertThat(schemaRecipe.json.getAsJsonArray("requirements")).hasSize(1);
        assertThat(MachineOutput.CODEC.parse(JsonOps.INSTANCE,
                schemaRecipe.json.getAsJsonArray("machine_outputs").get(0)).getOrThrow())
                .isInstanceOfSatisfying(MachineOutput.ItemOutput.class, parsed ->
                        assertThat(ItemStack.isSameItemSameComponents(parsed.stack(), output.stack())).isTrue());
        assertThat(builder.requirements).containsExactly(input);
        assertThat(builder.customOutputs).singleElement().isInstanceOfSatisfying(MachineOutput.ItemOutput.class,
                parsed -> assertThat(ItemStack.isSameItemSameComponents(parsed.stack(), output.stack())).isTrue());
        assertThatIllegalArgumentException().isThrownBy(() -> builder.custom("mmcr:missing", RecipeIo.INPUT, inputPayload));
    }

    @Test
    void generic_kubejs_paths_preserve_registered_output_without_requirement_factory() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(TestRequirement.TYPE);
            OutputRegistry.register(TestOutput.TYPE);
            var requirement = new TestRequirement(3);
            var output = new TestOutput(7, 1F);
            var requirementPayload = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, requirement).getOrThrow();
            var outputPayload = MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, output).getOrThrow();
            var recipe = new KubeRecipe();
            recipe.json = new JsonObject();

            MachineRecipeSchema.SCHEMA.functions.get("custom").function().execute(new TestRecipeContext(recipe),
                    List.of(TestRequirement.TYPE.id().toString(), "input", requirementPayload));
            MachineRecipeSchema.SCHEMA.functions.get("custom").function().execute(new TestRecipeContext(recipe),
                    List.of(TestOutput.TYPE.id().toString(), "output", outputPayload));
            var builder = new MachineRecipeBuilderJS(MMCR.id("generic_extension"))
                    .custom(TestRequirement.TYPE.id().toString(), RecipeIo.INPUT, requirementPayload)
                    .custom(TestOutput.TYPE.id().toString(), RecipeIo.OUTPUT, outputPayload);

            assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                    recipe.json.getAsJsonArray("requirements").get(0)).getOrThrow()).isEqualTo(requirement);
            assertThat(MachineOutput.CODEC.parse(JsonOps.INSTANCE,
                    recipe.json.getAsJsonArray("machine_outputs").get(0)).getOrThrow()).isEqualTo(output);
            assertThat(builder.requirements).containsExactly(requirement);
            assertThat(builder.customOutputs).containsExactly(output);
        }
    }

    private record TestRequirement(int value) implements CustomRequirement {
        private static final RequirementType<TestRequirement> TYPE = new RequirementType.Definition<>(
                MMCR.id("kubejs_test_requirement"), RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.fieldOf("type").forGetter(ignored -> "mmcr:kubejs_test_requirement"),
                        Codec.INT.fieldOf("value").forGetter(TestRequirement::value)
                ).apply(instance, (ignored, value) -> new TestRequirement(value))),
                (requirement, capabilities, context) -> null);

        @Override
        public RecipeModifier.IOType io() {
            return RecipeModifier.IOType.INPUT;
        }

        @Override
        public RequirementType<TestRequirement> type() {
            return TYPE;
        }
    }

    private record TestOutput(int value, float chance) implements CustomOutput {
        private static final OutputType<TestOutput> TYPE = new OutputType.Definition<>(
                MMCR.id("kubejs_test_output"), RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.fieldOf("type").forGetter(ignored -> "mmcr:kubejs_test_output"),
                        Codec.INT.fieldOf("value").forGetter(TestOutput::value),
                        Codec.FLOAT.fieldOf("chance").forGetter(TestOutput::chance)
                ).apply(instance, (ignored, value, chance) -> new TestOutput(value, chance))),
                (output, chance) -> new TestOutput(output.value(), chance), (output, modifiers) -> output, output -> output);

        @Override
        public OutputType<TestOutput> outputType() {
            return TYPE;
        }
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }

    private record TestRecipeContext(KubeRecipe recipe) implements RecipeScriptContext {
        @Override
        public ErrorStack errors() {
            return ErrorStack.NONE;
        }

        @Override
        public Context cx() {
            return null;
        }
    }

}
