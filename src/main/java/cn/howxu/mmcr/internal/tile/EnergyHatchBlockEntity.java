package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public abstract class EnergyHatchBlockEntity extends IOPortBlockEntity {

    private final EnergyStorage storage;

    protected EnergyHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IOPortKind kind) {
        super(type, pos, state);
        EnergyHatchSize size = kind.energyHatchSize()
                .orElseThrow(() -> new IllegalStateException("Energy hatch missing energy size: " + kind.id()));
        this.storage = new EnergyStorage(size.capacity(), size.transfer(), size.transfer()) {
            @Override
            public int getEnergyStored() {
                return Math.min(super.getEnergyStored(), getMaxEnergyStored());
            }

            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                int received = super.receiveEnergy(maxReceive, simulate);
                if (!simulate && received > 0) setChanged();
                return received;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                int extracted = Math.min(maxExtract, getEnergyStored());
                if (!simulate) {
                    super.extractEnergy(extracted, false);
                }
                if (!simulate && extracted > 0) setChanged();
                return extracted;
            }
        };
    }

    public IEnergyStorage getEnergyStorage(Direction side) { return storage; }

    public EnergyStorage getMutableEnergyStorage(Direction side) { return storage; }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public abstract IOType ioType();

    @Override
    public abstract IOPortKind kind();

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        storage.serialize(output.child("storage"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storage.deserialize(input.childOrEmpty("storage"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
