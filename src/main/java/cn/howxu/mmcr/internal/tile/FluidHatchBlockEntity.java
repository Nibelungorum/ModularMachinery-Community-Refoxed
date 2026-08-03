package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.block.FluidHatchBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.MMCRPortKinds;
import cn.howxu.mmcr.registry.MMCRBlockEntities;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FluidHatchBlockEntity extends IOPortBlockEntity {

    private final FluidTank tank = new FluidTank(8000);

    public FluidHatchBlockEntity(BlockPos pos, BlockState state) {
        super(MMCRBlockEntities.BES.get("io_port_fluid_basic").get(), pos, state);
    }

    public IFluidHandler getFluidHandler(Direction side) { return tank; }

    public FluidTank getFluidTank(Direction side) { return tank; }

    @Override
    public IOType ioType() { return getBlockState().getValue(FluidHatchBlock.IO_TYPE); }

    @Override
    public IOPortKind kind() { return MMCRPortKinds.FLUID; }
}
