package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class EnergyOutputHatchBlockEntity extends EnergyHatchBlockEntity {

    private final IOPortKind kind;

    public EnergyOutputHatchBlockEntity(BlockPos pos, BlockState state) {
        super(typeFromState(state, PortKinds.ENERGY_OUTPUT), pos, state, kindFromState(state, PortKinds.ENERGY_OUTPUT));
        this.kind = kindFromState(state, PortKinds.ENERGY_OUTPUT);
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
