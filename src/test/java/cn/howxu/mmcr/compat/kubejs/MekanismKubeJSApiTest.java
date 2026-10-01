package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalOutput;
import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.machine.definition.MachineIoPlan;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeDeclarations;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the Mekanism-neutral KubeJS recipe builder surfaces parity with the public builder
 * while rejecting malformed inputs at the boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class MekanismKubeJSApiTest {
    private static final ResourceLocation MACHINE = MMCR.id("kubejs_mekanism_machine");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(false));
        MekanismRecipeTypes.register();
        MachineRegistry.register(new DynamicMachine(MACHINE, "KubeJS Mekanism Machine", new BlockArray(Map.of())));
    }

    @AfterEach
    void resetBridge() {
        MekanismBridgeBootstrap.resetForTesting();
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(false));
        MekanismRecipeTypes.register();
    }

    @Test
    void raw_rhino_accepts_canonical_and_custom_io_without_record_coercion() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            MekanismRecipeDeclarations.registerUnavailable();
            var canonical = new EnergyRequirement(4, List.of("raw"));
            var custom = new CustomRecipeIo(MekanismRecipeTypes.CHEMICAL, IOType.INPUT,
                    MachineRecipeBuilder.chemicalInputPayload(
                            ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 500), 0.25F));
            var builder = new MachineRecipeBuilderJS("mmcr:raw_rhino_io");
            var context = new ContextFactory().enter();
            var scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "builder", builder, context);
            ScriptableObject.putProperty(scope, "canonical", canonical, context);
            ScriptableObject.putProperty(scope, "custom", custom, context);
            context.evaluateString(scope, """
                    builder.addRequirement(canonical);
                    builder.addRequirement(custom);
                    """, "raw-rhino-io", 1, null);
            assertThat(builder.requirements.getFirst()).isSameAs(canonical);
            assertThat(builder.requirements).hasSize(2);
            assertThat(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, builder.requirements.get(1)).getOrThrow())
                    .isEqualTo(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE,
                            MachineRequirement.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow()).getOrThrow());
            var canonicalOutput = new EnergyRequirement(IOType.OUTPUT, 8, List.of("raw_output"));
            var customOutput = new CustomRecipeIo(MekanismRecipeTypes.CHEMICAL, IOType.OUTPUT,
                    MachineRecipeBuilder.chemicalOutputPayload(ChemicalOutput.of(ResourceLocation.parse("mekanism:hydrogen"), 200, 0.5F)));
            var plan = new MachineIoPlan(new CapabilitySnapshot(List.of()));
            ScriptableObject.putProperty(scope, "plan", plan, context);
            ScriptableObject.putProperty(scope, "canonicalOutput", canonicalOutput, context);
            ScriptableObject.putProperty(scope, "customOutput", customOutput, context);
            ScriptableObject.putProperty(scope, "policy", OutputPolicy.REQUIRE_FULL, context);
            context.evaluateString(scope, """
                    plan.addInput(canonical);
                    plan.add(custom);
                    plan.addOutput(canonicalOutput);
                    plan.addOutput(customOutput, policy);
                    """, "raw-rhino-tick-io", 1, null);
            var expectedOutputPayload = customOutput.payload().getAsJsonObject();
            expectedOutputPayload.addProperty("io", "output");
            assertThat(plan.requirements()).extracting(value -> MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow())
                    .containsExactlyElementsOf(List.of(canonical,
                            MachineRequirement.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow(), canonicalOutput,
                            MachineRequirement.CODEC.parse(JsonOps.INSTANCE, expectedOutputPayload).getOrThrow())
                            .stream().map(value -> MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow()).toList());
        }
    }

    @Test
    void rhino_helpers_route_inputs_and_outputs_through_the_core_custom_builder() {
        for (boolean loaded : new boolean[]{false, true}) {
            try (var requirements = RequirementHandlerRegistry.openTestScope();
                 var outputs = OutputRegistry.openTestScope()) {
                MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(loaded));
                if (loaded) MekanismRecipeTypes.register();
                else MekanismRecipeDeclarations.registerUnavailable();
                var builder = new MachineRecipeBuilderJS("mmcr:rhino_custom_helpers").recipePool(MACHINE.toString());
                var context = new ContextFactory().enter();
                var scope = context.initStandardObjects();
                ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
                ScriptableObject.putProperty(scope, "builder", builder, context);
                ScriptableObject.putProperty(scope, "input", IOType.INPUT, context);
                JsonObject payload = MachineRecipeBuilder.chemicalInputPayload(
                        ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 17), 0.75F);
                var tags = new JsonArray();
                tags.add("helper");
                payload.add("tags", tags);
                ScriptableObject.putProperty(scope, "payload", payload, context);
                context.evaluateString(scope, """
                        builder.addRequirement(api.chemicalInput('mekanism:oxygen', 1000, 0.25));
                        builder.addRequirement(api.chemicalTagInput('mekanism:fuels', 10, 0.5));
                        builder.addRequirement(api.heatTemperatureInput(450));
                        builder.addRequirement(api.customRecipeIo('mekanism:chemical', input, payload));
                        builder.addRequirement(api.fluidInputRequirement('minecraft:water', 100));
                        builder.addRequirement(api.chemicalOutput('mekanism:hydrogen', 200, 0.5));
                        builder.addRequirement(api.heatOutput(120));
                        """, "custom-helper-routing", 1, null);
                var recipe = builder.createObject();
                assertThat(recipe.requirements()).hasSize(7);
                assertThat(recipe.requirements().subList(0, 5)).extracting(MachineRequirement::io)
                        .containsExactly(IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT);
                var exact = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, recipe.requirements().get(0)).getOrThrow().getAsJsonObject();
                assertThat(exact.get("id").getAsString()).isEqualTo("mekanism:oxygen");
                assertThat(exact.has("kind")).isFalse();
                assertThat(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE,
                        MachineRequirement.CODEC.parse(JsonOps.INSTANCE, MachineRecipeBuilder.chemicalInputPayload(
                                ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1000), 0.25F)).getOrThrow()).getOrThrow())
                        .isEqualTo(exact);
                assertThat(recipe.requirements().getFirst().type().id()).isEqualTo(MekanismRecipeTypes.CHEMICAL);
                assertThat(exact.get("amount").getAsLong()).isEqualTo(1000);
                assertThat(exact.get("consume_chance").getAsFloat()).isEqualTo(0.25F);
                var tag = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, recipe.requirements().get(1)).getOrThrow().getAsJsonObject();
                assertThat(tag.get("kind").getAsString()).isEqualTo("tag");
                assertThat(tag.get("id").getAsString()).isEqualTo("mekanism:fuels");
                assertThat(tag.get("amount").getAsLong()).isEqualTo(10);
                assertThat(tag.get("consume_chance").getAsFloat()).isEqualTo(0.5F);
                assertThat(recipe.requirements().get(2).type().id()).isEqualTo(MekanismRecipeTypes.HEAT_TEMPERATURE);
                assertThat(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, recipe.requirements().get(2)).getOrThrow()
                        .getAsJsonObject().get("value").getAsDouble()).isEqualTo(450D);
                assertThat(recipe.requirements().get(3).tags()).containsExactly("helper");
                assertThat(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, recipe.requirements().get(4)).getOrThrow())
                        .isEqualTo(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE,
                                new KubeJSApi().fluidInputRequirement("minecraft:water", 100)).getOrThrow());
                assertThat(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, recipe.requirements().get(3)).getOrThrow())
                        .isEqualTo(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE,
                                MachineRequirement.CODEC.parse(JsonOps.INSTANCE, payload).getOrThrow()).getOrThrow());
                assertThat(recipe.machineOutputs()).extracting(output -> MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, output).getOrThrow())
                        .containsExactly(MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE,
                                        MachineOutput.CODEC.parse(JsonOps.INSTANCE, MachineRecipeBuilder.chemicalOutputPayload(
                                                ChemicalOutput.of(ResourceLocation.parse("mekanism:hydrogen"), 200, 0.5F))).getOrThrow()).getOrThrow(),
                                MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE,
                                        MachineOutput.CODEC.parse(JsonOps.INSTANCE, MachineRecipeBuilder.heatOutputPayload(120)).getOrThrow()).getOrThrow());
                assertThatThrownBy(() -> context.evaluateString(scope,
                        "builder.addRequirement(api.customRecipeIo('mmcr:missing', input, payload))", "unknown-helper", 1, null))
                        .hasMessageContaining("Unknown requirement type");
                payload.addProperty("io", "output");
                assertThatThrownBy(() -> context.evaluateString(scope,
                        "builder.addRequirement(api.customRecipeIo('mekanism:chemical', input, payload))", "wrong-direction", 1, null))
                        .hasMessageContaining("does not match");
            }
        }
    }

    @Test
    void kubejs_builder_chemical_input_emits_identifier_resolved_payload_with_public_factory_kind() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:chemical_input");
        builder.recipePool(MACHINE.toString())
                .chemicalInput("mekanism:oxygen", 1_000L);

        ChemicalIngredient ingredient = ChemicalIngredient.chemical(
                ResourceLocation.parse("mekanism:oxygen"), 1_000L);
        MachineRequirement expected = MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                MachineRecipeBuilder.chemicalInputPayload(ingredient)).getOrThrow();

        assertThat(builder.requirements).singleElement().isEqualTo(expected);
    }

    @Test
    void kubejs_builder_chemical_tag_input_emits_tag_kind_payload() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:chemical_tag");
        builder.recipePool(MACHINE.toString())
                .chemicalTagInput("mekanism:fuels", 10L);

        ChemicalIngredient ingredient = ChemicalIngredient.tag(
                ResourceLocation.parse("mekanism:fuels"), 10L);
        MachineRequirement expected = MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                MachineRecipeBuilder.chemicalInputPayload(ingredient)).getOrThrow();

        assertThat(builder.requirements).singleElement().isEqualTo(expected);
    }

    @Test
    void kubejs_builder_chemical_output_emits_output_kind_payload() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:chemical_output");
        builder.recipePool(MACHINE.toString())
                .chemicalOutput("mekanism:hydrogen", 200L, 0.5D);

        ChemicalOutput output = ChemicalOutput.of(
                ResourceLocation.parse("mekanism:hydrogen"), 200L, 0.5F);
        JsonObject payload = MachineRecipeBuilder.chemicalOutputPayload(output);

        assertThat(builder.customOutputs).singleElement().satisfies(parsed -> {
            assertThat(parsed.outputType().id()).isEqualTo(MekanismRecipeTypes.CHEMICAL);
            assertThat(payload.get("id").getAsString()).isEqualTo("mekanism:hydrogen");
            assertThat(payload.get("amount").getAsLong()).isEqualTo(200L);
            assertThat(payload.get("chance").getAsFloat()).isEqualTo(0.5F);
        });
    }

    @Test
    void kubejs_builder_heat_temperature_input_emits_minimum_temperature_payload() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:heat_input");
        builder.recipePool(MACHINE.toString())
                .heatTemperatureInput(450D);

        MachineRequirement expected = MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                MachineRecipeBuilder.heatInputPayload(450D)).getOrThrow();

        assertThat(builder.requirements).singleElement().isEqualTo(expected);
        assertThat(builder.requirements.get(0).type().id())
                .isEqualTo(MekanismRecipeTypes.HEAT_TEMPERATURE);
    }

    @Test
    void kubejs_builder_heat_output_emits_output_heat_payload() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:heat_output");
        builder.recipePool(MACHINE.toString())
                .heatOutput(1_200D);

        HeatRequirement heat = HeatRequirement.outputHeat(1_200D);
        JsonObject payload = MachineRecipeBuilder.heatOutputPayload(heat.value());

        assertThat(builder.customOutputs).singleElement().satisfies(parsed -> {
            assertThat(parsed.outputType().id()).isEqualTo(MekanismRecipeTypes.HEAT);
            assertThat(payload.get("value").getAsDouble()).isEqualTo(1_200D);
            assertThat(payload.get("io").getAsString()).isEqualTo("output");
        });
    }

    @Test
    void kubejs_builder_chemical_input_matches_public_builder_typeId() {
        MachineRecipeDefinition publicDef = MachineRecipeBuilder.recipe(MMCR.id("parity_public")).recipePool(MACHINE)
                .inputChemical(ResourceLocation.parse("mekanism:oxygen"), 1_000L)
                .build();
        MachineRecipeBuilderJS kubeBuilder = new MachineRecipeBuilderJS("mmcr:parity_kubejs");
        kubeBuilder.recipePool(MACHINE.toString())
                .chemicalInput("mekanism:oxygen", 1_000L);

        MachineRequirement publicIo = publicDef.requirements().getFirst();
        assertThat(kubeBuilder.createObject().requirements()).singleElement()
                .satisfies(req -> assertThat(req.type().id()).isEqualTo(publicIo.type().id()));
    }

    @Test
    void kubejs_builder_chemical_output_matches_public_builder_typeId() {
        MachineRecipeDefinition publicDef = MachineRecipeBuilder.recipe(MMCR.id("parity_public_out")).recipePool(MACHINE)
                .outputChemical(ResourceLocation.parse("mekanism:hydrogen"), 200L, 0.5F)
                .build();
        MachineRecipeBuilderJS kubeBuilder = new MachineRecipeBuilderJS("mmcr:parity_kubejs_out");
        kubeBuilder.recipePool(MACHINE.toString())
                .chemicalOutput("mekanism:hydrogen", 200L, 0.5D);

        CustomRecipeIo publicIo = (CustomRecipeIo) publicDef.customOutputs().get(0);
        assertThat(publicIo.typeId()).isEqualTo(MekanismRecipeTypes.CHEMICAL);
        assertThat(kubeBuilder.customOutputs.get(0).outputType().id()).isEqualTo(publicIo.typeId());
    }

    @Test
    void kubejs_builder_heat_temperature_input_matches_public_builder_typeId() {
        MachineRecipeDefinition publicDef = MachineRecipeBuilder.recipe(MMCR.id("parity_heat_in")).recipePool(MACHINE)
                .inputHeatTemperature(450D)
                .build();
        MachineRecipeBuilderJS kubeBuilder = new MachineRecipeBuilderJS("mmcr:parity_heat_in_kubejs");
        kubeBuilder.recipePool(MACHINE.toString())
                .heatTemperatureInput(450D);

        MachineRequirement publicIo = publicDef.requirements().getFirst();
        assertThat(kubeBuilder.createObject().requirements()).singleElement()
                .satisfies(req -> assertThat(req.type().id()).isEqualTo(publicIo.type().id()));
    }

    @Test
    void kubejs_builder_heat_output_matches_public_builder_typeId() {
        MachineRecipeDefinition publicDef = MachineRecipeBuilder.recipe(MMCR.id("parity_heat_out")).recipePool(MACHINE)
                .outputHeat(1_200D)
                .build();
        MachineRecipeBuilderJS kubeBuilder = new MachineRecipeBuilderJS("mmcr:parity_heat_out_kubejs");
        kubeBuilder.recipePool(MACHINE.toString())
                .heatOutput(1_200D);

        CustomRecipeIo publicIo = publicDef.customOutputs().get(0);
        assertThat(publicIo.typeId()).isEqualTo(MekanismRecipeTypes.HEAT);
        assertThat(kubeBuilder.customOutputs.get(0).outputType().id()).isEqualTo(publicIo.typeId());
    }

    @Test
    void kubejs_builder_chemical_input_rejects_blank_id() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:blank_id");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalInput("", 1_000L));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalInput("   ", 1_000L));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalInput(null, 1_000L));
    }

    @Test
    void kubejs_builder_chemical_input_rejects_non_positive_amount() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:non_positive")
                .recipePool(MACHINE.toString());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalInput("mekanism:oxygen", 0L));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalInput("mekanism:oxygen", -1L));
    }

    @Test
    void kubejs_builder_chemical_tag_input_rejects_blank_id_and_non_positive_amount() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:tag_invalid")
                .recipePool(MACHINE.toString());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalTagInput("", 1L));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalTagInput("mekanism:fuels", 0L));
    }

    @Test
    void kubejs_builder_chemical_output_rejects_invalid_chance() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:chance")
                .recipePool(MACHINE.toString());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalOutput("mekanism:oxygen", 1_000L, -0.1D));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalOutput("mekanism:oxygen", 1_000L, 1.1D));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalOutput("mekanism:oxygen", 1_000L, Double.NaN));
    }

    @Test
    void kubejs_builder_chemical_output_rejects_non_positive_amount() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:output_amount")
                .recipePool(MACHINE.toString());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalOutput("mekanism:oxygen", 0L, 1D));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.chemicalOutput("mekanism:oxygen", -5L, 1D));
    }

    @Test
    void kubejs_builder_heat_methods_reject_non_finite_or_negative_value() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:heat_invalid")
                .recipePool(MACHINE.toString());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.heatTemperatureInput(-1D));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.heatTemperatureInput(Double.NaN));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.heatOutput(-0.5D));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.heatOutput(Double.POSITIVE_INFINITY));
    }

    @Test
    void kubejs_builder_chemical_input_rejects_malformed_identifier() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:bad_id")
                .recipePool(MACHINE.toString());

        assertThatThrownBy(() -> builder.chemicalInput("mekanism:bad path", 1_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
