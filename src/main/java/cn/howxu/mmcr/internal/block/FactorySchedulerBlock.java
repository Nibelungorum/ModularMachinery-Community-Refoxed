package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.menu.FactorySchedulerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/**
 * Factory controller component that stores thread dispersers and schedules factory recipe lanes.
 *
 * @author howxu <dev@howxu.cn>
 */
public class FactorySchedulerBlock extends Block implements EntityBlock {

    private final Supplier<? extends BlockEntityType<?>> beType;

    public FactorySchedulerBlock(Supplier<? extends BlockEntityType<?>> beType, Properties properties) {
        super(properties.strength(3.5F).sound(SoundType.METAL));
        this.beType = beType;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beType.get().create(pos, state);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, playerInv, player) -> level.getBlockEntity(pos) instanceof cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity scheduler
                        ? new FactorySchedulerMenu(containerId, playerInv, scheduler)
                        : new FactorySchedulerMenu(containerId, playerInv),
                Component.translatable("container.mmcr.factory_controller"));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) player.openMenu(provider, pos);
        }
        return InteractionResult.SUCCESS;
    }

}
