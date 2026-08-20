package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.publicapi.MachineDefinitionProvider;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineDefinationsEvent;

import java.util.ServiceLoader;

/** Loads public startup definition providers before registration is finalized.
 * @author howxu <dev@howxu.cn>
 */
public final class PublicMachineDefinitionProviders {
    private PublicMachineDefinitionProviders() {
    }

    public static void registerAll(RegisterMachineDefinationsEvent event) {
        ServiceLoader.load(MachineDefinitionProvider.class).forEach(provider -> provider.register(event));
    }

    public static void registerAll() {
        RegisterMachineDefinationsEvent event = new RegisterMachineDefinationsEvent();
        registerAll(event);
        event.freeze();
        PublicApiBootstrap.registerDefinitions(event);
    }
}
