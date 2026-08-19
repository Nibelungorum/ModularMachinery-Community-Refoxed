package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;

/** Public startup registration entry point for immutable machine definitions.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineApi {
    private MachineApi() {
    }

    /**
     * Registers a machine definition during the startup registration window.
     *
     * @param definition definition to register
     * @throws NullPointerException if {@code definition} is null
     * @throws ApiRegistrationException if the window is not open or the ID is duplicated
     */
    public static void registerMachine(MachineDefinition definition) {
        if (definition == null) throw new NullPointerException("definition");
        ApiRuntime.registerMachine(definition);
    }

    /**
     * Returns whether startup registration is currently accepting machine definitions.
     *
     * @return {@code true} while the startup registration window is open
     */
    public static boolean isRegistrationOpen() {
        return ApiRuntime.isRegistrationOpen();
    }
}
