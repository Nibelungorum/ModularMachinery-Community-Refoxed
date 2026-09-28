package cn.howxu.mmcr.internal.multiblock;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Runtime module recipe gate derived from the active module coupler connection.
 *
 * @author howxu <dev@howxu.cn>
 */
public record ModuleConnectionStatus(boolean required, ResourceLocation connectedHostId) {
    public static ModuleConnectionStatus notRequired() {
        return new ModuleConnectionStatus(false, null);
    }

    public static ModuleConnectionStatus disconnected() {
        return new ModuleConnectionStatus(true, null);
    }

    public static ModuleConnectionStatus connected(ResourceLocation hostId) {
        if (hostId == null) throw new IllegalArgumentException("Connected module host id must not be null");
        return new ModuleConnectionStatus(true, hostId);
    }

    public boolean connected() {
        return connectedHostId != null;
    }

    public boolean canRunRecipe(Set<ResourceLocation> requiredHostIds) {
        if (!required) return true;
        if (connectedHostId == null) return false;
        return requiredHostIds == null || requiredHostIds.isEmpty() || requiredHostIds.contains(connectedHostId);
    }
}
