package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.publicapi.ApiRuntime;
import cn.howxu.mmcr.api.capability.transfer.TransferStrategyRegistry;
import cn.howxu.mmcr.internal.autoio.CapabilityTransferPolicies;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
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
            begun = true;
            ApiRuntime.install(new ApiRuntime.Hook() {
                @Override
                public boolean isRegistrationOpen() {
                    return PublicApiBootstrap.isRegistrationOpen();
                }
            });
            registerBuiltinCapabilities();
        }
    }

    /** Closes capability registration before world content registration starts. */
    public static synchronized void freeze() {
        CapabilityTransferPolicies.ensureRegistered();
        CapabilityRegistry.freeze();
        TransferStrategyRegistry.freeze();
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

    private static void registerBuiltinCapabilities() {
        BuiltinCapabilityDefinitions.register();
    }
}
