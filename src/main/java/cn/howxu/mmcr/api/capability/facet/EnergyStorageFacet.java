package cn.howxu.mmcr.api.capability.facet;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Exposes a native energy storage for a machine capability.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface EnergyStorageFacet extends CapabilityFacet {
    IEnergyStorage energyStorage();
}
