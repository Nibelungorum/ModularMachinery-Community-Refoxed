package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;

import java.util.List;

/**
 * Selects factory candidates and applies the configured lane limit.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRecipeScheduler {
    private final FactoryRuntime factoryRuntime;

    public FactoryRecipeScheduler(int threadLimit, FactoryRuntime factoryRuntime) {
        if (factoryRuntime == null) throw new IllegalArgumentException("factoryRuntime must not be null");
        this.factoryRuntime = factoryRuntime;
        setThreadLimit(threadLimit);
    }

    public boolean hasCapacity() {
        return factoryRuntime.laneCount() < factoryRuntime.laneLimit();
    }

    public int laneCapacity() {
        return Math.max(0, factoryRuntime.laneLimit() - factoryRuntime.laneCount());
    }

    public int threadLimit() {
        return factoryRuntime.laneLimit();
    }

    public void setThreadLimit(int threadLimit) {
        factoryRuntime.setLaneLimit(threadLimit);
    }

    public List<MachineRecipe> availableCandidates(List<MachineRecipe> candidates) {
        return factoryRuntime.availableCandidates(candidates);
    }
}
