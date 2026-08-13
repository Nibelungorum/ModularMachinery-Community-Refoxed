package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.autoio.AutoIOCapabilityType;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public abstract class FluidHatchBlockEntity extends IOPortBlockEntity {

    private final FluidTank tank;
    private Boolean tankEmpty;

    protected FluidHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IOPortKind kind) {
        super(type, pos, state);
        int capacity = kind.fluidHatchSize()
                .orElseThrow(() -> new IllegalStateException("Fluid hatch missing fluid size: " + kind.id()))
                .capacity();
        this.tank = new FluidTank(capacity) {
            @Override
            public FluidStack getFluidInTank(int tank) {
                FluidStack stack = super.getFluidInTank(tank);
                if (stack.getAmount() <= getTankCapacity(tank)) return stack;
                FluidStack capped = stack.copy();
                capped.setAmount(getTankCapacity(tank));
                return capped;
            }

            @Override
            protected void onContentsChanged() {
                tankEmpty = null;
                markAutoIOCacheDirty();
                setChanged();
            }
        };
    }

    public IFluidHandler getFluidHandler(Direction side) { return tank; }

    public FluidTank getFluidTank(Direction side) { return tank; }

    public boolean isTankEmpty() {
        if (tankEmpty == null) tankEmpty = tank.getFluid().isEmpty();
        return tankEmpty;
    }

    @Override
    public int autoIoTransferLimit() {
        return Math.min(1000, tank.getCapacity());
    }

    @Override
    protected boolean hasAutoIOTransferWork() {
        if (ioType() == IOType.OUTPUT) return !isTankEmpty();
        return tank.getFluidAmount() < tank.getCapacity();
    }

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
    public AutoIOCapabilityType autoIOCapabilityType() {
        return AutoIOCapabilityType.FLUID;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("tank"));
    }
}
