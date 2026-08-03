package cn.howxu.mmcr;

import cn.howxu.mmcr.client.gui.MMCRMenuScreen;
import cn.howxu.mmcr.registry.ModUIs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = MMCR.MODID, dist = Dist.CLIENT)
public class Client {
    public Client(IEventBus modBus) {
        modBus.addListener(Client::registerMenuScreens);
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        MMCRMenuScreen.registerScreens(event);
    }
}