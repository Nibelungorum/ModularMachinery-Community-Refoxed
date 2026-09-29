package cn.howxu.mmcr.api.compat.mekanism;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Mekanism-neutral read access to a chemical capability.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ChemicalViewFacet extends CapabilityFacet {
    Optional<ResourceLocation> chemicalId();

    long amount();

    boolean matchesTag(ResourceLocation tagId);

    long outputCapacity(ResourceLocation chemicalId);
}
