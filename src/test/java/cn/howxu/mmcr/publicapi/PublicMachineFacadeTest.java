package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.internal.api.facade.machine.MachineAdapters;
import cn.howxu.mmcr.publicapi.machine.ControllerOptions;
import cn.howxu.mmcr.publicapi.machine.FactoryOptions;
import cn.howxu.mmcr.publicapi.machine.MachineKind;
import cn.howxu.mmcr.publicapi.machine.SmartInterfaces;
import cn.howxu.mmcr.publicapi.runtime.RecipeFailureMode;
import cn.howxu.mmcr.publicapi.network.RequestHandler;
import cn.howxu.mmcr.publicapi.network.FailureHandler;
import cn.howxu.mmcr.publicapi.network.RequestPayload;
import cn.howxu.mmcr.publicapi.network.RequestDetails;
import cn.howxu.mmcr.publicapi.network.RequestFailure;
import cn.howxu.mmcr.api.network.RequestBody;
import cn.howxu.mmcr.api.network.RequestInfo;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.api.machine.definition.MachineBuilder;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.network.view.RequestFailureReason;
import cn.howxu.mmcr.internal.api.facade.network.NetworkAdapters;
import cn.howxu.mmcr.publicapi.recipe.modifier.Modifiers;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierOperation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Machine facade delegation and snapshot tests. @author howxu <dev@howxu.cn> */
class PublicMachineFacadeTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("facade_test", path); }

    @Test
    void wrapping_an_existing_builder_preserves_its_state_and_delegates_both_directions() {
        var coreBuilder = MachineBuilder.machine(id("event_machine"))
                .recipePool(id("event_pool"))
                .controller(controller -> controller.tooltip("event.tooltip"));
        var draft = MachineAdapters.wrap(coreBuilder);
        draft.displayNameKey("event.machine.name").allowModifiers();
        assertEquals("event.machine.name", coreBuilder.build().displayNameKey());
        assertTrue(coreBuilder.build().allowModifiers());
        coreBuilder.appearance(appearance -> appearance.machineBasicBlock(id("event_base")));
        var first = draft.build();
        assertEquals(id("event_machine"), first.id());
        assertEquals(id("event_pool"), first.recipePoolId());
        assertEquals(List.of("event.tooltip"), first.controller().tooltip());
        assertEquals(id("event_base"), first.appearance().machineBasicBlock());
        coreBuilder.recipePool(id("later_pool"));
        assertEquals(id("later_pool"), draft.build().recipePoolId());
        assertEquals(id("event_pool"), first.recipePoolId());
    }

    @Test
    void configuration_is_immediate_and_views_are_core_snapshots() {
        AtomicReference<ControllerOptions> options = new AtomicReference<>();
        AtomicReference<ControllerOptions.View> earlier = new AtomicReference<>();
        var draft = Machines.machine(id("machine")).controller(controller -> {
            options.set(controller);
            controller.requireVerticalFacing().tooltip("first", "", null);
            earlier.set(controller.build());
            controller.allowVerticalFacing(false).tooltip("second");
        });
        assertNotNull(options.get());
        assertTrue(earlier.get().allowVerticalFacing());
        assertEquals(List.of("first"), earlier.get().tooltip());
        var spec = draft.build();
        assertFalse(spec.controller().allowVerticalFacing());
        assertTrue(spec.controller().requireVerticalFacing());
        assertEquals(List.of("first", "second"), spec.controller().tooltip());
        options.get().tooltip("after");
        assertEquals(List.of("first", "second"), spec.controller().tooltip());
        assertThrows(UnsupportedOperationException.class, () -> spec.controller().tooltip().add("mutable"));
        assertSame(MachineAdapters.unwrap(spec), MachineAdapters.unwrap(MachineAdapters.wrap(MachineAdapters.unwrap(spec))));
    }

    @Test
    void factory_lanes_enable_factory_and_preserve_nested_snapshots() {
        AtomicReference<FactoryOptions> options = new AtomicReference<>();
        var spec = Machines.machine(id("factory")).factory(factory -> {
            options.set(factory);
            factory.hasFactory(false).thread("lane", id("recipe"));
        }).build();
        assertTrue(spec.factory().hasFactory());
        options.get().thread("later", id("other"));
        assertEquals(List.of("lane"), spec.factory().threads().stream().map(FactoryOptions.ThreadView::name).toList());
        assertEquals(List.of(id("recipe")), spec.factory().threads().getFirst().recipeIds());
        assertThrows(UnsupportedOperationException.class, () -> spec.factory().threads().clear());
        assertThrows(UnsupportedOperationException.class, () -> spec.factory().threads().getFirst().recipeIds().clear());
    }

    @Test
    void core_owns_validation_and_role_rules() {
        assertThrows(IllegalStateException.class, () -> Machines.machine(id("host")).role(MachineKind.HOST).build());
        assertThrows(IllegalStateException.class, () -> Machines.machine(id("normal")).acceptedModule(id("module")).build());
        var draft = Machines.machine(id("valid_host")).role(MachineKind.HOST).acceptedModule(id("module"));
        var spec = draft.recipePool(id("first"), id("second")).build();
        draft.acceptedModule(id("later"));
        assertEquals(List.of(id("first"), id("second")), spec.recipePoolIds());
        assertEquals(id("first"), spec.recipePoolId());
        assertFalse(spec.acceptedModuleIds().contains(id("later")));
        assertThrows(IllegalArgumentException.class, () -> Machines.machine(id("duplicate")).recipePool(id("pool"), id("pool")));
        assertThrows(IllegalArgumentException.class, () -> Machines.machine(id("parallel")).maxParallelism(0).build());
        assertThrows(IllegalArgumentException.class, () -> Machines.machine(id("invalid_factory")).factory(factory -> factory.threadLimit(0)));
    }

    @Test
    void smart_values_delegate_validation_and_reject_duplicate_types() {
        var smart = SmartInterfaces.type("speed", 4, 1, 8, 2, SmartInterfaces.ValueType.INTEGER);
        assertFalse(smart.accepts(1.5f));
        assertEquals(1f, smart.validatedValue(Float.NaN));
        assertEquals(4f, smart.validatedValue(4));
        assertEquals(SmartInterfaces.ValueType.INTEGER, SmartInterfaces.ValueType.byName("int"));
        var draft = Machines.machine(id("smart")).smartInterface(smart);
        var spec = draft.build();
        assertEquals(smart.type(), spec.smartInterfaceTypes().get("speed").type());
        assertThrows(IllegalArgumentException.class, () -> draft.smartInterface(smart));
        assertThrows(UnsupportedOperationException.class, () -> spec.smartInterfaceTypes().clear());
    }

    @Test
    void recipe_and_tick_hook_presence_and_exclusivity_are_owned_by_core() {
        var defaultSpec = Machines.machine(id("default_behavior")).build();
        assertFalse(defaultSpec.recipeHooks().orElseThrow().hasPreServerTick());
        assertTrue(defaultSpec.tickHooks().isEmpty());
        var draft = Machines.machine(id("recipe_behavior"))
                .recipeBehavior(hooks -> hooks.idleStart(context -> {}))
                .preServerTick(context -> {});
        var spec = draft.build();
        assertTrue(spec.recipeHooks().orElseThrow().hasIdleStart());
        assertTrue(spec.recipeHooks().orElseThrow().hasPreServerTick());
        assertFalse(spec.recipeHooks().orElseThrow().hasPostServerTick());
        assertThrows(IllegalStateException.class, () -> draft.tickBehavior(hooks -> {}));
        var tickDraft = Machines.machine(id("tick_behavior")).tickBehavior(hooks -> hooks.serverTick(context -> {}));
        var tickSpec = tickDraft.build();
        assertTrue(tickSpec.recipeHooks().isEmpty());
        assertTrue(tickSpec.tickHooks().orElseThrow().hasServerTick());
        assertThrows(IllegalStateException.class, () -> tickDraft.preServerTick(context -> {}));
    }

    @Test
    void repeated_configuration_replaces_options_without_changing_previous_specs() {
        var draft = Machines.machine(id("metadata"))
                .appearance(options -> options.appearance("facade_test:base")
                        .controllerActiveOverlayTexture(id("active")))
                .controller(options -> options.tooltip("old"))
                .runningSound(id("running")).finishSound(id("finish"))
                .failureAction(RecipeFailureMode.RESET).allowModifiers().allowMultithreading()
                .maxParallelism(7).maxParallelAmount(3).parallelizable(true).shareSmartInterfaces();
        var first = draft.build();
        var second = draft.appearance(options -> options.machineBasicBlock(id("replacement")))
                .controller(options -> options.tooltip("new"))
                .runningSound(null).finishSound(null).failureAction(RecipeFailureMode.DECREASE).build();
        assertEquals(id("base"), first.appearance().machineBasicBlock());
        assertEquals(id("active"), first.appearance().controllerActiveOverlayTexture());
        assertEquals(id("replacement"), second.appearance().machineBasicBlock());
        assertNull(second.appearance().controllerActiveOverlayTexture());
        assertEquals(List.of("old"), first.controller().tooltip());
        assertEquals(List.of("new"), second.controller().tooltip());
        assertEquals(id("running"), first.runningSoundId());
        assertNull(second.runningSoundId());
        assertNull(second.finishSoundId());
        var core = MachineAdapters.unwrap(first);
        assertEquals(core.maxParallelism(), first.maxParallelism());
        assertEquals(core.maxParallelAmount(), first.maxParallelAmount());
        assertEquals(core.parallelizable(), first.parallelizable());
        assertEquals(core.allowModifiers(), first.allowModifiers());
        assertEquals(core.allowMultithreading(), first.allowMultithreading());
        assertEquals(core.shareSmartInterfaces(), first.shareSmartInterfaces());
        assertEquals(RecipeFailureMode.RESET, first.failureAction());
        assertEquals(RecipeFailureMode.DECREASE, second.failureAction());
    }

    @Test
    void smart_modifier_order_and_curves_survive_build_and_later_additions() {
        var duration = Modifiers.smartDuration("speed", 1, 8, 2, 4, ModifierOperation.MULTIPLY);
        var energy = Modifiers.smartEnergy("speed", 1, 8, 1, 3, ModifierOperation.MULTIPLY);
        var draft = Machines.machine(id("curves")).smartInterfaceModifier(duration).smartInterfaceModifier(energy);
        var spec = draft.build();
        draft.smartInterfaceModifier(duration);
        assertEquals(List.of(duration.target(), energy.target()), spec.smartInterfaceModifiers().stream().map(value -> value.target()).toList());
        assertEquals(duration.mappedValue(5), spec.smartInterfaceModifiers().getFirst().mappedValue(5));
        assertEquals(energy.toModifier(5).value(), spec.smartInterfaceModifiers().get(1).toModifier(5).value());
        assertThrows(UnsupportedOperationException.class, () -> spec.smartInterfaceModifiers().clear());
    }

    @Test
    void request_registration_remains_unique_and_snapshot_ids_do_not_change() {
        RequestHandler handler = (body, request, sender, receiver) -> {};
        FailureHandler failure = (body, request, sender, reason) -> {};
        var draft = Machines.machine(id("network"))
                .allowNetworkMachine(id("peer")).networkInterface(2, 3)
                .requestProcess(id("request"), handler).requestFailed(id("request"), failure);
        var spec = draft.build();
        draft.requestProcess(id("later"), handler).requestFailed(id("later"), failure);
        assertEquals(MachineAdapters.unwrap(spec).requestProcessors().keySet(), spec.requestProcessorIds());
        assertEquals(MachineAdapters.unwrap(spec).requestFailures().keySet(), spec.requestFailureIds());
        assertFalse(spec.requestProcessorIds().contains(id("later")));
        assertFalse(spec.requestFailureIds().contains(id("later")));
        assertTrue(spec.networkInterface().allowedMachineIds().contains(id("peer")));
        assertEquals(MachineAdapters.unwrap(spec).networkInterface().maxCount(), spec.networkInterface().maxCount());
        assertEquals(MachineAdapters.unwrap(spec).networkInterface().maxConnections(), spec.networkInterface().maxConnections());
        assertThrows(IllegalArgumentException.class, () -> draft.requestProcess(id("request"), handler));
        assertThrows(IllegalArgumentException.class, () -> draft.requestFailed(id("request"), failure));
        assertThrows(UnsupportedOperationException.class, () -> spec.requestProcessorIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> spec.requestFailureIds().clear());
    }

    @Test
    void registered_network_callbacks_receive_public_views_and_nullable_storage() {
        AtomicReference<RequestPayload> receivedBody = new AtomicReference<>();
        AtomicReference<RequestDetails> receivedDetails = new AtomicReference<>();
        AtomicReference<RequestFailure> receivedFailure = new AtomicReference<>();
        var spec = Machines.machine(id("callback"))
                .requestProcess(id("request"), (body, request, sender, receiver) -> {
                    receivedBody.set(body);
                    receivedDetails.set(request);
                    assertNull(sender);
                    assertNull(receiver);
                }).requestFailed(id("request"), (body, request, sender, reason) -> {
                    assertNull(sender);
                    receivedFailure.set(reason);
                }).build();
        assertNull(receivedBody.get());
        var core = MachineAdapters.unwrap(spec);
        var body = RequestBody.of(Map.of());
        var request = new RequestInfo(id("request"), new MachineReference(id("peer"), 42));
        core.requestProcessors().get(id("request")).process(body, request, null, null);
        assertTrue(receivedBody.get().values().isEmpty());
        assertEquals(id("request"), receivedDetails.get().requestId());
        assertEquals(id("peer"), receivedDetails.get().peer().type());
        assertEquals(request.peer().hash(), receivedDetails.get().peer().hash());
        core.requestFailures().get(id("request")).fail(
                NetworkAdapters.unwrap(receivedBody.get()),
                new cn.howxu.mmcr.api.network.view.RequestInfo(id("request"), NetworkAdapters.unwrap(receivedDetails.get().peer())),
                null, cn.howxu.mmcr.api.network.view.RequestFailureReason.UNREACHABLE);
        assertEquals(RequestFailure.UNREACHABLE, receivedFailure.get());
    }

    @Test
    void readonly_handler_maps_invoke_authoritative_core_callbacks_and_preserve_snapshots() {
        AtomicReference<RequestBody> processedBody = new AtomicReference<>();
        AtomicReference<RequestInfo> processedRequest = new AtomicReference<>();
        AtomicReference<RequestFailureReason> failureReason = new AtomicReference<>();
        var coreBuilder = MachineBuilder.machine(id("core_callbacks"))
                .requestProcessInternal(id("request"), (body, request, sender, receiver) -> {
                    processedBody.set(body);
                    processedRequest.set(request);
                    assertNull(sender);
                    assertNull(receiver);
                }).requestFailed(id("request"), (body, request, sender, reason) -> {
                    assertEquals(id("peer"), request.peer().type());
                    assertEquals(id("request"), request.requestId());
                    assertNull(sender);
                    failureReason.set(reason);
                });
        var draft = MachineAdapters.wrap(coreBuilder);
        var spec = draft.build();
        var processors = spec.requestProcessors();
        var failures = spec.requestFailures();
        assertEquals(MachineAdapters.unwrap(spec).requestProcessors().keySet(), processors.keySet());
        assertEquals(MachineAdapters.unwrap(spec).requestFailures().keySet(), failures.keySet());
        assertNull(processedBody.get());
        assertNull(failureReason.get());

        var coreBody = RequestBody.of(Map.of("message", DataValue.of("payload")));
        var payload = NetworkAdapters.wrap(cn.howxu.mmcr.api.network.view.RequestBody.fromInternal(coreBody));
        var peer = new cn.howxu.mmcr.api.network.view.MachineReference(id("peer"), 42);
        var details = NetworkAdapters.wrap(new cn.howxu.mmcr.api.network.view.RequestInfo(id("request"), peer));
        processors.get(id("request")).process(payload, details, null, null);
        assertEquals(coreBody.values(), processedBody.get().values());
        assertEquals(new RequestInfo(id("request"), new MachineReference(id("peer"), 42)), processedRequest.get());
        failures.get(id("request")).fail(payload, details, null, RequestFailure.UNREACHABLE);
        assertEquals(RequestFailureReason.UNREACHABLE, failureReason.get());

        coreBuilder.requestProcessInternal(id("later"), (body, request, sender, receiver) -> {})
                .requestFailed(id("later"), (body, request, sender, reason) -> {});
        assertFalse(processors.containsKey(id("later")));
        assertFalse(failures.containsKey(id("later")));
        assertFalse(spec.requestProcessors().containsKey(id("later")));
        assertFalse(spec.requestFailures().containsKey(id("later")));
        assertTrue(draft.build().requestProcessors().containsKey(id("later")));
        assertTrue(draft.build().requestFailures().containsKey(id("later")));
        assertThrows(UnsupportedOperationException.class, processors::clear);
        assertThrows(UnsupportedOperationException.class, failures::clear);
        assertThrows(UnsupportedOperationException.class, () -> processors.entrySet().iterator().next().setValue((body, request, sender, receiver) -> {}));
    }
}
