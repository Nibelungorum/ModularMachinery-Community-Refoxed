package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.commands.Commands;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the mod event wiring through injectable listener and payload registrars.
 * @author howxu <dev@howxu.cn>
 */
class ModEventRegistrationTest {
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

        assertThat(modBus.types()).containsExactlyInAnyOrder(
                RegisterCapabilitiesEvent.class, RegisterPayloadHandlersEvent.class, RegisterGameTestsEvent.class);
        assertThat(gameBus.types()).containsExactlyInAnyOrder(
                BlockEvent.EntityPlaceEvent.class, BlockEvent.EntityMultiPlaceEvent.class,
                BlockEvent.FluidPlaceBlockEvent.class, BreakBlockEvent.class, ChunkEvent.Unload.class,
                ChunkEvent.Load.class, LevelTickEvent.Post.class, LevelEvent.Unload.class,
                ServerAboutToStartEvent.class, ServerStoppedEvent.class, DefaultDataComponentsBoundEvent.class,
                AddServerReloadListenersEvent.class, PlayerEvent.PlayerLoggedInEvent.class,
                PlayerEvent.PlayerChangedDimensionEvent.class, RegisterCommandsEvent.class);

        modBus.fireAll();
        gameBus.fireAll();
        assertThat(invoked).containsExactlyInAnyOrderElementsOf(
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

        assertThat(registrar.version).isEqualTo("1");
        assertThat(registrar.flows).containsExactly(
                PacketFlow.CLIENTBOUND, PacketFlow.CLIENTBOUND, PacketFlow.CLIENTBOUND,
                PacketFlow.CLIENTBOUND, PacketFlow.CLIENTBOUND, PacketFlow.CLIENTBOUND,
                PacketFlow.CLIENTBOUND, PacketFlow.SERVERBOUND, PacketFlow.SERVERBOUND,
                PacketFlow.SERVERBOUND, PacketFlow.SERVERBOUND, PacketFlow.SERVERBOUND);
        assertThat(registrar.types).containsExactly(
                cn.howxu.mmcr.internal.network.PktMachineStatePayload.TYPE,
                cn.howxu.mmcr.internal.network.PktFactoryControllerStatePayload.TYPE,
                cn.howxu.mmcr.internal.network.PktControllerSpecsPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktMachineAppearancePayload.TYPE,
                cn.howxu.mmcr.internal.network.PktRuntimeContentPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktMultiblockMismatchHighlightPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktMultiblockPreviewPayload.TYPE,
                cn.howxu.mmcr.internal.network.PktMultiblockDetectorPickPayload.TYPE,
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

    private static final class RecordingBus implements ModEventRegistration.ListenerRegistrar {
        private final Map<Class<?>, Consumer<?>> listeners = new HashMap<>();

        @Override
        public <T extends Event> void add(Class<T> eventType, Consumer<T> listener) {
            listeners.put(eventType, listener);
        }

        Set<Class<?>> types() {
            return listeners.keySet();
        }

        @SuppressWarnings("unchecked")
        void fireAll() {
            listeners.forEach((type, listener) -> ((Consumer<Event>) listener).accept(null));
        }
    }

    private static final class RecordingPayloadRegistrar extends PayloadRegistrar {
        private final String version = "1";
        private final List<PacketFlow> flows = new ArrayList<>();
        private final List<CustomPacketPayload.Type<?>> types = new ArrayList<>();
        private final List<Boolean> handlers = new ArrayList<>();

        private RecordingPayloadRegistrar() {
            super("1");
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
