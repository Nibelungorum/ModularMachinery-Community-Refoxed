package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
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

    @SuppressWarnings("unchecked")
    public static <R> ResourceStorage<R> resourceStorage(MachineCapability capability, Class<R> resourceType) {
        if (capability == null || resourceType == null) return null;
        ResourceFacet<?> facet = capability.facet(ResourceFacet.class).orElse(null);
        if (facet != null) {
            return resourceType.equals(facet.resourceType()) ? (ResourceStorage<R>) facet.storage() : null;
        }
        ValueFacet<?> valueFacet = capability.facet(ValueFacet.class).orElse(null);
        if (valueFacet == null || !(valueFacet.storage() instanceof ResourceStorage<?> storage)
                || !resourceType.equals(storage.resourceType())) return null;
        return (ResourceStorage<R>) storage;
    }

    public static ResourceStorage<?> resourceStorage(MachineCapability capability) {
        if (capability == null) return null;
        ResourceFacet<?> facet = capability.facet(ResourceFacet.class).orElse(null);
        if (facet != null) return facet.storage();
        ValueFacet<?> valueFacet = capability.facet(ValueFacet.class).orElse(null);
        return valueFacet != null && valueFacet.storage() instanceof ResourceStorage<?> storage ? storage : null;
    }

    @SuppressWarnings("unchecked")
    public static <S extends CapabilityStorage> S valueStorage(MachineCapability capability, Class<S> storageType) {
        if (capability == null || storageType == null) return null;
        ValueFacet<?> facet = capability.facet(ValueFacet.class).orElse(null);
        if (facet != null && storageType.isInstance(facet.storage())) return (S) facet.storage();
        ResourceFacet<?> resourceFacet = capability.facet(ResourceFacet.class).orElse(null);
        if (resourceFacet == null || !storageType.isInstance(resourceFacet.storage())) return null;
        return (S) resourceFacet.storage();
    }

}
