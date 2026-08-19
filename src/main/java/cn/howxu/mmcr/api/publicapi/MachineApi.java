package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;

/** Public startup registration entry point for immutable machine definitions.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineApi {
    private MachineApi() {
    }

    public static void registerMachine(MachineDefinition definition) {
        if (definition == null) throw new NullPointerException("definition");
        ApiRuntime.registerMachine(definition);
    }

    public static boolean isRegistrationOpen() {
        return ApiRuntime.isRegistrationOpen();
    }
}
