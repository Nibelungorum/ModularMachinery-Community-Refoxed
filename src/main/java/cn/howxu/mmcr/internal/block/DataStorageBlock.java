package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/** Standalone block exposing typed machine data storage.
 * @author howxu <dev@howxu.cn>
 */
public final class DataStorageBlock extends Block implements EntityBlock {
    private final Supplier<? extends BlockEntityType<?>> blockEntityType;

    public DataStorageBlock(Supplier<? extends BlockEntityType<?>> blockEntityType, Properties properties) {
        super(properties.strength(3.5F).sound(SoundType.METAL));
        this.blockEntityType = blockEntityType;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) return null;
        return (world, pos, blockState, entity) -> {
            if (entity instanceof DataStorageBlockEntity storage) storage.serverTick();
        };
    }
}
