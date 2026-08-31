package cn.howxu.mmcr.api.capability.type;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;

import java.util.Objects;
import java.util.Set;

/**
 * Public declaration of a capability type and its creation contract.
 *
 * @param type the immutable capability identity
 * @param facets the facets exposed by the capability
 * @param factory the factory used to create the capability
 * @author howxu <dev@howxu.cn>
 */
public record CapabilityDefinition(CapabilityType type,
                                   Set<Class<? extends CapabilityFacet>> facets,
                                   CapabilityFactory factory) {
    public CapabilityDefinition {
        Objects.requireNonNull(type, "type");
        facets = Set.copyOf(Objects.requireNonNull(facets, "facets"));
        Objects.requireNonNull(factory, "factory");
    }
}
