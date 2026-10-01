package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.publicapi.registration.MachineDefinitionProvider;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;

/**
 * Service-loaded provider for the canonical machine definition event.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestMachineDefinitionProvider implements MachineDefinitionProvider {
    @Override
    public void register(RegisterMachineDefinitionsEvent event) {
        event.registerMachine(MMCR.id("service_loaded_machine"), builder -> builder
                .displayNameKey("machine.mmcr.service_loaded_machine"));
    }
}
