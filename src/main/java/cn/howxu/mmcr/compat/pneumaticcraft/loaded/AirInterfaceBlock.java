package cn.howxu.mmcr.compat.pneumaticcraft.loaded;

import cn.howxu.mmcr.internal.block.IOPortBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/** Native handler ticks on both logical sides without a port menu. @author howxu <dev@howxu.cn> */
public final class AirInterfaceBlock extends IOPortBlock {
    private final Supplier<? extends BlockEntityType<?>> type;

    public AirInterfaceBlock(AirInterfaceKind kind, Supplier<? extends BlockEntityType<?>> type, Properties properties) {
        super(kind, type, properties);
        this.type = type;
    }

    @Override public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) { return null; }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        if (beType != type.get()) return null;
        return (world, pos, blockState, entity) -> {
            if (entity instanceof AirPortBlockEntity port) {
                if (world.isClientSide()) port.clientTick();
                else port.serverTick();
            }
        };
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moved) {
        super.neighborChanged(state, level, pos, neighbor, from, moved);
        if (level.getBlockEntity(pos) instanceof AirPortBlockEntity port) port.updateConnections();
    }
}
