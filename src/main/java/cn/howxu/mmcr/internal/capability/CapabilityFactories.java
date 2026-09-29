package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.util.IOType;

import java.util.Set;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Compatibility helpers for built-in capability consumers and shared contract helpers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityFactories {
    private CapabilityFactories() {}

    public static CapabilityView view(CapabilityType type, CapabilityDirections directions) {
        return view(type, directions, Set.of());
    }

    public static CapabilityView view(CapabilityType type, CapabilityDirections directions,
                                      Set<Class<? extends CapabilityFacet>> facets) {
        return new CapabilityView() {
            @Override
            public CapabilityType type() {
                return type;
            }

            @Override
            public CapabilityDirections directions() {
                return directions;
            }

            @Override
            public Set<Class<? extends CapabilityFacet>> facets() {
                return facets;
            }
        };
    }

    public static CapabilityOperation operation(MachineCapability capability, CapabilityRequest request) {
        if (capability == null) throw new IllegalArgumentException("capability must not be null");
        if (request == null) throw new IllegalArgumentException("request must not be null");
        if (!capability.type().equals(request.type())) {
            throw new IllegalArgumentException("Capability request type does not match");
        }
        if (!capability.directions().supports(request.ioType())) {
            throw new IllegalArgumentException("Capability request IO type does not match");
        }
        if (request.parallelism() <= 0) throw new IllegalArgumentException("parallelism must be positive");
        return capability.facet(OperationFacet.class)
                .orElseThrow(() -> new IllegalStateException("Capability does not declare an operation facet"))
                .prepareOperation(request);
    }

    public static IItemHandler itemHandler(MachineCapability capability) {
        if (capability == null) return null;
        ItemHandlerFacet facet = capability.facet(ItemHandlerFacet.class).orElse(null);
        return facet == null ? null : facet.itemHandler();
    }

    public static IFluidHandler fluidHandler(MachineCapability capability) {
        if (capability == null) return null;
        FluidHandlerFacet facet = capability.facet(FluidHandlerFacet.class).orElse(null);
        return facet == null ? null : facet.fluidHandler();
    }

    public static IEnergyStorage energyStorage(MachineCapability capability) {
        if (capability == null) return null;
        EnergyStorageFacet facet = capability.facet(EnergyStorageFacet.class).orElse(null);
        return facet == null ? null : facet.energyStorage();
    }

    @SuppressWarnings("unchecked")
    public static <S extends CapabilityStorage> S valueStorage(MachineCapability capability, Class<S> storageType) {
        if (capability == null || storageType == null) return null;
        ValueFacet<?> facet = capability.facet(ValueFacet.class).orElse(null);
        return facet != null && storageType.isInstance(facet.storage()) ? (S) facet.storage() : null;
    }

}
