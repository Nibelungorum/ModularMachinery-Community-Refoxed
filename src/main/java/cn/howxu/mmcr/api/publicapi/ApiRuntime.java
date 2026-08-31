package cn.howxu.mmcr.api.publicapi;

/** Public-safe runtime hook used by the API artifact to reach its implementation.
 * @author howxu <dev@howxu.cn>
 */
public final class ApiRuntime {
    private static Hook hook;

    private ApiRuntime() {
    }

    public static synchronized void install(Hook implementation) {
        hook = implementation;
    }

    public static synchronized void uninstall() {
        hook = null;
    }

    public static synchronized boolean isRegistrationOpen() {
        return hook != null && hook.isRegistrationOpen();
    }

    /** Implementation boundary installed by the mod during startup. */
    public interface Hook {
        boolean isRegistrationOpen();
    }
}
