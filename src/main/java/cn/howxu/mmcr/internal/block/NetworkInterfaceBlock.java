package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
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
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) return null;
        return (lvl, pos, blockState, entity) -> {
            if (entity instanceof NetworkInterfaceBlockEntity networkInterface) networkInterface.serverTick();
        };
    }
}
