package cn.howxu.mmcr.api.capability.type;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.publicapi.ApiRuntime;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Startup registry for public capability definitions.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityRegistry {
    private static final Map<Identifier, CapabilityDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static boolean frozen;

    private CapabilityRegistry() {
    }

    /**
     * Registers a capability definition during the public startup window.
     *
     * @param definition the definition to register
     */
    public static synchronized void register(CapabilityDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (frozen) {
            throw new IllegalStateException("Capability registration rejected: registry is frozen");
        }
        if (!ApiRuntime.isRegistrationOpen()) {
            throw new IllegalStateException("Capability registration rejected: startup registration is closed");
        }
        Identifier id = definition.type().id();
        if (DEFINITIONS.putIfAbsent(id, definition) != null) {
            throw new IllegalStateException("Capability already registered: " + id);
        }
    }

    /**
     * Finds a definition by its canonical capability identity.
     *
     * @param type the capability identity
     * @return the definition, or {@code null} when it is not registered
     */
    public static synchronized CapabilityDefinition get(CapabilityType type) {
        return type == null ? null : DEFINITIONS.get(type.id());
    }

    /**
     * Returns an immutable ordered view of all registered definitions.
     *
     * @return the registered definitions
     */
    public static synchronized List<CapabilityDefinition> values() {
        return List.copyOf(DEFINITIONS.values());
    }

    /**
     * Closes capability registration for the current startup lifecycle.
     */
    public static synchronized void freeze() {
        frozen = true;
    }

    /** Test-only reset hook; not part of the public API surface. */
    public static synchronized void clearForTesting() {
        DEFINITIONS.clear();
        frozen = false;
    }
}
