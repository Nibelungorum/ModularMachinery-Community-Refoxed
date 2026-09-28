package cn.howxu.mmcr.api.capability.type;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * Binds one hosted capability to a port direction, tier policy, and creation factory.
 *
 * @param type the hosted capability type
 * @param directions the supported directions of the binding
 * @param factory the factory used to create the hosted capability
 * @param tierPolicy the policy used to determine whether the binding is available at a tier
 * @param externalExposure the optional native capability provider exposed for this binding
 * @param nativeTransferExposure whether this binding is exposed through native transfer capabilities
 * @author howxu <dev@howxu.cn>
 */
public record CapabilityBinding(CapabilityType type,
                                CapabilityDirections directions,
                                CapabilityFactory factory,
                                PortTierPolicy tierPolicy,
                                Optional<ExternalExposure<?>> externalExposure,
                                boolean nativeTransferExposure) {
    public CapabilityBinding(CapabilityType type, CapabilityDirections directions,
                             CapabilityFactory factory, PortTierPolicy tierPolicy) {
        this(type, directions, factory, tierPolicy, Optional.empty(), true);
    }

    public CapabilityBinding(CapabilityType type, CapabilityDirections directions,
                             CapabilityFactory factory, PortTierPolicy tierPolicy,
                             Optional<ExternalExposure<?>> externalExposure) {
        this(type, directions, factory, tierPolicy, externalExposure, true);
    }

    @Deprecated(forRemoval = true)
    public CapabilityBinding(CapabilityType type, IOType ioType,
                             CapabilityFactory factory, PortTierPolicy tierPolicy) {
        this(type, CapabilityDirections.of(ioType), factory, tierPolicy);
    }

    public CapabilityBinding(CapabilityType type, CapabilityDirections directions,
                             CapabilityFactory factory, PortTierPolicy tierPolicy,
                             ExternalExposure<?> externalExposure) {
        this(type, directions, factory, tierPolicy, Optional.ofNullable(externalExposure), true);
    }

    /**
     * Creates a binding with no external exposure and an explicit native transfer flag.
     *
     * @param type hosted capability type
     * @param directions supported capability directions
     * @param factory capability creation factory
     * @param tierPolicy tier availability policy
     * @param nativeTransferExposure whether native transfer providers may expose this binding
     * @author howxu <dev@howxu.cn>
     */
    public CapabilityBinding(CapabilityType type, CapabilityDirections directions,
                             CapabilityFactory factory, PortTierPolicy tierPolicy,
                             boolean nativeTransferExposure) {
        this(type, directions, factory, tierPolicy, Optional.empty(), nativeTransferExposure);
    }

    @Deprecated(forRemoval = true)
    public CapabilityBinding(CapabilityType type, IOType ioType,
                             CapabilityFactory factory, PortTierPolicy tierPolicy,
                             ExternalExposure<?> externalExposure) {
        this(type, CapabilityDirections.of(ioType), factory, tierPolicy, externalExposure);
    }

    /**
     * Creates a binding that remains available internally without native transfer registration.
     *
     * @param type hosted capability type
     * @param directions supported capability directions
     * @param factory capability creation factory
     * @param tierPolicy tier availability policy
     * @return an internally hosted binding
     * @author howxu <dev@howxu.cn>
     */
    public static CapabilityBinding internalOnly(CapabilityType type, CapabilityDirections directions,
                                                 CapabilityFactory factory, PortTierPolicy tierPolicy) {
        return new CapabilityBinding(type, directions, factory, tierPolicy, false);
    }

    public CapabilityBinding {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(directions, "directions");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(tierPolicy, "tierPolicy");
        Objects.requireNonNull(externalExposure, "externalExposure");
    }

    public boolean supports(int tier) {
        return tierPolicy.supports(this, tier);
    }

    /**
     * @deprecated use {@link #directions()} to determine whether a direction is supported
     */
    @Deprecated(forRemoval = true)
    public IOType ioType() {
        if (directions.values().size() != 1) {
            throw new IllegalStateException("Capability binding does not have exactly one direction");
        }
        return directions.values().iterator().next();
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
    public record ExternalExposure<T>(ResourceLocation id, Class<T> valueType, Resolver<T> resolver) {
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
