package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.command.BuildCommand;
import cn.howxu.mmcr.internal.command.ExportCommand;
import cn.howxu.mmcr.internal.command.ReloadCommand;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.event.StructureDirtyEvents;
import cn.howxu.mmcr.internal.network.PktAutoIOConfigPayload;
import cn.howxu.mmcr.internal.network.PktControllerSpecsPayload;
import cn.howxu.mmcr.internal.network.PktEjectPortContentsPayload;
import cn.howxu.mmcr.internal.network.PktFactoryControllerStatePayload;
import cn.howxu.mmcr.internal.network.PktMachineAppearancePayload;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.network.PktMultiblockDetectorPickPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockMismatchHighlightPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockPreviewPayload;
import cn.howxu.mmcr.internal.network.PktRecipeLockPayload;
import cn.howxu.mmcr.internal.network.PktRuntimeContentPayload;
import cn.howxu.mmcr.internal.network.PktSmartInterfaceUpdatePayload;
import cn.howxu.mmcr.internal.network.RuntimeContentServerBridge;
import cn.howxu.mmcr.internal.network.RuntimeContentSync;
import cn.howxu.mmcr.internal.reload.MachineRecipeDataReloadListener;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.ModRecipeTypes;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

/** Owns NeoForge mod and game event wiring for MMCR.
 * @author howxu <dev@howxu.cn>
 */
public final class ModEventRegistration {
    private ModEventRegistration() {
    }

    public static void register(IEventBus modBus, ModContainer modContainer) {
        registerDeferredRegisters(modBus);
        MMCR.CREATIVE_TABS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        registerListeners(registrar(modBus), registrar(NeoForge.EVENT_BUS), EventHandlers.production());
        MMCR.CREATIVE_TABS.register(MMCR.MODID, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mmcr"))
                .icon(() -> ModItems.ITEMS.get("basic_casing").get().getDefaultInstance())
                .displayItems((params, output) -> ModItems.ITEMS.values().forEach(holder -> output.accept(holder.get())))
                .build());
    }

    static void registerListeners(ListenerRegistrar modBus, ListenerRegistrar gameBus, EventHandlers handlers) {
        modBus.add(RegisterCapabilitiesEvent.class, handlers.capabilities());
        modBus.add(RegisterPayloadHandlersEvent.class, handlers.payloads());
        modBus.add(RegisterGameTestsEvent.class, handlers.gameTests());
        gameBus.add(BlockEvent.EntityPlaceEvent.class, handlers.blockPlaced());
        gameBus.add(BlockEvent.EntityMultiPlaceEvent.class, handlers.blocksPlaced());
        gameBus.add(BlockEvent.FluidPlaceBlockEvent.class, handlers.fluidPlaced());
        gameBus.add(BreakBlockEvent.class, handlers.blockBroken());
        gameBus.add(ChunkEvent.Unload.class, handlers.chunkUnloaded());
        gameBus.add(ChunkEvent.Load.class, handlers.chunkLoaded());
        gameBus.add(LevelTickEvent.Post.class, handlers.levelTick());
        gameBus.add(LevelEvent.Unload.class, handlers.levelUnload());
        gameBus.add(ServerAboutToStartEvent.class, handlers.serverAboutToStart());
        gameBus.add(ServerStoppedEvent.class, handlers.serverStopped());
        gameBus.add(DefaultDataComponentsBoundEvent.class, handlers.defaultDataComponentsBound());
        gameBus.add(AddServerReloadListenersEvent.class, handlers.reloadListeners());
        gameBus.add(PlayerEvent.PlayerLoggedInEvent.class, handlers.playerLoggedIn());
        gameBus.add(PlayerEvent.PlayerChangedDimensionEvent.class, handlers.playerChangedDimension());
        gameBus.add(RegisterCommandsEvent.class, handlers.commands());
    }

    private static void registerDeferredRegisters(IEventBus modBus) {
        ModDataComponents.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModUIs.register(modBus);
        ModRecipeTypes.register(modBus);
        StartupContentRegistration.markRegistersAttached();
    }

