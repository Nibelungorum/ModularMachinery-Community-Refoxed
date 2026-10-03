package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxPlug;

import java.util.List;

/** @author howxu <dev@howxu.cn> */
public final class FluxNetworkOutputBlockEntity extends FluxNetworkInterfaceBlockEntity implements IFluxPlug {
    private final FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(this::acceptsNetworkEnergy,
            () -> { checkServerThread(); return getNetwork().getBufferLimiter(); }, this::energyChanged);
    private final MachineCapability capability = new FluxNetworkOutputCapability(handler);
    private final CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(capability));

    public FluxNetworkOutputBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state, FluxNetworkInterfaceKind.OUTPUT);
    }

    @Override public FluxDeviceType getDeviceType() { return FluxDeviceType.PLUG; }
    @Override public FluxNetworkPlugHandler getTransferHandler() { return handler; }
    @Override public CapabilitySnapshot capabilitySnapshot() { return snapshot; }
}
