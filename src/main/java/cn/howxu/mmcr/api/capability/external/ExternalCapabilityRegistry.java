package cn.howxu.mmcr.api.capability.external;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Coordinates optional external capability adapters during startup.
 * @author howxu <dev@howxu.cn>
 */
public final class ExternalCapabilityRegistry {
    private static final ExternalCapabilityRegistry GLOBAL = new ExternalCapabilityRegistry();
    private final Map<Identifier, ExternalCapabilityAdapter> adapters = new LinkedHashMap<>();
    private boolean frozen;

    public static ExternalCapabilityRegistry global() {
        return GLOBAL;
    }

    public synchronized void register(ExternalCapabilityAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        Identifier id = Objects.requireNonNull(adapter.id(), "adapter id");
        Objects.requireNonNull(adapter.capabilityTypes(), "adapter capability types");
        if (frozen) throw new IllegalStateException("External capability adapters are frozen");
        if (adapters.putIfAbsent(id, adapter) != null) {
            throw new IllegalArgumentException("Duplicate external capability adapter: " + id);
        }
    }

    public synchronized void freeze(ExternalCapabilityContext context) {
        Objects.requireNonNull(context, "context");
        if (frozen) return;
        frozen = true;
        adapters.values().stream()
                .filter(ExternalCapabilityAdapter::isAvailable)
                .forEach(adapter -> adapter.register(context.restrictingTo(adapter.capabilityTypes())));
    }
}
