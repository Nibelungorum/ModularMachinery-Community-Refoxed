package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;

/**
 * Startup extension point for declaring public machine and recipe definitions.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface MachineDefinitionProvider {
    /**
     * Registers this provider's definitions during startup.
     *
     * @throws ApiRegistrationException if startup registration has not begun, has been finalized,
     *                                   or a declaration is invalid or duplicated
     */
    default void register(MMCRMachineDefinationsEvent event) {
    }
}
