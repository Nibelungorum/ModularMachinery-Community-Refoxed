package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.publicapi.MachineDefinitionProvider;

import java.util.ServiceLoader;

/** Loads public startup definition providers before registration is finalized.
 * @author howxu <dev@howxu.cn>
 */
public final class PublicMachineDefinitionProviders {
    private PublicMachineDefinitionProviders() {
    }

    public static void registerAll() {
        ServiceLoader.load(MachineDefinitionProvider.class).forEach(MachineDefinitionProvider::register);
    }
}
