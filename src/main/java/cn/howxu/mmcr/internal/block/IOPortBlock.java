package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.world.level.Level;
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
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class IOPortBlock extends Block implements EntityBlock {

    enum PortMenuKind { ITEM, FLUID, ENERGY, NONE }

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
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, playerInv, player) -> openServerMenu(kind.id(), containerId, playerInv, level, pos),
                titleFor(kind.id()));
    }

    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof FluidHatchBlockEntity hatch) {
            if (level.isClientSide()) return InteractionResult.TRY_WITH_EMPTY_HAND;
            ResourceHandler<FluidResource> handler = new PortFluidTransferHandler(
                    hatch.getResourceHandler(hit.getDirection()), hatch.ioType() == IOType.INPUT, hatch.ioType() == IOType.OUTPUT);
            if (net.neoforged.neoforge.transfer.fluid.FluidUtil.interactWithFluidHandler(
                    player, hand, pos, handler, null)) {
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
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

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof cn.howxu.mmcr.internal.tile.IOPortBlockEntity port) {
            port.markAutoIOCacheDirty();
        }
    }

    @Override
    public void onBlockExploded(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion) {
        if (level.getBlockEntity(pos) instanceof ItemBusBlockEntity bus) {
            bus.clearContents();
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> beType) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof cn.howxu.mmcr.internal.tile.IOPortBlockEntity port) port.serverTick();
        };
    }

    static PortMenuKind menuKindFor(String id) {
        if (matchesPortId(id, "item_input_bus") || matchesPortId(id, "item_output_bus")) return PortMenuKind.ITEM;
        if (matchesPortId(id, "fluid_input_hatch") || matchesPortId(id, "fluid_output_hatch")) return PortMenuKind.FLUID;
        if (matchesPortId(id, "energy_input_hatch") || matchesPortId(id, "energy_output_hatch")) return PortMenuKind.ENERGY;
        return PortMenuKind.NONE;
    }

    private static boolean matchesPortId(String id, String baseId) {
        return id.equals(baseId) || id.startsWith(baseId + "_");
    }

    private static AbstractContainerMenu openServerMenu(String kind, int containerId,
                                                        net.minecraft.world.entity.player.Inventory playerInv,
                                                        Level level, BlockPos pos) {
        return switch (menuKindFor(kind)) {
            case ITEM -> new ItemBusMenu(containerId, playerInv,
                    level.getBlockEntity(pos) instanceof ItemBusBlockEntity bus ? bus : null);
            case FLUID -> new FluidHatchMenu(containerId, playerInv,
                    level.getBlockEntity(pos) instanceof FluidHatchBlockEntity hatch ? hatch : null);
            case ENERGY -> new EnergyHatchMenu(containerId, playerInv,
                    level.getBlockEntity(pos) instanceof EnergyHatchBlockEntity hatch ? hatch : null);
            case NONE -> null;
        };
    }

    static Component titleFor(String kind) {
        return Component.translatable("container.mmcr." + kind);
    }

    private static final class PortFluidTransferHandler implements ResourceHandler<FluidResource> {
        private final ResourceHandler<FluidResource> handler;
        private final boolean canInsert;
        private final boolean canExtract;

        private PortFluidTransferHandler(ResourceHandler<FluidResource> handler, boolean canInsert, boolean canExtract) {
            this.handler = handler;
            this.canInsert = canInsert;
            this.canExtract = canExtract;
        }

        @Override public int size() { return handler.size(); }
        @Override public FluidResource getResource(int slot) {
            checkSlot(slot);
            return handler.getResource(slot);
        }
        @Override public long getAmountAsLong(int slot) {
            checkSlot(slot);
            return handler.getAmountAsLong(slot);
        }
        @Override public long getCapacityAsLong(int slot, FluidResource resource) {
            checkSlot(slot);
            return handler.getCapacityAsLong(slot, resource);
        }
        @Override public boolean isValid(int slot, FluidResource resource) {
            checkSlot(slot);
            TransferPreconditions.checkNonEmpty(resource);
            return handler.isValid(slot, resource);
        }
        @Override public int insert(int slot, FluidResource resource, int amount, TransactionContext tx) {
            checkSlot(slot);
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (!canInsert) return 0;
            return handler.insert(slot, resource, amount, tx);
        }
        @Override public int extract(int slot, FluidResource resource, int amount, TransactionContext tx) {
            checkSlot(slot);
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (!canExtract) return 0;
            return handler.extract(slot, resource, amount, tx);
        }

        private void checkSlot(int slot) {
            if (slot < 0 || slot >= handler.size()) throw new IndexOutOfBoundsException(slot);
        }
    }
}
