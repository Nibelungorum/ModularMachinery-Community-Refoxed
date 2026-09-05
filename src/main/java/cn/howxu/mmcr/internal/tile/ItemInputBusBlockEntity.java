package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class ItemInputBusBlockEntity extends ItemBusBlockEntity {

    private final IOPortKind kind;

    public ItemInputBusBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, kindFromState(state, PortKinds.ITEM_INPUT));
    }

    private ItemInputBusBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state, kind.itemBusSize()
                .orElseThrow(() -> new IllegalStateException("Item bus missing item size: " + kind.id()))
                .slots(), 64L);
        this.kind = kind;
    }

    @Override
    public IOType ioType() {
        return IOType.INPUT;
    }

    @Override
    public IOPortKind kind() {
        return kind;
    }
}
