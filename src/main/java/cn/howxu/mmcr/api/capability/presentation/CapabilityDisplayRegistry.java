package cn.howxu.mmcr.api.capability.presentation;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves capability presentation renderers by capability type.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityDisplayRegistry {
    private static final CapabilityDisplayRegistry GLOBAL = new CapabilityDisplayRegistry();
    private final Map<CapabilityType, Function<MachineCapability, List<CapabilityDisplay>>> renderers = new LinkedHashMap<>();

    public static CapabilityDisplayRegistry global() {
        return GLOBAL;
    }

    public void register(CapabilityType type, Function<MachineCapability, List<CapabilityDisplay>> renderer) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(renderer, "renderer");
        if (renderers.putIfAbsent(type, renderer) != null) {
            throw new IllegalArgumentException("Display renderer already registered: " + type.id());
        }
    }

    public List<CapabilityDisplay> displays(MachineCapability capability) {
        Objects.requireNonNull(capability, "capability");
        Optional<PresentationFacet> facet = capability.facet(PresentationFacet.class);
        if (facet.isPresent()) return List.copyOf(facet.get().displays(capability.view()));
        Function<MachineCapability, List<CapabilityDisplay>> renderer = renderers.get(capability.type());
        if (renderer != null) return List.copyOf(renderer.apply(capability));
        return List.of(new CapabilityDisplay(capability.type().id().toString(), "Unavailable", "", Optional.empty()));
    }
}
