package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class ItemOutputBusBlockEntity extends ItemBusBlockEntity {

    private final IOPortKind kind;

    public ItemOutputBusBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, kindFromState(state, PortKinds.ITEM_OUTPUT));
    }

    private ItemOutputBusBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state, kind.itemBusSize()
                .orElseThrow(() -> new IllegalStateException("Item bus missing item size: " + kind.id()))
                .slots(), 64L);
        this.kind = kind;
    }

    @Override
    public IOType ioType() {
        return IOType.OUTPUT;
    }

    @Override
    public IOPortKind kind() {
        return kind;
    }
}
