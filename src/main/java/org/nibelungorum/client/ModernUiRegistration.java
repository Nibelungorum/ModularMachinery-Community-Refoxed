package org.nibelungorum.client;

import cn.howxu.mmcr.publicapi.event.RegisterControllerUisEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;

/** Loads the demo UI only after the optional Modern UI mod is present.
 * @author howxu <dev@howxu.cn> */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ModernUiRegistration {
    private ModernUiRegistration() {}

    @SubscribeEvent
    public static void register(RegisterControllerUisEvent event) {
        if (ModList.get().isLoaded("modernui")) ModernUiControllerUi.register(event);
    }
}
