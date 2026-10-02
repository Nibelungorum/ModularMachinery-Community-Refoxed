package cn.howxu.mmcr;

import cn.howxu.mmcr.publicapi.event.RegisterControllerRenderersEvent;
import cn.howxu.mmcr.internal.api.facade.client.ClientRegistrationAdapters;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.create.CreateBridge;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauBridge;
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
import cn.howxu.mmcr.client.model.ControllerIdleEasterEggManager;
import cn.howxu.mmcr.client.model.MachineAppearanceCache;
import cn.howxu.mmcr.client.model.RuntimeMachineModelRegistry;
import cn.howxu.mmcr.client.model.RuntimeMachineResourcePack;
import cn.howxu.mmcr.client.renderer.MachineControllerRendererDispatcher;
import cn.howxu.mmcr.client.sound.MachineSoundManager;
import cn.howxu.mmcr.client.sound.LoadedSoundControllerTracker;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.client.preview.StructurePreviewReloadListener;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.lang.reflect.Constructor;

@Mod(value = MMCR.MODID, dist = Dist.CLIENT)
public class Client {
    private final LoadedSoundControllerTracker loadedSoundControllers = new LoadedSoundControllerTracker();
    private final MachineSoundManager machineSoundManager = new MachineSoundManager(loadedSoundControllers);

    public Client(IEventBus modBus, ModContainer modContainer) {
        MachineControllerBlockEntity.setClientLifecycleListeners(loadedSoundControllers::loaded, loadedSoundControllers::removed);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(Client::registerMenuScreens);
        modBus.addListener(Client::registerModelLoaders);
        modBus.addListener(Client::registerMachineRenderers);
        modBus.addListener(Client::registerCreateVisuals);
        // modBus.addListener(ArtificialStarRenderer::registerModel);
        modBus.addListener(Client::registerRuntimeResourcePack);
        modBus.addListener(Client::registerPreviewReloadListener);
        NeoForge.EVENT_BUS.addListener(this::tickMachineSounds);
        NeoForge.EVENT_BUS.addListener(Client::tickControllerIdleEasterEgg);
        NeoForge.EVENT_BUS.addListener(this::clearMachineSounds);
        NeoForge.EVENT_BUS.addListener(this::clearControllerScreenTextCache);
        MachineAppearanceCache.loadPersistedSnapshot();
        MachineAppearanceCache.addInvalidationListener(Client::invalidateMachineModels);
        ControllerSpecCache.addInvalidationListener(Client::invalidateMachineModels);
    }

    private void tickMachineSounds(ClientTickEvent.Post event) {
        machineSoundManager.clientTick(Minecraft.getInstance());
    }

    private static void tickControllerIdleEasterEgg(ClientTickEvent.Post event) {
        ControllerIdleEasterEggManager.clientTick(Minecraft.getInstance());
    }

    private void clearMachineSounds(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ControllerScreenTextCache.clearAll();
            ControllerIdleEasterEggManager.clear();
            machineSoundManager.clear();
        }
    }

    private void clearControllerScreenTextCache(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            loadedSoundControllers.unloadChunk(event.getChunk().getPos());
            ControllerScreenTextCache.clearChunk(event.getChunk().getPos().x, event.getChunk().getPos().z);
        }
    }

    private static void invalidateMachineModels() {
        DynamicOverlayBakedModel.clearCache();
        ControllerIdleEasterEggManager.clear();
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
        if (MekanismBridge.get().available()) {
            registerOptionalMenuScreen(event, ModUIs.CHEMICAL_PORT,
                    "cn.howxu.mmcr.client.gui.ChemicalHatchScreen");
            registerOptionalMenuScreen(event, ModUIs.HEAT_PORT,
                    "cn.howxu.mmcr.client.gui.HeatHatchScreen");
        }
        if (ArsNouveauBridge.get().available()) {
            registerOptionalMenuScreen(event, ModUIs.SOURCE_PORT,
                    "cn.howxu.mmcr.client.gui.SourceHatchScreen");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerOptionalMenuScreen(RegisterMenuScreensEvent event,
                                                    DeferredHolder<MenuType<?>, ?> menu,
                                                    String screenClassName) {
        if (menu == null) return;
        try {
            Class<?> screenClass = Class.forName(screenClassName);
            Constructor<?> constructor = screenClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            MenuScreens.ScreenConstructor factory = (container, inventory, title) -> {
                try {
                    return (AbstractContainerScreen) constructor.newInstance(container, inventory, title);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to create optional Mekanism screen "
                            + screenClassName, exception);
                }
            };
            event.register((MenuType) menu.get(), factory);
        } catch (ReflectiveOperationException exception) {
            MMCR.LOG.warn("Unable to register optional Mekanism screen {}", screenClassName, exception);
        }
    }

    private static void registerModelLoaders(ModelEvent.RegisterGeometryLoaders event) {
        RuntimeMachineModelRegistry.registerGeometryLoaders(event);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerMachineRenderers(EntityRenderersEvent.RegisterRenderers event) {
        invokeCreateClientRegistration("registerRenderers", EntityRenderersEvent.RegisterRenderers.class, event);
        RegisterControllerRenderersEvent registrations = new RegisterControllerRenderersEvent(
                ModBlockEntities.controllerMachineIds());
        NeoForge.EVENT_BUS.post(registrations);
        ClientRegistrationAdapters.freeze(registrations);
        ClientRegistrationAdapters.coreRenderers(registrations).forEach((machineId, renderer) -> {
            BlockEntityRendererProvider provider =
                    context -> new MachineControllerRendererDispatcher(machineId, renderer);
            event.registerBlockEntityRenderer(
                        (BlockEntityType) ModBlockEntities.controllerFor(machineId).get(),
                        provider);
        });
    }

    private static void registerCreateVisuals(FMLClientSetupEvent event) {
        invokeCreateClientRegistration("registerVisuals", FMLClientSetupEvent.class, event);
    }

    private static void invokeCreateClientRegistration(String method, Class<?> eventType, Object event) {
        if (!CreateBridge.get().available()) return;
        try {
            Class.forName("cn.howxu.mmcr.compat.create.loaded.CreateClientRegistration")
                    .getMethod(method, eventType).invoke(null, event);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to register Create client rendering", exception);
        }
    }

    private static void registerRuntimeResourcePack(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(RuntimeMachineResourcePack.source());
            // ae2 arrow resources
            event.addPackFinders(
                    MMCR.id("optional/arrow_interfaces"),
                    PackType.CLIENT_RESOURCES,
                    Component.translatable("pack.mmcr.arrow_interfaces_resource"),
                    PackSource.BUILT_IN,
                    false,
                    Pack.Position.TOP
            );
        }
    }

    private static void registerPreviewReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new StructurePreviewReloadListener());
    }

}
