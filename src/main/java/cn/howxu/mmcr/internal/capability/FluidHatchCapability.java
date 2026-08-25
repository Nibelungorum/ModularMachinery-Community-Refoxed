package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

/**
 * Machine capability backed by a long fluid hatch storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluidHatchCapability implements MachineCapability {
    private final IOPortBlockEntity port;
    private final IOType ioType;
    private final ResourceStorage<FluidResource> storage;
    private final CapabilityView view;

    public FluidHatchCapability(ResourceStorage<FluidResource> storage, IOType ioType) {
        this(null, storage, ioType);
    }

    public FluidHatchCapability(IOPortBlockEntity port, ResourceStorage<FluidResource> storage, IOType ioType) {
        if (storage == null) throw new IllegalArgumentException("storage must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.port = port;
        this.ioType = ioType;
        this.storage = storage;
        this.view = CapabilityFactories.view(type(), ioType);
    }

    public FluidHatchCapability(FluidHatchBlockEntity port) {
        this(port, port.fluidStorage(), port.ioType());
    }

    public ResourceStorage<FluidResource> storage() {
        return storage;
    }

    @Nullable
    public Level level() {
        return port == null ? null : port.getLevel();
    }

    public BlockPos position() {
        return port == null ? BlockPos.ZERO : port.getBlockPos();
    }

    public int transferLimit() {
        return Integer.MAX_VALUE;
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
