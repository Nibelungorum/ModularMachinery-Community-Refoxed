package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.api.capability.transfer.ContainerResourceTransfer;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.ExtendedCombinedMenu;
import cn.howxu.mmcr.internal.menu.ExtendedFluidMenu;
import cn.howxu.mmcr.internal.menu.ExtendedItemMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedflux.AppliedFluxBridge;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedCombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedFluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.CombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.ItemSpecialOperationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.common.SoundActions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class IOPortBlock extends Block implements EntityBlock, MachinePort {

    enum PortMenuKind {
        ITEM, FLUID, ENERGY,
        EXTENDED_ITEM, EXTENDED_FLUID, EXTENDED_ENERGY,
        COMBINED, EXTENDED_COMBINED,
        NONE
    }

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
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
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
                return openServerMenu(kind.id(), containerId, playerInv, level, pos);
            }

            @Override
            public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
                switch (menu) {
                    case ItemBusMenu bus -> ItemBusMenu.writeClientOpenData(buffer, pos, bus.owner());
                    case ExtendedItemMenu bus -> ExtendedItemMenu.writeClientOpenData(buffer, bus.owner());
                    case ExtendedFluidMenu hatch -> ExtendedFluidMenu.writeClientOpenData(buffer, hatch.owner());
                    case CombinedPortMenu combined -> CombinedPortMenu.writeClientOpenData(buffer, combined.owner());
                    case ExtendedCombinedMenu combined -> ExtendedCombinedMenu.writeClientOpenData(buffer, combined.owner());
                    case FluidHatchMenu ignored -> buffer.writeBlockPos(pos);
                    case EnergyHatchMenu ignored -> buffer.writeBlockPos(pos);
                    default -> {
                        if (ArsNouveauBridge.get().isSourcePort(kind.id())) buffer.writeBlockPos(pos);
                        else MekanismBridge.get().writeClientOpenData(buffer, kind.id(), pos);
                    }
                }
            }
        };
    }

    @Override
    protected @NonNull ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                       BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (ArsNouveauBridge.get().isSourcePort(kind.id()) && ArsNouveauBridge.get().isDominionWand(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (AE2Bridge.get().useMemoryCard(stack, level, pos, player)) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!ItemSpecialOperationUtil.isSpecialOperated(player)
                && level.getBlockEntity(pos) instanceof IOPortBlockEntity port) {
            if (level.isClientSide()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (port.kind().fluidHatchSize().isPresent() && port.kind().extendedFluidHatchSize().isEmpty()
                    || port.kind().combinedPortSize().isPresent() || MekanismBridge.get().isPort(port.kind().id())) {
                return interactWithContainer(player, hand, port)
                        ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static boolean interactWithContainer(Player player, InteractionHand hand, IOPortBlockEntity port) {
        IFluidHandler storage = port instanceof FluidHatchBlockEntity fluid ? fluid.fluidHandler(null)
                : port instanceof CombinedPortBlockEntity combined ? combined.fluidHandler(null) : null;
        if (storage == null) return MekanismBridge.get().interactWithChemicalContainer(player, hand, port) > 0;
        ItemStack held = player.getItemInHand(hand);
        var container = MekanismBridge.get().manualFluidContainerHandler(
                FluidUtil.getFluidHandler(held.copyWithCount(1)).orElse(null));
        var result = ContainerResourceTransfer.interact(port.ioType(), container, storage, false);
        if (result == null) return false;
        if (!player.getAbilities().instabuild) {
            ItemStack changed = container.getContainer();
            if (held.getCount() == 1) player.setItemInHand(hand, changed);
            else {
                held.shrink(1);
                ItemHandlerHelper.giveItemToPlayer(player, changed);
            }
        }
        var sound = result.extract() ? result.resource().getFluidType().getSound(SoundActions.BUCKET_FILL)
                : result.resource().getFluidType().getSound(SoundActions.BUCKET_EMPTY);
        if (sound != null) player.level().playSound(null, port.getBlockPos(), sound, SoundSource.BLOCKS, 1F, 1F);
        player.level().gameEvent(player, result.extract() ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, port.getBlockPos());
        return true;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                        Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (AE2Bridge.get().isPort(kind.id())) {
                if (player instanceof ServerPlayer serverPlayer
                        && AE2Bridge.get().openMenu(serverPlayer, level, pos)) {
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.CONSUME;
            }
            if (AppliedFluxBridge.get().isPort(kind.id())) return InteractionResult.CONSUME;
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) player.openMenu(provider);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof IOPortBlockEntity port) {
            port.markAutoIOCacheDirty();
            AE2Bridge.get().onPortNeighborChanged(port);
        }
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (level.getBlockEntity(pos) instanceof IOPortBlockEntity port) {
            port.dropContents();
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!moving && state.getBlock() != newState.getBlock()
                && !level.isClientSide() && level.getBlockEntity(pos) instanceof IOPortBlockEntity port) {
            port.onBlockRemoved();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> beType) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof IOPortBlockEntity port) port.serverTick();
        };
    }

    static PortMenuKind menuKindFor(String id) {
        if (matchesPortId(id, "item_input_bus") || matchesPortId(id, "item_output_bus")) {
            return PortMenuKind.ITEM;
        }
        if (matchesPortId(id, "fluid_input_hatch") || matchesPortId(id, "fluid_output_hatch")) {
            return PortMenuKind.FLUID;
        }
        if (matchesPortId(id, "energy_input_hatch") || matchesPortId(id, "energy_output_hatch")) {
            return PortMenuKind.ENERGY;
        }
        if (matchesPortId(id, "extended_item_input_bus") || matchesPortId(id, "extended_item_output_bus")) {
            return PortMenuKind.EXTENDED_ITEM;
        }
        if (matchesPortId(id, "extended_fluid_input_hatch") || matchesPortId(id, "extended_fluid_output_hatch")) {
            return PortMenuKind.EXTENDED_FLUID;
        }
        if (matchesPortId(id, "extended_energy_input_hatch") || matchesPortId(id, "extended_energy_output_hatch")) {
            return PortMenuKind.EXTENDED_ENERGY;
        }
        if (matchesPortId(id, "combined_input") || matchesPortId(id, "combined_output")) {
            return PortMenuKind.COMBINED;
        }
        if (matchesPortId(id, "extended_combined_input") || matchesPortId(id, "extended_combined_output")) {
            return PortMenuKind.EXTENDED_COMBINED;
        }
        return PortMenuKind.NONE;
    }

    private static boolean matchesPortId(String id, String baseId) {
        return id.equals(baseId) || id.startsWith(baseId + "_");
    }

    private static @Nullable AbstractContainerMenu openServerMenu(String kind, int containerId,
                                                        Inventory playerInv,
                                                        Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof IOPortBlockEntity port)
                || port.isRemoved() || !port.kind().id().equals(kind)) return null;
        return switch (menuKindFor(kind)) {
            case ITEM -> port instanceof ItemBusBlockEntity bus ? new ItemBusMenu(containerId, playerInv, bus) : null;
            case FLUID -> port instanceof FluidHatchBlockEntity hatch ? new FluidHatchMenu(containerId, playerInv, hatch) : null;
            case ENERGY, EXTENDED_ENERGY -> port instanceof EnergyHatchBlockEntity hatch
                    ? new EnergyHatchMenu(containerId, playerInv, hatch) : null;
            case EXTENDED_ITEM -> port instanceof ExtendedItemBusBlockEntity bus
                    ? new ExtendedItemMenu(containerId, playerInv, bus) : null;
            case EXTENDED_FLUID -> port instanceof ExtendedFluidHatchBlockEntity hatch
                    ? new ExtendedFluidMenu(containerId, playerInv, hatch) : null;
            case COMBINED -> port instanceof CombinedPortBlockEntity combined
                    ? new CombinedPortMenu(containerId, playerInv, combined) : null;
            case EXTENDED_COMBINED -> port instanceof ExtendedCombinedPortBlockEntity combined
                    ? new ExtendedCombinedMenu(containerId, playerInv, combined) : null;
            case NONE -> {
                AbstractContainerMenu menu = ArsNouveauBridge.get().createMenu(kind, containerId, playerInv, level, pos);
                yield menu != null ? menu : MekanismBridge.get().createMenu(kind, containerId, playerInv, level, pos);
            }
        };
    }

    static Component titleFor(String kind) {
        return Component.translatable("container.mmcr." + kind);
    }

}
