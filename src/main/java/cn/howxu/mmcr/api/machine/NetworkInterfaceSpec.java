package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable declaration of a machine's network interface limits and whitelist.
 * @author howxu <dev@howxu.cn>
 */
public record NetworkInterfaceSpec(int maxCount, int maxConnections, Set<ResourceLocation> allowedMachineIds) {
    public NetworkInterfaceSpec {
        if (maxCount < 0) throw new IllegalArgumentException("maxCount must be non-negative");
        if (maxConnections < 0) throw new IllegalArgumentException("maxConnections must be non-negative");
        allowedMachineIds = copyAllowedMachineIds(allowedMachineIds);
    }

    public static NetworkInterfaceSpec disabled() {
        return new NetworkInterfaceSpec(0, 0, Set.of());
    }

    public NetworkInterfaceSpec withAllowedMachine(ResourceLocation machineId) {
        LinkedHashSet<ResourceLocation> copy = new LinkedHashSet<>(allowedMachineIds);
        copy.add(Objects.requireNonNull(machineId, "machineId"));
        return new NetworkInterfaceSpec(maxCount, maxConnections, copy);
    }

    private static Set<ResourceLocation> copyAllowedMachineIds(Set<ResourceLocation> allowedMachineIds) {
        if (allowedMachineIds == null || allowedMachineIds.isEmpty()) return Set.of();
        LinkedHashSet<ResourceLocation> copy = new LinkedHashSet<>();
        for (ResourceLocation machineId : allowedMachineIds) {
            copy.add(Objects.requireNonNull(machineId, "machineId"));
        }
        return Collections.unmodifiableSet(copy);
    }
}
