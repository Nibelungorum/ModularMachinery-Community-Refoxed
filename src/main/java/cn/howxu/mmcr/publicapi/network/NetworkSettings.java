package cn.howxu.mmcr.publicapi.network;

import cn.howxu.mmcr.internal.api.facade.network.NetworkAdapters;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import java.util.Set;

/** MMCR-produced immutable network limits and allowlist. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface NetworkSettings {
    static NetworkSettings of(int maxCount, int maxConnections, Set<ResourceLocation> allowedMachineIds) {
        return NetworkAdapters.settings(maxCount, maxConnections, allowedMachineIds);
    }
    static NetworkSettings disabled() { return of(0, 0, Set.of()); }
    int maxCount();
    int maxConnections();
    Set<ResourceLocation> allowedMachineIds();
    NetworkSettings withAllowedMachine(ResourceLocation machineId);
}
