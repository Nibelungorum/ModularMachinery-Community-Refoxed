package cn.howxu.mmcr;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;

/** Test fixture for the shared optional source invocation path.
 * @author howxu <dev@howxu.cn>
 */
public final class OptionalGameTestSource {
    private static boolean invoked;

    private OptionalGameTestSource() {
    }

    public static void accept(MMCRMachineDefinationsEvent event) {
        invoked = true;
    }

    public static boolean invoked() {
        return invoked;
    }
}
