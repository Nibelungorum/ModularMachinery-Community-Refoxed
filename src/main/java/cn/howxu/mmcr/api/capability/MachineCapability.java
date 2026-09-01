package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.util.IOType;

import java.util.Objects;
import java.util.Optional;

/**
 * Provides access to a machine capability and prepares its operations.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface MachineCapability {
    CapabilityType type();

    IOType ioType();

    CapabilityView view();

    /**
     * Looks up a facet declared by this capability.
     *
     * @param facetType requested facet type
     * @param <F> facet type
     * @return the declared facet when this capability implements it
     */
    default <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        Objects.requireNonNull(facetType, "facetType");
        boolean declared = view().facets().contains(facetType);
        if (!declared || !facetType.isInstance(this)) return Optional.empty();
        return Optional.of(facetType.cast(this));
    }

    CapabilityOperation prepare(CapabilityRequest request);
}
