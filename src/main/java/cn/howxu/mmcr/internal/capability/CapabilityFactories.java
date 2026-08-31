package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.util.IOType;

import java.util.Set;

/**
 * Compatibility helpers for built-in capability consumers and shared contract helpers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityFactories {
    private CapabilityFactories() {}

    static CapabilityView view(CapabilityType type, IOType ioType) {
        return view(type, ioType, Set.of());
    }

    static CapabilityView view(CapabilityType type, IOType ioType,
                               Set<Class<? extends CapabilityFacet>> facets) {
        return new CapabilityView() {
            @Override
            public CapabilityType type() {
                return type;
            }

            @Override
            public IOType ioType() {
                return ioType;
            }

            @Override
            public Set<Class<? extends CapabilityFacet>> facets() {
                return facets;
            }
        };
    }

    static CapabilityOperation operation(MachineCapability capability, CapabilityRequest request) {
        if (capability == null) throw new IllegalArgumentException("capability must not be null");
        if (request == null) throw new IllegalArgumentException("request must not be null");
        if (!capability.type().equals(request.type())) {
            throw new IllegalArgumentException("Capability request type does not match");
        }
        if (capability.ioType() != request.ioType()) {
            throw new IllegalArgumentException("Capability request IO type does not match");
        }
        if (request.parallelism() <= 0) throw new IllegalArgumentException("parallelism must be positive");
        return capability.facet(OperationFacet.class)
                .orElseThrow(() -> new IllegalStateException("Capability does not declare an operation facet"))
                .prepareOperation(request);
    }

}
