package cn.howxu.mmcr.api.capability.facet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Owns a namespaced persistent capability state.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface PersistenceFacet extends CapabilityFacet {
    String stateKey();

    void save(CompoundTag output, HolderLookup.Provider registries);

    void load(CompoundTag input, HolderLookup.Provider registries);
}
