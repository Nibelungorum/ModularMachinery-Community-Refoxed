package cn.howxu.mmcr;

import cn.howxu.mmcr.client.gui.EnergyHatchScreen;
import cn.howxu.mmcr.client.gui.FactoryControllerScreen;
import cn.howxu.mmcr.client.gui.FactorySchedulerScreen;
import cn.howxu.mmcr.client.gui.FluidHatchScreen;
import cn.howxu.mmcr.client.gui.ItemBusScreen;
import cn.howxu.mmcr.client.gui.MachineControllerScreen;
import cn.howxu.mmcr.client.gui.SmartInterfaceScreen;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.menu.ExtendedCombinedMenu;
import cn.howxu.mmcr.internal.menu.ExtendedFluidMenu;
import cn.howxu.mmcr.internal.menu.ExtendedItemMenu;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

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
        event.register(ModUIs.ITEM_BUS.get(), ItemBusScreen::new);
        event.register(ModUIs.FLUID_HATCH.get(), FluidHatchScreen::new);
        event.register(ModUIs.ENERGY_HATCH.get(), EnergyHatchScreen::new);
        event.register(ModUIs.MACHINE_CONTROLLER.get(), MachineControllerScreen::new);
        event.register(ModUIs.FACTORY_SCHEDULER.get(), FactorySchedulerScreen::new);
        event.register(ModUIs.FACTORY_CONTROLLER.get(), FactoryControllerScreen::new);
        event.register(ModUIs.SMART_INTERFACE.get(), SmartInterfaceScreen::new);
        event.<ExtendedItemMenu, TextPortScreen<ExtendedItemMenu>>register(ModUIs.EXTENDED_ITEM.get(), (menu, inventory, title) ->
                new TextPortScreen<>(menu, inventory, title));
        event.<ExtendedFluidMenu, TextPortScreen<ExtendedFluidMenu>>register(ModUIs.EXTENDED_FLUID.get(), (menu, inventory, title) ->
                new TextPortScreen<>(menu, inventory, title));
        event.<CombinedPortMenu, TextPortScreen<CombinedPortMenu>>register(ModUIs.COMBINED.get(), (menu, inventory, title) ->
                new TextPortScreen<>(menu, inventory, title));
        event.<ExtendedCombinedMenu, TextPortScreen<ExtendedCombinedMenu>>register(ModUIs.EXTENDED_COMBINED.get(), (menu, inventory, title) ->
                new TextPortScreen<>(menu, inventory, title));
    }

    private static final class TextPortScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
        private static final int BACKGROUND = 0xFF262626;
        private static final int BORDER = 0xFF8A8A8A;
        private static final int TEXT = 0xFFE0E0E0;

        private TextPortScreen(M menu, Inventory inventory, Component title) {
            super(menu, inventory, title, 176, 166);
            if (!(menu instanceof CombinedPortMenu)) inventoryLabelY = -1000;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BACKGROUND);
            graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, BORDER);
            graphics.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, BORDER);
            graphics.fill(leftPos, topPos, leftPos + 1, topPos + imageHeight, BORDER);
            graphics.fill(leftPos + imageWidth - 1, topPos, leftPos + imageWidth, topPos + imageHeight, BORDER);
            if (menu instanceof CombinedPortMenu combined) {
                for (CombinedPortMenu.FluidTankLayout layout : combined.fluidTankLayouts()) {
                    graphics.fill(leftPos + layout.x(), topPos + layout.y(),
                            leftPos + layout.x() + 20, topPos + layout.y() + 61, 0xFF3A3A3A);
                }
            }
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            graphics.text(font, title, 8, 6, TEXT, false);
            int y = 22;
            if (menu instanceof ExtendedItemMenu item) {
                y = drawItems(graphics, item.entries(), y);
            } else if (menu instanceof ExtendedFluidMenu fluid) {
                y = drawFluids(graphics, fluid.entries(), y);
            } else if (menu instanceof CombinedPortMenu combined) {
                y = drawFluids(graphics, combined.fluidEntries(), y);
            } else if (menu instanceof ExtendedCombinedMenu combined) {
                y = drawItems(graphics, combined.itemEntries(), y);
                drawFluids(graphics, combined.fluidEntries(), y);
            }
        }

        private int drawItems(GuiGraphicsExtractor graphics, List<ItemStorageEntry> entries, int y) {
            for (ItemStorageEntry entry : entries) {
                graphics.text(font, Component.literal(entry.slot() + ": " + entry.resource().getHoverName()
                        + " " + entry.amount() + " / " + entry.capacity()), 8, y, TEXT, false);
                y += 10;
            }
            return y;
        }

        private int drawFluids(GuiGraphicsExtractor graphics, List<FluidStorageEntry> entries, int y) {
            for (FluidStorageEntry entry : entries) {
                graphics.text(font, Component.literal(entry.slot() + ": " + entry.resource().getHoverName()
                        + " " + entry.amount() + " / " + entry.capacity()), 8, y, TEXT, false);
                y += 10;
            }
            return y;
        }
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
