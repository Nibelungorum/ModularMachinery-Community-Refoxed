package cn.howxu.mmcr.api.port;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Family-wide routing policy, shared by port declarations and legacy bindings using the family ID.
 * @author howxu <dev@howxu.cn>
 */
public record PortFamily(Identifier id, boolean scopesInputs) {
    private static final ConcurrentMap<Identifier, PortFamily> FAMILIES = new ConcurrentHashMap<>();

    public PortFamily {
        Objects.requireNonNull(id, "id");
    }

    public static PortFamily declare(Identifier id, boolean scopesInputs) {
        PortFamily family = new PortFamily(id, scopesInputs);
        PortFamily previous = FAMILIES.putIfAbsent(id, family);
        if (previous != null && !previous.equals(family)) {
            throw new IllegalArgumentException("Conflicting input routing policy for port family " + id);
        }
        return previous == null ? family : previous;
    }

    /** An undeclared third-party family keeps its original unscoped behavior. */
    public static boolean scopesInputs(Identifier id) {
        PortFamily family = FAMILIES.get(id);
        return family != null && family.scopesInputs();
    }
}
