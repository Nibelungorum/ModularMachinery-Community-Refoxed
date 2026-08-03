package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class IOPortBlockEntity extends BlockEntity {

    protected IOPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IOType ioType() {
        return getBlockState().getValue(IOPortBlock.IO_TYPE);
    }

    public abstract IOPortKind kind();

    public void serverTick() {
        tick();
    }

    protected void tick() {
        kind().tick(this);
    }
}
