package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.client.model.DynamicOverlayBakedModel;
import cn.howxu.mmcr.client.model.DynamicOverlayItemModel;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.UpgradeBusBlock;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.api.capability.external.ExternalCapabilityRegistry;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.UpgradeBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the mod event wiring through injectable listener and payload registrars.
 * @author howxu <dev@howxu.cn>
 */
class ModEventRegistrationTest {
    private static final List<String> GENERATED_PORT_IDS = List.of(
            "extended_item_input_bus_basic", "extended_item_input_bus_advanced",
            "extended_item_input_bus_reinforced", "extended_item_input_bus_ultimate",
            "extended_item_output_bus_basic", "extended_item_output_bus_advanced",
            "extended_item_output_bus_reinforced", "extended_item_output_bus_ultimate",
            "extended_fluid_input_hatch_basic", "extended_fluid_input_hatch_advanced",
            "extended_fluid_input_hatch_reinforced", "extended_fluid_input_hatch_ultimate",
            "extended_fluid_output_hatch_basic", "extended_fluid_output_hatch_advanced",
            "extended_fluid_output_hatch_reinforced", "extended_fluid_output_hatch_ultimate",
            "extended_energy_input_hatch_reinforced", "extended_energy_input_hatch_ultimate",
            "extended_energy_output_hatch_reinforced", "extended_energy_output_hatch_ultimate",
            "combined_input_basic", "combined_input_advanced", "combined_input_reinforced", "combined_input_ultimate",
            "combined_output_basic", "combined_output_advanced", "combined_output_reinforced", "combined_output_ultimate",
            "extended_combined_input_advanced", "extended_combined_input_reinforced", "extended_combined_input_ultimate",
            "extended_combined_output_advanced", "extended_combined_output_reinforced", "extended_combined_output_ultimate");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void registers_all_mod_and_game_listeners_with_the_expected_event_types() {
        RecordingBus modBus = new RecordingBus();
        RecordingBus gameBus = new RecordingBus();
        List<Class<?>> invoked = new ArrayList<>();

        ModEventRegistration.registerListeners(modBus, gameBus, handlers(invoked));

        assertThat(modBus.types()).containsExactly(
                RegisterCapabilitiesEvent.class, RegisterPayloadHandlersEvent.class, RegisterGameTestsEvent.class);
        assertThat(gameBus.types()).containsExactly(
                BlockEvent.EntityPlaceEvent.class, BlockEvent.EntityMultiPlaceEvent.class,
                BlockEvent.FluidPlaceBlockEvent.class, BreakBlockEvent.class, ChunkEvent.Unload.class,
                ChunkEvent.Load.class, LevelTickEvent.Post.class, LevelEvent.Unload.class,
                ServerAboutToStartEvent.class, ServerStoppedEvent.class, DefaultDataComponentsBoundEvent.class,
                AddServerReloadListenersEvent.class, PlayerEvent.PlayerLoggedInEvent.class,
                PlayerEvent.PlayerChangedDimensionEvent.class, RegisterCommandsEvent.class);

        modBus.fireAll();
        gameBus.fireAll();
        assertThat(invoked).containsExactlyElementsOf(
                List.of(RegisterCapabilitiesEvent.class, RegisterPayloadHandlersEvent.class,
                        RegisterGameTestsEvent.class, BlockEvent.EntityPlaceEvent.class,
                        BlockEvent.EntityMultiPlaceEvent.class, BlockEvent.FluidPlaceBlockEvent.class,
                        BreakBlockEvent.class, ChunkEvent.Unload.class, ChunkEvent.Load.class,
                        LevelTickEvent.Post.class, LevelEvent.Unload.class, ServerAboutToStartEvent.class,
                        ServerStoppedEvent.class, DefaultDataComponentsBoundEvent.class,
                        AddServerReloadListenersEvent.class, PlayerEvent.PlayerLoggedInEvent.class,
                        PlayerEvent.PlayerChangedDimensionEvent.class, RegisterCommandsEvent.class));
    }

