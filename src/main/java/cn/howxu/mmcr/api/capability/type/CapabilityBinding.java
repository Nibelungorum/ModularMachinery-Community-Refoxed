package cn.howxu.mmcr.api.capability.type;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.util.Objects;
import java.util.Optional;

/**
 * Binds one hosted capability to a port direction, tier policy, and creation factory.
 *
 * @param type the hosted capability type
 * @param ioType the direction of the binding
 * @param factory the factory used to create the hosted capability
 * @param tierPolicy the policy used to determine whether the binding is available at a tier
 * @param externalExposure the optional native block capability exposed for this binding
 * @author howxu <dev@howxu.cn>
 */
public record CapabilityBinding(CapabilityType type,
                                IOType ioType,
                                CapabilityFactory factory,
                                PortTierPolicy tierPolicy,
                                Optional<BlockCapability<?, Direction>> externalExposure) {
    public CapabilityBinding(CapabilityType type, IOType ioType,
                             CapabilityFactory factory, PortTierPolicy tierPolicy) {
        this(type, ioType, factory, tierPolicy, Optional.empty());
    }

    public CapabilityBinding(CapabilityType type, IOType ioType,
                             CapabilityFactory factory, PortTierPolicy tierPolicy,
                             BlockCapability<?, Direction> externalExposure) {
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
}
