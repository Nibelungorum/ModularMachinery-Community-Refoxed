package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.block.FluidHatchBlock;
import cn.howxu.mmcr.registry.MMCRRegistries;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FluidHatchBlockEntity extends BlockEntity {

    private final FluidTank tank = new FluidTank(8000);

    public FluidHatchBlockEntity(BlockPos pos, BlockState state) {
        super(MMCRRegistries.FLUID_HATCH_BE.get(), pos, state);
    }

    public IFluidHandler getFluidHandler(Direction side) { return tank; }

    public IOType ioType() { return getBlockState().getValue(FluidHatchBlock.IO_TYPE); }
}
