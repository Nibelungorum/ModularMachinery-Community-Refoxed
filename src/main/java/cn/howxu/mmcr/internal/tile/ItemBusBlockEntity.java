package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.block.ItemBusBlock;
import cn.howxu.mmcr.registry.MMCRRegistries;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ItemBusBlockEntity extends BlockEntity {

    private final ItemStackHandler handler = new ItemStackHandler(9);

    public ItemBusBlockEntity(BlockPos pos, BlockState state) {
        super(MMCRRegistries.ITEM_BUS_BE.get(), pos, state);
    }

    public IItemHandler getItemHandler(Direction side) { return handler; }

    public IOType ioType() { return getBlockState().getValue(ItemBusBlock.IO_TYPE); }
}
