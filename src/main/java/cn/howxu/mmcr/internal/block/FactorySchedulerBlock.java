package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.menu.FactorySchedulerMenu;

import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

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
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
                                    @Nullable BlockState sourceState, @Nullable BlockPos sourcePos) {
        return AppearanceStateResolver.resolveLinked(state, level, pos);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() { return Component.translatable("container.mmcr.factory_controller"); }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
                return level.getBlockEntity(pos) instanceof FactorySchedulerBlockEntity scheduler
                        ? new FactorySchedulerMenu(containerId, playerInv, scheduler)
                        : new FactorySchedulerMenu(containerId, playerInv);
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
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!moving && state.getBlock() != newState.getBlock()
                && !level.isClientSide() && level.getBlockEntity(pos) instanceof FactorySchedulerBlockEntity scheduler) {
            scheduler.onBlockRemoved();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

}