    private static void syncPlayer(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            RuntimeContentSync.sendTo(serverPlayer);
        }
    }

    static void registerCommands(RegisterCommandsEvent event) {
        ReloadCommand.register(event.getDispatcher());
        BuildCommand.register(event.getDispatcher());
        ExportCommand.register(event.getDispatcher());
    }

    static void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToClient(
                        PktMachineStatePayload.TYPE, PktMachineStatePayload.STREAM_CODEC, PktMachineStatePayload::handle)
                .playToClient(PktFactoryControllerStatePayload.TYPE, PktFactoryControllerStatePayload.STREAM_CODEC,
                        PktFactoryControllerStatePayload::handle)
                .playToClient(PktControllerSpecsPayload.TYPE, PktControllerSpecsPayload.STREAM_CODEC,
                        PktControllerSpecsPayload::handle)
                .playToClient(PktMachineAppearancePayload.TYPE, PktMachineAppearancePayload.STREAM_CODEC,
                        PktMachineAppearancePayload::handle)
                .playToClient(PktRuntimeContentPayload.TYPE, PktRuntimeContentPayload.STREAM_CODEC,
                        PktRuntimeContentPayload::handle)
                .playToClient(PktMultiblockMismatchHighlightPayload.TYPE,
                        PktMultiblockMismatchHighlightPayload.STREAM_CODEC, PktMultiblockMismatchHighlightPayload::handle)
                .playToClient(PktMultiblockPreviewPayload.TYPE, PktMultiblockPreviewPayload.STREAM_CODEC,
                        PktMultiblockPreviewPayload::handle)
                .playToServer(PktMultiblockDetectorPickPayload.TYPE, PktMultiblockDetectorPickPayload.STREAM_CODEC,
                        PktMultiblockDetectorPickPayload::handle)
                .playToServer(PktSmartInterfaceUpdatePayload.TYPE, PktSmartInterfaceUpdatePayload.STREAM_CODEC,
                        PktSmartInterfaceUpdatePayload::handle)
                .playToServer(PktAutoIOConfigPayload.TYPE, PktAutoIOConfigPayload.STREAM_CODEC,
                        PktAutoIOConfigPayload::handle)
                .playToServer(PktEjectPortContentsPayload.TYPE, PktEjectPortContentsPayload.STREAM_CODEC,
                        PktEjectPortContentsPayload::handle)
                .playToServer(PktRecipeLockPayload.TYPE, PktRecipeLockPayload.STREAM_CODEC,
                        PktRecipeLockPayload::handle);
    }

    private static void onDefaultDataComponentsBound(DefaultDataComponentsBoundEvent event) {
        if (event.shouldUpdateStaticData()) {
            RuntimeContentRegistration.registerRecipes();
        }
    }

    private static ListenerRegistrar registrar(IEventBus bus) {
        return new ListenerRegistrar() {
            @Override
            public <T extends Event> void add(Class<T> eventType, Consumer<T> listener) {
                bus.addListener(eventType, listener);
            }
        };
    }

    @FunctionalInterface
    interface ListenerRegistrar {
        <T extends Event> void add(Class<T> eventType, Consumer<T> listener);
    }

    record EventHandlers(
            Consumer<RegisterCapabilitiesEvent> capabilities,
            Consumer<RegisterPayloadHandlersEvent> payloads,
            Consumer<RegisterGameTestsEvent> gameTests,
            Consumer<BlockEvent.EntityPlaceEvent> blockPlaced,
            Consumer<BlockEvent.EntityMultiPlaceEvent> blocksPlaced,
            Consumer<BlockEvent.FluidPlaceBlockEvent> fluidPlaced,
            Consumer<BreakBlockEvent> blockBroken,
            Consumer<ChunkEvent.Unload> chunkUnloaded,
            Consumer<ChunkEvent.Load> chunkLoaded,
            Consumer<LevelTickEvent.Post> levelTick,
            Consumer<LevelEvent.Unload> levelUnload,
            Consumer<ServerAboutToStartEvent> serverAboutToStart,
            Consumer<ServerStoppedEvent> serverStopped,
            Consumer<DefaultDataComponentsBoundEvent> defaultDataComponentsBound,
            Consumer<AddServerReloadListenersEvent> reloadListeners,
            Consumer<PlayerEvent.PlayerLoggedInEvent> playerLoggedIn,
            Consumer<PlayerEvent.PlayerChangedDimensionEvent> playerChangedDimension,
            Consumer<RegisterCommandsEvent> commands) {
        static EventHandlers production() {
            return new EventHandlers(
                    ModCapabilities::register,
                    event -> registerPayloads(event.registrar("1")),
                    GameTestRegistration::registerTests,
                    StructureDirtyEvents::onBlockPlaced,
                    StructureDirtyEvents::onBlocksPlaced,
                    StructureDirtyEvents::onFluidPlaced,
                    StructureDirtyEvents::onBlockBroken,
                    StructureDirtyEvents::onChunkUnloaded,
                    StructureDirtyEvents::onChunkLoaded,
                    SharedIoEvents::onLevelTick,
                    SharedIoEvents::onLevelUnload,
                    RuntimeContentServerBridge::onServerAboutToStart,
                    RuntimeContentServerBridge::onServerStopped,
                    ModEventRegistration::onDefaultDataComponentsBound,
                    MachineRecipeDataReloadListener::register,
                    event -> syncPlayer(event.getEntity()),
                    event -> syncPlayer(event.getEntity()),
                    ModEventRegistration::registerCommands);
        }
    }
}
