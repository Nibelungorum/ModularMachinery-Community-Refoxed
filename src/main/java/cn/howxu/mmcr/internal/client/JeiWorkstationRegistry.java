package cn.howxu.mmcr.internal.client;

import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;

import java.util.List;

/**
 * Startup-script workstation registrations retained until JEI initializes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiWorkstationRegistry {
    private static volatile List<JeiWorkstationRegistration> kubeJSEntries = List.of();

    private JeiWorkstationRegistry() {
    }

    public static void replaceKubeJS(List<JeiWorkstationRegistration> entries) {
        kubeJSEntries = List.copyOf(entries);
    }

    public static List<JeiWorkstationRegistration> kubeJSEntries() {
        return kubeJSEntries;
    }
}
