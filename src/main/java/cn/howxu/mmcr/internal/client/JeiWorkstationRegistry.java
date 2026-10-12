package cn.howxu.mmcr.internal.client;

import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;

import java.util.List;

/**
 * Startup-script workstations and the last atomically committed viewer cycle.
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

    public static List<JeiWorkstationRegistration> publicEntries() {
        return RecipeInformationRegistry.publicSnapshot().workstations();
    }
}
