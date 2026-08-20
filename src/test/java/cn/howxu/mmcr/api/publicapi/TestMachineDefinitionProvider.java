package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineDefinationsEvent;

/**
 * Service-loaded compatibility provider that implements only the deprecated event signature.
 * It verifies that canonical startup collection still dispatches to external providers from Task 1.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestMachineDefinitionProvider implements MachineDefinitionProvider {
    @Override
    public void register(RegisterMachineDefinationsEvent event) {
        event.registerMachine(MMCR.id("service_loaded_machine"), builder -> builder
                .displayNameKey("machine.mmcr.service_loaded_machine"));
    }
}
