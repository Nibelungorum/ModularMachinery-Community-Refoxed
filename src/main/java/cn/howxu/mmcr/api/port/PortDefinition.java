package cn.howxu.mmcr.api.port;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable declaration of the capabilities bound to one stable port identity.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface PortDefinition {
    ResourceLocation id();

    List<CapabilityBinding> bindings();

    static PortDefinition of(ResourceLocation id, List<CapabilityBinding> bindings) {
        return new Immutable(id, bindings);
    }

    static PortDefinition of(ResourceLocation id, CapabilityBinding... bindings) {
        return of(id, Arrays.asList(bindings));
    }

    /**
     * Immutable value implementation used by the factory methods.
     *
     * @author howxu <dev@howxu.cn>
     */
    record Immutable(ResourceLocation id, List<CapabilityBinding> bindings) implements PortDefinition {
        public Immutable {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(bindings, "bindings");
            for (CapabilityBinding binding : bindings) {
                Objects.requireNonNull(binding, "binding");
            }
            bindings = List.copyOf(bindings);
        }
    }
}
