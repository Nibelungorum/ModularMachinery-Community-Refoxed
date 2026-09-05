package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.sound.MachineSoundRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.ScriptableObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.network.RequestBody;
import cn.howxu.mmcr.api.network.RequestFailed;
import cn.howxu.mmcr.api.network.RequestFailureReason;
import cn.howxu.mmcr.api.network.RequestInfo;
import cn.howxu.mmcr.api.network.RequestProcess;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineBuilderJSTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        MachineDefinitions.beginRegistryPhase();
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void beginRegistryPhase() {
        MachineDefinitions.beginRegistryPhase();
        PublicApiBootstrap.clearForTesting();
        PublicApiBootstrap.begin();
    }

    @AfterEach
    void resetDefinitions() {
        MachineDefinitions.clearForTesting();
        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.beginRegistryPhase();
    }

    @Test
    void item_outputs_use_registry_backed_holders() {
        var builder = new MachineRecipeBuilderJS(MMCR.id("holder_test"))
                .itemOutput("mmcr:item_output_bus", 1)
                .chancedItemOutput("mmcr:item_input_bus", 2, 0.5F);

        assertThat(builder.outputs)
                .extracting(stack -> stack.typeHolder().unwrapKey())
                .allMatch(Optional::isPresent);
        assertThat(builder.outputs)
                .extracting(stack -> stack.typeHolder().unwrapKey().orElseThrow().identifier())
                .containsExactly(MMCR.id("item_output_bus"), MMCR.id("item_input_bus"));
    }

    @Test
    void controller_textures_sets_front_and_all_other_faces() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .controllerTextures(MMCR.id("block/arc_front"), MMCR.id("block/arc_side"))
                .createObject();

        assertThat(machine.controllerSpec()).isEqualTo(new MachineControllerSpec(
                MMCR.id("arc_furnace_controller"),
                MMCR.id("block/arc_front"),
                MMCR.id("block/arc_side"),
                MMCR.id("block/arc_side"),
                MMCR.id("block/arc_side"),
                false));
    }

    @Test
    void individual_texture_setters_override_only_that_face() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .controllerTextures("mmcr:block/arc_front", "mmcr:block/arc_side")
                .controllerTopTexture(MMCR.id("block/arc_top"))
                .controllerBottomTexture(MMCR.id("block/arc_bottom"))
                .createObject();

        assertThat(machine.controllerSpec().frontTexture()).isEqualTo(MMCR.id("block/arc_front"));
        assertThat(machine.controllerSpec().sideTexture()).isEqualTo(MMCR.id("block/arc_side"));
        assertThat(machine.controllerSpec().topTexture()).isEqualTo(MMCR.id("block/arc_top"));
        assertThat(machine.controllerSpec().bottomTexture()).isEqualTo(MMCR.id("block/arc_bottom"));
    }

    @Test
    void allow_vertical_facing_sets_controller_spec_flag() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .allowVerticalFacing()
                .createObject();

        assertThat(machine.controllerSpec().allowVerticalFacing()).isTrue();
    }

    @Test
    void full_rotational_symmetry_sets_controller_spec_flag() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .fullyRotationallySymmetric()
                .createObject();

        assertThat(machine.controllerSpec().fullyRotationallySymmetric()).isTrue();
    }

    @Test
    void require_vertical_facing_sets_controller_spec_flags() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .requireVerticalFacing()
                .createObject();

        assertThat(machine.controllerSpec().allowVerticalFacing()).isTrue();
        assertThat(machine.controllerSpec().requireVerticalFacing()).isTrue();
    }

    @Test
    void startup_builder_creates_machine_registration_without_structure() {
        var registration = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .displayNameKey("machine.mmcr.arc_furnace")
                .allowVerticalFacing()
                .allowModifiers()
                .createObject();

        assertThat(registration.id()).isEqualTo(MMCR.id("arc_furnace"));
        assertThat(registration.displayNameKey()).isEqualTo("machine.mmcr.arc_furnace");
        assertThat(registration.controllerSpec().allowVerticalFacing()).isTrue();
        assertThat(registration.allowModifiers()).isTrue();
    }

    @Test
    void expandable_structure_settings_enter_machine_registration() {
        var expandable = new MachineBuilderJS(MMCR.id("expandable_machine"))
                .expandableStructure(true)
                .createObject();
        var fixed = new MachineBuilderJS(MMCR.id("fixed_machine"))
                .expandableStructure(false)
                .createObject();

        assertThat(expandable.expandableStructure()).isTrue();
        assertThat(fixed.expandableStructure()).isFalse();
    }

    @Test
    void startup_event_creates_machine_builder() {
        var builder = new MMCRStartupEventJS().createMachine("mmcr:event_machine");

        assertThat(builder).isInstanceOf(MachineBuilderJS.class);
        assertThat(builder.createObject().id()).isEqualTo(MMCR.id("event_machine"));
    }

    @Test
    void startup_builder_sets_controller_tooltip_lines() {
        var registration = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .controllerTooltip("tooltip.mmcr.arc_furnace.0", "tooltip.mmcr.arc_furnace.1")
                .createObject();

        assertThat(registration.controllerSpec().tooltip())
                .containsExactly("tooltip.mmcr.arc_furnace.0", "tooltip.mmcr.arc_furnace.1");
    }

    @Test
    void builder_declares_module_and_host_roles_with_stable_deduplicated_modules() {
        var module = new MachineBuilderJS("mmcr:module")
                .module(false)
                .module()
                .createObject();
        var host = new MachineBuilderJS("mmcr:host")
                .host("mmcr:first", "mmcr:second", "mmcr:first")
                .host(List.of("mmcr:second", "mmcr:third"))
                .createObject();

        assertThat(module.isModule()).isTrue();
        assertThat(host.isHost()).isTrue();
        assertThat(host.acceptedModuleIds())
                .containsExactly(Identifier.parse("mmcr:first"), Identifier.parse("mmcr:second"), Identifier.parse("mmcr:third"));
    }

    @Test
    void startup_builder_sets_concurrency_capabilities() {
        var registration = new MachineBuilderJS(MMCR.id("concurrent_press"))
                .allowMultithreading()
                .allowParallelism(true)
                .maxParallelAmount(12)
                .createObject();

        assertThat(registration.allowMultithreading()).isTrue();
        assertThat(registration.allowParallelism()).isTrue();
        assertThat(registration.maxParallelAmount()).isEqualTo(12);
    }

    @Test
    void startup_builder_forwards_network_settings_and_request_callbacks() throws Exception {
        Identifier targetId = MMCR.id("network_target");
        Identifier processId = MMCR.id("process");
        Identifier failureId = MMCR.id("failure");
        AtomicInteger processCalls = new AtomicInteger();
        AtomicInteger failureCalls = new AtomicInteger();
        RequestProcess process = (body, request, sender, receiver) -> processCalls.incrementAndGet();
        RequestFailed failure = (body, request, sender, reason) -> failureCalls.incrementAndGet();
        MachineBuilderJS builder = new MachineBuilderJS("mmcr:network_builder")
                .networkInterface(2, 3)
                .allowNetworkMachine(targetId.toString())
                .requestProcess(processId.toString(), process)
                .requestFailed(failureId.toString(), failure);

        MachineRegistration registration = builder.createObject();
        assertThat(registration.networkInterface().maxCount()).isEqualTo(2);
        assertThat(registration.networkInterface().maxConnections()).isEqualTo(3);
        assertThat(registration.networkInterface().allowedMachineIds()).containsExactly(targetId);
        assertThat(registration.requestProcessors()).containsEntry(processId, process);
        assertThat(registration.requestFailures()).containsEntry(failureId, failure);

        registration.requestProcessors().get(processId).process(RequestBody.of(java.util.Map.of()),
                new RequestInfo(processId, new MachineReference(targetId, 1L)), null, null);
        registration.requestFailures().get(failureId).fail(RequestBody.of(java.util.Map.of()),
                new RequestInfo(failureId, new MachineReference(targetId, 1L)), null,
                RequestFailureReason.UNREACHABLE);
        assertThat(processCalls).hasValue(1);
        assertThat(failureCalls).hasValue(1);
    }

    @Test
    void startup_builder_rejects_duplicate_request_callback_ids() throws Exception {
        MachineBuilderJS builder = new MachineBuilderJS("mmcr:duplicate_callbacks");
        RequestProcess process = (body, request, sender, receiver) -> { };

        builder.requestProcess("mmcr:request", process);
        assertThatThrownBy(() -> builder.requestProcess("mmcr:request", process))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate request processor");
    }

    @Test
    void registered_machine_keeps_runtime_capabilities_and_sounds() {
        Identifier id = MMCR.id("registered_capabilities");
        new MachineBuilderJS(id)
                .allowModifiers()
                .allowMultithreading()
                .allowParallelism()
                .maxParallelAmount(6)
                .expandableStructure(true)
                .shareSmartInterface()
                .smartInterface("speed", 1F, 4F).end()
                .runningSound("minecraft:block.furnace.fire_crackle")
                .finishSound("minecraft:entity.ender_dragon.growl")
                .registerObject();

        Plugin.freezeStartupRegistryPhaseForTesting();
        MachineRegistration registration = MachineDefinitions.getRegistration(id);
        assertThat(registration.allowModifiers()).isTrue();
        assertThat(registration.allowMultithreading()).isTrue();
        assertThat(registration.allowParallelism()).isTrue();
        assertThat(registration.maxParallelAmount()).isEqualTo(6);
        assertThat(registration.expandableStructure()).isTrue();
        assertThat(registration.shareSmartInterfaces()).isTrue();
        assertThat(registration.smartInterfaceTypes()).containsKey("speed");
        assertThat(registration.runningSoundId()).isEqualTo(Identifier.parse("minecraft:block.furnace.fire_crackle"));
        assertThat(registration.finishSoundId()).isEqualTo(Identifier.parse("minecraft:entity.ender_dragon.growl"));
    }

    @Test
    void registered_machine_preserves_all_recipe_behavior_callbacks() {
        Identifier id = MMCR.id("registered_recipe_callbacks");
        AtomicInteger calls = new AtomicInteger();
        new MachineBuilderJS(id)
                .recipeBehavior(builder -> builder
                        .idleStart(context -> calls.incrementAndGet())
                        .idleEnd(context -> calls.incrementAndGet())
                        .beforeStart(context -> calls.incrementAndGet())
                        .recipeTick(context -> calls.incrementAndGet())
                        .beforeFinish(context -> calls.incrementAndGet()))
                .preServerTick(context -> calls.incrementAndGet())
                .postServerTick(context -> calls.incrementAndGet())
                .registerObject();

        Plugin.freezeStartupRegistryPhaseForTesting();
        MachineRegistration registration = MachineDefinitions.getRegistration(id);
        assertThat(registration.behavior()).isInstanceOf(RecipeBehavior.class);
        RecipeBehavior behavior = (RecipeBehavior) registration.behavior();
        behavior.idleStart().accept(null);
        behavior.idleEnd().accept(null);
        behavior.beforeStart().accept(null);
        behavior.recipeTick().accept(null);
        behavior.beforeFinish().accept(null);
        behavior.preServerTick().accept(null);
        behavior.postServerTick().accept(null);
        assertThat(calls).hasValue(7);
    }

    @Test
    void builder_maps_direct_registration_settings() {
        MachineControllerSpec controllerSpec = new MachineControllerSpec(
                Identifier.parse("mmcr_kubejs:explicit_controller"),
                Identifier.parse("mmcr_kubejs:block/front"),
                Identifier.parse("mmcr_kubejs:block/side"),
                Identifier.parse("mmcr_kubejs:block/top"),
                Identifier.parse("mmcr_kubejs:block/bottom"),
                true);
        MachineAppearanceSpec appearance = new MachineAppearanceSpec(
                Identifier.parse("mmcr_kubejs:explicit_casing"),
                Identifier.parse("mmcr_kubejs:block/controller"),
                Identifier.parse("mmcr_kubejs:block/port"));

        var registration = new MachineBuilderJS("mmcr_kubejs:kubejs_test")
                .recipeFamily("mmcr_kubejs:kubejs_family")
                .expandableStructure(true)
                .factoryThreads(4)
                .maxParallelism(4)
                .controllerTextures("mmcr_kubejs:block/derived_front", "mmcr_kubejs:block/derived_side")
                .controllerSpec(controllerSpec)
                .machineBasicBlock("mmcr_kubejs:derived_casing")
                .appearance(appearance)
                .role("MoDuLe")
                .createObject();

        assertThat(registration.recipeFamilyId()).isEqualTo(Identifier.parse("mmcr_kubejs:kubejs_family"));
        assertThat(registration.expandableStructure()).isTrue();
        assertThat(registration.maxParallelAmount()).isEqualTo(4);
        assertThat(registration.controllerSpec()).isSameAs(controllerSpec);
        assertThat(registration.appearance()).isSameAs(appearance);
        assertThat(registration.role()).isEqualTo(MachineRole.MODULE);
    }

    @Test
    void builder_rejects_conflicting_explicit_and_legacy_roles() {
        assertThatThrownBy(() -> new MachineBuilderJS("mmcr_kubejs:host_module")
                .host("mmcr_kubejs:module")
                .role("module")
                .createObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
        assertThatThrownBy(() -> new MachineBuilderJS("mmcr_kubejs:module_host")
                .module()
                .role("host")
                .createObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
        assertThatThrownBy(() -> new MachineBuilderJS("mmcr_kubejs:host_normal")
                .host("mmcr_kubejs:module")
                .role("normal")
                .createObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void builder_preserves_host_declarations_with_host_role() {
        var registration = new MachineBuilderJS("mmcr_kubejs:host")
                .role("host")
                .host("mmcr_kubejs:module")
                .createObject();

        assertThat(registration.role()).isEqualTo(MachineRole.HOST);
        assertThat(registration.acceptedModuleIds()).containsExactly(Identifier.parse("mmcr_kubejs:module"));
    }

    @Test
    void builder_reports_valid_roles_for_invalid_role() {
        assertThatThrownBy(() -> new MachineBuilderJS("mmcr_kubejs:kubejs_test").role("factory"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Valid roles: [NORMAL, HOST, MODULE]");
    }

    @Test
    void builder_declares_configured_smart_interface() {
        var registration = new MachineBuilderJS("mmcr:interface_builder_test")
                // .localizedName("Interface Builder Test")
                .smartInterface("speed", 1F, 4F).priority(10).end()
                .createObject();

        SmartInterfaceType speed = registration.smartInterfaceTypes().get("speed");
        assertThat(speed.priority()).isEqualTo(10);
        assertThat(speed.defaultValue()).isEqualTo(1F);
        assertThat(speed.minValue()).isEqualTo(1F);
        assertThat(speed.maxValue()).isEqualTo(4F);
    }

    @Test
    void builder_preserves_parallel_capacity_above_integer_maximum() {
        long parallelism = (long) Integer.MAX_VALUE + 1L;

        MachineRegistration registration = new MachineBuilderJS("mmcr:long_parallel_builder")
                .maxParallelism(parallelism)
                .createObject();

        assertThat(registration.maxParallelAmount()).isEqualTo(parallelism);
    }

    @Test
    void registering_parallel_capacity_above_integer_maximum_does_not_narrow_factory_settings() {
        assertThatCode(() -> new MachineBuilderJS("mmcr:long_registered_machine")
                .maxParallelism(Long.MAX_VALUE)
                .factoryThreads(4)
                .registerObject())
                .doesNotThrowAnyException();
    }

    @Test
    void builder_declares_shared_integer_smart_interface() {
        var registration = new MachineBuilderJS("mmcr:shared_interface_builder")
                .shareSmartInterface()
                .smartInterface("batch", 2F).valueType("integer").end()
                .createObject();

        assertThat(registration.shareSmartInterfaces()).isTrue();
        assertThat(registration.smartInterfaceTypes().get("batch").valueType())
                .isEqualTo(SmartInterfaceType.ValueType.INTEGER);
    }

    @Test
    void builder_declares_interface_driven_modifiers() {
        var registration = new MachineBuilderJS("mmcr:interface_modifier_builder")
                .durationByInterface("temperature", 0F, 100F, 2F, 0.5F)
                .itemOutputChanceByInterface("temperature", 0F, 100F, 0.25F, 1F, RecipeModifier.Operation.ADD)
                .createObject();

        assertThat(registration.smartInterfaceModifiers()).hasSize(2);
        assertThat(registration.smartInterfaceModifiers().get(0).toRecipeModifier(100F).getTarget())
                .isEqualTo(IntegrationTypeHelper.TARGET_DURATION);
        assertThat(registration.smartInterfaceModifiers().get(1).toRecipeModifier(100F)).isEqualTo(new RecipeModifier(
                IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.OUTPUT, 1F,
                RecipeModifier.Operation.ADD, true));
    }

    @Test
    void builder_sets_machine_basic_block_for_all_base_textures() {
        var registration = new MachineBuilderJS("mmcr:electric_press")
                // .localizedName("Electric Press")
                .appearance("kubejs:steel_casing")
                .createObject();

        assertThat(registration.appearance()).isEqualTo(new MachineAppearanceSpec(
                Identifier.parse("kubejs:steel_casing"),
                Identifier.parse("kubejs:block/steel_casing"),
                Identifier.parse("kubejs:block/steel_casing")));
    }

    @Test
    void rhino_string_appearance_uses_machine_basic_block_overload() {
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "builder", new MachineBuilderJS("mmcr:electric_press"), context);

        var result = (Wrapper) context.evaluateString(scope, """
                builder.appearance('minecraft:bricks').createObject();
                """, "appearance-test", 1, null);
        var registration = (MachineRegistration) result.unwrap();

        assertThat(registration.appearance()).isEqualTo(new MachineAppearanceSpec(
                Identifier.withDefaultNamespace("bricks"),
                Identifier.withDefaultNamespace("block/bricks"),
                Identifier.withDefaultNamespace("block/bricks")));
    }

    @Test
    void rhino_startup_builder_accepts_example_string_overloads() {
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "builder", new MachineBuilderJS("mmcr:space_elevator"), context);

        var result = (Wrapper) context.evaluateString(scope, """
                builder.appearance('minecraft:smooth_quartz')
                  .machineBasicBlock('minecraft:smooth_quartz')
                  .controllerTextures('minecraft:block/quartz_block_top', 'minecraft:block/quartz_block_side')
                  .controllerBaseTexture('minecraft:block/quartz_block_bottom')
                  .formedPortBaseTexture('minecraft:block/quartz_block_bottom')
                  .host('mmcr:space_reassembler')
                  .runningSound('minecraft:block.furnace.fire_crackle')
                  .finishSound('minecraft:entity.ender_dragon.growl')
                  .createObject();
                """, "startup-builder-test", 1, null);
        var registration = (MachineRegistration) result.unwrap();

        assertThat(registration.acceptedModuleIds()).containsExactly(Identifier.parse("mmcr:space_reassembler"));
        assertThat(registration.appearance().machineBasicBlock()).isEqualTo(Identifier.withDefaultNamespace("smooth_quartz"));
        assertThat(registration.controllerSpec().frontTexture()).isEqualTo(Identifier.parse("minecraft:block/quartz_block_top"));
        assertThat(registration.appearance().controllerBaseTexture())
                .isEqualTo(Identifier.parse("minecraft:block/quartz_block_bottom"));
        assertThat(registration.runningSoundId()).isEqualTo(Identifier.parse("minecraft:block.furnace.fire_crackle"));
        assertThat(registration.finishSoundId()).isEqualTo(Identifier.parse("minecraft:entity.ender_dragon.growl"));
    }

    @Test
    void rhino_startup_builder_accepts_machine_level_recipe_tick_hooks() {
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "builder", new MachineBuilderJS("mmcr:kubejs_hook_rhino"), context);

        var result = (Wrapper) context.evaluateString(scope, """
                builder.preServerTick(ctx => {})
                  .postServerTick(ctx => {})
                  .createObject();
                """, "machine-hook-test", 1, null);

        var registration = (MachineRegistration) result.unwrap();
        assertThat(registration.behavior()).isInstanceOf(RecipeBehavior.class);
    }

    @Test
    void sound_identifier_overloads_reject_unknown_ids_immediately() {
        assertThatThrownBy(() -> new MachineBuilderJS("mmcr:unknown_sound").runningSound("mmcr:not_registered"))
                .isInstanceOf(ApiRegistrationException.class);
        assertThatThrownBy(() -> new MachineBuilderJS("mmcr:unknown_sound").finishSound("mmcr:not_registered"))
                .isInstanceOf(ApiRegistrationException.class);
    }

    @Test
    void machine_registration_canonical_constructor_validates_sound_ids() {
        var registration = new MachineBuilderJS("mmcr:canonical_sound").createObject();

        assertThatThrownBy(() -> new MachineRegistration(registration.id(), registration.displayNameKey(),
                registration.controllerSpec(), registration.appearance(), registration.recipeFamilyId(),
                registration.allowModifiers(), registration.allowMultithreading(), registration.allowParallelism(),
                registration.maxParallelAmount(), registration.expandableStructure(), registration.smartInterfaceTypes(),
                registration.shareSmartInterfaces(), registration.smartInterfaceModifiers(),
                Identifier.parse("mmcr:not_registered"), null, registration.role(), registration.acceptedModuleIds(),
                registration.pattern()))
                .isInstanceOf(ApiRegistrationException.class);
    }

    @Test
    void builder_allows_explicit_controller_and_port_base_overrides() {
        var registration = new MachineBuilderJS("mmcr:electric_press")
                .machineBasicBlock("kubejs:steel_casing")
                .controllerBaseTexture("mmcr:block/basic_casing")
                .formedPortBaseTexture("kubejs:block/clean_steel_casing")
                .createObject();

        assertThat(registration.appearance().machineBasicBlock()).isEqualTo(Identifier.parse("kubejs:steel_casing"));
        assertThat(registration.appearance().controllerBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
        assertThat(registration.appearance().formedPortBaseTexture()).isEqualTo(Identifier.parse("kubejs:block/clean_steel_casing"));
    }

    @Test
    void startup_builder_registers_only_during_registry_phase() {
        var builder = new MachineBuilderJS(MMCR.id("startup_press"));
        builder.registerObject();

        assertThat(PublicApiBootstrap.isRegistrationOpen()).isTrue();

        Plugin.freezeStartupRegistryPhaseForTesting();
        assertThatThrownBy(builder::registerObject)
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining("lifecycle");
    }

    @SuppressWarnings("unchecked")
    private static Set<Identifier> requestedMachineSoundIds() throws Exception {
        Method method = MachineSoundRegistry.class.getDeclaredMethod("requestedIds");
        method.setAccessible(true);
        return (Set<Identifier>) method.invoke(null);
    }

}
