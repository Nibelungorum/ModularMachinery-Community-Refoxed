package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.machine.FactoryThreadSpec;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeCatalog;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.recipe.FactorySearchContext;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.recipe.RecipeThread;
import cn.howxu.mmcr.internal.recipe.RecipeSearchContextKey;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Owns factory lanes, their execution state, and their published snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRuntime {
    private static final int MAX_LANES = 1024;
    private final List<FactoryRecipeThread> lanes = new ArrayList<>();
    private final Map<FactoryRecipeThread, Identifier> recipeLocks = new IdentityHashMap<>();
    private final Set<FactoryRecipeThread> recipeLockUsed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<FactoryRecipeThread, Identifier> startReservations = new IdentityHashMap<>();
    private int laneLimit = 1;
    private int perThreadParallelLimit = 1;
    private boolean paused;
    private long nextFactoryLaneId;
    private long coreCatalogVersion = Long.MIN_VALUE;
    private @Nullable MachineControllerBlockEntity controller;
    private @Nullable ExecutionStatus failure;
    private long searchAttemptsForTesting;
    private boolean failureDirty = true;
    private boolean activeCountDirty = true;
    private long factoryStateEpoch;
    private int cachedActiveLaneCount;
    private long cachedSnapshotEpoch = Long.MIN_VALUE;
    private @Nullable FactorySnapshot cachedSnapshot;
    private final Set<FactoryRecipeThread> readyLanes = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private List<MachineRecipe> cachedOrderedCandidateSource = List.of();
    private long cachedOrderedCandidateCatalogVersion = Long.MIN_VALUE;
    private List<MachineRecipe> cachedOrderedCandidates = List.of();
    private List<MachineRecipe> cachedIndexedCandidateSource = List.of();
    private long cachedIndexedCandidateCatalogVersion = Long.MIN_VALUE;
    private Set<Item> cachedIndexedInputItems = Set.of();
    private Set<Identifier> cachedIndexedLockedRecipeIds = Set.of();
    private List<MachineRecipe> cachedIndexedCandidates = List.of();

    public FactoryTickResult tick(List<MachineRecipe> candidates, int maxParallelism) {
        return tick(candidates, maxParallelism, currentGameTime());
    }

    public FactoryTickResult tick(List<MachineRecipe> candidates, int maxParallelism, Runnable onFinished) {
        return tick(candidates, maxParallelism, onFinished, currentGameTime());
    }

    public FactoryTickResult tick(List<MachineRecipe> candidates, int maxParallelism, long gameTime) {
        return tick(candidates, maxParallelism, () -> { }, gameTime);
    }

    public FactoryTickResult tick(List<MachineRecipe> candidates, int maxParallelism, long gameTime, Runnable onFinished) {
        return tick(candidates, maxParallelism, onFinished, gameTime);
    }

    public FactoryTickResult tick(List<MachineRecipe> candidates, int maxParallelism, Runnable onFinished, long gameTime) {
        if (controller == null) return currentTickResult(factoryStateEpoch, false);
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        return tick(createSearchContext(snapshot, candidates, maxParallelism, gameTime), onFinished);
    }

    public FactoryTickResult tick(FactorySearchContext context, Runnable onFinished) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        long initialEpoch = factoryStateEpoch;
        if (paused || controller == null) return currentTickResult(initialEpoch, false);

        if (perThreadParallelLimit != context.maxParallelism()) {
            perThreadParallelLimit = context.maxParallelism();
            markLaneStateChanged();
        }
        long structureVersion = context.snapshot().structure().version();
        long capabilityVersion = context.snapshot().capabilityVersion();
        long modifierVersion = context.snapshot().modifierVersion();
        long componentStateVersion = context.snapshot().stateVersion();
        long gameTime = context.gameTime();
        Runnable finishCallback = onFinished == null ? () -> { } : onFinished;
        List<FactoryRecipeThread> laneSnapshot = List.copyOf(lanes);
        Map<FactoryRecipeThread, LaneObservation> observations = new IdentityHashMap<>();
        for (FactoryRecipeThread lane : laneSnapshot) {
            observations.put(lane, observe(lane));
            if (!lane.isStartPending() && !lane.runtime().active()) startReservations.remove(lane);
            if (lane.runtime().active()) startReservations.remove(lane);
            lane.setFinishContinuation(() -> {
                try {
                    finishCallback.run();
                } finally {
                    markFinishedLaneReady(lane);
                }
            });
            lane.setSearchGameTime(gameTime);
            lane.setSearchContextKey(searchContextKey(context, lane, recipeLocks.get(lane)));
            lane.tick();
        }

        Set<FactoryRecipeThread> readyThisTick = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (FactoryRecipeThread lane : laneSnapshot) {
            if (readyLanes.remove(lane)) readyThisTick.add(lane);
        }

        Map<Identifier, Integer> activeCounts = activeRecipeCounts();
        if (!context.orderedCandidates().isEmpty()) {
            for (FactoryRecipeThread lane : laneSnapshot) {
                if (!lane.isIdle()) continue;
                List<MachineRecipe> available = filterAvailableCandidates(context.orderedCandidates(), activeCounts);
                if (available.isEmpty()) break;
                Identifier lock = recipeLocks.get(lane);
                RecipeSearchContextKey key = searchContextKey(context, lane, lock);
                if (!lane.canSearch(gameTime, key)) continue;
                lane.setSearchGameTime(gameTime);
                lane.setSearchContextKey(key);
                if (lane.tryRestartLastRecipe(context, available, perThreadParallelLimit, structureVersion,
                        capabilityVersion, modifierVersion, componentStateVersion, lock)) {
                    reserveStart(lane, true, activeCounts);
                    readyThisTick.remove(lane);
                    continue;
                }
                searchAttemptsForTesting++;
                reserveStart(lane, lane.searchAndStartRecipe(context, available, structureVersion, lock), activeCounts);
                readyThisTick.remove(lane);
            }
            while (lanes.size() < laneLimit && perThreadParallelLimit > 0) {
                List<MachineRecipe> available = filterAvailableCandidates(context.orderedCandidates(), activeCounts);
                if (available.isEmpty()) break;
                FactoryRecipeThread lane = FactoryRecipeThread.simple(controller, "factory-" + nextFactoryLaneId++);
                addLane(lane);
                Identifier lock = recipeLocks.get(lane);
                RecipeSearchContextKey key = searchContextKey(context, lane, lock);
                if (!lane.canSearch(gameTime, key)) break;
                lane.setSearchGameTime(gameTime);
                lane.setSearchContextKey(key);
                searchAttemptsForTesting++;
                boolean started = lane.searchAndStartRecipe(context, available, structureVersion, lock);
                reserveStart(lane, started, activeCounts);
                if (!started) break;
            }
        }

        for (FactoryRecipeThread lane : laneSnapshot) {
            lane.tickIdle();
            if (lane.isTimedOut(recipeLockUsed.contains(lane))) removeLane(lane);
        }
        clearFinishedContinuations();
        for (Map.Entry<FactoryRecipeThread, LaneObservation> entry : observations.entrySet()) {
            if (lanes.contains(entry.getKey()) && !entry.getValue().equals(observe(entry.getKey()))) {
                markLaneStateChanged();
            }
        }
        recomputeFailureIfDirty();
        int activeLaneCount = activeLaneCount();
        return new FactoryTickResult(activeLaneCount, failure,
                initialEpoch != factoryStateEpoch, initialEpoch != factoryStateEpoch);
    }

    public void syncCoreLanes(MachineControllerBlockEntity controller, Machine machine,
                              List<MachineRecipe> candidates) {
        ensureBaseLane(controller);
        Identifier machineId = machine == null ? null : machine.registryName();
        long catalogVersion = RecipeRegistry.catalog(machineId).version();
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
                    addLane(lane);
                } else {
                    if (coreCatalogVersion != catalogVersion || !lane.recipeSet().equals(recipes)) {
                        lane.replaceRecipeSet(recipes);
                        markLaneStateChanged();
                    }
                }
                reconciled.add(lane);
            }
        }
        for (FactoryRecipeThread removed : existingCoreLanes.values()) removeLane(removed);
        reconciled.addAll(dynamicLanes);
        if (!lanes.equals(reconciled)) {
            lanes.clear();
            lanes.addAll(reconciled);
            markLaneStateChanged();
        }
        setLaneLimit(laneLimit);
        trimLanesToLimit();
        coreCatalogVersion = catalogVersion;
    }

    public void ensureBaseLane(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        if (!lanes.isEmpty() && lanes.getFirst().isBaseThread()) return;
        for (FactoryRecipeThread lane : List.copyOf(lanes)) {
            if (lane.isBaseThread()) removeLane(lane);
        }
        lanes.addFirst(FactoryRecipeThread.base(controller));
        markLaneStateChanged();
    }

    public List<CraftingRuntime> activeRuntimes() {
        return lanes.stream().map(FactoryRecipeThread::runtime).filter(CraftingRuntime::active).toList();
    }

    public void invalidateForSmartInterfaceChange() {
        Map<FactoryRecipeThread, LaneObservation> observations = new IdentityHashMap<>();
        for (FactoryRecipeThread lane : List.copyOf(lanes)) {
            observations.put(lane, observe(lane));
            lane.invalidateForSmartInterfaceChange();
        }
        for (Map.Entry<FactoryRecipeThread, LaneObservation> entry : observations.entrySet()) {
            if (!entry.getValue().equals(observe(entry.getKey()))) markLaneStateChanged();
        }
        recomputeFailureIfDirty();
    }

    public void wakeSearches(ResourceAvailabilityNotifier.Reason reason, @Nullable Object resource) {
        if (reason == null) return;
        for (FactoryRecipeThread lane : lanes) {
            if (lane.matchesAvailability(reason, resource)) lane.wakeSearch();
        }
    }

    public long searchAttemptsForTesting() {
        return searchAttemptsForTesting;
    }

    public long stateEpoch() {
        return factoryStateEpoch;
    }

    public int activeLaneCount() {
        if (activeCountDirty) {
            int count = 0;
            for (FactoryRecipeThread lane : lanes) {
                if (lane.runtime().active()) count++;
            }
            cachedActiveLaneCount = count;
            activeCountDirty = false;
        }
        return cachedActiveLaneCount;
    }

    public List<MachineRecipe> availableCandidates(List<MachineRecipe> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return filterAvailableCandidates(candidates, activeRecipeCounts());
    }

    public boolean toggleRecipeLock(int index) {
        if (index < 0 || index >= lanes.size()) return false;
        FactoryRecipeThread lane = lanes.get(index);
        Identifier current = recipeLocks.remove(lane);
        if (current != null) {
            markLaneStateChanged();
            return true;
        }
        MachineRecipe recipe = lane.runtime().recipe();
        if (recipe == null) return false;
        recipeLocks.put(lane, recipe.id());
        recipeLockUsed.add(lane);
        markLaneStateChanged();
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
                    lane.runtime().failureUnloc(), lockedRecipe != null,
                    lockedRecipe == null ? "" : lockedRecipe.toString()));
        }
        while (snapshots.size() < laneLimit) {
            snapshots.add(new ThreadSnapshot(snapshots.size(), false, false, false, "", 0, 0, 1, "", false, ""));
        }
        return List.copyOf(snapshots);
    }

    public FactorySnapshot snapshot() {
        recomputeFailureIfDirty();
        if (cachedSnapshot != null && cachedSnapshotEpoch == factoryStateEpoch) return cachedSnapshot;
        List<CraftingStateSnapshot> laneSnapshots = lanes.stream()
                .map(FactoryRecipeThread::runtime)
                .map(CraftingRuntime::snapshot)
                .filter(state -> state.recipeId() != null || state.failure() != null)
                .toList();
        int activeCount = activeLaneCount();
        int parallelism = 0;
        for (FactoryRecipeThread lane : lanes) {
            if (lane.runtime().active()) parallelism += lane.runtime().parallelism();
        }
        cachedSnapshot = new FactorySnapshot(false, activeCount > 0, laneSnapshots, parallelism, laneLimit,
                activeCount, Math.max(1, perThreadParallelLimit), paused, threadSnapshots(), "", 0, failure,
                List.of());
        cachedSnapshotEpoch = factoryStateEpoch;
        return cachedSnapshot;
    }

    public void pause() {
        if (paused) return;
        paused = true;
        for (FactoryRecipeThread lane : List.copyOf(lanes)) {
            lane.setFinishContinuation(null);
            lane.runtime().pause();
        }
        markLaneStateChanged();
    }

    public void resume() {
        if (!paused) return;
        paused = false;
        for (FactoryRecipeThread lane : lanes) lane.runtime().resume();
        markLaneStateChanged();
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

    public boolean setLaneLimit(int laneLimit) {
        int normalized = Math.min(MAX_LANES, Math.max(1, laneLimit));
        if (this.laneLimit == normalized) return false;
        this.laneLimit = normalized;
        markLaneStateChanged();
        trimLanesToLimit();
        return true;
    }

    private void trimLanesToLimit() {
        while (lanes.size() > this.laneLimit) {
            FactoryRecipeThread removed = lanes.stream()
                    .filter(lane -> !lane.isBaseThread())
                    .min(Comparator.comparingInt(lane -> lane.runtime().parallelism()))
                    .orElse(null);
            if (removed == null) break;
            removeLane(removed);
        }
    }

    public int laneLimit() {
        return laneLimit;
    }

    public void clear() {
        boolean changed = !lanes.isEmpty() || !recipeLocks.isEmpty() || !recipeLockUsed.isEmpty()
                || !startReservations.isEmpty() || coreCatalogVersion != Long.MIN_VALUE || failure != null;
        for (FactoryRecipeThread lane : List.copyOf(lanes)) removeLane(lane);
        recipeLocks.clear();
        recipeLockUsed.clear();
        startReservations.clear();
        readyLanes.clear();
        coreCatalogVersion = Long.MIN_VALUE;
        if (changed && lanes.isEmpty()) markLaneStateChanged();
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
        setLaneLimit(input.getIntOr("lane_limit", laneLimit));
        boolean restoredPaused = input.getBooleanOr("paused", false);
        if (paused != restoredPaused) {
            paused = restoredPaused;
            markLaneStateChanged();
        }
        int count = Math.min(MAX_LANES, Math.max(0, input.getIntOr("lane_count", 0)));
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        Machine machine = current.structure().machine() == null
                ? current.structure().configuredMachine() : current.structure().machine();
        MachineRecipeCatalog catalog = RecipeRegistry.catalog(machine == null ? null : machine.registryName());
        Map<String, List<MachineRecipe>> coreCandidates = new LinkedHashMap<>();
        if (machine != null) {
            for (FactoryThreadSpec spec : machine.factoryThreads()) {
                coreCandidates.put(spec.name(), catalog.recipes().stream()
                        .filter(recipe -> spec.recipeIds().contains(recipe.id())).toList());
            }
        }
        for (int index = 0; index < count; index++) {
            ValueInput laneInput = input.childOrEmpty("lane_" + index);
            String lockedRecipeName = laneInput.getStringOr("locked_recipe", "");
            Identifier lockedRecipeId = lockedRecipeName.isEmpty() ? null : Identifier.parse(lockedRecipeName);
            List<MachineRecipe> candidates = laneInput.getBooleanOr("core", false)
                    ? coreCandidates.getOrDefault(laneInput.getStringOr("name", ""), List.of())
                    : catalog.recipes();
            FactoryRecipeThread lane = FactoryRecipeThread.load(laneInput, controller, lockedRecipeId, candidates);
            addLane(lane);
            if (laneInput.getBooleanOr("had_recipe_lock", false)) recipeLockUsed.add(lane);
            if (!lockedRecipeName.isEmpty()) {
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
        trimLanesToLimit();
        for (FactoryRecipeThread lane : lanes) {
            RecipeSearchContextKey key = currentSearchContextKey(lane, recipeLocks.get(lane));
            if (lane.searchFailureKey() != null && !lane.searchFailureKey().equals(key)) {
                lane.clearSearchFailure();
            }
        }
    }

    public void rebindCurrentVersions() {
        Map<FactoryRecipeThread, LaneObservation> observations = new IdentityHashMap<>();
        for (FactoryRecipeThread lane : lanes) {
            observations.put(lane, observe(lane));
            lane.rebindCurrentVersions();
        }
        for (Map.Entry<FactoryRecipeThread, LaneObservation> entry : observations.entrySet()) {
            if (!entry.getValue().equals(observe(entry.getKey()))) markLaneStateChanged();
        }
    }

    public FactorySearchContext createSearchContext(ControllerRuntimeSnapshot snapshot,
                                                    List<MachineRecipe> candidates,
                                                    int maxParallelism, long gameTime) {
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        Identifier machineId = machine == null ? null : machine.registryName();
        MachineRecipeCatalog catalog = RecipeRegistry.catalog(machineId);
        List<MachineRecipe> candidateSnapshot = nonNullCandidates(candidates);
        List<MachineRecipe> ordered = orderedCandidates(candidateSnapshot, catalog);
        Set<Item> inputItems = currentInputItems();
        if (inputItems != null && candidatesBelongToCatalog(candidateSnapshot, catalog)) {
            Set<Identifier> lockedRecipeIds = new LinkedHashSet<>(recipeLocks.values());
            ordered = filterIndexedCandidates(ordered, catalog, inputItems, lockedRecipeIds);
        }
        return new FactorySearchContext(snapshot, ordered, controller.componentRuntime().capabilities(),
                controller.componentRuntime().modifierList(), catalog.version(),
                controller.resourceAvailabilityEpoch(), maxParallelism, gameTime);
    }

    private static List<MachineRecipe> nonNullCandidates(List<MachineRecipe> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        for (MachineRecipe candidate : candidates) {
            if (candidate == null) return candidates.stream().filter(Objects::nonNull).toList();
        }
        return candidates;
    }

    private List<MachineRecipe> orderedCandidates(List<MachineRecipe> candidates, MachineRecipeCatalog catalog) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (candidates.equals(catalog.recipes())) return catalog.orderedRecipes();
        if (candidates.size() == catalog.orderedRecipes().size()
                && candidates.stream().allMatch(catalog.orderedRecipes()::contains)
                && catalog.orderedRecipes().stream().allMatch(candidates::contains)) {
            return catalog.orderedRecipes();
        }
        if (cachedOrderedCandidateCatalogVersion == catalog.version()
                && cachedOrderedCandidateSource.equals(candidates)) {
            return cachedOrderedCandidates;
        }
        List<MachineRecipe> ordered;
        if (candidates.stream().allMatch(catalog.orderedRecipes()::contains)) {
            ordered = catalog.orderedRecipes().stream().filter(candidates::contains).toList();
        } else {
            ordered = candidates.stream()
                    .sorted(Comparator.comparingInt(MachineRecipe::priority)
                            .thenComparing(Comparator.comparingInt(MachineRecipe::inputRequirementCount).reversed())
                            .thenComparing(MachineRecipe::id))
                    .toList();
        }
        cachedOrderedCandidateSource = List.copyOf(candidates);
        cachedOrderedCandidateCatalogVersion = catalog.version();
        cachedOrderedCandidates = ordered;
        return ordered;
    }

    private List<MachineRecipe> filterIndexedCandidates(List<MachineRecipe> ordered,
                                                        MachineRecipeCatalog catalog,
                                                        Set<Item> inputItems,
                                                        Set<Identifier> lockedRecipeIds) {
        if (ordered.isEmpty()) return ordered;
        if (cachedIndexedCandidateCatalogVersion == catalog.version()
                && cachedIndexedCandidateSource.equals(ordered)
                && cachedIndexedInputItems.equals(inputItems)
                && cachedIndexedLockedRecipeIds.equals(lockedRecipeIds)) {
            return cachedIndexedCandidates;
        }
        Set<MachineRecipe> indexed = new LinkedHashSet<>(catalog.inputIndex().candidates(inputItems));
        boolean filteringRequired = false;
        for (MachineRecipe recipe : ordered) {
            if (!indexed.contains(recipe) && !lockedRecipeIds.contains(recipe.id())) {
                filteringRequired = true;
                break;
            }
        }
        List<MachineRecipe> filtered = filteringRequired
                ? ordered.stream().filter(recipe -> indexed.contains(recipe) || lockedRecipeIds.contains(recipe.id())).toList()
                : ordered;
        cachedIndexedCandidateSource = List.copyOf(ordered);
        cachedIndexedCandidateCatalogVersion = catalog.version();
        cachedIndexedInputItems = Set.copyOf(inputItems);
        cachedIndexedLockedRecipeIds = Set.copyOf(lockedRecipeIds);
        cachedIndexedCandidates = filtered;
        return filtered;
    }

    private static boolean candidatesBelongToCatalog(List<MachineRecipe> candidates, MachineRecipeCatalog catalog) {
        return candidates != null && candidates.stream().allMatch(catalog.orderedRecipes()::contains);
    }

    private @Nullable Set<Item> currentInputItems() {
        Set<Item> items = new LinkedHashSet<>();
        boolean supported = false;
        for (MachineCapability capability : controller.componentRuntime().capabilities()) {
            if (capability == null || capability.ioType() != IOType.INPUT
                    || !(capability.storage() instanceof ResourceStorage<?> storage)
                    || storage.resourceType() != ItemResource.class) continue;
            supported = true;
            for (int slot = 0; slot < storage.size(); slot++) {
                Object resource = storage.resource(slot);
                if (resource instanceof ItemResource item && !item.isEmpty()) {
                    items.add(item.toStack(1).getItem());
                }
            }
        }
        return supported && !items.isEmpty() ? items : null;
    }

    private RecipeSearchContextKey currentSearchContextKey(FactoryRecipeThread lane,
                                                            @Nullable Identifier lockedRecipeId) {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        Identifier machineId = machine == null ? null : machine.registryName();
        return new RecipeSearchContextKey(snapshot.structure().version(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), snapshot.stateVersion(), RecipeRegistry.catalog(machineId).version(),
                lane.searchResourceEpoch(controller.resourceAvailabilityEpoch()), lockedRecipeId,
                lane.coreRecipeSetVersion());
    }

    private static RecipeSearchContextKey searchContextKey(FactorySearchContext context,
                                                           FactoryRecipeThread lane,
                                                           @Nullable Identifier lockedRecipeId) {
        return new RecipeSearchContextKey(context.snapshot().structure().version(), context.snapshot().capabilityVersion(),
                context.snapshot().modifierVersion(), context.snapshot().stateVersion(), context.catalogVersion(),
                lane.searchResourceEpoch(context.resourceAvailabilityEpoch()), lockedRecipeId,
                lane.coreRecipeSetVersion());
    }

    private long currentGameTime() {
        if (controller == null || controller.getLevel() == null) return 0L;
        return controller.getLevel().getGameTime();
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

    private static List<MachineRecipe> filterAvailableCandidates(List<MachineRecipe> candidates,
                                                                  Map<Identifier, Integer> activeCounts) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        boolean filteringRequired = false;
        for (MachineRecipe recipe : candidates) {
            if (recipe == null || (recipe.maxThreads() > 0
                    && activeCounts.getOrDefault(recipe.id(), 0) >= recipe.maxThreads())) {
                filteringRequired = true;
                break;
            }
        }
        if (!filteringRequired) return candidates;
        List<MachineRecipe> filtered = new ArrayList<>(candidates.size());
        for (MachineRecipe recipe : candidates) {
            if (recipe != null && (recipe.maxThreads() <= 0
                    || activeCounts.getOrDefault(recipe.id(), 0) < recipe.maxThreads())) {
                filtered.add(recipe);
            }
        }
        return filtered.isEmpty() ? List.of() : List.copyOf(filtered);
    }

    private void reserveStart(FactoryRecipeThread lane, boolean started,
                              Map<Identifier, Integer> activeCounts) {
        Identifier previous = startReservations.remove(lane);
        if (previous != null) {
            activeCounts.computeIfPresent(previous, (ignored, count) -> count <= 1 ? null : count - 1);
        }
        if (!started) return;
        MachineRecipe pending = lane.getPendingStartRecipe();
        Identifier recipeId = pending == null || pending.id() == null
                ? lane.runtime().recipe() == null ? null : lane.runtime().recipe().id() : pending.id();
        if (recipeId == null) return;
        if (pending != null) startReservations.put(lane, recipeId);
        activeCounts.merge(recipeId, 1, Integer::sum);
    }

    private void clearFinishedContinuations() {
        for (FactoryRecipeThread lane : lanes) {
            if (!lane.isStartPending() && !lane.runtime().active()) lane.setFinishContinuation(null);
        }
    }

    private void addLane(FactoryRecipeThread lane) {
        lanes.add(lane);
        markLaneStateChanged();
    }

    private void removeLane(FactoryRecipeThread lane) {
        if (!lanes.contains(lane)) return;
        lane.setFinishContinuation(null);
        lane.invalidate();
        lanes.remove(lane);
        removeLaneState(lane);
        readyLanes.remove(lane);
        markLaneStateChanged();
    }

    private void removeLaneState(FactoryRecipeThread lane) {
        recipeLocks.remove(lane);
        recipeLockUsed.remove(lane);
        startReservations.remove(lane);
    }

    public void markLaneRuntimeChanged(CraftingRuntime runtime) {
        if (runtime == null) return;
        for (FactoryRecipeThread lane : lanes) {
            if (lane.runtime() == runtime) {
                markLaneStateChanged();
                return;
            }
        }
    }

    public void recomputeFailure() {
        failureDirty = true;
        recomputeFailureIfDirty();
    }

    private void recomputeFailureIfDirty() {
        if (!failureDirty) return;
        failureDirty = false;
        ExecutionStatus next = null;
        for (FactoryRecipeThread lane : lanes) {
            ExecutionStatus laneFailure = lane.runtime().failure();
            if (laneFailure != null) {
                next = laneFailure;
                break;
            }
        }
        if (!Objects.equals(failure, next)) {
            failure = next;
            factoryStateEpoch++;
            if (controller != null) controller.syncFactoryFailure(failure);
        }
    }

    private void markFinishedLaneReady(FactoryRecipeThread lane) {
        if (readyLanes.add(lane)) markLaneStateChanged();
    }

    private void markLaneStateChanged() {
        activeCountDirty = true;
        failureDirty = true;
        factoryStateEpoch++;
    }

    private LaneObservation observe(FactoryRecipeThread lane) {
        return new LaneObservation(lane.runtime().snapshot(), lane.getStatus(), lane.isStartPending(),
                lane.getPendingStartRecipe(), recipeLocks.get(lane));
    }

    private FactoryTickResult currentTickResult(long initialEpoch, boolean laneStateChanged) {
        recomputeFailureIfDirty();
        return new FactoryTickResult(activeLaneCount(), failure, laneStateChanged,
                initialEpoch != factoryStateEpoch);
    }

    private record LaneObservation(CraftingStateSnapshot runtime, RecipeThread.Status status,
                                   boolean startPending, @Nullable MachineRecipe pendingRecipe,
                                   @Nullable Identifier lockedRecipe) {
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
