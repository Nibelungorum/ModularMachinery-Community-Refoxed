package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksIds;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.FluxDataComponents;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxPoint;

import java.util.List;

/** @author howxu <dev@howxu.cn> */
public final class FluxNetworkInputBlockEntity extends FluxNetworkInterfaceBlockEntity implements IFluxPoint {
    private final FluxNetworkPointHandler handler = new FluxNetworkPointHandler(this::acceptsNetworkEnergy,
            () -> { checkServerThread(); return level == null ? 0L : level.getGameTime(); }, this::energyChanged);
    private final MachineCapability capability = new FluxNetworkInputCapability(handler, () -> {
        checkServerThread();
        return FluxNetworksIds.INPUT + ":" + getGlobalPos().dimension().location() + ":" + getBlockPos().asLong();
    });
    private final CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(capability));
    private BlockPos reservationControllerPos;

    public FluxNetworkInputBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state, FluxNetworkInterfaceKind.INPUT);
    }

    @Override public FluxDeviceType getDeviceType() { return FluxDeviceType.POINT; }
    @Override public FluxNetworkPointHandler getTransferHandler() { return handler; }
    @Override public CapabilitySnapshot capabilitySnapshot() { return snapshot; }

    @Override
    public void onMachineFormed(BlockPos pos) {
        super.onMachineFormed(pos);
        reservationControllerPos = pos.immutable();
    }

    @Override
    public void onMachineUnformed(BlockPos pos) {
        super.onMachineUnformed(pos);
        if (controllerPos == null) handler.clearDemand();
    }

    @Override
    public void readCustomTag(CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        if (type == FluxConstants.NBT_SAVE_ALL) reservationControllerPos = controllerPos;
    }

    @Override
    public void writeCustomTag(CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type == FluxConstants.NBT_SAVE_ALL && handler.reserved() > 0L && reservationControllerPos != null) {
            // Appearance may unlink before the saved controller has published its structure.
            tag.getCompound("mmcr_port").putLong("controller", reservationControllerPos.asLong());
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        if (input.get(FluxDataComponents.FLUX_CONFIG) != null) {
            reconcileLoadedReservations();
            if (input.get(FluxDataComponents.STORED_ENERGY) != null) {
                // An item's explicit inventory is a replacement, even if its numeric amount is equal.
                handler.cancelRecipeOwners();
                handler.releaseReserved(handler.reserved());
            }
        }
        super.applyImplicitComponents(input);
    }

    @Override
    protected void onServerTick() {
        reconcileLoadedReservations();
        super.onServerTick();
    }

    private void reconcileLoadedReservations() {
        checkServerThread();
        if (level == null || level.isClientSide() || isRemoved() || handler.reserved() == 0L) return;
        BlockPos owner = reservationControllerPos == null ? controllerPos : reservationControllerPos;
        if (owner != null) {
            if (!level.hasChunkAt(owner)) return;
            if (level.getBlockEntity(owner) instanceof MachineControllerBlockEntity controller) {
                // Binding a machine is not discovery. Keep saved budgets until every lane has
                // either restored its owner or explicitly rejected its saved recipe.
                if (!controller.runtimeRestorationComplete()) return;
                handler.reconcileRecipeOwners();
                return;
            }
        }
        handler.cancelRecipeOwners();
        handler.reconcileRecipeOwners();
        reservationControllerPos = null;
    }
}
