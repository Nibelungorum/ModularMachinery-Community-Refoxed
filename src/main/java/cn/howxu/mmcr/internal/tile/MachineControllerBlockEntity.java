package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MachineControllerBlockEntity extends BlockEntity {

    private Machine machine;
    private MachineRecipe activeRecipe;
    private int tickCounter = 0;

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(MMCRRegistries.CONTROLLER_BE.get(), pos, state);
    }

    public Machine getMachine() { return machine; }
    public void setMachine(Machine m) { this.machine = m; setChanged(); }

    public boolean isFormed() { return getBlockState().getValue(MachineControllerBlock.FORMED); }
    public void setFormed(boolean f) {
        level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.FORMED, f), 3);
    }

    public MachineRecipe getActiveRecipe() { return activeRecipe; }
    public void setActiveRecipe(MachineRecipe r) { this.activeRecipe = r; setChanged(); }

    public int getTickCounter() { return tickCounter; }
    public void setTickCounter(int t) { this.tickCounter = t; setChanged(); }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        if (machine == null) return;

        boolean formed = StructureMatcher.matches(
                machine.pattern(), level, getBlockPos(),
                getBlockState().getValue(MachineControllerBlock.FACING));
        if (formed != isFormed()) setFormed(formed);
    }
}