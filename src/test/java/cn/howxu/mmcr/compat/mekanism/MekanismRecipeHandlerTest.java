package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.api.machine.definition.MachineIoPlan;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalOutput;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler.ResourceWakeup;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortCapability;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortCapability;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.attribute.ChemicalAttribute;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import mekanism.api.datamaps.chemical.attribute.IChemicalAttribute;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies optional Mekanism recipe handler planning behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
class MekanismRecipeHandlerTest {
    private RequirementHandlerRegistry.TestScope requirementScope;
    private OutputRegistry.TestScope outputScope;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrapCapabilities();
        BuiltinFailureReasons.register();
        MekanismBridgeBootstrap.bootstrap();
    }

    @BeforeEach
    void openRegistryScope() {
        requirementScope = RequirementHandlerRegistry.openTestScope();
        outputScope = OutputRegistry.openTestScope();
    }

    @AfterEach
    void resetBridge() {
        outputScope.close();
        requirementScope.close();
        MekanismBridgeBootstrap.resetForTesting();
    }

    @Test
    void rhino_custom_helpers_simulate_then_commit_tick_chemical_and_heat_io() {
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(true));
        MekanismRecipeTypes.register();
        var chemical = registerChemical("rhino_tick_custom");
        var resource = new ChemicalStack(chemical, 1);
        var inputTank = new FakeChemicalTank(1000, ChemicalAttributeValidator.ALWAYS_ALLOW);
        var outputTank = new FakeChemicalTank(1000, ChemicalAttributeValidator.ALWAYS_ALLOW);
        inputTank.setStack(resource.copyWithAmount(1000));
        var temperaturePort = new FakeHeatPort(360);
        var heatOutputPort = new FakeHeatPort(300, IOType.OUTPUT);
        var energy = new LongEnergyStorage(100, 100, null);
        energy.setAmount(4);
        var plan = new MachineIoPlan(new CapabilitySnapshot(List.of(
                new FakeChemicalPort(inputTank, IOType.INPUT),
                new FakeChemicalPort(outputTank, IOType.OUTPUT), temperaturePort, heatOutputPort,
                new EnergyHatchCapability(energy, IOType.INPUT))));
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        var outputPayload = MachineRecipeBuilder.chemicalOutputPayload(
                ChemicalOutput.of(chemical.key().location(), 200, 1F));
        ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
        ScriptableObject.putProperty(scope, "plan", plan, context);
        ScriptableObject.putProperty(scope, "chemicalId", chemical.key().location().toString(), context);
        ScriptableObject.putProperty(scope, "output", RecipeModifier.IOType.OUTPUT, context);
        ScriptableObject.putProperty(scope, "input", RecipeModifier.IOType.INPUT, context);
        ScriptableObject.putProperty(scope, "payload", outputPayload, context);
        context.evaluateString(scope, """
                plan.addOutput(api.customRecipeIo('mekanism:chemical', output, payload));
                plan.addInput(api.chemicalInput(chemicalId, 400));
                plan.addInput(api.heatTemperatureInput(350));
                plan.addInput(api.energyRequirement(input, 4));
                plan.add(api.heatOutput(5));
                """, "tick-custom-helpers", 1, null);
        assertThat(plan.requirements()).extracting(MachineRequirement::io).containsExactly(
                RecipeModifier.IOType.INPUT, RecipeModifier.IOType.INPUT, RecipeModifier.IOType.INPUT,
                RecipeModifier.IOType.OUTPUT, RecipeModifier.IOType.OUTPUT);
        assertThat(plan.requirements()).extracting(value -> value.type().id()).containsExactly(
                MekanismRecipeTypes.CHEMICAL, MekanismRecipeTypes.HEAT_TEMPERATURE, EnergyRequirement.TYPE.id(),
                MekanismRecipeTypes.CHEMICAL, MekanismRecipeTypes.HEAT);
        assertThat(((LoadedChemicalRequirement) plan.requirements().getFirst()).ingredient())
                .isEqualTo(ChemicalIngredient.chemical(chemical.key().location(), 400));
        assertThat(((LoadedHeatRequirement) plan.requirements().get(1)).heat().value()).isEqualTo(350);
        assertThat(MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE,
                OutputRegistry.fromRequirement(plan.requirements().get(3))).getOrThrow())
                .isEqualTo(MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE,
                        MachineOutput.CODEC.parse(JsonOps.INSTANCE, outputPayload).getOrThrow()).getOrThrow());
        assertThatThrownBy(() -> context.evaluateString(scope,
                "plan.addInput(api.chemicalOutput(chemicalId, 1, 1))", "tick-output-as-input", 1, null))
                .hasMessageContaining("direction must be INPUT");
        assertThatThrownBy(() -> context.evaluateString(scope,
                "plan.addOutput(api.heatTemperatureInput(350))", "tick-input-as-output", 1, null))
                .hasMessageContaining("direction must be OUTPUT");
        assertThat(plan.requirements()).hasSize(5);
        assertThat(plan.simulate()).satisfies(simulation -> {
            assertThat(simulation.failure()).isNull();
            assertThat(simulation.inputsSatisfied()).isTrue();
            assertThat(simulation.energySatisfied()).isTrue();
            assertThat(simulation.outputs()).containsExactly(new OutputSimulation(200, 200, OutputFit.FULL),
                    new OutputSimulation(5, 5, OutputFit.FULL));
        });
        assertThat(inputTank.amount()).isEqualTo(1000);
        assertThat(energy.getAmountAsLong()).isEqualTo(4);
        assertThat(outputTank.amount()).isZero();
        assertThat(temperaturePort.handledHeat()).isZero();
        assertThat(heatOutputPort.handledHeat()).isZero();
        assertThat(plan.commit().successful()).isTrue();
        assertThat(inputTank.amount()).isEqualTo(600);
        assertThat(energy.getAmountAsLong()).isZero();
        assertThat(outputTank.amount()).isEqualTo(200);
        assertThat(outputTank.getStack().getChemical()).isSameAs(resource.getChemical());
        assertThat(temperaturePort.temperature()).isEqualTo(360);
        assertThat(temperaturePort.handledHeat()).isZero();
        assertThat(heatOutputPort.handledHeat()).isEqualTo(5);
        assertThat(plan.commit().successful()).isFalse();
        assertThat(outputTank.amount()).isEqualTo(200);
    }

    @Test
    void recipe_types_only_contains_the_bridge_registration_entry_point() {
        assertThat(Arrays.stream(MekanismRecipeTypes.class.getDeclaredMethods())
                .map(Method::getName))
                .containsExactly("register");
    }

    @Test
    void unavailable_chemical_input_is_blocked_with_registered_reason() {
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(false));
        LoadedChemicalRequirement.installUnavailableHandler();

        LoadedChemicalRequirement requirement = LoadedChemicalRequirement.input(
                ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1_000));
        RequirementPlan result = chemicalHandler().plan(requirement, List.of(), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(MekanismFailureReasons.MEKANISM_UNAVAILABLE);
    }

    @Test
    void unavailable_bridge_registers_without_linking_loaded_classes() throws Exception {
        ClassLoader parent = MekanismRecipeTypes.class.getClassLoader();
        ClassLoader isolated = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("cn.howxu.mmcr.compat.mekanism.loaded.")) {
                    throw new ClassNotFoundException(name);
                }
                if (name.equals("cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes")
                        || name.equals("cn.howxu.mmcr.compat.mekanism.UnavailableMekanismBridge")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded == null) {
                            byte[] bytes;
                            try (var stream = MekanismRecipeTypes.class.getResourceAsStream(
                                    "/" + name.replace('.', '/') + ".class")) {
                                if (stream == null) throw new ClassNotFoundException(name);
                                bytes = stream.readAllBytes();
                            } catch (java.io.IOException exception) {
                                throw new ClassNotFoundException(name, exception);
                            }
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        }
                        if (resolve) resolveClass(loaded);
                        return loaded;
                    }
                }
                return super.loadClass(name, resolve);
            }
        };

        Class<?> bridgeClass = Class.forName(
                "cn.howxu.mmcr.compat.mekanism.UnavailableMekanismBridge", true, isolated);
        Field instance = bridgeClass.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        MekanismBridge bridge = (MekanismBridge) instance.get(null);

        bridge.registerRecipeTypes(MekanismRecipeTypes.CHEMICAL, MekanismRecipeTypes.HEAT_TEMPERATURE,
                MekanismRecipeTypes.HEAT);
    }

    @Test
    void unavailable_registration_keeps_identifier_codecs_canonical() {
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(false));
        MekanismRecipeTypes.register();

        assertThat(RequirementHandlerRegistry.typeFor(MekanismRecipeTypes.CHEMICAL)).isNotNull();
        assertThat(RequirementHandlerRegistry.typeFor(MekanismRecipeTypes.HEAT_TEMPERATURE)).isNotNull();
        assertThat(RequirementHandlerRegistry.typeFor(MekanismRecipeTypes.HEAT)).isNotNull();
        assertThat(OutputRegistry.typeFor(MekanismRecipeTypes.CHEMICAL)).isNotNull();
        assertThat(OutputRegistry.typeFor(MekanismRecipeTypes.HEAT)).isNotNull();

        JsonObject payload = new JsonObject();
        payload.addProperty("type", MekanismRecipeTypes.CHEMICAL.toString());
        payload.addProperty("kind", "chemical");
        payload.addProperty("id", "mekanism:oxygen");
        payload.addProperty("amount", 1_000L);
        payload.addProperty("io", "input");
        MachineRequirement requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, payload).getOrThrow();
        @SuppressWarnings("unchecked")
        RequirementHandler<MachineRequirement> handler = (RequirementHandler<MachineRequirement>)
                RequirementHandlerRegistry.handlerFor(requirement.type());

        RequirementPlan result = handler.plan(requirement, List.of(), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(MekanismFailureReasons.MEKANISM_UNAVAILABLE);
        assertThat(OutputRegistry.fromRequirement(requirement)).isNull();
    }

    @Test
    void loaded_registration_installs_loaded_canonical_requirement_types() {
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(true));
        MekanismRecipeTypes.register();

        assertThat(RequirementHandlerRegistry.typeFor(MekanismRecipeTypes.CHEMICAL))
                .isSameAs(LoadedChemicalRequirement.TYPE);
        assertThat(RequirementHandlerRegistry.typeFor(MekanismRecipeTypes.HEAT_TEMPERATURE))
                .isSameAs(LoadedHeatRequirement.TEMPERATURE_TYPE);
        assertThat(RequirementHandlerRegistry.typeFor(MekanismRecipeTypes.HEAT))
                .isSameAs(LoadedHeatRequirement.HEAT_TYPE);
        assertThat(OutputRegistry.typeFor(MekanismRecipeTypes.CHEMICAL))
                .isSameAs(LoadedChemicalOutput.TYPE);
        assertThat(OutputRegistry.typeFor(MekanismRecipeTypes.HEAT))
                .isSameAs(LoadedHeatOutput.TYPE);

        JsonObject payload = new JsonObject();
        payload.addProperty("type", MekanismRecipeTypes.CHEMICAL.toString());
        payload.addProperty("id", "mekanism:oxygen");
        payload.addProperty("amount", 1_000L);
        MachineRequirement requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, payload).getOrThrow();
        assertThat(requirement).isInstanceOf(LoadedChemicalRequirement.class);
        assertThat(requirement.type()).isSameAs(LoadedChemicalRequirement.TYPE);

        JsonObject outputPayload = new JsonObject();
        outputPayload.addProperty("type", MekanismRecipeTypes.CHEMICAL.toString());
        outputPayload.addProperty("id", "mekanism:oxygen");
        outputPayload.addProperty("amount", 1_000L);
        MachineOutput output = MachineOutput.CODEC.parse(JsonOps.INSTANCE, outputPayload).getOrThrow();
        assertThat(output.outputType()).isSameAs(LoadedChemicalOutput.TYPE);
    }

    @Test
    void minimum_temperature_input_does_not_mutate_heat() {
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(true));
        LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());
        FakeHeatPort port = new FakeHeatPort(360D);

        LoadedHeatRequirement requirement = LoadedHeatRequirement.minimumTemperature(350D);
        RequirementPlan result = heatHandler().plan(requirement, List.of(port), testContext());

        assertThat(result.successful()).isTrue();
        assertThat(port.temperature()).isEqualTo(360D);
        assertThat(port.handledHeat()).isEqualTo(0D);
    }

    @Test
    void non_radioactive_attribute_rejection_reports_output_blocked() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
        Holder.Reference<Chemical> chemical = registerChemical("attribute_rejected");
        ChemicalAttributeValidator rejectAttributes =
                new ChemicalAttributeValidator() {
            @SuppressWarnings("removal")
            @Override
            public boolean validate(ChemicalAttribute attribute) {
                return false;
            }

            @Override
            public boolean validate(IChemicalAttribute attribute) {
                return false;
            }

            @Override
            public boolean process(Chemical value) {
                return false;
            }
        };
        FakeChemicalTank tank = new FakeChemicalTank(1_000L, rejectAttributes);
        FakeChemicalPort port = new FakeChemicalPort(tank, IOType.OUTPUT);

        RequirementPlan result = chemicalHandler().plan(
                LoadedChemicalRequirement.output(chemical.key().location(), 100L, 1F),
                List.of(port), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED);
    }

    @Test
    void exact_chemical_input_is_reserved_and_extracted_on_commit() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
        Holder.Reference<Chemical> chemical = registerChemical("exact_input");
        FakeChemicalTank tank = new FakeChemicalTank(2_000L, ChemicalAttributeValidator.ALWAYS_ALLOW);
        ChemicalStack resource = new ChemicalStack(chemical, 1L);
        tank.setStack(resource.copyWithAmount(1_000L));
        FakeChemicalPort port = new FakeChemicalPort(tank, IOType.INPUT);

        RequirementPlan planned = chemicalHandler().plan(
                LoadedChemicalRequirement.input(
                        ChemicalIngredient.chemical(chemical.key().location(), 1_000L)),
                List.of(port), testContext());
        RequirementPlan materialized = planned.materialize(1, new PlanningReservations(), null);

        assertThat(materialized.successful()).isTrue();
        CapabilityResult result = materialized.operations().getFirst().commit();
        assertThat(result.success()).isTrue();
        assertThat(tank.amount()).isZero();
    }

    @Test
    void unknown_exact_chemical_is_a_type_mismatch() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());

        RequirementPlan result = chemicalHandler().plan(
                LoadedChemicalRequirement.input(
                        ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:not_registered"), 1L)),
                List.of(), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH);
    }

    @Test
    void registered_exact_chemical_input_without_matching_ports_reports_insufficient_resource() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
        Holder.Reference<Chemical> chemical = registerChemical("missing_input");

        RequirementPlan result = chemicalHandler().plan(
                LoadedChemicalRequirement.input(
                        ChemicalIngredient.chemical(chemical.key().location(), 1L)),
                List.of(), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.MISSING_INPUT);
    }

    @Test
    void chemical_input_with_only_output_direction_ports_reports_insufficient_resource() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
        Holder.Reference<Chemical> chemical = registerChemical("output_only");
        FakeChemicalTank tank = new FakeChemicalTank(2_000L, ChemicalAttributeValidator.ALWAYS_ALLOW);
        tank.setStack(new ChemicalStack(chemical, 1_000L));

        RequirementPlan result = chemicalHandler().plan(
                LoadedChemicalRequirement.input(ChemicalIngredient.chemical(chemical.key().location(), 1L)),
                List.of(new FakeChemicalPort(tank, IOType.OUTPUT)), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.MISSING_INPUT);
    }

    @Test
    void radioactive_chemical_rejection_reports_radioactivity() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
        Holder.Reference<Chemical> chemical = registerChemical("radioactive", true);
        ChemicalAttributeValidator rejectAttributes =
                new ChemicalAttributeValidator() {
            @SuppressWarnings("removal")
            @Override
            public boolean validate(ChemicalAttribute attribute) {
                return false;
            }

            @Override
            public boolean validate(IChemicalAttribute attribute) {
                return false;
            }

            @Override
            public boolean process(Chemical value) {
                return false;
            }
        };
        FakeChemicalPort port = new FakeChemicalPort(
                new FakeChemicalTank(1_000L, rejectAttributes), IOType.OUTPUT, true);

        RequirementPlan result = chemicalHandler().plan(
                LoadedChemicalRequirement.output(chemical.key().location(), 1L, 1F),
                List.of(port), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(MekanismFailureReasons.CHEMICAL_RADIOACTIVITY_REJECTED);
    }

    @Test
    void chemical_port_default_radioactive_flag_is_false() {
        FakeChemicalPort port = new FakeChemicalPort(
                new FakeChemicalTank(1_000L, ChemicalAttributeValidator.ALWAYS_ALLOW), IOType.OUTPUT);

        assertThat(port.radioactive()).isFalse();
    }

    @Test
    void radioactive_chemical_output_with_only_normal_ports_reports_missing_output() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
        Holder.Reference<Chemical> chemical = registerChemical("missing_radioactive_output", true);
        FakeChemicalPort port = new FakeChemicalPort(
                new FakeChemicalTank(1_000L, ChemicalAttributeValidator.ALWAYS_ALLOW), IOType.OUTPUT, false);

        RequirementPlan result = chemicalHandler().plan(
                LoadedChemicalRequirement.output(chemical.key().location(), 1L, 1F),
                List.of(port), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.MISSING_OUTPUT);
    }

    @Test
    void normal_chemical_output_with_only_radioactive_ports_reports_missing_output() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
        Holder.Reference<Chemical> chemical = registerChemical("missing_normal_output", false);
        FakeChemicalPort port = new FakeChemicalPort(
                new FakeChemicalTank(1_000L, ChemicalAttributeValidator.ALWAYS_ALLOW), IOType.OUTPUT, true);

        RequirementPlan result = chemicalHandler().plan(
                LoadedChemicalRequirement.output(chemical.key().location(), 1L, 1F),
                List.of(port), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.MISSING_OUTPUT);
    }

    @Test
    void radioactive_chemical_input_with_only_normal_ports_reports_insufficient_resource() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
        Holder.Reference<Chemical> chemical = registerChemical("missing_radioactive_input", true);
        FakeChemicalPort port = new FakeChemicalPort(
                new FakeChemicalTank(1_000L, ChemicalAttributeValidator.ALWAYS_ALLOW), IOType.INPUT, false);

        RequirementPlan result = chemicalHandler().plan(
                LoadedChemicalRequirement.input(ChemicalIngredient.chemical(chemical.key().location(), 1L)),
                List.of(port), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.MISSING_INPUT);
    }

    @Test
    void normal_chemical_input_with_only_radioactive_ports_reports_insufficient_resource() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
        Holder.Reference<Chemical> chemical = registerChemical("missing_normal_input", false);
        FakeChemicalPort port = new FakeChemicalPort(
                new FakeChemicalTank(1_000L, ChemicalAttributeValidator.ALWAYS_ALLOW), IOType.INPUT, true);

        RequirementPlan result = chemicalHandler().plan(
                LoadedChemicalRequirement.input(ChemicalIngredient.chemical(chemical.key().location(), 1L)),
                List.of(port), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(BuiltinFailureReasons.MISSING_INPUT);
    }

    @Test
    void insufficient_temperature_reports_registered_reason() {
        LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());

        RequirementPlan result = heatHandler().plan(
                LoadedHeatRequirement.minimumTemperature(350D), List.of(new FakeHeatPort(300D)), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT);
        assertThat(result.failure().details())
                .containsEntry("required_temperature", "350.0")
                .containsEntry("available_temperature", "300.0");
    }

    @Test
    void minimum_temperature_requirement_uses_heat_input_capability() {
        LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());
        RequirementHandlerRegistry.register(LoadedHeatRequirement.TEMPERATURE_TYPE);

        PlanningResult result = new RequirementPlanner().plan(
                List.of(LoadedHeatRequirement.minimumTemperature(350D)), List.of(new FakeHeatPort(360D)), testContext());

        assertThat(result.successful()).isTrue();
    }

    @Test
    void minimum_temperature_ignores_output_only_ports() {
        LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());

        RequirementPlan result = heatHandler().plan(
                LoadedHeatRequirement.minimumTemperature(350D), List.of(new FakeHeatPort(360D, IOType.OUTPUT)),
                testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT);
    }

    @Test
    void heat_output_without_a_port_is_blocked() {
        LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());

        RequirementPlan result = heatHandler().plan(
                LoadedHeatRequirement.outputHeat(5D), List.of(), testContext());

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isSameAs(MekanismFailureReasons.HEAT_OUTPUT_BLOCKED);
        assertThat(result.failure().details()).containsEntry("requested_heat", "5");
    }

    @Test
    void heat_capability_extraction_shortfall_reports_typed_commit_failure() {
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(300D, () -> 300D, () -> {
        });
        HeatPortCapability capability = new HeatPortCapability(capacitor, IOType.INPUT);
        CapabilityRequests.ValueRequest request = new CapabilityRequests.ValueRequest(
                new CapabilityType(MekanismRecipeTypes.HEAT), IOType.INPUT, 1L, Long.MAX_VALUE, false);

        CapabilityResult result = capability.prepare(request).commit();

        assertThat(result.success()).isFalse();
        assertThat(result.status().reason()).isSameAs(MekanismFailureReasons.HEAT_INPUT_MISSING);
        assertThat(result.status().failure().trace().frames().getFirst().phase())
                .isEqualTo(FailurePhase.CAPABILITY_COMMIT);
    }

    @Test
    void chemical_capability_extraction_shortfall_reports_typed_commit_failure() {
        Holder.Reference<Chemical> chemical = registerChemical("chemical_commit_shortfall");
        ChemicalStack resource = new ChemicalStack(chemical, 1L);
        FakeChemicalTank tank = new FakeChemicalTank(1_000L, ChemicalAttributeValidator.ALWAYS_ALLOW);
        tank.setStack(resource);
        ChemicalPortCapability capability = new ChemicalPortCapability(tank, IOType.INPUT);
        CapabilityRequests.ResourceRequest<ChemicalStack> request = new CapabilityRequests.ResourceRequest<>(
                new CapabilityType(MekanismRecipeTypes.CHEMICAL), IOType.INPUT, 1L,
                List.of(new CapabilityRequests.ResourceAction<>(0, resource, 2L, false)));

        CapabilityResult result = capability.prepare(request).commit();

        assertThat(result.success()).isFalse();
        assertThat(result.status().reason()).isSameAs(MekanismFailureReasons.CHEMICAL_INPUT_MISSING);
        assertThat(result.status().failure().trace().frames().getFirst().phase())
                .isEqualTo(FailurePhase.CAPABILITY_COMMIT);
    }

    @Test
    void chemical_capability_operations_use_typed_commit_failures() {
        Holder.Reference<Chemical> chemical = registerChemical("capability_operation");
        ChemicalPortCapability capability = new ChemicalPortCapability(
                new FakeChemicalTank(1_000L, ChemicalAttributeValidator.ALWAYS_ALLOW), IOType.INPUT);

        CapabilityResult unsupported = capability.prepare(new CapabilityRequests.ValueRequest(
                new CapabilityType(MekanismRecipeTypes.CHEMICAL), IOType.INPUT, 1L, 1L, false)).commit();
        assertThat(unsupported.success()).isFalse();
        assertThat(unsupported.status().reason()).isSameAs(BuiltinFailureReasons.UNSUPPORTED_REQUEST);
        assertThat(unsupported.status().failure().trace().frames().getFirst().phase())
                .isEqualTo(FailurePhase.CAPABILITY_COMMIT);

        ChemicalStack resource = new ChemicalStack(chemical, 1L);
        CapabilityResult missing = capability.prepare(new CapabilityRequests.ResourceRequest<>(
                new CapabilityType(MekanismRecipeTypes.CHEMICAL), IOType.INPUT, 1L,
                List.of(new CapabilityRequests.ResourceAction<>(0, resource, 1L, false)))).commit();
        assertThat(missing.success()).isFalse();
        assertThat(missing.status().reason()).isSameAs(MekanismFailureReasons.CHEMICAL_INPUT_MISSING);
        assertThat(missing.status().failure().trace().frames().getFirst().phase())
                .isEqualTo(FailurePhase.CAPABILITY_COMMIT);
    }

    @Test
    void mekanism_requirement_wakeups_use_typed_failure_ids_and_matchers() {
        LoadedChemicalRequirement.installHandler(LoadedMekanismBridge.chemicalHandler());
        LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());
        Holder.Reference<Chemical> chemical = registerChemical("wakeup");
        ChemicalStack resource = new ChemicalStack(chemical, 1L);

        ResourceWakeup chemicalInput = chemicalHandler().resourceWakeups(
                LoadedChemicalRequirement.input(ChemicalIngredient.chemical(chemical.key().location(), 1L))).getFirst();
        assertThat(chemicalInput.failureReasonIds()).contains(
                BuiltinFailureReasons.MISSING_INPUT.id(), BuiltinFailureReasons.PER_TICK.id(),
                MekanismFailureReasons.CHEMICAL_INPUT_MISSING.id());
        assertThat(chemicalInput.reason()).isEqualTo(RequirementHandler.WakeupReason.INPUT_AVAILABLE);
        assertThat(chemicalInput.matcher().test(resource)).isTrue();
        assertThat(chemicalInput.matcher().test(chemical.key().location())).isTrue();
        assertThat(chemicalInput.matcher().test(ChemicalStack.EMPTY)).isFalse();

        ResourceWakeup chemicalOutput = chemicalHandler().resourceWakeups(
                LoadedChemicalRequirement.output(chemical.key().location(), 1L, 1F)).getFirst();
        assertThat(chemicalOutput.failureReasonIds()).contains(
                BuiltinFailureReasons.MISSING_OUTPUT.id(), BuiltinFailureReasons.FINISH.id(),
                MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED.id(),
                MekanismFailureReasons.CHEMICAL_RADIOACTIVITY_REJECTED.id());
        assertThat(chemicalOutput.reason()).isEqualTo(RequirementHandler.WakeupReason.OUTPUT_CAPACITY);
        assertThat(chemicalOutput.matcher().test(resource)).isTrue();
        assertThat(chemicalOutput.matcher().test(chemical.key().location())).isTrue();

        ResourceWakeup heatInput = heatHandler().resourceWakeups(
                LoadedHeatRequirement.minimumTemperature(350D)).getFirst();
        assertThat(heatInput.failureReasonIds()).contains(
                MekanismFailureReasons.HEAT_INPUT_MISSING.id(),
                MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT.id());
        assertThat(heatInput.reason()).isEqualTo(RequirementHandler.WakeupReason.INPUT_AVAILABLE);
        assertThat(heatInput.matcher().test(new CapabilityType(MekanismRecipeTypes.HEAT))).isTrue();
        assertThat(heatInput.matcher().test(new CapabilityType(MekanismRecipeTypes.HEAT_TEMPERATURE))).isFalse();

        ResourceWakeup heatOutput = heatHandler().resourceWakeups(
                LoadedHeatRequirement.outputHeat(5D)).getFirst();
        assertThat(heatOutput.failureReasonIds()).contains(
                BuiltinFailureReasons.MISSING_OUTPUT.id(), BuiltinFailureReasons.FINISH.id(),
                MekanismFailureReasons.HEAT_OUTPUT_BLOCKED.id());
        assertThat(heatOutput.reason()).isEqualTo(RequirementHandler.WakeupReason.OUTPUT_CAPACITY);
        assertThat(heatOutput.matcher().test(new CapabilityType(MekanismRecipeTypes.HEAT))).isTrue();
    }

    @Test
    void heat_output_uses_handle_heat() {
        LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());
        FakeHeatPort port = new FakeHeatPort(360D, IOType.OUTPUT);

        RequirementPlan planned = heatHandler().plan(LoadedHeatRequirement.outputHeat(5D), List.of(port), testContext());
        RequirementPlan materialized = planned.materialize(1, new PlanningReservations(), null);

        assertThat(materialized.successful()).isTrue();
        CapabilityResult result = materialized.operations().getFirst().commit();
        assertThat(result.success()).isTrue();
        assertThat(port.handledHeat()).isEqualTo(5D);
    }

    @Test
    void heat_output_saturates_at_double_maximum_for_long_parallelism() {
        LoadedHeatRequirement.installHandler(LoadedMekanismBridge.heatHandler());
        FakeHeatPort port = new FakeHeatPort(360D, IOType.OUTPUT);

        RequirementPlan planned = heatHandler().plan(LoadedHeatRequirement.outputHeat(Double.MAX_VALUE),
                List.of(port), testContext());
        RequirementPlan materialized = planned.materialize(Long.MAX_VALUE, new PlanningReservations(), null);

        assertThat(materialized.successful()).isTrue();
        CapabilityResult result = materialized.operations().getFirst().commit();
        assertThat(result.success()).isTrue();
        assertThat(port.handledHeat()).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void chemical_declarations_preserve_long_amounts_supported_by_mekanism_1_21_1() {
        ResourceLocation id = ResourceLocation.parse("mekanism:oxygen");

        assertThat(ChemicalIngredient.chemical(id, Long.MAX_VALUE).amount()).isEqualTo(Long.MAX_VALUE);
        assertThat(cn.howxu.mmcr.api.compat.mekanism.ChemicalOutput.of(id, Long.MAX_VALUE, 1F).amount())
                .isEqualTo(Long.MAX_VALUE);
    }

    @SuppressWarnings("unchecked")
    private static RequirementHandler<LoadedChemicalRequirement> chemicalHandler() {
        RequirementType<LoadedChemicalRequirement> type = LoadedChemicalRequirement.TYPE;
        return (RequirementHandler<LoadedChemicalRequirement>) type.handler();
    }

    @SuppressWarnings("unchecked")
    private static RequirementHandler<LoadedHeatRequirement> heatHandler() {
        return (RequirementHandler<LoadedHeatRequirement>) LoadedHeatRequirement.TEMPERATURE_TYPE.handler();
    }

    private static cn.howxu.mmcr.api.capability.plan.PlanningContext testContext() {
        return new PlanningContext(1, 0);
    }

    private static Holder.Reference<Chemical> registerChemical(String path) {
        return registerChemical(path, false);
    }

    private static Holder.Reference<Chemical> registerChemical(String path, boolean radioactive) {
        ResourceKey<Chemical> key = ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME,
                ResourceLocation.fromNamespaceAndPath("mmcr_test", path));
        MappedRegistry<Chemical> registry = (MappedRegistry<Chemical>) MekanismAPI.CHEMICAL_REGISTRY;
        if (registry.get(key) != null) return registry.getHolder(key).orElseThrow();
        registry.unfreeze();
        if (registry.get(MekanismAPI.EMPTY_CHEMICAL_KEY) == null) {
            Registry.registerForHolder(registry, MekanismAPI.EMPTY_CHEMICAL_KEY, MekanismAPI.EMPTY_CHEMICAL);
        }
        Chemical value = new Chemical(ChemicalBuilder.builder()) {
            @Override
            public boolean isRadioactive() {
                return radioactive;
            }
        };
        Registry.registerForHolder(registry, key, value);
        registry.freeze();
        return registry.getHolder(key).orElseThrow();
    }

    private static final class FakeChemicalPort implements LoadedMekanismBridge.ChemicalPort {
        private final IChemicalTank tank;
        private final boolean radioactive;
        private final CapabilityView view;

        private FakeChemicalPort(IChemicalTank tank, cn.howxu.mmcr.util.IOType ioType) {
            this(tank, ioType, false);
        }

        private FakeChemicalPort(IChemicalTank tank, cn.howxu.mmcr.util.IOType ioType, boolean radioactive) {
            this.tank = tank;
            this.radioactive = radioactive;
            this.view = new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return new CapabilityType(MekanismRecipeTypes.CHEMICAL);
                }

                @Override
                public CapabilityDirections directions() {
                    return CapabilityDirections.of(ioType);
                }
            };
        }

        @Override
        public IChemicalTank chemicalTank() {
            return tank;
        }

        @Override
        public boolean radioactive() {
            return radioactive;
        }

        @Override
        public CapabilityType type() {
            return view.type();
        }

        @Override
        public CapabilityDirections directions() {
            return view.directions();
        }

        @Override
        public CapabilityView view() {
            return view;
        }
    }

    private static final class FakeChemicalTank implements IChemicalTank {
        private final long capacity;
        private final ChemicalAttributeValidator attributeValidator;
        private ChemicalStack identity = ChemicalStack.EMPTY;
        private long amount;

        private FakeChemicalTank(long capacity, ChemicalAttributeValidator attributeValidator) {
            this.capacity = capacity;
            this.attributeValidator = attributeValidator;
        }

        @Override
        public ChemicalStack getStack() {
            return identity.isEmpty() ? ChemicalStack.EMPTY : identity.copyWithAmount(amount);
        }

        @Override
        public void setStack(ChemicalStack stack) {
            if (!stack.isEmpty() && !isValid(stack)) throw new IllegalArgumentException("Invalid chemical");
            setStackUnchecked(stack);
        }

        @Override
        public void setStackUnchecked(ChemicalStack stack) {
            identity = stack.isEmpty() ? ChemicalStack.EMPTY : stack.copyWithAmount(1L);
            amount = stack.isEmpty() ? 0L : stack.getAmount();
        }

        @Override
        public long getCapacity() {
            return capacity;
        }

        @Override
        public boolean isValid(ChemicalStack stack) {
            return !stack.isEmpty();
        }

        @Override
        public void onContentsChanged() {
        }

        @Override
        public ChemicalAttributeValidator getAttributeValidator() {
            return attributeValidator;
        }

        private long amount() {
            return amount;
        }
    }

    private static final class FakeHeatPort implements LoadedMekanismBridge.HeatPort {
        private final FakeHeatHandler handler;
        private final IOType ioType;
        private final CapabilityView view = new CapabilityView() {
            @Override
            public CapabilityType type() {
                return new CapabilityType(MekanismRecipeTypes.HEAT);
            }

            @Override
            public CapabilityDirections directions() {
                return CapabilityDirections.of(ioType);
            }
        };

        private FakeHeatPort(double temperature) {
            this(temperature, IOType.INPUT);
        }

        private FakeHeatPort(double temperature, IOType ioType) {
            handler = new FakeHeatHandler(temperature);
            this.ioType = ioType;
        }

        @Override
        public IHeatHandler heatHandler() {
            return handler;
        }

        @Override
        public CapabilityType type() {
            return view.type();
        }

        @Override
        public CapabilityView view() {
            return view;
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            throw new UnsupportedOperationException();
        }

        private double temperature() {
            return handler.getTotalTemperature();
        }

        private double handledHeat() {
            return handler.handledHeat;
        }
    }

    private static final class FakeHeatHandler implements IHeatHandler {
        private final double temperature;
        private double handledHeat;

        private FakeHeatHandler(double temperature) {
            this.temperature = temperature;
        }

        @Override
        public int getHeatCapacitorCount() {
            return 1;
        }

        @Override
        public double getTemperature(int capacitor) {
            return temperature;
        }

        @Override
        public double getInverseConduction(int capacitor) {
            return 1D;
        }

        @Override
        public double getHeatCapacity(int capacitor) {
            return 1D;
        }

        @Override
        public void handleHeat(int capacitor, double transfer) {
            handledHeat += transfer;
        }
    }
}
