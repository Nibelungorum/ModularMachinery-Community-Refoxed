package cn.howxu.mmcr;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRendersEvent;
import cn.howxu.mmcr.client.gui.CombinedPortScreen;
import cn.howxu.mmcr.client.gui.EnergyHatchScreen;
import cn.howxu.mmcr.client.gui.ExtendedCombinedScreen;
import cn.howxu.mmcr.client.gui.ExtendedFluidScreen;
import cn.howxu.mmcr.client.gui.ExtendedItemScreen;
import cn.howxu.mmcr.client.gui.FactoryControllerScreen;
import cn.howxu.mmcr.client.gui.FactorySchedulerScreen;
import cn.howxu.mmcr.client.gui.FluidHatchScreen;
import cn.howxu.mmcr.client.gui.ItemBusScreen;
import cn.howxu.mmcr.client.gui.MachineControllerScreen;
import cn.howxu.mmcr.client.gui.SmartInterfaceScreen;
import cn.howxu.mmcr.client.gui.UpgradeBusScreen;
import cn.howxu.mmcr.client.controller.ControllerModelInvalidator;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.client.model.DynamicOverlayBakedModel;
import cn.howxu.mmcr.client.model.MachineAppearanceCache;
import cn.howxu.mmcr.client.model.RuntimeMachineModelRegistry;
import cn.howxu.mmcr.client.model.RuntimeMachineResourcePack;
import cn.howxu.mmcr.client.renderer.MachineControllerRendererDispatcher;
import cn.howxu.mmcr.client.sound.MachineSoundManager;
import cn.howxu.mmcr.client.preview.StructurePreviewReloadListener;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModUIs;
// import org.nibelungorum.client.ArtificialStarRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.server.packs.PackType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.level.block.entity.BlockEntityType;

@Mod(value = MMCR.MODID, dist = Dist.CLIENT)
public class Client {
    private final MachineSoundManager machineSoundManager = new MachineSoundManager();

    public Client(IEventBus modBus) {
        modBus.addListener(Client::registerMenuScreens);
        modBus.addListener(Client::registerModelLoaders);
        modBus.addListener(Client::registerItemModels);
        modBus.addListener(Client::registerMachineRenderers);
        // modBus.addListener(ArtificialStarRenderer::registerModel);
        modBus.addListener(Client::registerRuntimeResourcePack);
        modBus.addListener(Client::registerPreviewReloadListener);
        NeoForge.EVENT_BUS.addListener(this::tickMachineSounds);
        NeoForge.EVENT_BUS.addListener(this::clearMachineSounds);
        NeoForge.EVENT_BUS.addListener(this::clearControllerScreenTextCache);
        MachineAppearanceCache.loadPersistedSnapshot();
        MachineAppearanceCache.addInvalidationListener(Client::invalidateMachineModels);
        ControllerSpecCache.addInvalidationListener(Client::invalidateMachineModels);
    }

    private void tickMachineSounds(ClientTickEvent.Post event) {
        machineSoundManager.clientTick(Minecraft.getInstance());
    }

    private void clearMachineSounds(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ControllerScreenTextCache.clearAll();
            machineSoundManager.clear();
        }
    }

    private void clearControllerScreenTextCache(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ControllerScreenTextCache.clearChunk(event.getChunk().getPos().x(), event.getChunk().getPos().z());
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
        event.register(ModUIs.ITEM_BUS.get(), ItemBusScreen::new);
        event.register(ModUIs.FLUID_HATCH.get(), FluidHatchScreen::new);
        event.register(ModUIs.ENERGY_HATCH.get(), EnergyHatchScreen::new);
        event.register(ModUIs.MACHINE_CONTROLLER.get(), MachineControllerScreen::new);
        event.register(ModUIs.FACTORY_SCHEDULER.get(), FactorySchedulerScreen::new);
        event.register(ModUIs.FACTORY_CONTROLLER.get(), FactoryControllerScreen::new);
        event.register(ModUIs.SMART_INTERFACE.get(), SmartInterfaceScreen::new);
        event.register(ModUIs.EXTENDED_ITEM.get(), ExtendedItemScreen::new);
        event.register(ModUIs.EXTENDED_FLUID.get(), ExtendedFluidScreen::new);
        event.register(ModUIs.COMBINED.get(), CombinedPortScreen::new);
        event.register(ModUIs.EXTENDED_COMBINED.get(), ExtendedCombinedScreen::new);
        event.register(ModUIs.UPGRADE_BUS.get(), UpgradeBusScreen::new);
    }

    private static void registerModelLoaders(RegisterBlockStateModels event) {
        RuntimeMachineModelRegistry.registerBlockStateModels(event);
    }

    private static void registerItemModels(RegisterItemModelsEvent event) {
        RuntimeMachineModelRegistry.registerItemModels(event);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerMachineRenderers(EntityRenderersEvent.RegisterRenderers event) {
        MMCRMachineRendersEvent registrations = new MMCRMachineRendersEvent(
                ModBlockEntities.controllerMachineIds());
        NeoForge.EVENT_BUS.post(registrations);
        registrations.freeze();
        registrations.renderers().forEach((machineId, renderer) -> {
            BlockEntityRendererProvider provider =
                    context -> new MachineControllerRendererDispatcher(machineId, renderer);
            event.registerBlockEntityRenderer(
                        (BlockEntityType) ModBlockEntities.controllerFor(machineId).get(),
                        provider);
        });
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
