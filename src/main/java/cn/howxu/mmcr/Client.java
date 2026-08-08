package cn.howxu.mmcr;

import cn.howxu.mmcr.client.gui.MachineMenuScreen;
import cn.howxu.mmcr.client.controller.ControllerModelInvalidator;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.client.model.DynamicOverlayBakedModel;
import cn.howxu.mmcr.client.model.MachineAppearanceCache;
import cn.howxu.mmcr.registry.ModUIs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.client.Minecraft;

@Mod(value = MMCR.MODID, dist = Dist.CLIENT)
public class Client {
    public Client(IEventBus modBus) {
        modBus.addListener(Client::registerMenuScreens);
        MachineAppearanceCache.loadPersistedSnapshot();
        MachineAppearanceCache.addInvalidationListener(Client::invalidateMachineModels);
        ControllerSpecCache.addInvalidationListener(Client::invalidateMachineModels);
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
    }
}
