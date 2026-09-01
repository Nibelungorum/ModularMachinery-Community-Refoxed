package cn.howxu.mmcr.api.capability.external;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Collects external handlers bound to MMCR capability types during startup.
 * @author howxu <dev@howxu.cn>
 */
public final class ExternalCapabilityContext {
    private final Map<CapabilityType, List<CapabilityBinding.ExternalExposure<?>>> bindings;
    private final Set<CapabilityType> supportedTypes;

    public ExternalCapabilityContext() {
        this(new LinkedHashMap<>(), null);
    }

    private ExternalCapabilityContext(Map<CapabilityType, List<CapabilityBinding.ExternalExposure<?>>> bindings,
                                      Set<CapabilityType> supportedTypes) {
        this.bindings = bindings;
        this.supportedTypes = supportedTypes;
    }

    public <T> void bind(CapabilityType type, CapabilityBinding.ExternalExposure<T> exposure) {
        type = Objects.requireNonNull(type, "type");
        if (supportedTypes != null && !supportedTypes.contains(type)) {
            throw new IllegalArgumentException("Adapter does not support capability type: " + type.id());
        }
        bindings.computeIfAbsent(type, ignored -> new ArrayList<>())
                .add(Objects.requireNonNull(exposure, "exposure"));
    }

    public List<CapabilityBinding.ExternalExposure<?>> bindings(CapabilityType type) {
        return List.copyOf(bindings.getOrDefault(Objects.requireNonNull(type, "type"), List.of()));
    }

    ExternalCapabilityContext restrictingTo(Set<CapabilityType> types) {
        return new ExternalCapabilityContext(bindings, Set.copyOf(types));
    }
}
