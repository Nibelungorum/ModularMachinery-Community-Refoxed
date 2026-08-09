package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * @author howxu <dev@howxu.cn>
 */
public class FactoryControllerBlockEntity extends BlockEntity {

    private int threadLimit = 1;
    private FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(threadLimit);

    public FactoryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get("factory_controller").get(), pos, state);
    }

    public int activeLaneCount() {
        return scheduler.activeLaneCount();
    }

    public int threadLimit() {
        return threadLimit;
    }

    public boolean hasLaneCapacity() {
        return scheduler.hasCapacity();
    }

    public int laneCapacity() {
        return scheduler.laneCapacity();
    }

    public boolean startLane(FactoryRecipeScheduler.Lane lane) {
        boolean started = scheduler.startLane(lane);
        if (started) setChanged();
        return started;
    }

    public void tickScheduler() {
        tickScheduler(0L);
    }

    public void tickScheduler(long gameTime) {
        int before = scheduler.activeLaneCount();
        scheduler.tick(gameTime);
        if (scheduler.activeLaneCount() != before) setChanged();
    }

    public void setThreadLimit(int threadLimit) {
        stopAll();
        this.threadLimit = Math.max(1, threadLimit);
        this.scheduler = new FactoryRecipeScheduler(this.threadLimit);
        setChanged();
    }

    public void stopAll() {
        scheduler.stopAll();
    }

    @Override
    public void setRemoved() {
        stopAll();
        super.setRemoved();
    }
}
