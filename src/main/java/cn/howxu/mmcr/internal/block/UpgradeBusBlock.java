package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.menu.UpgradeBusMenu;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.internal.tile.UpgradeBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/**
 * Standalone block that exposes one fixed-tier upgrade bus inventory.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class UpgradeBusBlock extends Block implements EntityBlock {
    private final UpgradeBusSize size;
    private final Supplier<? extends BlockEntityType<?>> blockEntityType;

    public UpgradeBusBlock(UpgradeBusSize size, Supplier<? extends BlockEntityType<?>> blockEntityType,
                           Properties properties) {
        super(properties.strength(3.5F).sound(SoundType.METAL));
        if (size == null) throw new IllegalArgumentException("Upgrade bus size must not be null");
        this.size = size;
        this.blockEntityType = blockEntityType;
    }

    public UpgradeBusSize size() {
        return size;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, playerInventory, player) -> new UpgradeBusMenu(containerId, playerInventory,
                        level.getBlockEntity(pos) instanceof UpgradeBusBlockEntity bus ? bus : null),
                Component.translatable("container.mmcr.upgrade_bus_" + size.id()));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) {
                player.openMenu(provider, buffer -> UpgradeBusMenu.writeClientOpenData(buffer, pos, size));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onBlockExploded(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion) {
        if (level.getBlockEntity(pos) instanceof UpgradeBusBlockEntity bus) bus.dropContents();
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> blockEntityType) {
        return null;
    }
}
