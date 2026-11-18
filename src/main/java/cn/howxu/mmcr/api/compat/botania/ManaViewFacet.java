package cn.howxu.mmcr.api.compat.botania;

import cn.howxu.mmcr.api.capability.facet.AvailabilityFacet;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;


/**
 * Neutral view of real mana storage and its physical query identity.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ManaViewFacet extends AvailabilityFacet {
    @Override default Map<ResourceLocation, Fact> availability(ResourceLocation family) {
        return Map.of(family, new Fact(amount(), 0D, capacity() - amount()));
    }

    long amount();

    long capacity();

    Object queryIdentity();
}
