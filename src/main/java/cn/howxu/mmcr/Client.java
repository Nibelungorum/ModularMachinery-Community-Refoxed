package cn.howxu.mmcr;

import cn.howxu.mmcr.client.gui.MachineMenuScreen;
import cn.howxu.mmcr.client.gui.FactoryControllerScreen;
import cn.howxu.mmcr.client.gui.SmartInterfaceScreen;
import cn.howxu.mmcr.client.controller.ControllerModelInvalidator;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.client.model.DynamicOverlayBakedModel;
import cn.howxu.mmcr.client.model.MachineAppearanceCache;
import cn.howxu.mmcr.client.model.RuntimeMachineModelRegistry;
import cn.howxu.mmcr.client.model.RuntimeMachineResourcePack;
import cn.howxu.mmcr.client.sound.MachineSoundManager;
import cn.howxu.mmcr.client.preview.StructurePreviewReloadListener;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = MMCR.MODID, dist = Dist.CLIENT)
public class Client {
    private final MachineSoundManager machineSoundManager = new MachineSoundManager();

    public Client(IEventBus modBus) {
        modBus.addListener(Client::registerMenuScreens);
        modBus.addListener(Client::registerModelLoaders);
        modBus.addListener(Client::registerItemModels);
        modBus.addListener(Client::registerRuntimeResourcePack);
        modBus.addListener(Client::registerPreviewReloadListener);
        NeoForge.EVENT_BUS.addListener(this::tickMachineSounds);
        NeoForge.EVENT_BUS.addListener(this::clearMachineSounds);
        MachineAppearanceCache.loadPersistedSnapshot();
        MachineAppearanceCache.addInvalidationListener(Client::invalidateMachineModels);
        ControllerSpecCache.addInvalidationListener(Client::invalidateMachineModels);
    }

    private void tickMachineSounds(ClientTickEvent.Post event) {
        machineSoundManager.clientTick(Minecraft.getInstance());
    }

    private void clearMachineSounds(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            machineSoundManager.clear();
        }
    }

    private static void invalidateMachineModels() {
        DynamicOverlayBakedModel.clearCache();
        if (Minecraft.getInstance().levelRenderer != null) {
            if (Minecraft.getInstance().isSameThread()) {
                ControllerModelInvalidator.invalidate();
            } else {
                Minecraft.getInstance().execute(ControllerModelInvalidator::invalidate);
            }
        }
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        MachineMenuScreen.registerScreens(event);
        event.register(ModUIs.FACTORY_CONTROLLER.get(), FactoryControllerScreen::new);
        event.register(ModUIs.SMART_INTERFACE.get(), SmartInterfaceScreen::new);
    }

    private static void registerModelLoaders(RegisterBlockStateModels event) {
        RuntimeMachineModelRegistry.registerBlockStateModels(event);
    }

    private static void registerItemModels(RegisterItemModelsEvent event) {
        RuntimeMachineModelRegistry.registerItemModels(event);
    }

    private static void registerRuntimeResourcePack(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(RuntimeMachineResourcePack.source());
        }
    }

    private static void registerPreviewReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(MMCR.id("structure_preview"), new StructurePreviewReloadListener());
    }

}
