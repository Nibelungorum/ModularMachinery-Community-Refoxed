package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.publicapi.RecipeApi;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.CustomRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.api.PublicRecipeAdapter;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for requirement and output registry dispatch used by custom recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
class CustomRecipeContractTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void registered_output_dispatches_to_its_requirement_and_round_trips_through_the_canonical_type() {
        MachineOutput output = new MachineOutput.ItemOutput(new ItemStack(Items.IRON_INGOT, 2), 1F);

        MachineRequirement requirement = OutputRegistry.toRequirement(output, List.of("contract"));

        assertThat(OutputRegistry.matchesOutputRequirement(output, requirement)).isTrue();
        assertThat(OutputRegistry.fromRequirement(requirement)).isEqualTo(output);
        assertThat(MachineOutput.copyOf(output)).isEqualTo(output).isNotSameAs(output);
    }

    @Test
    void requirement_registry_dispatches_registered_input_types_only() {
        MachineRequirement input = MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1));

        RequirementHandlerRegistry.registerBuiltIns();

        assertThat(RequirementHandlerRegistry.handlerFor(input.type())).isNotNull();
        assertThat(input.io()).isEqualTo(RecipeModifier.IOType.INPUT);
        assertThat(RecipeIo.INPUT.isInput()).isTrue();
    }

    @Test
    void custom_types_traverse_recipe_api_canonical_codecs_handlers_and_recipe_adaptation() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(TEST_REQUIREMENT_TYPE);
            OutputRegistry.register(TEST_OUTPUT_TYPE);

            TestRequirement input = new TestRequirement(RecipeModifier.IOType.INPUT, 3);
            TestOutput output = new TestOutput(7, 1F);
            JsonElement inputPayload = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, input).getOrThrow();
            JsonElement outputPayload = MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, output).getOrThrow();

            CustomRecipeIo customInput = RecipeApi.custom(TEST_REQUIREMENT_TYPE.id(), RecipeIo.INPUT, inputPayload);
            CustomRecipeIo customOutput = RecipeApi.custom(TEST_OUTPUT_TYPE.id(), RecipeIo.OUTPUT, outputPayload);
            MachineRequirement decodedInput = MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                    customInput.payload()).getOrThrow();
            MachineOutput decodedOutput = MachineOutput.CODEC.parse(JsonOps.INSTANCE,
                    customOutput.payload()).getOrThrow();

            assertThat(decodedInput.type()).isSameAs(TEST_REQUIREMENT_TYPE);
            assertThat(decodedInput).isEqualTo(input);
            assertThat(decodedOutput.outputType()).isSameAs(TEST_OUTPUT_TYPE);
            assertThat(decodedOutput).isEqualTo(output);
            assertThat(RequirementHandlerRegistry.handlerFor(TEST_REQUIREMENT_TYPE)).isSameAs(TEST_HANDLER);

            var definition = MachineRecipeBuilder.recipe(id("custom_contract"), id("machine"))
                    .custom(customInput)
                    .custom(customOutput)
                    .build();
            var recipe = PublicRecipeAdapter.toRecipe(definition,
                    new MMCRMachineStructuresEvent.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));

            assertThat(recipe.requirements()).containsExactly(input);
            assertThat(recipe.inputs()).containsExactly(new MachineIngredient.EnergyIngredient(input.value()));
            assertThat(recipe.machineOutputs()).containsExactly(output);
            assertThat(LEGACY_INPUT_CALLS.get()).isGreaterThan(0);
        }
    }

    private static final Identifier TEST_REQUIREMENT_ID = id("custom_contract_requirement");
    private static final Identifier TEST_OUTPUT_ID = id("custom_contract_output");
    private static final AtomicInteger LEGACY_INPUT_CALLS = new AtomicInteger();
    private static final MapCodec<TestRequirement> TEST_REQUIREMENT_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> TEST_REQUIREMENT_ID.toString()),
            RecipeModifier.IO_TYPE_CODEC.fieldOf("io").forGetter(TestRequirement::io),
            Codec.INT.fieldOf("value").forGetter(TestRequirement::value)
    ).apply(instance, (ignored, io, value) -> new TestRequirement(io, value)));
    private static final RequirementHandler<TestRequirement> TEST_HANDLER = new RequirementHandler<>() {
        @Override
        public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return null;
        }

        @Override
        public MachineIngredient legacyInput(TestRequirement requirement) {
            LEGACY_INPUT_CALLS.incrementAndGet();
            return new MachineIngredient.EnergyIngredient(requirement.value());
        }
    };
    private static final RequirementType<TestRequirement> TEST_REQUIREMENT_TYPE = new RequirementType.Definition<>(
            TEST_REQUIREMENT_ID, TEST_REQUIREMENT_CODEC, TEST_HANDLER);
    private static final OutputType<TestOutput> TEST_OUTPUT_TYPE = new OutputType.Definition<>(
            TEST_OUTPUT_ID,
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("type").forGetter(ignored -> TEST_OUTPUT_ID.toString()),
                    Codec.INT.fieldOf("value").forGetter(TestOutput::value),
                    Codec.FLOAT.fieldOf("chance").forGetter(TestOutput::chance)
            ).apply(instance, (ignored, value, chance) -> new TestOutput(value, chance))),
            (value, chance) -> new TestOutput(value.value(), chance),
            (value, modifiers) -> new TestOutput(value.value(), value.chance()),
            value -> new TestOutput(value.value(), value.chance()));

    private record TestRequirement(RecipeModifier.IOType io, int value) implements CustomRequirement {
        @Override
        public RequirementType<TestRequirement> type() {
            return TEST_REQUIREMENT_TYPE;
        }
    }

    private record TestOutput(int value, float chance) implements CustomOutput {
        private TestOutput {
            chance = MachineOutput.clampChance(chance);
        }

        @Override
        public OutputType<TestOutput> outputType() {
            return TEST_OUTPUT_TYPE;
        }
    }

    private static Identifier id(String path) {
        return MMCR.id(path);
    }
}
