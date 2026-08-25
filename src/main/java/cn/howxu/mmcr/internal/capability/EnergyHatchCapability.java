package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Machine capability backed by a long energy value storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class EnergyHatchCapability implements MachineCapability {
    private final EnergyHatchBlockEntity port;
    private final IOType ioType;
    private final LongValueStorage storage;
    private final CapabilityView view;

    public EnergyHatchCapability(EnergyHatchBlockEntity port) {
        this.port = port;
        this.ioType = port.ioType();
        this.storage = port.getMutableEnergyStorage().storage();
        this.view = CapabilityFactories.view(type(), ioType);
    }

    public LongValueStorage storage() {
        return storage;
    }

    @Nullable
    public Level level() {
        return port.getLevel();
    }

    public BlockPos position() {
        return port.getBlockPos();
    }

    @Override
    public CapabilityType type() {
        return CapabilityFactories.ENERGY_TYPE;
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
