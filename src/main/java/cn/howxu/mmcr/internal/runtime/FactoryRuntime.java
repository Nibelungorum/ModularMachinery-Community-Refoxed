package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickResult;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.machine.FactoryThreadSpec;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeCatalog;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.recipe.FactorySearchContext;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.internal.recipe.EffectiveRecipeSet;
import cn.howxu.mmcr.internal.recipe.RecipeThread;
import cn.howxu.mmcr.internal.recipe.RecipeSearchContextKey;
import cn.howxu.mmcr.internal.async.AsyncContinuation;
import cn.howxu.mmcr.internal.async.AsyncExecutionContext;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.async.MainThreadStep;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.Collections;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns factory lanes, their execution state, and their published snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRuntime {
    public static final int MAX_LANES = 1024;
    private final List<FactoryRecipeThread> lanes = new ArrayList<>();
    private final Map<FactoryRecipeThread, ResourceLocation> recipeLocks = new IdentityHashMap<>();
    private final Set<FactoryRecipeThread> recipeLockUsed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<FactoryRecipeThread, ResourceLocation> startReservations = new IdentityHashMap<>();
    private final Set<FactoryRecipeThread> patternStartReservations = Collections.newSetFromMap(new IdentityHashMap<>());
    private int laneLimit = 1;
    private long perThreadParallelLimit = 1L;
    private boolean paused;
    private long nextFactoryLaneId;
    private long coreCatalogVersion = Long.MIN_VALUE;
    private @Nullable Machine syncedCoreMachine;
    private @Nullable ResourceLocation syncedCoreRecipePoolId;
    private @Nullable MachineControllerBlockEntity controller;
    private @Nullable ExecutionStatus failure;
    private long searchAttemptsForTesting;
    private long asyncSearchScansForTesting;
    private boolean failureDirty = true;
    private long factoryStateEpoch;
    private int cachedActiveLaneCount;
    private long cachedSnapshotEpoch = Long.MIN_VALUE;
    private @Nullable FactorySnapshot cachedSnapshot;
    private final Map<FactoryRecipeThread, LanePresentationCache> lanePresentationCaches = new IdentityHashMap<>();
    private int presentationBuildCountForTesting;
    private final Set<FactoryRecipeThread> readyLanes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<FactoryRecipeThread, AsyncSearchRequest> pendingAsyncSearches = new IdentityHashMap<>();
    private long nextAsyncSearchId;
    private List<MachineRecipe> cachedOrderedCandidateSource = List.of();
    private long cachedOrderedCandidateCatalogVersion = Long.MIN_VALUE;
    private List<MachineRecipe> cachedOrderedCandidates = List.of();
    private List<MachineRecipe> cachedIndexedCandidateSource = List.of();
    private long cachedIndexedCandidateCatalogVersion = Long.MIN_VALUE;
    private Set<Item> cachedIndexedInputItems = Set.of();
    private Set<ResourceLocation> cachedIndexedLockedRecipeIds = Set.of();
    private List<MachineRecipe> cachedIndexedCandidates = List.of();
    private @Nullable ResourceLocation cachedCandidateRecipePoolId;
    private final EffectiveRecipeSet.Cache effectiveRecipeCache = new EffectiveRecipeSet.Cache();

    public FactoryTickResult tick(List<MachineRecipe> candidates, long maxParallelism) {
        return tick(candidates, maxParallelism, currentGameTime());
    }

    /** Drops deferred lane work when the owning controller changes execution lifecycle. */
    public void cancelAsyncState() {
        for (FactoryRecipeThread lane : lanes) {
            lane.cancelAsyncState();
            if (patternStartReservations.remove(lane)) lane.runtime().releasePatternStart();
        }
        startReservations.clear();
        readyLanes.clear();
        pendingAsyncSearches.clear();
    }

    public void discardForRecipePoolChange() {
        boolean changed = !recipeLocks.isEmpty() || !recipeLockUsed.isEmpty() || !startReservations.isEmpty()
                || !patternStartReservations.isEmpty() || !readyLanes.isEmpty() || !pendingAsyncSearches.isEmpty();
        for (FactoryRecipeThread lane : lanes) {
            LaneObservation before = observe(lane);
            lane.discardForRecipePoolChange();
            changed |= !before.equals(observe(lane));
        }
        recipeLocks.clear();
        recipeLockUsed.clear();
        startReservations.clear();
        patternStartReservations.clear();
        readyLanes.clear();
        pendingAsyncSearches.clear();
        clearCandidateCaches();
        failureDirty = true;
        recomputeFailureIfDirty();
        if (changed) markLaneStateChanged();
    }

    public FactoryTickResult tick(List<MachineRecipe> candidates, long maxParallelism, Runnable onFinished) {
        return tick(candidates, maxParallelism, onFinished, currentGameTime());
    }

    public FactoryTickResult tick(List<MachineRecipe> candidates, long maxParallelism, long gameTime) {
        return tick(candidates, maxParallelism, () -> { }, gameTime);
    }

    public FactoryTickResult tick(List<MachineRecipe> candidates, long maxParallelism, long gameTime, Runnable onFinished) {
        return tick(candidates, maxParallelism, onFinished, gameTime);
    }

    public FactoryTickResult tick(List<MachineRecipe> candidates, long maxParallelism, Runnable onFinished, long gameTime) {
        if (controller == null) return currentTickResult(factoryStateEpoch, false);
        return tick(controller.currentRuntimeSnapshot(), candidates, maxParallelism, gameTime, onFinished);
    }

    public FactoryTickResult tick(ControllerRuntimeSnapshot snapshot, List<MachineRecipe> candidates,
                                  long maxParallelism, long gameTime, Runnable onFinished) {
        long initialEpoch = factoryStateEpoch;
        if (paused || controller == null) return currentTickResult(initialEpoch, false);
        if (!requiresFullTick(snapshot, candidates, maxParallelism, gameTime)) return tickIdleBaseRuntime(initialEpoch);
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
        Map<FactoryRecipeThread, LaneObservation> observations = new IdentityHashMap<>(laneSnapshot.size());
        Map<ResourceLocation, Integer> analyzedActiveCounts = new HashMap<>(Math.max(16, laneSnapshot.size() * 2));
        boolean hasIdleLane = false;
        for (FactoryRecipeThread lane : laneSnapshot) {
            observations.put(lane, observe(lane));
            if (!lane.isStartPending() && !lane.runtime().active()) startReservations.remove(lane);
            if (lane.runtime().active()) startReservations.remove(lane);
            lane.setFinishContinuation(() -> {
                try {
                    finishCallback.run();
                } finally {
                    // Releasing a shared lane may unblock output capacity for another lane.
                    controller.notifyResourceAvailability(ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY, null);
                    int laneIndex = lanes.indexOf(lane);
                    boolean withinLaneLimit = laneIndex >= 0 && laneIndex < laneLimit;
                    boolean restarted = withinLaneLimit && (controller.activeWorkMode() == MachineWorkMode.ASYNC
                            ? lane.prepareAsyncFinishRestart(context, context.orderedCandidates(), perThreadParallelLimit,
                            structureVersion, capabilityVersion, modifierVersion, componentStateVersion,
                            recipeLocks.get(lane))
                            : lane.tryRestartLastRecipe(context, context.orderedCandidates(), perThreadParallelLimit,
                            structureVersion, capabilityVersion, modifierVersion, componentStateVersion,
                            recipeLocks.get(lane)));
                    if (restarted) {
                        markLaneStateChanged();
                    }
                    markFinishedLaneReady(lane);
                }
            });
            lane.setSearchGameTime(gameTime);
            lane.setSearchContextKey(searchContextKey(context, lane, recipeLocks.get(lane)));
            lane.tick(context.snapshot());
            accumulateActiveRecipeCount(lane, analyzedActiveCounts);
            if (!pendingAsyncSearches.containsKey(lane) && !patternStartReservations.contains(lane) && lane.isIdle()) {
                hasIdleLane = true;
            }
        }
        LaneAnalysis analysis = new LaneAnalysis(observations, analyzedActiveCounts);
        Map<ResourceLocation, Integer> activeCounts = analysis.activeRecipeCounts();
        trimLanesToLimit();
        if (laneSnapshot.size() != lanes.size()) {
            laneSnapshot = List.copyOf(lanes);
            hasIdleLane = laneSnapshot.stream().anyMatch(lane -> !pendingAsyncSearches.containsKey(lane)
                    && !patternStartReservations.contains(lane) && lane.isIdle());
        }

        Set<FactoryRecipeThread> readyThisTick = Collections.newSetFromMap(new IdentityHashMap<>());
        for (FactoryRecipeThread lane : laneSnapshot) {
            if (readyLanes.remove(lane)) readyThisTick.add(lane);
        }

        boolean asyncSearchTick = controller.activeWorkMode() == MachineWorkMode.ASYNC
                && controller.getLevel() instanceof ServerLevel && controller.resourceDomain() != null
                && context.gameTime() == controller.getLevel().getGameTime();
        if (asyncSearchTick) {
            if (hasIdleLane || lanes.size() < laneLimit) {
                scheduleAsyncSearches(context, laneSnapshot, activeCounts, (ServerLevel) controller.getLevel());
            }
        } else if (!context.orderedCandidates().isEmpty()) {
            for (FactoryRecipeThread lane : laneSnapshot) {
                if (patternStartReservations.contains(lane) || !lane.isIdle()) continue;
                List<MachineRecipe> available = filterAvailableCandidates(context.orderedCandidates(), activeCounts);
                if (available.isEmpty()) break;
                ResourceLocation lock = recipeLocks.get(lane);
                RecipeSearchContextKey key = searchContextKey(context, lane, lock);
                if (!lane.canSearch(gameTime, key)) continue;
                lane.setSearchGameTime(gameTime);
                lane.setSearchContextKey(key);
                boolean restarted = lane.tryRestartLastRecipe(context, available, perThreadParallelLimit, structureVersion,
                        capabilityVersion, modifierVersion, componentStateVersion, lock);
                if (restarted) {
                    reserveStart(lane, true, activeCounts);
                    readyThisTick.remove(lane);
                    continue;
                }
                searchAttemptsForTesting++;
                reserveStart(lane, searchAndStartRecipe(lane, context, available, structureVersion, lock), activeCounts);
                readyThisTick.remove(lane);
            }
            while (lanes.size() < laneLimit && perThreadParallelLimit > 0) {
                List<MachineRecipe> available = filterAvailableCandidates(context.orderedCandidates(), activeCounts);
                if (available.isEmpty()) break;
                FactoryRecipeThread lane = FactoryRecipeThread.simple(controller, "factory-" + nextFactoryLaneId++);
                addLane(lane);
                ResourceLocation lock = recipeLocks.get(lane);
                RecipeSearchContextKey key = searchContextKey(context, lane, lock);
                if (!lane.canSearch(gameTime, key)) break;
                lane.setSearchGameTime(gameTime);
                lane.setSearchContextKey(key);
                searchAttemptsForTesting++;
                boolean started = searchAndStartRecipe(lane, context, available, structureVersion, lock);
                reserveStart(lane, started, activeCounts);
                if (!started) break;
            }
        }

        for (FactoryRecipeThread lane : laneSnapshot) {
            lane.tickIdle(gameTime);
            if (lane.isTimedOut(recipeLockUsed.contains(lane))) removeLane(lane);
        }
        if (lanes.stream().noneMatch(lane -> lane.runtime().active() || lane.isStartPending())) {
            CraftingRuntime baseRuntime = lanes.isEmpty() ? null : lanes.getFirst().runtime();
            if (baseRuntime != null) baseRuntime.tickIdle();
        }
        trimLanesToLimit();
        clearFinishedContinuations();
        Set<FactoryRecipeThread> currentLanes = Collections.newSetFromMap(new IdentityHashMap<>(lanes.size()));
        currentLanes.addAll(lanes);
        for (Map.Entry<FactoryRecipeThread, LaneObservation> entry : analysis.observations().entrySet()) {
            if (currentLanes.contains(entry.getKey()) && !entry.getValue().equals(observe(entry.getKey()))) {
                markLaneStateChanged();
            }
        }
        recomputeFailureIfDirty();
        int activeLaneCount = activeLaneCount();
        return new FactoryTickResult(activeLaneCount, failure,
                initialEpoch != factoryStateEpoch, initialEpoch != factoryStateEpoch);
    }

    private static boolean searchAndStartRecipe(FactoryRecipeThread lane, FactorySearchContext context,
                                                List<MachineRecipe> candidates, long structureVersion,
                                                @Nullable ResourceLocation lockedRecipeId) {
        List<MachineRecipe> eligible = candidates.stream()
                .filter(candidate -> capturedStageFailure(context.snapshot(), candidate) == null)
                .toList();
        if (!eligible.isEmpty()) {
            return lane.searchAndStartRecipe(context, eligible, structureVersion, lockedRecipeId);
        }
        Machine machine = context.snapshot().structure().machine() == null
                ? context.snapshot().structure().configuredMachine() : context.snapshot().structure().machine();
        if (machine == null) return false;
        List<RecipeSearchTask.PlanningValue> failures = candidates.stream()
                .map(candidate -> RecipeSearchTask.PlanningValue.failure(candidate.id(),
                        capturedStageFailure(context.snapshot(), candidate), 0, false))
                .toList();
        RecipeSearchResult result = RecipeSearchTask.forPlanningValues(context.snapshot(), machine.registryName(),
                structureVersion, context.maxParallelism(), candidates, lockedRecipeId, failures).compute();
        return lane.startSearchResult(context, candidates, structureVersion, lockedRecipeId,
                new FactoryRecipeThread.SearchResult(result, null, false));
    }

    private void scheduleAsyncSearches(FactorySearchContext context, List<FactoryRecipeThread> laneSnapshot,
                                       Map<ResourceLocation, Integer> activeCounts, ServerLevel level) {
        asyncSearchScansForTesting++;
        if (context.orderedCandidates().isEmpty() || context.maxParallelism() <= 0L) return;
        for (FactoryRecipeThread lane : laneSnapshot) {
            scheduleAsyncSearch(context, lane, activeCounts, level);
        }
        if (activeCounts.isEmpty() && startReservations.isEmpty() && patternStartReservations.isEmpty()) return;
        while (lanes.size() < laneLimit) {
            FactoryRecipeThread lane = FactoryRecipeThread.simple(controller, "factory-" + nextFactoryLaneId++);
            addLane(lane);
            if (!scheduleAsyncSearch(context, lane, activeCounts, level)
                    && !lane.isStartPending() && !lane.runtime().active()) break;
        }
    }

    private boolean scheduleAsyncSearch(FactorySearchContext context, FactoryRecipeThread lane,
                                        Map<ResourceLocation, Integer> activeCounts, ServerLevel level) {
        if (pendingAsyncSearches.containsKey(lane) || patternStartReservations.contains(lane) || !lane.isIdle()) {
            return false;
        }
        ResourceLocation lock = recipeLocks.get(lane);
        RecipeSearchContextKey key = searchContextKey(context, lane, lock);
        if (!lane.canSearch(context.gameTime(), key)) return false;
        List<MachineRecipe> available = filterAvailableCandidates(context.orderedCandidates(), activeCounts);
        List<MachineRecipe> candidates = lane.candidatesFor(available, context.catalogVersion());
        if (candidates.isEmpty()) return false;
        if (lane.tryRestartLastRecipe(context, candidates, perThreadParallelLimit,
                context.snapshot().structure().version(), context.snapshot().capabilityVersion(),
                context.snapshot().modifierVersion(), context.snapshot().stateVersion(), lock)) {
            reserveStart(lane, true, activeCounts);
            markLaneStateChanged();
            return false;
        }
        WorkerSearchRequest workerRequest;
        try {
            workerRequest = captureWorkerSearch(context, candidates, lock);
        } catch (RuntimeException exception) {
            searchAttemptsForTesting++;
            boolean started = lane.startSearchResult(context, candidates, context.snapshot().structure().version(), lock,
                    FactoryRecipeThread.search(context, candidates, context.snapshot().structure().version(), lock));
            reserveStart(lane, started, activeCounts);
            if (started) markLaneStateChanged();
            return false;
        }
        AsyncSearchRequest request = new AsyncSearchRequest(context, candidates, lock, ++nextAsyncSearchId, workerRequest);
        MachineAsyncCoordinator.TaskKey taskKey = new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(),
                level.getGameTime(), MachineWorkMode.ASYNC, "factory-search/" + lane.laneId(), controller.lifecycleEpoch());
        pendingAsyncSearches.put(lane, request);
        MachineAsyncCoordinator.SubmissionResult submission = MachineAsyncCoordinator.get(level).submitDetailed(taskKey,
                new FactorySearchContinuation(lane.laneId(), context.catalogVersion(), request.searchId(), workerRequest),
                this::executeAsyncSearchStep,
                new MachineAsyncCoordinator.TaskHooks(
                        () -> taskKey.lifecycleEpoch() == controller.lifecycleEpoch()
                                && pendingAsyncSearches.get(lane) == request,
                        (ignored, outcome) -> {
                            if (!(outcome instanceof MachineAsyncCoordinator.TaskOutcome.Succeeded)) {
                                pendingAsyncSearches.remove(lane, request);
                            }
                        }));
        if (submission != MachineAsyncCoordinator.SubmissionResult.ACCEPTED) {
            pendingAsyncSearches.remove(lane, request);
            return false;
        }
        searchAttemptsForTesting++;
        return true;
    }

    private MainThreadStep.Result executeAsyncSearchStep(MachineAsyncCoordinator.TaskKey taskKey, MainThreadStep step) {
        if (!(step instanceof MainThreadStep.FactorySearch(String laneId, long catalogVersion, long searchId))) {
            return MainThreadStep.Result.failure(new IllegalArgumentException("Unexpected factory async step"));
        }
        FactoryRecipeThread lane = lanes.stream().filter(candidate -> candidate.laneId().equals(laneId))
                .findFirst().orElse(null);
        AsyncSearchRequest request = lane == null ? null : pendingAsyncSearches.get(lane);
        if (lane == null || request == null || request.searchId() != searchId
                || !asyncSearchStillValid(lane, request, catalogVersion)) {
            if (lane != null) pendingAsyncSearches.remove(lane);
            return MainThreadStep.Result.failure(new IllegalStateException("Factory async search became stale"));
        }
        ServerLevel level = (ServerLevel) controller.getLevel();
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (domain == null) {
            pendingAsyncSearches.remove(lane);
            return MainThreadStep.Result.failure(new IllegalStateException("Factory search lost its resource domain"));
        }
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.TickRequest(domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), lane.laneId()),
                request.context().snapshot().structure().version(), request.context().snapshot().stateVersion(), () -> {
                    pendingAsyncSearches.remove(lane);
                    Map<ResourceLocation, Integer> activeCounts = activeRecipeCounts();
                    List<MachineRecipe> available = filterAvailableCandidates(request.candidates(), activeCounts);
                    FactoryRecipeThread.SearchResult result = request.workerRequest().result();
                    if (result == null || result.result() != null && result.result().success()
                            && !available.contains(result.result().recipe())) return false;
                    boolean started = lane.startSearchResult(request.context(), available,
                            request.context().snapshot().structure().version(), request.lockedRecipeId(), result);
                    reserveStart(lane, started, activeCounts);
                    if (started) markLaneStateChanged();
                    return true;
                }, () -> validateAsyncSearchRequest(lane, request, catalogVersion, taskKey, level),
                () -> controller.currentRuntimeSnapshot().structure().version(),
                () -> controller.currentRuntimeSnapshot().stateVersion(), catalogVersion,
                this::currentCatalogVersion, () -> MachineAsyncCoordinator.get(level).resume(taskKey)));
        return MainThreadStep.Result.pending();
    }

    private boolean validateAsyncSearchRequest(FactoryRecipeThread lane, AsyncSearchRequest request, long catalogVersion,
                                               MachineAsyncCoordinator.TaskKey taskKey, ServerLevel level) {
        boolean valid = asyncSearchStillValid(lane, request, catalogVersion);
        if (!valid) {
            pendingAsyncSearches.remove(lane, request);
            MachineAsyncCoordinator.get(level).resume(taskKey);
        }
        return valid;
    }

    private boolean asyncSearchStillValid(FactoryRecipeThread lane, AsyncSearchRequest request, long catalogVersion) {
        if (controller == null || controller.isRedstonePaused() || !lanes.contains(lane) || !lane.isIdle()
                || pendingAsyncSearches.get(lane) != request || catalogVersion != request.context().catalogVersion()
                || catalogVersion != currentCatalogVersion()) return false;
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        return current.structure().version() == request.context().snapshot().structure().version()
                && current.capabilityVersion() == request.context().snapshot().capabilityVersion()
                && current.modifierVersion() == request.context().snapshot().modifierVersion()
                && current.stateVersion() == request.context().snapshot().stateVersion()
                && Objects.equals(recipeLocks.get(lane), request.lockedRecipeId());
    }

    private long currentCatalogVersion() {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        return RecipeRegistry.catalogForMachine(machine).version();
    }

    private WorkerSearchRequest captureWorkerSearch(FactorySearchContext context,
                                                     List<MachineRecipe> candidates,
                                                     @Nullable ResourceLocation lockedRecipeId) {
        return new WorkerSearchRequest(AsyncRequirementPlanner.captureRecipeSearch(context.snapshot(), candidates,
                context.maxParallelism(), lockedRecipeId, context.capabilities(), context.modifiers(),
                context.catalogVersion(), effectiveRecipeCache));
    }

    private record AsyncSearchRequest(FactorySearchContext context, List<MachineRecipe> candidates,
                                      @Nullable ResourceLocation lockedRecipeId, long searchId,
                                      WorkerSearchRequest workerRequest) {
    }

    /** Wraps the shared immutable request with its factory-lane result. */
    private static final class WorkerSearchRequest {
        private final AsyncRequirementPlanner.RecipeSearchRequest request;
        private volatile @Nullable FactoryRecipeThread.SearchResult result;

        private WorkerSearchRequest(AsyncRequirementPlanner.RecipeSearchRequest request) {
            this.request = request;
        }

        private @Nullable FactoryRecipeThread.SearchResult result() {
            return result;
        }
    }

    private static final class FactorySearchContinuation implements AsyncContinuation {
        private final String laneId;
        private final long catalogVersion;
        private final long searchId;
        private final WorkerSearchRequest request;
        private boolean planned;

        private FactorySearchContinuation(String laneId, long catalogVersion, long searchId, WorkerSearchRequest request) {
            this.laneId = laneId;
            this.catalogVersion = catalogVersion;
            this.searchId = searchId;
            this.request = request;
        }

        @Override
        public Yield advance(AsyncExecutionContext context) {
            if (!planned) {
                planned = true;
                AsyncRequirementPlanner.RecipeSearchResult result = request.request.search();
                request.result = new FactoryRecipeThread.SearchResult(result.result(), result.failure(),
                        result.requiresMainThreadReplan());
            }
            return Yield.mainThread(new MainThreadStep.FactorySearch(laneId, catalogVersion, searchId),
                    ignored -> ignoredContext -> Yield.complete());
        }

    }


    private static @Nullable FailureReason capturedStageFailure(ControllerRuntimeSnapshot snapshot,
                                                                 MachineRecipe recipe) {
        FailureReason failure = AsyncRequirementPlanner.capturedRequirementFailure(snapshot, recipe);
        return failure == BuiltinFailureReasons.STAGE_INSUFFICIENT ? failure : null;
    }

    public boolean syncCoreLanes(MachineControllerBlockEntity controller, Machine machine,
                                 List<MachineRecipe> candidates) {
        long initialEpoch = factoryStateEpoch;
        ensureBaseLane(controller);
        long catalogVersion = RecipeRegistry.catalogForMachine(machine).version();
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        Map<ResourceLocation, MachineRecipe> byId = new LinkedHashMap<>();
        for (MachineRecipe recipe : candidates == null ? List.<MachineRecipe>of() : candidates) {
            if (recipe != null && recipePoolId != null && recipePoolId.equals(recipe.recipePoolId())) {
                byId.putIfAbsent(recipe.id(), recipe);
            }
        }
        clearInvalidRecipeLocks(machine);

        Map<String, List<FactoryRecipeThread>> existingCoreLanes = new LinkedHashMap<>();
        List<FactoryRecipeThread> dynamicLanes = new ArrayList<>();
        for (FactoryRecipeThread lane : lanes) {
            if (lane.isBaseThread()) continue;
            if (lane.isCoreThread()) existingCoreLanes
                    .computeIfAbsent(lane.threadName(), ignored -> new ArrayList<>()).add(lane);
            else dynamicLanes.add(lane);
        }

        List<FactoryRecipeThread> reconciled = new ArrayList<>();
        reconciled.add(lanes.getFirst());
        if (machine != null) {
            Map<String, Integer> coreOccurrences = new LinkedHashMap<>();
            for (FactoryThreadSpec spec : machine.factoryThreads()) {
                Set<MachineRecipe> recipes = new LinkedHashSet<>();
                for (ResourceLocation id : spec.recipeIds()) {
                    MachineRecipe recipe = byId.get(id);
                    if (recipe != null) recipes.add(recipe);
                }
                int occurrence = coreOccurrences.merge(spec.name(), 1, Integer::sum) - 1;
                List<FactoryRecipeThread> matchingLanes = existingCoreLanes.get(spec.name());
                FactoryRecipeThread lane = matchingLanes == null || matchingLanes.isEmpty()
                        ? null : matchingLanes.removeFirst();
                if (lane == null) {
                    String laneId = "core-" + spec.name() + (occurrence == 0 ? "" : "-" + occurrence);
                    lane = FactoryRecipeThread.core(controller, spec.name(), recipes, laneId);
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
        for (List<FactoryRecipeThread> removed : existingCoreLanes.values()) {
            for (FactoryRecipeThread lane : removed) removeLane(lane);
        }
        reconciled.addAll(dynamicLanes);
        if (!lanes.equals(reconciled)) {
            lanes.clear();
            lanes.addAll(reconciled);
            markLaneStateChanged();
        }
        setLaneLimit(laneLimit);
        trimLanesToLimit();
        coreCatalogVersion = catalogVersion;
        syncedCoreMachine = machine;
        syncedCoreRecipePoolId = recipePoolId;
        return initialEpoch != factoryStateEpoch;
    }

    public boolean syncCoreLanesIfNeeded(MachineControllerBlockEntity controller, Machine machine,
                                         List<MachineRecipe> candidates) {
        long catalogVersion = RecipeRegistry.catalogForMachine(machine).version();
        if (lanes.isEmpty() || syncedCoreMachine != machine || coreCatalogVersion != catalogVersion
                || !Objects.equals(syncedCoreRecipePoolId, controller.currentRecipePoolId())) {
            return syncCoreLanes(controller, machine, candidates);
        }
        return false;
    }

    public boolean ensureBaseLane(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        if (!lanes.isEmpty() && lanes.getFirst().isBaseThread()) return false;
        for (FactoryRecipeThread lane : List.copyOf(lanes)) {
            if (lane.isBaseThread()) removeLane(lane);
        }
        lanes.addFirst(FactoryRecipeThread.base(controller));
        markLaneStateChanged();
        return true;
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

    long asyncSearchScansForTesting() {
        return asyncSearchScansForTesting;
    }

    public long stateEpoch() {
        return factoryStateEpoch;
    }

    public int activeLaneCount() {
        int count = 0;
        for (FactoryRecipeThread lane : lanes) {
            if (lane.runtime().active()) count++;
        }
        cachedActiveLaneCount = count;
        return cachedActiveLaneCount;
    }

    public List<MachineRecipe> availableCandidates(List<MachineRecipe> candidates) {
        if (controller == null || candidates == null || candidates.isEmpty()) return List.of();
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        List<MachineRecipe> machineCandidates = candidatesForPool(nonNullCandidates(candidates),
                controller.currentRecipePoolId());
        return filterAvailableCandidates(machineCandidates, activeRecipeCounts());
    }

    public boolean toggleRecipeLock(int index) {
        if (index < 0 || index >= lanes.size()) return false;
        FactoryRecipeThread lane = lanes.get(index);
        ResourceLocation current = recipeLocks.remove(lane);
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
            ResourceLocation lockedRecipe = recipeLocks.get(lane);
            snapshots.add(new ThreadSnapshot(index, lane.laneId(), lane.isBaseThread(), lane.isCoreThread(), lane.runtime().active(),
                    state.recipeId() == null ? "" : state.recipeId().toString(), lane.runtime().tickCount(),
                    lane.runtime().totalTick(), lane.runtime().active() ? lane.runtime().parallelism() : 1,
                    state.failure(), lockedRecipe != null,
                    lockedRecipe == null ? "" : lockedRecipe.toString(),
                    presentationFor(lane)));
        }
        while (snapshots.size() < laneLimit) {
            int index = snapshots.size();
            snapshots.add(new ThreadSnapshot(index, "idle-" + index, false, false, false,
                    "", 0, 0, 1, null, false, "",
                    ControllerRecipePresentation.empty()));
        }
        return List.copyOf(snapshots);
    }

    int presentationBuildCountForTesting() {
        return presentationBuildCountForTesting;
    }

    public Map<String, ControllerScreenTextSnapshot> screenTextSnapshots() {
        if (controller == null) return Map.of();
        Map<String, ControllerScreenTextSnapshot> snapshots = new LinkedHashMap<>();
        for (FactoryRecipeThread lane : lanes) {
            snapshots.put(lane.laneId(), controller.recipeScreenText(lane.laneId()).snapshot());
        }
        return Map.copyOf(snapshots);
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
        int matchedStage = 0;
        int stageCount = 1;
        if (controller != null) {
            ControllerRuntimeSnapshot runtimeSnapshot = controller.runtimeSnapshot();
            matchedStage = runtimeSnapshot.structure().matchedStage();
            Machine machine = runtimeSnapshot.structure().machine() == null
                    ? runtimeSnapshot.structure().configuredMachine()
                    : runtimeSnapshot.structure().machine();
            if (machine != null) {
                List<MachineStructureStage> stages = machine.structureStages();
                stageCount = stages == null || stages.isEmpty() ? 1 : stages.size();
            }
        }
        cachedSnapshot = new FactorySnapshot(false, activeCount > 0, laneSnapshots, laneLimit,
                activeCount, Math.max(1L, perThreadParallelLimit), paused, threadSnapshots(), "", 0, failure,
                List.of(), matchedStage, stageCount);
        cachedSnapshotEpoch = factoryStateEpoch;
        return cachedSnapshot;
    }

    public void pause() {
        if (paused) return;
        paused = true;
        pendingAsyncSearches.clear();
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

    /** Reserves an idle lane after its runtime has successfully planned the requested pattern inputs. */
    public @Nullable PatternLane reservePatternStart(MachineRecipe recipe, long requestedParallelism,
                                                     List<MachineCapability> requestCapabilities) {
        if (recipe == null || controller == null || paused) return null;
        EffectiveRecipe effective = effectiveRecipe(recipe);
        if (effective.recipeThreadLimit() > 0) {
            int activeForRecipe = activeRecipeCountFor(recipe.id());
            long reservedForRecipe = patternStartReservations.stream()
                    .filter(lane -> recipe.id().equals(lane.runtime().pendingPatternRecipeId())).count();
            long projected = activeForRecipe + 1L + reservedForRecipe;
            if (projected > effective.recipeThreadLimit()) return null;
        }
        for (FactoryRecipeThread lane : lanes) {
            PatternLane reservation = reservePatternStart(lane, recipe, requestedParallelism, requestCapabilities);
            if (reservation != null) return reservation;
        }
        if (lanes.size() >= laneLimit) return null;
        FactoryRecipeThread lane = FactoryRecipeThread.simple(controller, "factory-" + nextFactoryLaneId++);
        PatternLane reservation = reservePatternStart(lane, recipe, requestedParallelism, requestCapabilities);
        if (reservation == null) return null;
        addLane(lane);
        return reservation;
    }

    /** Reserves enough idle lanes to cover one pattern batch, or releases every partial reservation. */
    public List<PatternLane> reservePatternStarts(MachineRecipe recipe, long requestedParallelism,
                                                  List<MachineCapability> requestCapabilities) {
        if (requestedParallelism <= 0L) return List.of();
        long maxParallelism = controller.currentRuntimeSnapshot().maxParallelism();
        List<PatternLane> reservations = new ArrayList<>();
        long remaining = requestedParallelism;
        while (remaining > 0L) {
            PatternLane reservation = reservePatternStart(recipe, Math.min(remaining, maxParallelism), requestCapabilities);
            if (reservation == null) {
                reservations.forEach(this::releasePatternStart);
                return List.of();
            }
            reservations.add(reservation);
            remaining -= reservation.preparedStart().plan().parallelism();
        }
        return List.copyOf(reservations);
    }

    public void releasePatternStart(PatternLane reservation) {
        if (reservation == null) return;
        for (FactoryRecipeThread lane : lanes) {
            if (lane.runtime() != reservation.runtime()) continue;
            patternStartReservations.remove(lane);
            lane.runtime().releasePatternStart();
            markLaneStateChanged();
            return;
        }
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
                    .filter(lane -> !lane.runtime().active() && !lane.isStartPending()
                            && !patternStartReservations.contains(lane))
                    .min(Comparator.comparingLong(lane -> lane.runtime().parallelism()))
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
                || !startReservations.isEmpty() || !patternStartReservations.isEmpty()
                || coreCatalogVersion != Long.MIN_VALUE || failure != null;
        for (FactoryRecipeThread lane : List.copyOf(lanes)) removeLane(lane);
        recipeLocks.clear();
        recipeLockUsed.clear();
        startReservations.clear();
        patternStartReservations.clear();
        readyLanes.clear();
        pendingAsyncSearches.clear();
        coreCatalogVersion = Long.MIN_VALUE;
        syncedCoreMachine = null;
        syncedCoreRecipePoolId = null;
        clearCandidateCaches();
        if (changed && lanes.isEmpty()) markLaneStateChanged();
    }

    private void clearCandidateCaches() {
        cachedOrderedCandidateSource = List.of();
        cachedOrderedCandidateCatalogVersion = Long.MIN_VALUE;
        cachedOrderedCandidates = List.of();
        cachedIndexedCandidateSource = List.of();
        cachedIndexedCandidateCatalogVersion = Long.MIN_VALUE;
        cachedIndexedInputItems = Set.of();
        cachedIndexedLockedRecipeIds = Set.of();
        cachedIndexedCandidates = List.of();
        cachedCandidateRecipePoolId = null;
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
            ResourceLocation lockedRecipe = recipeLocks.get(lane);
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
        MachineRecipeCatalog catalog = RecipeRegistry.catalogForMachine(machine);
        Map<String, List<MachineRecipe>> coreCandidates = new LinkedHashMap<>();
        if (machine != null) {
            for (FactoryThreadSpec spec : machine.factoryThreads()) {
                coreCandidates.put(spec.name(), catalog.recipes().stream()
                        .filter(recipe -> spec.recipeIds().contains(recipe.id())).toList());
            }
        }
        Map<String, Integer> restoredCoreOccurrences = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            ValueInput laneInput = input.childOrEmpty("lane_" + index);
            String lockedRecipeName = laneInput.getStringOr("locked_recipe", "");
            ResourceLocation lockedRecipeId = lockedRecipeName.isEmpty() ? null : ResourceLocation.parse(lockedRecipeName);
            List<MachineRecipe> candidates = laneInput.getBooleanOr("core", false)
                    ? coreCandidates.getOrDefault(laneInput.getStringOr("name", ""), List.of())
                    : catalog.recipes();
            String fallbackLaneId = null;
            if (laneInput.getBooleanOr("core", false)) {
                String name = laneInput.getStringOr("name", "");
                int occurrence = restoredCoreOccurrences.merge(name, 1, Integer::sum) - 1;
                fallbackLaneId = "core-" + name + (occurrence == 0 ? "" : "-" + occurrence);
            }
            FactoryRecipeThread lane = FactoryRecipeThread.load(laneInput, controller, lockedRecipeId, candidates,
                    fallbackLaneId);
            addLane(lane);
            if (laneInput.getBooleanOr("had_recipe_lock", false)) recipeLockUsed.add(lane);
            if (lockedRecipeId != null && candidates.stream()
                    .anyMatch(candidate -> candidate != null && lockedRecipeId.equals(candidate.id()))) {
                recipeLocks.put(lane, lockedRecipeId);
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
        if (controller != null) {
            ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
            Machine machine = snapshot.structure().machine() == null
                    ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
            clearInvalidRecipeLocks(machine);
        }
        Map<FactoryRecipeThread, LaneObservation> observations = new IdentityHashMap<>();
        for (FactoryRecipeThread lane : lanes) {
            observations.put(lane, observe(lane));
            lane.rebindCurrentVersions();
        }
        for (Map.Entry<FactoryRecipeThread, LaneObservation> entry : observations.entrySet()) {
            if (!entry.getValue().equals(observe(entry.getKey()))) markLaneStateChanged();
        }
    }

    private void clearInvalidRecipeLocks(@Nullable Machine machine) {
        Set<ResourceLocation> validRecipeIds = RecipeRegistry.catalogForMachine(machine).recipes().stream()
                .map(MachineRecipe::id).collect(Collectors.toSet());
        Set<FactoryRecipeThread> invalidLocks = recipeLocks.entrySet().stream()
                .filter(entry -> !validRecipeIds.contains(entry.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        if (invalidLocks.isEmpty()) return;
        invalidLocks.forEach(recipeLocks::remove);
        recipeLockUsed.removeAll(invalidLocks);
        markLaneStateChanged();
    }

    public FactorySearchContext createSearchContext(ControllerRuntimeSnapshot snapshot,
                                                    List<MachineRecipe> candidates,
                                                     long maxParallelism, long gameTime) {
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        MachineRecipeCatalog catalog = RecipeRegistry.catalogForMachine(machine);
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        if (!Objects.equals(cachedCandidateRecipePoolId, recipePoolId)) {
            clearCandidateCaches();
            cachedCandidateRecipePoolId = recipePoolId;
        }
        List<MachineRecipe> candidateSnapshot = candidatesForPool(nonNullCandidates(candidates), recipePoolId);
        List<MachineRecipe> ordered = orderedCandidates(candidateSnapshot, catalog);
        Set<Item> inputItems = currentInputItems();
        if (inputItems != null && candidatesBelongToCatalog(candidateSnapshot, catalog)) {
            Set<ResourceLocation> lockedRecipeIds = new LinkedHashSet<>(recipeLocks.values());
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

    private static List<MachineRecipe> candidatesForPool(List<MachineRecipe> candidates,
                                                         @Nullable ResourceLocation recipePoolId) {
        if (candidates == null || candidates.isEmpty() || recipePoolId == null) return List.of();
        return candidates.stream().filter(recipe -> recipePoolId.equals(recipe.recipePoolId())).toList();
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
                                                        Set<ResourceLocation> lockedRecipeIds) {
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
            if (capability == null || !capability.directions().supports(IOType.INPUT)) continue;
            var storage = CapabilityFactories.resourceStorage(capability, ItemResource.class);
            if (storage == null) continue;
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
                                                            @Nullable ResourceLocation lockedRecipeId) {
        return currentSearchContextKey(controller.currentRuntimeSnapshot(), lane, lockedRecipeId);
    }

    private RecipeSearchContextKey currentSearchContextKey(ControllerRuntimeSnapshot snapshot,
                                                            FactoryRecipeThread lane,
                                                            @Nullable ResourceLocation lockedRecipeId) {
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        return new RecipeSearchContextKey(snapshot.structure().version(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), snapshot.stateVersion(), RecipeRegistry.catalogForMachine(machine).version(),
                lane.searchResourceEpoch(controller.resourceAvailabilityEpoch()), lockedRecipeId,
                lane.coreRecipeSetVersion());
    }

    private boolean requiresFullTick(ControllerRuntimeSnapshot snapshot, List<MachineRecipe> candidates,
                                     long maxParallelism, long gameTime) {
        if (perThreadParallelLimit != maxParallelism || !startReservations.isEmpty()
                || !patternStartReservations.isEmpty() || !readyLanes.isEmpty() || !pendingAsyncSearches.isEmpty()) {
            return true;
        }
        for (FactoryRecipeThread lane : lanes) {
            if (lane.isStartPending() || lane.runtime().active()) return true;
            if (lane.needsSearch(currentSearchContextKey(snapshot, lane, recipeLocks.get(lane)), gameTime)
                    && candidates != null && !candidates.isEmpty()) return true;
            if (lane.idleTimeoutDue(gameTime, recipeLockUsed.contains(lane))) return true;
        }
        return false;
    }

    private FactoryTickResult tickIdleBaseRuntime(long initialEpoch) {
        CraftingRuntime baseRuntime = lanes.isEmpty() ? null : lanes.getFirst().runtime();
        if (baseRuntime == null) return currentTickResult(initialEpoch, false);
        CraftingStateSnapshot before = baseRuntime.snapshot();
        CapabilityTickResult result = baseRuntime.tickIdle();
        boolean changed = result.stateChanged() || !before.equals(baseRuntime.snapshot());
        if (changed) markLaneStateChanged();
        return currentTickResult(initialEpoch, changed);
    }

    private static RecipeSearchContextKey searchContextKey(FactorySearchContext context,
                                                           FactoryRecipeThread lane,
                                                           @Nullable ResourceLocation lockedRecipeId) {
        return new RecipeSearchContextKey(context.snapshot().structure().version(), context.snapshot().capabilityVersion(),
                context.snapshot().modifierVersion(), context.snapshot().stateVersion(), context.catalogVersion(),
                lane.searchResourceEpoch(context.resourceAvailabilityEpoch()), lockedRecipeId,
                lane.coreRecipeSetVersion());
    }

    private long currentGameTime() {
        if (controller == null || controller.getLevel() == null) return 0L;
        return controller.getLevel().getGameTime();
    }

    private Map<ResourceLocation, Integer> activeRecipeCounts() {
        startReservations.entrySet().removeIf(entry -> !lanes.contains(entry.getKey())
                || (!entry.getKey().isStartPending() && !entry.getKey().runtime().active()));
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        for (ResourceLocation recipeId : startReservations.values()) counts.merge(recipeId, 1, Integer::sum);
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

    private void accumulateActiveRecipeCount(FactoryRecipeThread lane, Map<ResourceLocation, Integer> counts) {
        ResourceLocation reservation = startReservations.get(lane);
        if (reservation != null && !lane.isStartPending() && !lane.runtime().active()) {
            startReservations.remove(lane);
            reservation = null;
        }
        if (reservation != null) counts.merge(reservation, 1, Integer::sum);
        if (lane.getStatus() == RecipeThread.Status.FAILED) return;
        MachineRecipe pendingRecipe = lane.getPendingStartRecipe();
        if (pendingRecipe != null && reservation == null) counts.merge(pendingRecipe.id(), 1, Integer::sum);
        MachineRecipe activeRecipe = lane.runtime().recipe();
        if (activeRecipe != null) counts.merge(activeRecipe.id(), 1, Integer::sum);
    }

    public int activeRecipeCountFor(ResourceLocation recipeId) {
        return activeRecipeCounts().getOrDefault(recipeId, 0);
    }

    private List<MachineRecipe> filterAvailableCandidates(List<MachineRecipe> candidates,
                                                          Map<ResourceLocation, Integer> activeCounts) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        boolean filteringRequired = false;
        for (MachineRecipe recipe : candidates) {
            if (recipe == null || activeCounts.getOrDefault(recipe.id(), 0)
                    >= effectiveRecipe(recipe).recipeThreadLimit()) {
                filteringRequired = true;
                break;
            }
        }
        if (!filteringRequired) return candidates;
        List<MachineRecipe> filtered = new ArrayList<>(candidates.size());
        for (MachineRecipe recipe : candidates) {
            if (recipe != null && activeCounts.getOrDefault(recipe.id(), 0)
                    < effectiveRecipe(recipe).recipeThreadLimit()) {
                filtered.add(recipe);
            }
        }
        return filtered.isEmpty() ? List.of() : List.copyOf(filtered);
    }

    private EffectiveRecipe effectiveRecipe(MachineRecipe recipe) {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        return new EffectiveRecipeResolver().resolve(recipe, snapshot,
                controller.componentRuntime().modifierList());
    }

    private void reserveStart(FactoryRecipeThread lane, boolean started,
                              Map<ResourceLocation, Integer> activeCounts) {
        ResourceLocation previous = startReservations.remove(lane);
        if (previous != null) {
            activeCounts.computeIfPresent(previous, (ignored, count) -> count <= 1 ? null : count - 1);
        }
        if (!started) return;
        MachineRecipe pending = lane.getPendingStartRecipe();
        ResourceLocation recipeId = pending == null || pending.id() == null
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
        String laneId = lane.laneId();
        lane.setFinishContinuation(null);
        pendingAsyncSearches.remove(lane);
        if (patternStartReservations.remove(lane)) lane.runtime().releasePatternStart();
        lane.invalidate();
        if (controller != null) {
            controller.markRecipeScreenTextRemoved(laneId, controller.recipeScreenText(laneId).revision());
        }
        lanes.remove(lane);
        removeLaneState(lane);
        readyLanes.remove(lane);
        markLaneStateChanged();
    }

    private void removeLaneState(FactoryRecipeThread lane) {
        recipeLocks.remove(lane);
        recipeLockUsed.remove(lane);
        startReservations.remove(lane);
        lanePresentationCaches.remove(lane);
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
        failureDirty = true;
        factoryStateEpoch++;
    }

    private ControllerRecipePresentation presentationFor(FactoryRecipeThread lane) {
        CraftingRuntime runtime = lane.runtime();
        ActiveMachineRecipe recipe = runtime.activeRecipe();
        long parallelism = runtime.active() ? runtime.parallelism() : 0L;
        LanePresentationCache cached = lanePresentationCaches.get(lane);
        if (cached != null && cached.recipe == recipe && cached.parallelism == parallelism) return cached.presentation;

        ControllerRecipePresentation presentation = ControllerRecipePresentation.from(runtime);
        lanePresentationCaches.put(lane, new LanePresentationCache(recipe, parallelism, presentation));
        presentationBuildCountForTesting++;
        return presentation;
    }

    private record LanePresentationCache(@Nullable ActiveMachineRecipe recipe, long parallelism,
                                         ControllerRecipePresentation presentation) {
    }

    private @Nullable PatternLane reservePatternStart(FactoryRecipeThread lane, MachineRecipe recipe,
                                                      long requestedParallelism,
                                                      List<MachineCapability> requestCapabilities) {
        if (patternStartReservations.contains(lane) || !lane.isIdle()
                || !lane.candidatesFor(List.of(recipe)).contains(recipe)) return null;
        CraftingRuntime.PreparedStart prepared = lane.runtime()
                .preparePatternStart(recipe, requestedParallelism, requestCapabilities);
        if (prepared == null || !lane.runtime().reservePatternStart()) return null;
        patternStartReservations.add(lane);
        markLaneStateChanged();
        return new PatternLane(lane.runtime(), lane.laneId(), prepared);
    }

    /** Concrete factory lane selected for one admitted pattern start. */
    public record PatternLane(CraftingRuntime runtime, String laneId, CraftingRuntime.PreparedStart preparedStart) {
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
                                   @Nullable ResourceLocation lockedRecipe) {
    }

    private record LaneAnalysis(Map<FactoryRecipeThread, LaneObservation> observations,
                                Map<ResourceLocation, Integer> activeRecipeCounts) {
    }

    /** Immutable runtime-owned lane snapshot. */
    public record ThreadSnapshot(int index, String laneId, boolean baseThread, boolean coreThread, boolean active,
                                 String recipeId, int tick, int totalTick, long parallelism,
                                 @Nullable ExecutionStatus failure, boolean locked, String lockedRecipeId,
                                 ControllerRecipePresentation presentation) {
        public ThreadSnapshot(int index, boolean baseThread, boolean coreThread, boolean active,
                              String recipeId, int tick, int totalTick, long parallelism,
                              String lastFailureUnloc, boolean locked, String lockedRecipeId) {
            this(index, index == 0 ? "base" : "factory-" + index, baseThread, coreThread, active,
                    recipeId, tick, totalTick, parallelism, legacyFailure(lastFailureUnloc), locked, lockedRecipeId,
                    ControllerRecipePresentation.empty());
        }

        public ThreadSnapshot(int index, String laneId, boolean baseThread, boolean coreThread, boolean active,
                              String recipeId, int tick, int totalTick, long parallelism,
                              String lastFailureUnloc, boolean locked, String lockedRecipeId) {
            this(index, laneId, baseThread, coreThread, active, recipeId, tick, totalTick, parallelism,
                    legacyFailure(lastFailureUnloc), locked, lockedRecipeId, ControllerRecipePresentation.empty());
        }

        public ThreadSnapshot(int index, String laneId, boolean baseThread, boolean coreThread, boolean active,
                              String recipeId, int tick, int totalTick, long parallelism,
                              @Nullable ExecutionStatus failure, boolean locked, String lockedRecipeId) {
            this(index, laneId, baseThread, coreThread, active, recipeId, tick, totalTick, parallelism,
                    failure, locked, lockedRecipeId, ControllerRecipePresentation.empty());
        }

        public ThreadSnapshot {
            laneId = laneId == null ? "" : laneId;
            recipeId = recipeId == null ? "" : recipeId;
            lockedRecipeId = locked ? lockedRecipeId == null ? "" : lockedRecipeId : "";
            presentation = presentation == null ? ControllerRecipePresentation.empty() : presentation;
        }

        /**
         * Temporary string presentation boundary for unchanged Task 7 packet/menu callers.
         * Remove this accessor and the string constructors when Task 7 migrates those callers.
         */
        public String lastFailureUnloc() {
            if (failure == null) return "";
            String legacyMessage = failure.details().get("legacy_message");
            if (legacyMessage != null) return legacyMessage;
            FailureReason reason = failure.reason();
            return (reason == null ? BuiltinFailureReasons.UNKNOWN : reason).translationKey();
        }

        private static @Nullable ExecutionStatus legacyFailure(@Nullable String message) {
            if (message == null || message.isEmpty()) return null;
            ResourceLocation source = MMCR.id("factory_runtime");
            return ExecutionStatus.blocked(source, source,
                    FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN, source, FailurePhase.UNKNOWN,
                            null, null, Map.of("legacy_message", message)));
        }

        public static ThreadSnapshot idleBase() {
            return new ThreadSnapshot(0, "base", true, false, false, "", 0, 0, 1L,
                    null, false, "", ControllerRecipePresentation.empty());
        }
    }
}
