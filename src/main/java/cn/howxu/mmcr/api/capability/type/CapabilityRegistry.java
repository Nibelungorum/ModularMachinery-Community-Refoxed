package cn.howxu.mmcr.api.capability.type;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.publicapi.ApiRuntime;

import java.util.List;

/**
 * Startup registry for public capability definitions.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityRegistry {
    private CapabilityRegistry() {
    }

    /**
     * Registers a capability definition during the public startup window.
     *
     * @param definition the definition to register
     */
    public static void register(CapabilityDefinition definition) {
        ApiRuntime.registerCapability(definition);
    }

    /**
     * Finds a definition by its canonical capability identity.
     *
     * @param type the capability identity
     * @return the definition, or {@code null} when it is not registered
     */
    public static CapabilityDefinition get(CapabilityType type) {
        return ApiRuntime.capability(type);
    }

    /**
     * Returns an immutable ordered snapshot of all registered definitions.
     *
     * @return the registered definitions
     */
    public static List<CapabilityDefinition> values() {
        return ApiRuntime.capabilityValues();
    }

    /**
     * Closes capability registration for the current startup lifecycle.
     */
    public static void freeze() {
        ApiRuntime.freezeCapabilities();
    }
}
