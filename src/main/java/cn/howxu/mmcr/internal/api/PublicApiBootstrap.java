package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.publicapi.ApiRuntime;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;

import java.util.Set;

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
        CapabilityRegistry.freeze();
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
        CapabilityRegistry.clearForTesting();
        begun = false;
        ApiRuntime.uninstall();
    }

    private static void registerBuiltinCapabilities() {
        CapabilityRegistry.register(new CapabilityDefinition(
                new CapabilityType(MMCR.id("item")),
                Set.of(),
                context -> CapabilityFactories.ITEM_BUS.create(port(context))));
        CapabilityRegistry.register(new CapabilityDefinition(
                new CapabilityType(MMCR.id("fluid")),
                Set.of(),
                context -> CapabilityFactories.FLUID_HATCH.create(port(context))));
        CapabilityRegistry.register(new CapabilityDefinition(
                new CapabilityType(MMCR.id("energy")),
                Set.of(),
                context -> CapabilityFactories.ENERGY_HATCH.create(port(context))));
    }

    private static IOPortBlockEntity port(CapabilityCreationContext context) {
        if (context.host() instanceof IOPortBlockEntity port) return port;
        throw new IllegalArgumentException("Built-in port capability requires an IOPortBlockEntity host");
    }
}
