package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Standalone endpoint for formed machine network connections.
 * @author howxu <dev@howxu.cn>
 */
public class NetworkInterfaceBlock extends Block implements EntityBlock {
    private final Supplier<? extends BlockEntityType<?>> beType;

    public NetworkInterfaceBlock(Supplier<? extends BlockEntityType<?>> beType, Properties properties) {
        super(properties.strength(3.5F).sound(SoundType.METAL));
        this.beType = beType;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beType.get().create(pos, state);
    }

    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
                                    @Nullable BlockState sourceState, @Nullable BlockPos sourcePos) {
        return AppearanceStateResolver.resolveLinked(state, level, pos);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) return null;
        return (lvl, pos, blockState, entity) -> {
            if (entity instanceof NetworkInterfaceBlockEntity networkInterface) networkInterface.serverTick();
        };
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!moving && state.getBlock() != newState.getBlock()
                && !level.isClientSide() && level.getBlockEntity(pos) instanceof NetworkInterfaceBlockEntity network) {
            network.onBlockRemoved();
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
