package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.util.IOType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Read-only identity and direction information for a capability.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CapabilityView {
    CapabilityType type();

    IOType ioType();

    /**
     * Returns the facet types declared by this capability snapshot.
     *
     * @return immutable declared facet types, or an empty set when none are declared
     */
    default Set<Class<? extends CapabilityFacet>> facets() {
        return Set.of();
    }

    /**
     * Returns the structural tags exposed by this capability snapshot.
     *
     * @return immutable capability tags, or an empty list when no tags are available
     */
    default List<String> tags() {
        return List.of();
    }

    /**
     * Applies the generic component tag matching contract.
     *
     * @param requirementTag the required tag, or {@code null}
     * @return whether this capability can satisfy the tag
     */
    default boolean matchesTag(@Nullable String requirementTag) {
        if (requirementTag == null || requirementTag.isBlank()) return true;
        return tags().isEmpty() || tags().contains(requirementTag);
    }
}
