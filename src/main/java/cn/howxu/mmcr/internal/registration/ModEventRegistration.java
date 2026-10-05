package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.config.ClientConfig;
import cn.howxu.mmcr.config.CommonConfig;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.command.BuildCommand;
import cn.howxu.mmcr.internal.command.ExportCommand;
import cn.howxu.mmcr.internal.command.ReloadCommand;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.event.ControllerSyncEvents;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.event.StructureDirtyEvents;
import cn.howxu.mmcr.internal.network.PktAutoIOConfigPayload;
import cn.howxu.mmcr.internal.network.PktBlueprintStageUpdatePayload;
import cn.howxu.mmcr.internal.network.PktControllerScreenTextPayload;
import cn.howxu.mmcr.internal.network.PktControllerSpecsPayload;
import cn.howxu.mmcr.internal.network.PktEjectPortContentsPayload;
import cn.howxu.mmcr.internal.network.PktFactoryControllerStatePayload;
import cn.howxu.mmcr.internal.network.PktMachineAppearancePayload;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.network.PktMachineProgressPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockDetectorExportPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockDetectorPickPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockDetectorUpdatePayload;
import cn.howxu.mmcr.internal.network.PktMultiblockMismatchHighlightPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockPreviewPayload;
import cn.howxu.mmcr.internal.network.PktRecipePoolSelectPayload;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.network.PktRuntimeContentPayload;
import cn.howxu.mmcr.internal.network.PktSmartInterfaceUpdatePayload;
import cn.howxu.mmcr.internal.network.PktTerminalActionPayload;
import cn.howxu.mmcr.internal.network.PktTerminalStatePayload;
import cn.howxu.mmcr.internal.network.NetworkServerState;
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
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

/** Owns NeoForge mod and game event wiring for MMCR.
 * @author howxu <dev@howxu.cn>
 */
public final class ModEventRegistration {
    static final String PAYLOAD_PROTOCOL_VERSION = "8";

    private ModEventRegistration() {
    }

