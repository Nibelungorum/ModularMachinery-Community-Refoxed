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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public class IOPortBlock extends Block implements EntityBlock {

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
            if (provider != null) player.openMenu(provider);
        }
        return InteractionResult.SUCCESS;
    }

    private static AbstractContainerMenu openServerMenu(String kind, int containerId,
                                                        net.minecraft.world.entity.player.Inventory playerInv,
                                                        Level level, BlockPos pos) {
        return switch (kind) {
            case "item_input_bus", "item_output_bus" -> new ItemBusMenu(containerId, playerInv,
                    level.getBlockEntity(pos) instanceof ItemBusBlockEntity bus ? bus : null);
            case "fluid_input_hatch", "fluid_output_hatch" -> new FluidHatchMenu(containerId, playerInv,
                    level.getBlockEntity(pos) instanceof FluidHatchBlockEntity hatch ? hatch : null);
            case "energy_input_hatch", "energy_output_hatch" -> new EnergyHatchMenu(containerId, playerInv,
                    level.getBlockEntity(pos) instanceof EnergyHatchBlockEntity hatch ? hatch : null);
            default       -> null;
        };
    }

    private static Component titleFor(String kind) {
        return switch (kind) {
            case "item_input_bus" -> Component.translatable("container.mmcr.item_input_bus");
            case "item_output_bus" -> Component.translatable("container.mmcr.item_output_bus");
            case "fluid_input_hatch" -> Component.translatable("container.mmcr.fluid_input_hatch");
            case "fluid_output_hatch" -> Component.translatable("container.mmcr.fluid_output_hatch");
            case "energy_input_hatch" -> Component.translatable("container.mmcr.energy_input_hatch");
            case "energy_output_hatch" -> Component.translatable("container.mmcr.energy_output_hatch");
            default       -> Component.literal(kind);
        };
    }
}
