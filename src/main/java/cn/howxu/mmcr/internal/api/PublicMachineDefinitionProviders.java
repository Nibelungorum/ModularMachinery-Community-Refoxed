package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.publicapi.MachineDefinitionProvider;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;

import java.util.ServiceLoader;

/** Loads public startup definition providers before registration is finalized.
 * @author howxu <dev@howxu.cn>
 */
public final class PublicMachineDefinitionProviders {
    private PublicMachineDefinitionProviders() {
    }

    public static void registerAll(MMCRMachineDefinationsEvent event) {
        ServiceLoader.load(MachineDefinitionProvider.class).forEach(provider -> provider.register(event));
    }

}
