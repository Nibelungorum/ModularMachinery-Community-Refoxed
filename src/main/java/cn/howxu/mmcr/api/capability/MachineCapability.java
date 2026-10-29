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

    default CapabilityDirections directions() {
        return CapabilityDirections.of(ioType());
    }

    /**
     * @deprecated use {@link #directions()} to determine whether an operation direction is supported
     */
    @Deprecated(forRemoval = true)
    default IOType ioType() {
        if (directions().values().size() != 1) {
            throw new IllegalStateException("Capability does not have exactly one direction");
        }
        return directions().values().iterator().next();
    }

    CapabilityView view();

    default int outputPriority() {
        return 0;
    }

    /**
     * Looks up a facet declared by this capability.
     *
     * @param facetType requested facet type
     * @param <F> facet type
     * @return the declared facet when this capability implements it
     */
    default <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
        Objects.requireNonNull(facetType, "facetType");
        if (!facetType.isInstance(this)) return Optional.empty();
        if (!view().facets().contains(facetType)) return Optional.empty();
        return Optional.of(facetType.cast(this));
    }

    CapabilityOperation prepare(CapabilityRequest request);
}
