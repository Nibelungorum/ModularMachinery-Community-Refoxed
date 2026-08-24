package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns factory lanes and aggregates their crafting runtime snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRuntime {
    private final MachineControllerBlockEntity controller;
    private final List<CraftingRuntime> lanes = new ArrayList<>();
    private final Map<CraftingRuntime, Runnable> laneTickers = new IdentityHashMap<>();
    private int laneLimit = 1;
    private @Nullable ExecutionStatus failure;

    public FactoryRuntime(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
    }

    public void tick(List<MachineRecipe> candidates, int maxParallelism) {
        for (CraftingRuntime runtime : List.copyOf(lanes)) {
            Runnable ticker = laneTickers.get(runtime);
            if (ticker != null) ticker.run();
            if (runtime.failure() != null) failure = runtime.failure();
        }
    }

    public List<CraftingRuntime> activeRuntimes() {
        return lanes.stream().filter(CraftingRuntime::active).toList();
    }

    public FactorySnapshot snapshot() {
        List<CraftingRuntime> active = activeRuntimes();
        int parallelism = active.stream().mapToInt(CraftingRuntime::parallelism).sum();
        return new FactorySnapshot(!active.isEmpty(), active.stream().map(CraftingRuntime::snapshot).toList(),
                parallelism, laneLimit, failure);
    }

    public void add(CraftingRuntime runtime) {
        add(runtime, () -> { });
    }

    public void add(CraftingRuntime runtime, Runnable ticker) {
        if (runtime == null || lanes.contains(runtime)) return;
        lanes.add(runtime);
        laneTickers.put(runtime, ticker == null ? () -> { } : ticker);
    }

    public void remove(CraftingRuntime runtime) {
        lanes.remove(runtime);
        laneTickers.remove(runtime);
    }

    public void setLaneLimit(int laneLimit) {
        this.laneLimit = Math.max(1, laneLimit);
        while (lanes.size() > this.laneLimit) {
            CraftingRuntime removed = lanes.stream().min(Comparator.comparingInt(CraftingRuntime::parallelism)).orElse(null);
            if (removed == null) break;
            removed.invalidate();
            remove(removed);
        }
    }

    public int laneLimit() {
        return laneLimit;
    }

    public void clear() {
        for (CraftingRuntime runtime : lanes) runtime.invalidate();
        lanes.clear();
        laneTickers.clear();
        failure = null;
    }
}
