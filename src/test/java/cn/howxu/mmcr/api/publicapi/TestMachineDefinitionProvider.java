package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;

/**
 * Service-loaded provider for the canonical machine definition event.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestMachineDefinitionProvider implements MachineDefinitionProvider {
    @Override
    public void register(MMCRMachineDefinationsEvent event) {
        event.registerMachine(MMCR.id("service_loaded_machine"), builder -> builder
                .displayNameKey("machine.mmcr.service_loaded_machine"));
    }
}
