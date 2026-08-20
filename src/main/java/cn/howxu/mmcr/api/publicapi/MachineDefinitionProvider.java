package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.publicapi.event.RegisterMachineDefinationsEvent;
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
    void register(RegisterMachineDefinationsEvent event);

    /**
     * Canonical entrypoint used by MMCR's startup collection path. Older providers that only override
     * {@link #register(RegisterMachineDefinationsEvent)} continue to work through this bridge.
     */
    default void register(MMCRMachineDefinationsEvent event) {
        register((RegisterMachineDefinationsEvent) event);
    }
}
