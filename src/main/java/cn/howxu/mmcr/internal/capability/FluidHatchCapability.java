package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Machine capability backed by a long fluid hatch storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluidHatchCapability implements MachineCapability {
    private final IOType ioType;
    private final LongFluidStorage storage;
    private final CapabilityView view;

    public FluidHatchCapability(FluidHatchBlockEntity port) {
        this.ioType = port.ioType();
        this.storage = port.getMutableFluidStorage();
        this.view = CapabilityFactories.view(type(), ioType);
    }

    public ResourceStorage<FluidResource> storage() {
        return storage;
    }

    @Override
    public CapabilityType type() {
        return CapabilityFactories.FLUID_TYPE;
    }

    @Override
    public IOType ioType() {
        return ioType;
    }

    @Override
    public CapabilityView view() {
        return view;
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        return CapabilityFactories.operation(type(), ioType, request, storage);
    }
}
