package cn.howxu.mmcr.compat.pneumaticcraft.loaded;

import cn.howxu.mmcr.internal.block.IOPortBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/** Native handler ticks on both logical sides with a read-only pressure menu. @author howxu <dev@howxu.cn> */
public final class AirInterfaceBlock extends IOPortBlock {
    private final Supplier<? extends BlockEntityType<?>> type;

    public AirInterfaceBlock(AirInterfaceKind kind, Supplier<? extends BlockEntityType<?>> type, Properties properties) {
        super(kind, type, properties);
        this.type = type;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AirPortBlockEntity port)) return null;
        return new MenuProvider() {
            @Override
            public Component getDisplayName() { return Component.translatable("block.mmcr." + kind().id()); }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return level.getBlockEntity(pos) == port && !port.isRemoved()
                        ? new AirPortMenu(containerId, inventory, port) : null;
            }

            @Override
            public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
                buffer.writeBlockPos(pos);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) player.openMenu(provider);
        }
        return InteractionResult.SUCCESS;
    }

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
