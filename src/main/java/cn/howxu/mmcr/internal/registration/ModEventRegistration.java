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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

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
        modBus.addListener(ModCapabilities::register);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlocksPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onFluidPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlockBroken);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onChunkUnloaded);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onChunkLoaded);
        NeoForge.EVENT_BUS.addListener(SharedIoEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(SharedIoEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(RuntimeContentServerBridge::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(RuntimeContentServerBridge::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ModEventRegistration::onDefaultDataComponentsBound);
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) -> MachineRecipeDataReloadListener.register(event));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> syncPlayer(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> syncPlayer(event.getEntity()));
        NeoForge.EVENT_BUS.addListener(ModEventRegistration::registerCommands);
        modBus.addListener(ModEventRegistration::registerPayloads);
        modBus.addListener((RegisterGameTestsEvent event) -> GameTestRegistration.registerTests(event));
        MMCR.CREATIVE_TABS.register(MMCR.MODID, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mmcr"))
                .icon(() -> ModItems.ITEMS.get("basic_casing").get().getDefaultInstance())
                .displayItems((params, output) -> ModItems.ITEMS.values().forEach(holder -> output.accept(holder.get())))
                .build());
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

    private static void registerCommands(RegisterCommandsEvent event) {
        ReloadCommand.register(event.getDispatcher());
        BuildCommand.register(event.getDispatcher());
        ExportCommand.register(event.getDispatcher());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
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
}
