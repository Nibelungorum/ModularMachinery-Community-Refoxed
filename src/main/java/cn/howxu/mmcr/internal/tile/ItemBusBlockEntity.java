package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.MMCRBlockEntities;
import cn.howxu.mmcr.registry.MMCRPortKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ItemBusBlockEntity extends IOPortBlockEntity {

    private final ItemStackHandler handler = new ItemStackHandler(9);

    public ItemBusBlockEntity(BlockPos pos, BlockState state) {
        super(MMCRBlockEntities.BES.get("io_port_item_basic").get(), pos, state);
    }

    public IItemHandler getItemHandler(Direction side) { return handler; }

    public ItemStackHandler getItemStackHandler(Direction side) { return handler; }

    @Override
    public IOPortKind kind() { return MMCRPortKinds.ITEM; }
}
