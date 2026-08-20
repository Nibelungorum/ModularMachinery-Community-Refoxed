package cn.howxu.mmcr.api.publicapi;

/** Public startup recipe lifecycle status API.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeApi {
    private RecipeApi() {
    }

    /**
     * Returns whether startup registration is currently accepting recipe definitions.
     *
     * @return {@code true} while the startup registration window is open
     */
    public static boolean isRegistrationOpen() {
        return ApiRuntime.isRegistrationOpen();
    }
}
