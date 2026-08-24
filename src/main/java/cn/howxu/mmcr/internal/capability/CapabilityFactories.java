package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.util.IOType;

/**
 * Built-in capability factories and shared capability contract helpers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityFactories {
    public static final CapabilityFactory ITEM_BUS = port -> new ItemBusCapability(require(port, ItemBusBlockEntity.class));
    public static final CapabilityFactory FLUID_HATCH = port -> new FluidHatchCapability(require(port, FluidHatchBlockEntity.class));
    public static final CapabilityFactory ENERGY_HATCH = port -> new EnergyHatchCapability(require(port, EnergyHatchBlockEntity.class));

    static final CapabilityType ITEM_TYPE = new CapabilityType(MMCR.id("item"));
    static final CapabilityType FLUID_TYPE = new CapabilityType(MMCR.id("fluid"));
    static final CapabilityType ENERGY_TYPE = new CapabilityType(MMCR.id("energy"));

    private CapabilityFactories() {}

    public interface CapabilityFactory {
        MachineCapability create(IOPortBlockEntity port);
    }

    static CapabilityView view(CapabilityType type, IOType ioType) {
        return new CapabilityView() {
            @Override
            public CapabilityType type() {
                return type;
            }

            @Override
            public IOType ioType() {
                return ioType;
            }
        };
    }

    static CapabilityOperation operation(CapabilityType type, IOType ioType, CapabilityRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        if (!type.equals(request.type())) throw new IllegalArgumentException("Capability request type does not match");
        if (ioType != request.ioType()) throw new IllegalArgumentException("Capability request IO type does not match");
        if (request.parallelism() <= 0) throw new IllegalArgumentException("parallelism must be positive");
        return transaction -> CapabilityResult.successful();
    }

    private static <T> T require(IOPortBlockEntity port, Class<T> type) {
        if (!type.isInstance(port)) {
            throw new IllegalArgumentException("Capability factory cannot handle " + port.getClass().getName());
        }
        return type.cast(port);
    }
}
