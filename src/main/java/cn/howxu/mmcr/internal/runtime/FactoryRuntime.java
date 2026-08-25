package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.machine.FactoryThreadSpec;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.recipe.RecipeThread;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns factory lanes, their execution state, and their published snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRuntime {
    private final List<FactoryRecipeThread> lanes = new ArrayList<>();
    private final Map<FactoryRecipeThread, Identifier> recipeLocks = new IdentityHashMap<>();
    private final Set<FactoryRecipeThread> recipeLockUsed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<FactoryRecipeThread, Identifier> startReservations = new IdentityHashMap<>();
    private int laneLimit = 1;
    private int perThreadParallelLimit = 1;
    private boolean paused;
    private long nextFactoryLaneId;
    private @Nullable MachineControllerBlockEntity controller;
    private @Nullable ExecutionStatus failure;

    public void tick(List<MachineRecipe> candidates, int maxParallelism) {
        tick(candidates, maxParallelism, () -> { });
    }

    public void tick(List<MachineRecipe> candidates, int maxParallelism, Runnable onFinished) {
        if (paused || controller == null) return;
        perThreadParallelLimit = Math.max(1, maxParallelism);
        List<MachineRecipe> candidateSnapshot = List.copyOf(candidates == null ? List.of() : candidates);
        ControllerRuntimeSnapshot snapshot = controller.runtimeSnapshot();
        long structureVersion = snapshot.structure().version();
        long capabilityVersion = snapshot.capabilityVersion();
        long modifierVersion = snapshot.modifierVersion();
        long componentStateVersion = snapshot.stateVersion();
        Runnable finishCallback = onFinished == null ? () -> { } : onFinished;

        for (FactoryRecipeThread lane : List.copyOf(lanes)) {
            if (!lane.isStartPending() && !lane.runtime().active()) startReservations.remove(lane);
            if (lane.runtime().active()) startReservations.remove(lane);
            lane.setFinishContinuation(() -> {
                finishCallback.run();
                continueFinishedLane(lane, candidateSnapshot, structureVersion, capabilityVersion,
                        modifierVersion, componentStateVersion);
            });
            lane.tick();
        }
        for (FactoryRecipeThread lane : List.copyOf(lanes)) {
            lane.tickIdle();
            if (lane.isTimedOut(recipeLockUsed.contains(lane))) removeLane(lane);
        }
        if (candidateSnapshot.isEmpty()) {
            clearFinishedContinuations();
            recomputeFailure();
            return;
        }

        for (FactoryRecipeThread lane : List.copyOf(lanes)) {
            if (!lane.isIdle()) continue;
            List<MachineRecipe> available = availableCandidates(candidateSnapshot);
            if (available.isEmpty()) break;
            Identifier lock = recipeLocks.get(lane);
            if (lane.tryRestartLastRecipe(available, perThreadParallelLimit, structureVersion,
                    capabilityVersion, modifierVersion, componentStateVersion, lock)) continue;
            reserveStart(lane, lane.searchAndStartRecipe(available, perThreadParallelLimit,
                    structureVersion, lock));
        }
        while (lanes.size() < laneLimit && perThreadParallelLimit > 0) {
            List<MachineRecipe> available = availableCandidates(candidateSnapshot);
            if (available.isEmpty()) break;
            FactoryRecipeThread lane = FactoryRecipeThread.simple(controller, "factory-" + nextFactoryLaneId++);
            lanes.add(lane);
            Identifier lock = recipeLocks.get(lane);
            boolean started = lane.searchAndStartRecipe(available, perThreadParallelLimit, structureVersion, lock);
            reserveStart(lane, started);
            if (!started) break;
        }
        clearFinishedContinuations();
        recomputeFailure();
    }

    public void syncCoreLanes(MachineControllerBlockEntity controller, Machine machine,
                              List<MachineRecipe> candidates) {
        ensureBaseLane(controller);
        Map<Identifier, MachineRecipe> byId = new LinkedHashMap<>();
        for (MachineRecipe recipe : candidates == null ? List.<MachineRecipe>of() : candidates) {
            byId.putIfAbsent(recipe.id(), recipe);
        }

        Map<String, FactoryRecipeThread> existingCoreLanes = new LinkedHashMap<>();
        List<FactoryRecipeThread> dynamicLanes = new ArrayList<>();
        for (FactoryRecipeThread lane : lanes) {
            if (lane.isBaseThread()) continue;
            if (lane.isCoreThread()) existingCoreLanes.putIfAbsent(lane.threadName(), lane);
            else dynamicLanes.add(lane);
        }

        List<FactoryRecipeThread> reconciled = new ArrayList<>();
        reconciled.add(lanes.getFirst());
        if (machine != null) {
            for (FactoryThreadSpec spec : machine.factoryThreads()) {
                Set<MachineRecipe> recipes = new LinkedHashSet<>();
                for (Identifier id : spec.recipeIds()) {
                    MachineRecipe recipe = byId.get(id);
                    if (recipe != null) recipes.add(recipe);
                }
                FactoryRecipeThread lane = existingCoreLanes.remove(spec.name());
                if (lane == null) {
                    lane = FactoryRecipeThread.core(controller, spec.name(), recipes);
                    lanes.add(lane);
                } else {
                    lane.replaceRecipeSet(recipes);
                }
                reconciled.add(lane);
            }
        }
        for (FactoryRecipeThread removed : existingCoreLanes.values()) removeLane(removed);
        reconciled.addAll(dynamicLanes);
        lanes.clear();
        lanes.addAll(reconciled);
        setLaneLimit(laneLimit);
        recomputeFailure();
    }

    public void ensureBaseLane(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        if (!lanes.isEmpty() && lanes.getFirst().isBaseThread()) return;
        for (FactoryRecipeThread lane : List.copyOf(lanes)) {
            if (lane.isBaseThread()) removeLane(lane);
        }
        lanes.addFirst(FactoryRecipeThread.base(controller));
    }

    public List<CraftingRuntime> activeRuntimes() {
        return lanes.stream().map(FactoryRecipeThread::runtime).filter(CraftingRuntime::active).toList();
    }

    public int activeLaneCount() {
        return activeRuntimes().size();
    }

    public List<MachineRecipe> availableCandidates(List<MachineRecipe> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        Map<Identifier, Integer> activeCounts = activeRecipeCounts();
        return candidates.stream().filter(recipe -> {
            if (recipe == null) return false;
            int maxThreads = recipe.maxThreads();
            return maxThreads <= 0 || activeCounts.getOrDefault(recipe.id(), 0) < maxThreads;
        }).toList();
    }

    public boolean toggleRecipeLock(int index) {
        if (index < 0 || index >= lanes.size()) return false;
        FactoryRecipeThread lane = lanes.get(index);
        Identifier current = recipeLocks.remove(lane);
        if (current != null) return true;
        MachineRecipe recipe = lane.runtime().recipe();
        if (recipe == null) return false;
        recipeLocks.put(lane, recipe.id());
        recipeLockUsed.add(lane);
        return true;
    }

    public List<ThreadSnapshot> threadSnapshots() {
        List<ThreadSnapshot> snapshots = new ArrayList<>(laneLimit);
        for (int index = 0; index < lanes.size(); index++) {
            FactoryRecipeThread lane = lanes.get(index);
            CraftingStateSnapshot state = lane.runtime().snapshot();
            Identifier lockedRecipe = recipeLocks.get(lane);
            snapshots.add(new ThreadSnapshot(index, lane.isBaseThread(), lane.isCoreThread(), lane.runtime().active(),
                    state.recipeId() == null ? "" : state.recipeId().toString(), lane.runtime().tickCount(),
                    lane.runtime().totalTick(), lane.runtime().active() ? lane.runtime().parallelism() : 1,
                    lane.getLastFailureUnloc(), lockedRecipe != null,
                    lockedRecipe == null ? "" : lockedRecipe.toString()));
        }
        while (snapshots.size() < laneLimit) {
            snapshots.add(new ThreadSnapshot(snapshots.size(), false, false, false, "", 0, 0, 1, "", false, ""));
        }
        return List.copyOf(snapshots);
    }

    public FactorySnapshot snapshot() {
        recomputeFailure();
        List<CraftingStateSnapshot> laneSnapshots = lanes.stream()
                .map(FactoryRecipeThread::runtime)
                .map(CraftingRuntime::snapshot)
                .filter(state -> state.recipeId() != null || state.failure() != null)
                .toList();
        List<CraftingRuntime> activeRuntimes = activeRuntimes();
        int parallelism = activeRuntimes.stream().mapToInt(CraftingRuntime::parallelism).sum();
        return new FactorySnapshot(!activeRuntimes.isEmpty(), laneSnapshots, parallelism, laneLimit,
                activeRuntimes.size(), Math.max(1, perThreadParallelLimit), paused, threadSnapshots(), failure);
    }

    public void pause() {
        paused = true;
        for (FactoryRecipeThread lane : List.copyOf(lanes)) {
            lane.setFinishContinuation(null);
            lane.runtime().pause();
        }
    }

    public void resume() {
        paused = false;
        for (FactoryRecipeThread lane : List.copyOf(lanes)) lane.runtime().resume();
    }

    public boolean isPaused() {
        return paused;
    }

    public int laneCount() {
        return lanes.size();
    }

    public boolean contains(CraftingRuntime runtime) {
        return lanes.stream().anyMatch(lane -> lane.runtime() == runtime);
    }

    public void setLaneLimit(int laneLimit) {
        this.laneLimit = Math.max(1, laneLimit);
        while (lanes.size() > this.laneLimit) {
            FactoryRecipeThread removed = lanes.stream()
                    .filter(lane -> !lane.isBaseThread())
                    .min(Comparator.comparingInt(lane -> lane.runtime().parallelism()))
                    .orElse(null);
            if (removed == null) break;
            removeLane(removed);
        }
        recomputeFailure();
    }

    public int laneLimit() {
        return laneLimit;
    }

    public void clear() {
        for (FactoryRecipeThread lane : List.copyOf(lanes)) removeLane(lane);
        recipeLocks.clear();
        recipeLockUsed.clear();
        startReservations.clear();
        recomputeFailure();
    }

    public void save(ValueOutput output) {
        output.putInt("lane_limit", laneLimit);
        output.putBoolean("paused", paused);
        output.putInt("lane_count", lanes.size());
        for (int index = 0; index < lanes.size(); index++) {
            FactoryRecipeThread lane = lanes.get(index);
            ValueOutput laneOutput = output.child("lane_" + index);
            lane.save(laneOutput);
            laneOutput.putBoolean("had_recipe_lock", recipeLockUsed.contains(lane));
            Identifier lockedRecipe = recipeLocks.get(lane);
            if (lockedRecipe != null) laneOutput.putString("locked_recipe", lockedRecipe.toString());
        }
    }

    public void load(ValueInput input, MachineControllerBlockEntity controller) {
        this.controller = controller;
        clear();
        laneLimit = Math.max(1, input.getIntOr("lane_limit", laneLimit));
        paused = input.getBooleanOr("paused", false);
        int count = Math.max(0, input.getIntOr("lane_count", 0));
        for (int index = 0; index < count; index++) {
            ValueInput laneInput = input.childOrEmpty("lane_" + index);
            FactoryRecipeThread lane = FactoryRecipeThread.load(laneInput, controller);
            lanes.add(lane);
            if (laneInput.getBooleanOr("had_recipe_lock", false)) recipeLockUsed.add(lane);
            String lockedRecipeName = laneInput.getStringOr("locked_recipe", "");
            if (!lockedRecipeName.isEmpty()) {
                Identifier lockedRecipeId = Identifier.parse(lockedRecipeName);
                if (RecipeRegistry.getRecipe(lockedRecipeId) != null) recipeLocks.put(lane, lockedRecipeId);
            }
            if (lane.laneId().startsWith("factory-")) {
                try {
                    nextFactoryLaneId = Math.max(nextFactoryLaneId,
                            Long.parseLong(lane.laneId().substring("factory-".length())) + 1);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        ensureBaseLane(controller);
        setLaneLimit(laneLimit);
    }

    private void continueFinishedLane(FactoryRecipeThread lane, List<MachineRecipe> candidates,
                                      long structureVersion, long capabilityVersion,
                                      long modifierVersion, long componentStateVersion) {
        if (paused || lane.getStatus() != RecipeThread.Status.IDLE || !lane.isIdle()) return;
        List<MachineRecipe> available = availableCandidates(candidates);
        if (available.isEmpty()) return;
        Identifier lock = recipeLocks.get(lane);
        if (lane.tryRestartLastRecipe(available, perThreadParallelLimit, structureVersion,
                capabilityVersion, modifierVersion, componentStateVersion, lock)) {
            reserveStart(lane, true);
            return;
        }
        reserveStart(lane, lane.searchAndStartRecipe(available, perThreadParallelLimit,
                structureVersion, lock));
    }

    private Map<Identifier, Integer> activeRecipeCounts() {
        startReservations.entrySet().removeIf(entry -> !lanes.contains(entry.getKey())
                || (!entry.getKey().isStartPending() && !entry.getKey().runtime().active()));
        Map<Identifier, Integer> counts = new LinkedHashMap<>();
        for (Identifier recipeId : startReservations.values()) counts.merge(recipeId, 1, Integer::sum);
        for (FactoryRecipeThread lane : lanes) {
            if (lane.getStatus() == RecipeThread.Status.FAILED) continue;
            MachineRecipe pendingRecipe = lane.getPendingStartRecipe();
            if (pendingRecipe != null && !startReservations.containsKey(lane)) {
                counts.merge(pendingRecipe.id(), 1, Integer::sum);
            }
            MachineRecipe activeRecipe = lane.runtime().recipe();
            if (activeRecipe != null) counts.merge(activeRecipe.id(), 1, Integer::sum);
        }
        return counts;
    }

    private void reserveStart(FactoryRecipeThread lane, boolean started) {
        if (started && lane.getPendingStartRecipe() != null) {
            startReservations.put(lane, lane.getPendingStartRecipe().id());
        } else {
            startReservations.remove(lane);
        }
    }

    private void clearFinishedContinuations() {
        for (FactoryRecipeThread lane : lanes) {
            if (!lane.isStartPending() && !lane.runtime().active()) lane.setFinishContinuation(null);
        }
    }

    private void removeLane(FactoryRecipeThread lane) {
        lane.invalidate();
        lanes.remove(lane);
        removeLaneState(lane);
    }

    private void removeLaneState(FactoryRecipeThread lane) {
        recipeLocks.remove(lane);
        recipeLockUsed.remove(lane);
        startReservations.remove(lane);
    }

    public void recomputeFailure() {
        ExecutionStatus previousFailure = failure;
        failure = lanes.stream()
                .map(FactoryRecipeThread::runtime)
                .map(CraftingRuntime::failure)
                .filter(status -> status != null)
                .findFirst()
                .orElse(null);
        if (controller != null && (previousFailure != null || failure != null)) {
            controller.syncFactoryFailure(failure);
        }
    }

    /** Immutable runtime-owned lane snapshot. */
    public record ThreadSnapshot(int index, boolean baseThread, boolean coreThread, boolean active,
                                 String recipeId, int tick, int totalTick, int parallelism,
                                 String lastFailureUnloc, boolean locked, String lockedRecipeId) {
        public ThreadSnapshot {
            recipeId = recipeId == null ? "" : recipeId;
            lastFailureUnloc = lastFailureUnloc == null ? "" : lastFailureUnloc;
            lockedRecipeId = locked ? lockedRecipeId == null ? "" : lockedRecipeId : "";
        }

        public static ThreadSnapshot idleBase() {
            return new ThreadSnapshot(0, true, false, false, "", 0, 0, 1, "", false, "");
        }
    }
}
