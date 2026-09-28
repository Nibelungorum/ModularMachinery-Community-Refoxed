package cn.howxu.mmcr.api.capability;

import net.minecraft.resources.ResourceLocation;

/**
 * Identifies a machine capability by its immutable {@link ResourceLocation}.
 * Equality and hash code are derived from that ResourceLocation, which is the
 * canonical key used by capability registries.
 *
 * @param id the capability ResourceLocation
 * @author howxu <dev@howxu.cn>
 */
public record CapabilityType(ResourceLocation id) {
    public CapabilityType {
        if (id == null) throw new IllegalArgumentException("id must not be null");
    }
}
