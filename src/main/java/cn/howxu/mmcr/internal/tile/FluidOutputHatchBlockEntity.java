package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class FluidOutputHatchBlockEntity extends FluidHatchBlockEntity {

    private final IOPortKind kind;

    public FluidOutputHatchBlockEntity(BlockPos pos, BlockState state) {
        super(typeFromState(state, PortKinds.FLUID_OUTPUT), pos, state, kindFromState(state, PortKinds.FLUID_OUTPUT));
        this.kind = kindFromState(state, PortKinds.FLUID_OUTPUT);
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
