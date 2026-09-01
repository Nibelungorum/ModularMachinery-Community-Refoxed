package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
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
        boolean declared = view().facets().stream().anyMatch(facetType::isAssignableFrom);
        if (!declared || !facetType.isInstance(this)) return Optional.empty();
        return Optional.of(facetType.cast(this));
    }

    /**
     * Returns the capability's backing storage protocol for requirement handlers.
     *
     * @return the backing storage, or {@code null} for non-storage capabilities
     * @deprecated compatibility boundary for pre-facet consumers; use a declared
     * typed facet such as {@code ResourceFacet} or {@code ValueFacet} instead
     */
    @Deprecated
    default CapabilityStorage storage() {
        CapabilityStorage resourceStorage = facet(ResourceFacet.class)
                .map(ResourceFacet::storage)
                .orElse(null);
        if (resourceStorage != null) return resourceStorage;
        return facet(ValueFacet.class).map(ValueFacet::storage).orElse(null);
    }

    CapabilityOperation prepare(CapabilityRequest request);
}
