package cn.howxu.mmcr.api.capability.facet;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Owns the network representation of a capability state.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface SyncFacet extends CapabilityFacet {
    void encode(RegistryFriendlyByteBuf buffer);

    void decode(RegistryFriendlyByteBuf buffer);
}
