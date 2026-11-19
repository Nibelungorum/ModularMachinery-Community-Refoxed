package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.api.port.MachinePort;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.ItemSpecialOperationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;

public class IOPortBlock extends Block implements EntityBlock, MachinePort {

    private final IOPortKind kind;
    private final Supplier<? extends BlockEntityType<?>> beType;

    public IOPortBlock(IOPortKind kind,
                       Supplier<? extends BlockEntityType<?>> beType,
                       Properties props) {
        super(props.strength(3.5F).sound(SoundType.METAL));
        this.kind = kind;
        this.beType = beType;
    }

    public IOPortKind kind() { return kind; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beType.get().create(pos, state);
    }

    @Override
    public BlockState getAppearance(BlockState state, BlockAndLightGetter level, BlockPos pos, Direction side,
                                    @Nullable BlockState sourceState, @Nullable BlockPos sourcePos) {
        return AppearanceStateResolver.resolveLinked(state, level, pos);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() { return titleFor(kind.id()); }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
                BlockEntity entity = level.getBlockEntity(pos);
                if (!(entity instanceof MachinePort port) || entity.isRemoved() || !kind.id().equals(port.kind().id())) return null;
                return kind.createMenu(containerId, playerInv, level, pos);
            }

            @Override
            public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
                kind.writeOpenData(buffer, pos, menu);
            }
        };
    }

    @Override
    protected @NonNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                   BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (AE2Bridge.get().useMemoryCard(stack, level, pos, player)) {
            return InteractionResult.SUCCESS;
        }
        if (ItemSpecialOperationUtil.isSpecialOperated(player) || level.isClientSide()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        BlockEntity owner = level.getBlockEntity(pos);
        if (owner instanceof MachinePort port) {
            if (port.kind().handlesContainerInteraction()) {
                ItemAccess access = ItemAccess.forPlayerInteraction(player, hand).oneByOne();
                try (Transaction tx = Transaction.openRoot()) {
                    if (port.kind().interactWithContainer(access, owner, player, tx) > 0) {
                        tx.commit();
                        return InteractionResult.SUCCESS;
                    }
                }
            } else if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection(), null)) {
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                        Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            InteractionResult special = kind.openSpecialMenu(player, level, pos);
            if (special != InteractionResult.PASS) return special;
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) player.openMenu(provider);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof IOPortBlockEntity port) {
            port.markAutoIOCacheDirty();
            AE2Bridge.get().onPortNeighborChanged(port);
        }
    }

    @Override
    public void onBlockExploded(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion) {
        if (level.getBlockEntity(pos) instanceof IOPortBlockEntity port) {
            port.dropContents();
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> beType) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof IOPortBlockEntity port) port.serverTick();
        };
    }

    static Component titleFor(String kind) {
        return Component.translatable("container.mmcr." + kind);
    }

}