    public static void register(IEventBus modBus, ModContainer modContainer) {
        registerDeferredRegisters(modBus);
        MMCR.CREATIVE_TABS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC,
                "modular-machinery-community-refoxed/client.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC,
                "modular-machinery-community-refoxed/common.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC,
                "modular-machinery-community-refoxed/server.toml");
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
        gameBus.add(EntityJoinLevelEvent.class, handlers.fallingBlockJoined());
        gameBus.add(ChunkEvent.Unload.class, handlers.chunkUnloaded());
        gameBus.add(ChunkEvent.Load.class, handlers.chunkLoaded());
        gameBus.add(ChunkWatchEvent.Sent.class, handlers.chunkSent());
        gameBus.add(ChunkWatchEvent.UnWatch.class, handlers.chunkUnWatch());
        gameBus.add(LevelTickEvent.Post.class, handlers.levelTick());
        gameBus.add(ServerTickEvent.Post.class, handlers.serverTick());
        gameBus.add(LevelEvent.Unload.class, handlers.levelUnload());
        gameBus.add(ServerAboutToStartEvent.class, handlers.serverAboutToStart());
        gameBus.add(ServerStoppedEvent.class, handlers.serverStopped());
        gameBus.add(DefaultDataComponentsBoundEvent.class, handlers.defaultDataComponentsBound());
        gameBus.add(AddServerReloadListenersEvent.class, handlers.reloadListeners());
        gameBus.add(OnDatapackSyncEvent.class, handlers.datapackSync());
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
                .playToClient(PktMachineProgressPayload.TYPE, PktMachineProgressPayload.STREAM_CODEC,
                        PktMachineProgressPayload::handle)
                .playToClient(PktFactoryControllerStatePayload.TYPE, PktFactoryControllerStatePayload.STREAM_CODEC,
                        PktFactoryControllerStatePayload::handle)
                .playToClient(PktControllerSpecsPayload.TYPE, PktControllerSpecsPayload.STREAM_CODEC,
                        PktControllerSpecsPayload::handle)
                .playToClient(PktControllerScreenTextPayload.TYPE, PktControllerScreenTextPayload.STREAM_CODEC,
                        PktControllerScreenTextPayload::handle)
                .playToClient(PktMachineAppearancePayload.TYPE, PktMachineAppearancePayload.STREAM_CODEC,
                        PktMachineAppearancePayload::handle)
                 .playToClient(PktRuntimeContentPayload.TYPE, PktRuntimeContentPayload.STREAM_CODEC,
                         PktRuntimeContentPayload::handle)
                 .playToClient(PktPortStorageSyncPayload.TYPE, PktPortStorageSyncPayload.STREAM_CODEC,
                         PktPortStorageSyncPayload::handle)
                .playToClient(PktMultiblockMismatchHighlightPayload.TYPE,
                        PktMultiblockMismatchHighlightPayload.STREAM_CODEC, PktMultiblockMismatchHighlightPayload::handle)
                 .playToClient(PktMultiblockPreviewPayload.TYPE, PktMultiblockPreviewPayload.STREAM_CODEC,
                         PktMultiblockPreviewPayload::handle)
                 .playToClient(PktTerminalStatePayload.TYPE, PktTerminalStatePayload.STREAM_CODEC,
                          PktTerminalStatePayload::handle)
                .playToServer(PktMultiblockDetectorPickPayload.TYPE, PktMultiblockDetectorPickPayload.STREAM_CODEC,
                        PktMultiblockDetectorPickPayload::handle)
                .playToServer(PktMultiblockDetectorUpdatePayload.TYPE, PktMultiblockDetectorUpdatePayload.STREAM_CODEC,
                        PktMultiblockDetectorUpdatePayload::handle)
                .playToServer(PktMultiblockDetectorExportPayload.TYPE, PktMultiblockDetectorExportPayload.STREAM_CODEC,
                        PktMultiblockDetectorExportPayload::handle)
                .playToServer(PktSmartInterfaceUpdatePayload.TYPE, PktSmartInterfaceUpdatePayload.STREAM_CODEC,
                        PktSmartInterfaceUpdatePayload::handle)
                .playToServer(PktAutoIOConfigPayload.TYPE, PktAutoIOConfigPayload.STREAM_CODEC,
                        PktAutoIOConfigPayload::handle)
                 .playToServer(PktEjectPortContentsPayload.TYPE, PktEjectPortContentsPayload.STREAM_CODEC,
                         PktEjectPortContentsPayload::handle)
                 .playToServer(PktBlueprintStageUpdatePayload.TYPE, PktBlueprintStageUpdatePayload.STREAM_CODEC,
                         PktBlueprintStageUpdatePayload::handle)
                  .playToServer(PktRecipePoolSelectPayload.TYPE, PktRecipePoolSelectPayload.STREAM_CODEC,
                         PktRecipePoolSelectPayload::handle)
                 .playToServer(PktTerminalActionPayload.TYPE, PktTerminalActionPayload.STREAM_CODEC,
                         PktTerminalActionPayload::handle);
    }

    private static void onDefaultDataComponentsBound(DefaultDataComponentsBoundEvent event) {
        if (event.shouldUpdateStaticData()) {
            StartupContentRegistration.completeProductionRecipesAfterComponentsBound();
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
            Consumer<EntityJoinLevelEvent> fallingBlockJoined,
            Consumer<ChunkEvent.Unload> chunkUnloaded,
            Consumer<ChunkEvent.Load> chunkLoaded,
            Consumer<ChunkWatchEvent.Sent> chunkSent,
            Consumer<ChunkWatchEvent.UnWatch> chunkUnWatch,
            Consumer<LevelTickEvent.Post> levelTick,
            Consumer<ServerTickEvent.Post> serverTick,
            Consumer<LevelEvent.Unload> levelUnload,
            Consumer<ServerAboutToStartEvent> serverAboutToStart,
            Consumer<ServerStoppedEvent> serverStopped,
            Consumer<DefaultDataComponentsBoundEvent> defaultDataComponentsBound,
            Consumer<AddServerReloadListenersEvent> reloadListeners,
            Consumer<OnDatapackSyncEvent> datapackSync,
            Consumer<PlayerEvent.PlayerChangedDimensionEvent> playerChangedDimension,
            Consumer<RegisterCommandsEvent> commands) {
        static EventHandlers production() {
            return new EventHandlers(
                    ModCapabilities::register,
                    event -> registerPayloads(event.registrar(PAYLOAD_PROTOCOL_VERSION)),
                    GameTestRegistration::registerTests,
                    StructureDirtyEvents::onBlockPlaced,
                    StructureDirtyEvents::onBlocksPlaced,
                    StructureDirtyEvents::onFluidPlaced,
                    StructureDirtyEvents::onBlockBroken,
                    StructureDirtyEvents::onFallingBlockJoined,
                    StructureDirtyEvents::onChunkUnloaded,
                    StructureDirtyEvents::onChunkLoaded,
                    ControllerSyncEvents::onChunkSent,
                    ControllerSyncEvents::onChunkUnWatch,
                    SharedIoEvents::onLevelTick,
                    SharedIoEvents::onServerTick,
                    SharedIoEvents::onLevelUnload,
                    RuntimeContentServerBridge::onServerAboutToStart,
                    event -> {
                        NetworkServerState.discard(event.getServer());
                        RuntimeContentServerBridge.onServerStopped(event);
                    },
                    ModEventRegistration::onDefaultDataComponentsBound,
                    MachineRecipeDataReloadListener::register,
                    RuntimeContentSync::onDatapackSync,
                    event -> syncPlayer(event.getEntity()),
                    ModEventRegistration::registerCommands);
        }
    }
}
