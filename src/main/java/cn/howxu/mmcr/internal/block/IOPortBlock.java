package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.util.IOType;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public class IOPortBlock extends Block implements EntityBlock {

    public static final EnumProperty<IOType> IO_TYPE = EnumProperty.create("io_type", IOType.class);

    private final String kind;
    private final Supplier<? extends BlockEntityType<?>> beType;

    public IOPortBlock(String kind,
                       Supplier<? extends BlockEntityType<?>> beType,
                       Properties props) {
        super(props.sound(SoundType.METAL));
        this.kind = kind;
        this.beType = beType;
        registerDefaultState(stateDefinition.any().setValue(IO_TYPE, IOType.INPUT));
    }

    public String kind() { return kind; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beType.get().create(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(IO_TYPE);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, playerInv, player) -> openServerMenu(kind, containerId, playerInv, level, pos),
                titleFor(kind));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && !level.isClientSide()) {
            level.setBlock(pos, state.cycle(IO_TYPE), 3);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) player.openMenu(provider);
        }
        return InteractionResult.SUCCESS;
    }

    private static AbstractContainerMenu openServerMenu(String kind, int containerId,
                                                        net.minecraft.world.entity.player.Inventory playerInv,
                                                        Level level, BlockPos pos) {
        return switch (kind) {
            case "item"   -> new ItemBusMenu(containerId, playerInv,
                    level.getBlockEntity(pos) instanceof ItemBusBlockEntity ib ? ib : null);
            case "fluid"  -> new FluidHatchMenu(containerId, playerInv,
                    level.getBlockEntity(pos) instanceof FluidHatchBlockEntity fh ? fh : null);
            case "energy" -> new EnergyHatchMenu(containerId, playerInv,
                    level.getBlockEntity(pos) instanceof EnergyHatchBlockEntity eh ? eh : null);
            default       -> null;
        };
    }

    private static Component titleFor(String kind) {
        return switch (kind) {
            case "item"   -> Component.translatable("container.mmcr.item_bus");
            case "fluid"  -> Component.translatable("container.mmcr.fluid_hatch");
            case "energy" -> Component.translatable("container.mmcr.energy_hatch");
            default       -> Component.literal(kind);
        };
    }
}