    @Test
    void registers_all_payloads_with_play_protocol_directions_and_handlers() {
        RecordingPayloadRegistrar registrar = new RecordingPayloadRegistrar();

        ModEventRegistration.registerPayloads(registrar);

        assertThat(registrar.version).isEqualTo("3");
        assertThat(registrar.flows).containsExactly(
                PacketFlow.CLIENTBOUND, PacketFlow.CLIENTBOUND, PacketFlow.CLIENTBOUND,
                PacketFlow.CLIENTBOUND, PacketFlow.CLIENTBOUND, PacketFlow.CLIENTBOUND,
                PacketFlow.CLIENTBOUND, PacketFlow.CLIENTBOUND, PacketFlow.CLIENTBOUND, PacketFlow.SERVERBOUND,
                PacketFlow.SERVERBOUND, PacketFlow.SERVERBOUND, PacketFlow.SERVERBOUND, PacketFlow.SERVERBOUND,
                PacketFlow.SERVERBOUND, PacketFlow.SERVERBOUND);
        assertThat(registrar.types).containsExactly(
                cn.howxu.mmcr.internal.network.PktMachineStatePayload.TYPE,
                cn.howxu.mmcr.internal.network.PktFactoryControllerStatePayload.TYPE,
                cn.howxu.mmcr.internal.network.PktControllerSpecsPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktControllerScreenTextPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktMachineAppearancePayload.TYPE,
                cn.howxu.mmcr.internal.network.PktRuntimeContentPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktMultiblockMismatchHighlightPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktMultiblockPreviewPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktMultiblockDetectorPickPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktMultiblockDetectorUpdatePayload.TYPE,
                cn.howxu.mmcr.internal.network.PktMultiblockDetectorExportPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktSmartInterfaceUpdatePayload.TYPE,
                cn.howxu.mmcr.internal.network.PktAutoIOConfigPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktEjectPortContentsPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktRecipeLockPayload.TYPE);
        assertThat(registrar.handlers).containsOnly(true);
    }

    @Test
    void registers_all_mmcr_commands_on_the_commands_event() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        ModEventRegistration.registerCommands(new RegisterCommandsEvent(
                dispatcher, Commands.CommandSelection.ALL, (CommandBuildContext) null));

