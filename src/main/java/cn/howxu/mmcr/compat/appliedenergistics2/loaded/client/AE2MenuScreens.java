package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.init.client.InitScreens;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Registers MMCR's AE2 menu screens using the native styles.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AE2MenuScreens {
    private AE2MenuScreens() {
    }

    public static void register(RegisterMenuScreensEvent event) {
        InitScreens.register(event, AE2MenuTypes.INTERFACE, AE2InterfaceScreen::new, "/screens/interface.json");
        InitScreens.register(event, AE2MenuTypes.PATTERN, PatternInterfaceScreen::new, "/screens/pattern_provider.json");
    }
}
