package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.publicapi.ApiRuntime;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;

/**
 * Coordinates installation of immutable public machine and recipe startup declarations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PublicApiBootstrap {
    private static boolean begun;

    private PublicApiBootstrap() {
    }

    public static synchronized void begin() {
        if (!begun) {
            ContentRegistrationCoordinator.beginStartup();
            ApiRuntime.install(new ApiRuntime.Hook() {
                @Override
                public boolean isRegistrationOpen() {
                    return PublicApiBootstrap.isRegistrationOpen();
                }
            });
            begun = true;
        }
    }

    public static synchronized boolean isRegistrationOpen() {
        return begun && !ContentRegistrationCoordinator.isCommitted();
    }

    /** Test-only reset hook; not part of the public API surface. */
    public static synchronized void clearForTesting() {
        ContentRegistrationCoordinator.clearForTesting();
        resetStateForTesting();
    }

    /** Resets the public API lifecycle without recursively resetting the coordinator. */
    public static synchronized void resetStateForTesting() {
        MachineStructureRegistry.clearForTesting();
        begun = false;
        ApiRuntime.uninstall();
    }
}
