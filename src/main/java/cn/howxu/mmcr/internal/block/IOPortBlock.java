package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
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
        super(props.sound(SoundType.METAL));
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
}
