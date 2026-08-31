package cn.howxu.mmcr.api.capability.type;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;

/**
 * Binds one hosted capability to a port direction, tier policy, and creation factory.
 *
 * @param type the hosted capability type
 * @param ioType the direction of the binding
 * @param factory the factory used to create the hosted capability
 * @param tierPolicy the policy used to determine whether the binding is available at a tier
 * @param externalExposure the optional native capability provider exposed for this binding
 * @author howxu <dev@howxu.cn>
 */
public record CapabilityBinding(CapabilityType type,
                                IOType ioType,
                                CapabilityFactory factory,
                                PortTierPolicy tierPolicy,
                                Optional<ExternalExposure<?>> externalExposure) {
    public CapabilityBinding(CapabilityType type, IOType ioType,
                             CapabilityFactory factory, PortTierPolicy tierPolicy) {
        this(type, ioType, factory, tierPolicy, Optional.empty());
    }

    public CapabilityBinding(CapabilityType type, IOType ioType,
                             CapabilityFactory factory, PortTierPolicy tierPolicy,
                             ExternalExposure<?> externalExposure) {
        this(type, ioType, factory, tierPolicy, Optional.ofNullable(externalExposure));
    }

    public CapabilityBinding {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ioType, "ioType");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(tierPolicy, "tierPolicy");
        Objects.requireNonNull(externalExposure, "externalExposure");
    }

    public boolean supports(int tier) {
        return tierPolicy.supports(this, tier);
    }

    /**
     * Typed, API-neutral declaration of a native capability provider.
     *
     * @param id native capability identity
     * @param valueType type returned by the provider
     * @param resolver provider resolver
     * @param <T> exposed value type
     * @author howxu <dev@howxu.cn>
     */
    public record ExternalExposure<T>(Identifier id, Class<T> valueType, Resolver<T> resolver) {
        public ExternalExposure {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(valueType, "valueType");
            Objects.requireNonNull(resolver, "resolver");
        }
    }

    /** Resolves the native value for a hosted capability and queried side. */
    @FunctionalInterface
    public interface Resolver<T> {
        T resolve(CapabilityHost host, IOType ioType, Direction side);
    }
}
