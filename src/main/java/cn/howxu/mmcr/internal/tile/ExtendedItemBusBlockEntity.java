package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.port.ExtendedItemBusSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Storage host for extended item buses.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ExtendedItemBusBlockEntity extends ItemBusBlockEntity {
    private final IOPortKind kind;

    public ExtendedItemBusBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, kindFromState(state, fallback(state)));
    }

    private ExtendedItemBusBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state, size(kind).slots(), Long.MAX_VALUE);
        this.kind = kind;
    }

    private static ExtendedItemBusSize size(IOPortKind kind) {
        return kind.extendedItemBusSize()
                .orElseThrow(() -> new IllegalStateException("Extended item bus missing size: " + kind.id()));
    }

    @Override
    public IOType ioType() {
        return kind.ioType();
    }

    @Override
    public IOPortKind kind() {
        return kind;
    }

    private static IOPortKind fallback(BlockState state) {
        if (state.getBlock() instanceof IOPortBlock port && port.kind().ioType() == IOType.OUTPUT) {
            return PortKinds.EXTENDED_ITEM_OUTPUT;
        }
        return PortKinds.EXTENDED_ITEM_INPUT;
    }
}
