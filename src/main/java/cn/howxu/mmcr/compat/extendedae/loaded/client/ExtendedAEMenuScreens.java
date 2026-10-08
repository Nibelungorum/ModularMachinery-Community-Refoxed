package cn.howxu.mmcr.compat.extendedae.loaded.client;

import appeng.client.InitScreens;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.client.PatternInterfaceScreen;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedAEMenuTypes;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Registers MMCR's extended interface and pattern-provider layouts.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExtendedAEMenuScreens {
    private ExtendedAEMenuScreens() {
    }

    public static void register(RegisterMenuScreensEvent event) {
        InitScreens.register(event, ExtendedAEMenuTypes.INTERFACE, ExtendedInterfaceScreen::new, "/screens/ex_interface.json");
        InitScreens.register(event, ExtendedAEMenuTypes.OVERSIZE, ExtendedInterfaceScreen::new, "/screens/oversize_interface.json");
        InitScreens.register(event, ExtendedAEMenuTypes.PATTERN, PatternInterfaceScreen::new, "/screens/ex_pattern_provider.json");
    }
}
