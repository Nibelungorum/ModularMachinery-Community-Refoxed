package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class EnergyHatchBlock extends Block implements EntityBlock {

    public static final EnumProperty<IOType> IO_TYPE = IOPortBlock.IO_TYPE;

    public EnergyHatchBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(IO_TYPE, IOType.INPUT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(IO_TYPE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyHatchBlockEntity(pos, state);
    }
}