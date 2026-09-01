package cn.howxu.mmcr.api.capability;

import java.util.List;
import java.util.Objects;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;

/**
 * An immutable snapshot of the capabilities hosted by a machine.
 *
 * @param capabilities the capabilities in this snapshot
 * @author howxu <dev@howxu.cn>
 */
public record CapabilitySnapshot(List<MachineCapability> capabilities, List<CapabilityFacet> additionalFacets) {
    public CapabilitySnapshot(List<MachineCapability> capabilities) {
        this(capabilities, List.of());
    }

    public CapabilitySnapshot {
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        additionalFacets = List.copyOf(Objects.requireNonNull(additionalFacets, "additionalFacets"));
    }

    public <F extends CapabilityFacet> List<F> facets(Class<F> facetType) {
        Objects.requireNonNull(facetType, "facetType");
        return java.util.stream.Stream.concat(
                        capabilities.stream().map(capability -> capability.facet(facetType))
                                .flatMap(java.util.Optional::stream),
                        additionalFacets.stream().filter(facetType::isInstance).map(facetType::cast))
                .toList();
    }
}
