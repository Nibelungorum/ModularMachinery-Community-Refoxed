package cn.howxu.mmcr.api.port;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;

/**
 * Immutable declaration of the capabilities bound to one stable port identity.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface PortDefinition {
    Identifier id();

    List<CapabilityBinding> bindings();

    static PortDefinition of(Identifier id, List<CapabilityBinding> bindings) {
        return new Immutable(id, bindings);
    }

    static PortDefinition of(Identifier id, CapabilityBinding... bindings) {
        return of(id, Arrays.asList(bindings));
    }

    /**
     * Immutable value implementation used by the factory methods.
     *
     * @author howxu <dev@howxu.cn>
     */
    record Immutable(Identifier id, List<CapabilityBinding> bindings) implements PortDefinition {
        public Immutable {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(bindings, "bindings");
            HashSet<CapabilityType> types = new HashSet<>();
            for (CapabilityBinding binding : bindings) {
                Objects.requireNonNull(binding, "binding");
                if (!types.add(binding.type())) {
                    throw new IllegalArgumentException("duplicate capability binding: " + binding.type().id());
                }
            }
            bindings = List.copyOf(bindings);
        }
    }
}
