package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class FluidInputHatchBlockEntity extends FluidHatchBlockEntity {

    private final IOPortKind kind;

    public FluidInputHatchBlockEntity(BlockPos pos, BlockState state) {
        super(typeFromState(state, PortKinds.FLUID_INPUT), pos, state, kindFromState(state, PortKinds.FLUID_INPUT));
        this.kind = kindFromState(state, PortKinds.FLUID_INPUT);
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