        assertThat(dispatcher.getRoot().getChild("mmcr")).isNotNull();
        assertThat(dispatcher.getRoot().getChild("mmcr").getChildren())
                .extracting(command -> command.getName())
                .contains("reload", "build", "export");
    }

    @Test
    void production_handlers_are_wired_and_execute_the_real_command_handler() {
        RecordingBus modBus = new RecordingBus();
        RecordingBus gameBus = new RecordingBus();
        ModEventRegistration.registerListeners(modBus, gameBus, ModEventRegistration.EventHandlers.production());
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        gameBus.fire(RegisterCommandsEvent.class, new RegisterCommandsEvent(
                dispatcher, Commands.CommandSelection.ALL, (CommandBuildContext) null));

        assertThat(dispatcher.getRoot().getChild("mmcr").getChildren())
                .extracting(command -> command.getName())
                .contains("reload", "build", "export");
    }

    @Test
    void production_capability_handler_freezes_external_registry_and_registers_providers() {
        RegisterCapabilitiesEvent event = capabilityEvent();

        ModEventRegistration.EventHandlers.production().capabilities().accept(event);

        assertThat(ExternalCapabilityRegistry.global().isFrozen()).isTrue();
        assertThat(event.isBlockRegistered(ModCapabilities.ITEM_BLOCK,
                ModBlocks.BLOCKS.get("item_input_bus").get())).isTrue();
    }

    @Test
    void every_generated_port_kind_has_registered_content_and_dynamic_model_description() {
        assertThat(PortKinds.all().stream().map(IOPortKind::id).filter(GENERATED_PORT_IDS::contains).toList())
                .containsExactlyElementsOf(GENERATED_PORT_IDS);

        for (String id : GENERATED_PORT_IDS) {
            IOPortKind kind = PortKinds.all().stream()
                    .filter(candidate -> candidate.id().equals(id))
                    .findFirst().orElseThrow();
            Block block = ModBlocks.BLOCKS.get(id).get();
            assertThat(block).isInstanceOf(IOPortBlock.class);
            assertThat(((IOPortBlock) block).kind().id()).isEqualTo(id);

            IOPortBlockEntity entity = kind.entityFactory().create(BlockPos.ZERO, block.defaultBlockState());
            assertThat(entity.kind().id()).isEqualTo(id);
            assertThat(ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, block.defaultBlockState()))
                    .isInstanceOf(IOPortBlockEntity.class);

            DynamicOverlayItemModel.Description description = DynamicOverlayItemModel.describeItem(
                    ModItems.ITEMS.get(id).get());
            assertThat(description.kind()).isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
            assertThat(description.portKind().id()).isEqualTo(id);
        }
    }

    @Test
    void generated_exact_ids_are_not_reused_as_family_aliases() {
        for (String id : GENERATED_PORT_IDS) {
            IOPortKind kind = PortKinds.all().stream()
                    .filter(candidate -> candidate.id().equals(id))
                    .findFirst().orElseThrow();
            assertThat(kind.families()).isNotEmpty().allSatisfy(family ->
                    assertThat(family.countAliases()).doesNotContain(id));
        }
    }

    @Test
    void registers_standalone_upgrade_buses_without_port_kinds() {
        List<String> ids = List.of(
                "upgrade_bus_normal", "upgrade_bus_reinforced", "upgrade_bus_elite",
                "upgrade_bus_super", "upgrade_bus_ultimate");

        assertThat(ModBlocks.BLOCKS.keySet()).containsAll(ids);
        assertThat(ModBlockEntities.BES.keySet()).containsAll(ids);
        assertThat(ModItems.ITEMS.keySet()).containsAll(ids);
        assertThat(PortKinds.all().stream().map(IOPortKind::id).toList()).doesNotContainAnyElementsOf(ids);

        for (String id : ids) {
            Block block = ModBlocks.BLOCKS.get(id).get();
            assertThat(block).isInstanceOf(UpgradeBusBlock.class);
            assertThat(ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, block.defaultBlockState()))
                    .isInstanceOf(UpgradeBusBlockEntity.class);
        }
    }

    private static ModEventRegistration.EventHandlers handlers(List<Class<?>> invoked) {
        return new ModEventRegistration.EventHandlers(
                recording(invoked, RegisterCapabilitiesEvent.class),
                recording(invoked, RegisterPayloadHandlersEvent.class),
                recording(invoked, RegisterGameTestsEvent.class),
                recording(invoked, BlockEvent.EntityPlaceEvent.class),
                recording(invoked, BlockEvent.EntityMultiPlaceEvent.class),
                recording(invoked, BlockEvent.FluidPlaceBlockEvent.class),
                recording(invoked, BreakBlockEvent.class), recording(invoked, ChunkEvent.Unload.class),
                recording(invoked, ChunkEvent.Load.class), recording(invoked, LevelTickEvent.Post.class),
                recording(invoked, LevelEvent.Unload.class), recording(invoked, ServerAboutToStartEvent.class),
                recording(invoked, ServerStoppedEvent.class),
                recording(invoked, DefaultDataComponentsBoundEvent.class),
                recording(invoked, AddServerReloadListenersEvent.class),
                recording(invoked, PlayerEvent.PlayerLoggedInEvent.class),
                recording(invoked, PlayerEvent.PlayerChangedDimensionEvent.class),
                recording(invoked, RegisterCommandsEvent.class));
    }

    private static <T> Consumer<T> recording(List<Class<?>> invoked, Class<?> eventType) {
        return ignored -> invoked.add(eventType);
    }

    private static RegisterCapabilitiesEvent capabilityEvent() {
        try {
            var constructor = RegisterCapabilitiesEvent.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to create capability registration event", exception);
        }
    }

    private static final class RecordingBus implements ModEventRegistration.ListenerRegistrar {
        private final Map<Class<?>, Consumer<?>> listeners = new LinkedHashMap<>();

        @Override
        public <T extends Event> void add(Class<T> eventType, Consumer<T> listener) {
            listeners.put(eventType, listener);
        }

        List<Class<?>> types() {
            return new ArrayList<>(listeners.keySet());
        }

        @SuppressWarnings("unchecked")
        <T extends Event> void fire(Class<T> eventType, T event) {
            ((Consumer<T>) listeners.get(eventType)).accept(event);
        }

        @SuppressWarnings("unchecked")
        void fireAll() {
            listeners.forEach((type, listener) -> ((Consumer<Event>) listener).accept(null));
        }
    }

    private static final class RecordingPayloadRegistrar extends PayloadRegistrar {
        private final String version = "3";
        private final List<PacketFlow> flows = new ArrayList<>();
        private final List<CustomPacketPayload.Type<?>> types = new ArrayList<>();
        private final List<Boolean> handlers = new ArrayList<>();

        private RecordingPayloadRegistrar() {
            super("3");
        }

        @Override
        public <T extends CustomPacketPayload> PayloadRegistrar playToClient(
                CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                IPayloadHandler<T> handler) {
            record(type, PacketFlow.CLIENTBOUND, handler);
            return this;
        }

        @Override
        public <T extends CustomPacketPayload> PayloadRegistrar playToServer(
                CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                IPayloadHandler<T> handler) {
            record(type, PacketFlow.SERVERBOUND, handler);
            return this;
        }

        private void record(CustomPacketPayload.Type<?> type, PacketFlow flow, Object handler) {
            types.add(type);
            flows.add(flow);
            handlers.add(handler != null);
        }
    }
}
