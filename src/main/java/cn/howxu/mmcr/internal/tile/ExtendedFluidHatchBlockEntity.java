package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Storage host for extended fluid hatches.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ExtendedFluidHatchBlockEntity extends FluidHatchBlockEntity {
    private final IOPortKind kind;

    public ExtendedFluidHatchBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, kindFromState(state, fallback(state)));
    }

    private ExtendedFluidHatchBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state, kind);
        this.kind = kind;
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
            return PortKinds.EXTENDED_FLUID_OUTPUT;
        }
        return PortKinds.EXTENDED_FLUID_INPUT;
    }
}
