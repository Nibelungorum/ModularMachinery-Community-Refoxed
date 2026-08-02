package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

public record DynamicMachine(
        Identifier registryName,
        String localizedName,
        BlockArray pattern
) implements Machine {
    public DynamicMachine {
        if (registryName == null) throw new IllegalArgumentException("registryName null");
        if (localizedName == null) throw new IllegalArgumentException("localizedName null");
        if (pattern == null) throw new IllegalArgumentException("pattern null");
    }
}
