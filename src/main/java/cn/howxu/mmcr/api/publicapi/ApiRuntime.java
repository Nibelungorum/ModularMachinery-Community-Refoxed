package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Public-safe runtime hook used by the API artifact to reach its implementation.
 * @author howxu <dev@howxu.cn>
 */
public final class ApiRuntime {
    private static final Map<Identifier, CapabilityDefinition> CAPABILITY_DEFINITIONS = new LinkedHashMap<>();
    private static volatile Hook hook;
    private static boolean capabilitiesFrozen;

    private ApiRuntime() {
    }

    public static synchronized void install(Hook implementation) {
        hook = implementation;
    }

    public static synchronized void uninstall() {
        hook = null;
        CAPABILITY_DEFINITIONS.clear();
        capabilitiesFrozen = false;
    }

    public static boolean isRegistrationOpen() {
        Hook current = hook;
        return current != null && current.isRegistrationOpen();
    }

    public static void registerCapability(CapabilityDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (!isRegistrationOpen()) {
            throw new IllegalStateException("Capability registration rejected: startup registration is closed");
        }
        synchronized (ApiRuntime.class) {
            if (capabilitiesFrozen) {
                throw new IllegalStateException("Capability registration rejected: registry is frozen");
            }
            if (hook == null) {
                throw new IllegalStateException("Capability registration rejected: startup registration is closed");
            }
            Identifier id = definition.type().id();
            if (CAPABILITY_DEFINITIONS.putIfAbsent(id, definition) != null) {
                throw new IllegalStateException("Capability already registered: " + id);
            }
        }
    }

    public static synchronized CapabilityDefinition capability(CapabilityType type) {
        return type == null ? null : CAPABILITY_DEFINITIONS.get(type.id());
    }

    public static synchronized List<CapabilityDefinition> capabilityValues() {
        return List.copyOf(CAPABILITY_DEFINITIONS.values());
    }

    public static synchronized void freezeCapabilities() {
        capabilitiesFrozen = true;
    }

    /** Implementation boundary installed by the mod during startup. */
    public interface Hook {
        boolean isRegistrationOpen();
    }
}
