package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class ItemInputBusBlockEntity extends ItemBusBlockEntity {

    public ItemInputBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get(PortKinds.ITEM_INPUT.id()).get(), pos, state);
    }

    @Override
    public IOType ioType() {
        return IOType.INPUT;
    }

    @Override
    public IOPortKind kind() {
        return PortKinds.ITEM_INPUT;
    }
}