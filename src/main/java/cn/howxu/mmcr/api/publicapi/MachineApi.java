package cn.howxu.mmcr.api.publicapi;

/** Public startup machine lifecycle status API.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineApi {
    private MachineApi() {
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
