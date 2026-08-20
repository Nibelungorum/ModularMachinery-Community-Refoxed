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
    /**
     * Deprecated compatibility entrypoint for providers compiled against the original event signature.
     * The no-op default is the terminal end of the compatibility bridge; it must not call the canonical
     * entrypoint back.
     *
     * @deprecated implement {@link #register(MMCRMachineDefinationsEvent)} instead
     */
    @Deprecated(forRemoval = true)
    default void register(RegisterMachineDefinationsEvent event) {
    }

    /**
     * Canonical entrypoint used by MMCR's startup collection path. Older providers that only override
     * {@link #register(RegisterMachineDefinationsEvent)} continue to work through this one-way bridge.
     */
    default void register(MMCRMachineDefinationsEvent event) {
        register((RegisterMachineDefinationsEvent) event);
    }
}
