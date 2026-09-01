package cn.howxu.mmcr.api.capability.facet;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Owns a namespaced persistent capability state.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface PersistenceFacet extends CapabilityFacet {
    String stateKey();

    void save(ValueOutput output);

    void load(ValueInput input);
}
