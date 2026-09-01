package cn.howxu.mmcr.api.capability.external;

import cn.howxu.mmcr.api.capability.CapabilityType;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Coordinates optional external capability adapters during startup.
 * @author howxu <dev@howxu.cn>
 */
public final class ExternalCapabilityRegistry {
    private static final ExternalCapabilityRegistry GLOBAL = new ExternalCapabilityRegistry();
    private final Map<Identifier, AdapterRegistration> adapters = new LinkedHashMap<>();
    private boolean frozen;

    public static ExternalCapabilityRegistry global() {
        return GLOBAL;
    }

    public synchronized void register(ExternalCapabilityAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        Identifier id = Objects.requireNonNull(adapter.id(), "adapter id");
        Set<CapabilityType> capabilityTypes = Set.copyOf(
                Objects.requireNonNull(adapter.capabilityTypes(), "adapter capability types"));
        if (frozen) throw new IllegalStateException("External capability adapters are frozen");
        if (adapters.putIfAbsent(id, new AdapterRegistration(adapter, capabilityTypes)) != null) {
            throw new IllegalArgumentException("Duplicate external capability adapter: " + id);
        }
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    public synchronized boolean isRegistered(Identifier id) {
        return id != null && adapters.containsKey(id);
    }

    public synchronized void freeze(ExternalCapabilityContext context) {
        Objects.requireNonNull(context, "context");
        if (frozen) return;
        frozen = true;
        adapters.values().stream()
                .filter(registration -> registration.adapter().isAvailable())
                .forEach(registration -> registration.adapter().register(
                        context.restrictingTo(registration.capabilityTypes())));
    }

    private record AdapterRegistration(ExternalCapabilityAdapter adapter,
                                       Set<CapabilityType> capabilityTypes) {
    }
}
