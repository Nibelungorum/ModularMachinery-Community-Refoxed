package cn.howxu.mmcr.api.capability;

import net.minecraft.resources.Identifier;

/**
 * Identifies a machine capability by its immutable {@link Identifier}.
 * Equality and hash code are derived from that identifier, which is the
 * canonical key used by capability registries.
 *
 * @param id the capability identifier
 * @author howxu <dev@howxu.cn>
 */
public record CapabilityType(Identifier id) {
    public CapabilityType {
        if (id == null) throw new IllegalArgumentException("id must not be null");
    }
}
