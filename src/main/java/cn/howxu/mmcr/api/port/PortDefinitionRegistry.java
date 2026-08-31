package cn.howxu.mmcr.api.port;

import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Startup registry for immutable port capability definitions.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PortDefinitionRegistry {
    private static final Map<Identifier, PortDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static boolean frozen;

    private PortDefinitionRegistry() {
    }

    public static synchronized void register(PortDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (frozen) throw new IllegalStateException("Port definitions are frozen");
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("Duplicate port definition: " + definition.id());
        }
    }

    public static synchronized PortDefinition get(Identifier id) {
        return id == null ? null : DEFINITIONS.get(id);
    }

    public static synchronized List<PortDefinition> values() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static synchronized List<CapabilityBinding> resolve(Identifier id, IOType ioType, int tier) {
        Objects.requireNonNull(ioType, "ioType");
        PortDefinition definition = get(id);
        if (definition == null) return List.of();
        return definition.bindings().stream()
                .filter(binding -> binding.ioType() == ioType && binding.supports(tier))
                .toList();
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    /** Test-only lifecycle reset; production registration is not reopened by this method. */
    public static synchronized void clearForTesting() {
        DEFINITIONS.clear();
        frozen = false;
    }
}
