package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class IOPortBlockEntity extends BlockEntity implements MachineComponentTile {

    protected IOPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract IOType ioType();

    public abstract IOPortKind kind();

    @Override
    public MachineComponent provideComponent() {
        return new MachineComponent(kind(), ioType());
    }

    public void serverTick() {
        tick();
    }

    protected void tick() {
        kind().tick(this);
    }
}
