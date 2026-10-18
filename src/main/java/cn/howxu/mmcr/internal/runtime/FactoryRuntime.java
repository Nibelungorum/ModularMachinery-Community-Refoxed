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
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeCatalog;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.recipe.FactorySearchContext;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.internal.recipe.EffectiveRecipeSet;
import cn.howxu.mmcr.internal.recipe.RecipeThread;
import cn.howxu.mmcr.internal.recipe.RecipeThread.StartResult;
import cn.howxu.mmcr.internal.recipe.RecipeSearchContextKey;
import cn.howxu.mmcr.internal.async.AsyncContinuation;
import cn.howxu.mmcr.internal.async.AsyncExecutionContext;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.async.MainThreadStep;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import java.util.Collections;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
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
import java.util.function.LongFunction;

/**
 * Owns factory lanes, their execution state, and their published snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRuntime {
    public static final int MAX_LANES = 1024;
    private static final Runnable NO_FINISH_CALLBACK = () -> { };
    private final List<FactoryRecipeThread> lanes = new ArrayList<>();
    private final Map<FactoryRecipeThread, ResourceLocation> startReservations = new IdentityHashMap<>();
    private final Map<FactoryRecipeThread, ResourceLocation> patternPreparations = new IdentityHashMap<>();
    private final Map<ResourceLocation, Integer> activeRecipeCounts = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> patternRecipeCounts = new HashMap<>();
    private final Map<FactoryRecipeThread, LaneAccounting> laneAccounting = new IdentityHashMap<>();
    private final Map<CraftingRuntime, FactoryRecipeThread> lanesByRuntime = new IdentityHashMap<>();
    private final Map<FactoryRecipeThread, ExecutionStatus> laneFailures = new IdentityHashMap<>();
    private final Set<FactoryRecipeThread> searchWaitingLanes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayDeque<LaneTickObservation> availableTickObservations = new ArrayDeque<>();
    private int tickObservationBuildCountForTesting;
    private int accountingUpdatesForTesting;
    private Runnable currentFinishCallback = NO_FINISH_CALLBACK;
    private List<MachineRecipe> currentFinishCandidates = List.of();
    private final Set<FactoryRecipeThread> patternStartReservations = Collections.newSetFromMap(new IdentityHashMap<>());
    private int laneLimit = 1;
    private boolean laneAdmissionPending;
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
    private long cachedSnapshotStructureVersion = Long.MIN_VALUE;
    private @Nullable FactorySnapshot cachedSnapshot;
    private final Map<FactoryRecipeThread, ControllerRecipePresentation> lanePresentationCaches = new IdentityHashMap<>();
    private final Map<FactoryRecipeThread, ThreadSnapshot> laneThreadSnapshots = new IdentityHashMap<>();
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
    private List<MachineRecipe> cachedIndexedCandidates = List.of();
    private @Nullable ResourceLocation cachedCandidateRecipePoolId;
    private final EffectiveRecipeSet.Cache effectiveRecipeCache = new EffectiveRecipeSet.Cache();

    public FactoryTickResult tick(List<MachineRecipe> candidates, long maxParallelism) {
        return tick(candidates, maxParallelism, currentGameTime());
    }

    /** Drops deferred lane work when the owning controller changes execution lifecycle. */
    public void cancelAsyncState() {
        releaseStressContributions();
        for (FactoryRecipeThread lane : lanes) {
            lane.cancelAsyncState();
            if (patternStartReservations.remove(lane)) lane.runtime().releasePatternStart();
        }
        startReservations.clear();
        readyLanes.clear();
        pendingAsyncSearches.clear();
        for (FactoryRecipeThread lane : lanes) refreshLaneAccounting(lane);
    }

    public void discardForRecipePoolChange() {
        boolean changed = !startReservations.isEmpty()
                || !patternStartReservations.isEmpty() || !readyLanes.isEmpty() || !pendingAsyncSearches.isEmpty();
        for (FactoryRecipeThread lane : lanes) {
            LaneObservation before = observe(lane);
            lane.discardForRecipePoolChange();
            changed |= !matchesObservation(lane, before);
        }
        startReservations.clear();
        patternStartReservations.clear();
        readyLanes.clear();
        pendingAsyncSearches.clear();
        for (FactoryRecipeThread lane : lanes) refreshLaneAccounting(lane);
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
        currentFinishCallback = onFinished == null ? NO_FINISH_CALLBACK : onFinished;
        currentFinishCandidates = candidates == null ? List.of() : candidates;
        if (canTickInstalledLanes(maxParallelism)) {
            for (FactoryRecipeThread lane : List.copyOf(lanes)) tickLane(lane, snapshot);
            trimLanesToLimit();
            return currentTickResult(initialEpoch, initialEpoch != factoryStateEpoch);
        }
        if (candidates == null || maxParallelism <= 0L) {
            lanes.forEach(this::stopEmptyLanePrefetch);
        }
        if (!requiresFullTick(snapshot, candidates, maxParallelism, gameTime)) return tickIdleBaseRuntime(initialEpoch);
        return tick(createSearchContext(snapshot, candidates, maxParallelism, gameTime), onFinished,
                candidates == null ? List.of() : candidates);
    }

    public FactoryTickResult tick(FactorySearchContext context, Runnable onFinished) {
        return tick(context, onFinished, context == null ? List.of() : context.orderedCandidates());
    }

    private FactoryTickResult tick(FactorySearchContext context, Runnable onFinished, List<MachineRecipe> allCandidates) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        long initialEpoch = factoryStateEpoch;
        if (paused || controller == null) return currentTickResult(initialEpoch, false);
        currentFinishCallback = onFinished == null ? NO_FINISH_CALLBACK : onFinished;
        currentFinishCandidates = allCandidates;
        laneAdmissionPending = false;
        readyLanes.clear();
        pendingAsyncSearches.entrySet().removeIf(entry ->
                entry.getKey().coreRecipeSetVersion() != entry.getValue().coreRecipeSetVersion());

        if (perThreadParallelLimit != context.maxParallelism()) {
            perThreadParallelLimit = context.maxParallelism();
            markLaneStateChanged();
        }
        long structureVersion = context.snapshot().structure().version();
        long capabilityVersion = context.snapshot().capabilityVersion();
        long modifierVersion = context.snapshot().modifierVersion();
        long componentStateVersion = context.snapshot().stateVersion();
        long gameTime = context.gameTime();
        List<FactoryRecipeThread> laneSnapshot = List.copyOf(lanes);
        boolean hasIdleLane = false;
        for (FactoryRecipeThread lane : laneSnapshot) {
            if (!lane.isStartPending() && !lane.runtime().active()) startReservations.remove(lane);
            if (lane.runtime().active()) startReservations.remove(lane);
            lane.setFinishContinuation(() -> {
                try {
                    currentFinishCallback.run();
                } finally {
                    // Releasing a shared lane may unblock output capacity for another lane.
                    controller.notifyResourceAvailability(ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY, null);
                    int laneIndex = lanes.indexOf(lane);
                    boolean withinLaneLimit = laneIndex >= 0 && laneIndex < laneLimit;
                    FactorySearchContext restartContext = createSearchContext(controller.currentRuntimeSnapshot(),
                            currentFinishCandidates, perThreadParallelLimit, currentGameTime());
                    ControllerRuntimeSnapshot current = restartContext.snapshot();
                    refreshLaneAccounting(lane);
                    List<MachineRecipe> available = fullSearchCandidates(restartContext, lane);
                    StartResult restarted = !withinLaneLimit || patternStartReservations.contains(lane)
                            || available.isEmpty() && !searchCandidateScope(restartContext, lane).isEmpty() ? StartResult.DEFERRED
                            : controller.activeWorkMode() == MachineWorkMode.ASYNC
                            ? lane.prepareAsyncFinishRestart(restartContext, available, perThreadParallelLimit,
                             current.structure().version(), current.capabilityVersion(), current.modifierVersion(), current.stateVersion())
                            : lane.tryRestartLastRecipe(restartContext, available, perThreadParallelLimit,
                             current.structure().version(), current.capabilityVersion(), current.modifierVersion(), current.stateVersion());
                    reserveStart(lane, restarted.ownsStart());
                    markFinishedLaneReady(lane);
                }
            });
            lane.setSearchGameTime(gameTime);
            lane.setSearchContextKey(searchContextKey(context, lane));
            tickLane(lane, context.snapshot());
            if (!pendingAsyncSearches.containsKey(lane) && !patternStartReservations.contains(lane) && lane.isIdle()) {
                hasIdleLane = true;
            }
        }
        Map<ResourceLocation, Integer> activeCounts = activeRecipeCounts;
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
        if (context.orderedCandidates().isEmpty() || context.maxParallelism() <= 0L) {
            laneSnapshot.forEach(this::stopEmptyLanePrefetch);
        }
        if (asyncSearchTick) {
            if (hasIdleLane || lanes.size() < laneLimit) {
                scheduleAsyncSearches(context, laneSnapshot, activeCounts, (ServerLevel) controller.getLevel(), readyThisTick);
            }
        } else if (perThreadParallelLimit > 0L) {
            for (FactoryRecipeThread lane : laneSnapshot) {
                if (patternStartReservations.contains(lane) || !lane.isIdle()) continue;
                if (readyThisTick.contains(lane) && !lane.hasFullSearchPending()) continue;
                if (lanes.indexOf(lane) >= laneLimit) continue;
                List<MachineRecipe> available = fullSearchCandidates(context, lane);
                if (available.isEmpty() && !searchCandidateScope(context, lane).isEmpty()) continue;
                if (available.isEmpty() && !lane.hasFullSearchPending() && lane.lastRecipeId() == null) {
                    stopEmptyLanePrefetch(lane);
                    continue;
                }
                RecipeSearchContextKey key = searchContextKey(context, lane);
                if (!lane.canSearch(gameTime, key)) continue;
                lane.setSearchGameTime(gameTime);
                lane.setSearchContextKey(key);
                StartResult restarted = lane.tryRestartLastRecipe(context, available, perThreadParallelLimit, structureVersion,
                        capabilityVersion, modifierVersion, componentStateVersion);
                if (restarted.ownsStart()) {
                    reserveStart(lane, true);
                    readyThisTick.remove(lane);
                    continue;
                }
                if (restarted == StartResult.DEFERRED || restarted == StartResult.CANCELLED) continue;
                available = fullSearchCandidates(context, lane);
                searchAttemptsForTesting++;
                reserveStart(lane, searchAndStartRecipe(lane, context, available, structureVersion).ownsStart());
                readyThisTick.remove(lane);
            }
            while (lanes.size() < laneLimit && perThreadParallelLimit > 0) {
                List<MachineRecipe> available = fullSearchCandidates(context, null);
                if (available.isEmpty()) break;
                FactoryRecipeThread lane = FactoryRecipeThread.simple(controller, "factory-" + nextFactoryLaneId++);
                addLane(lane);
                RecipeSearchContextKey key = searchContextKey(context, lane);
                if (!lane.canSearch(gameTime, key)) break;
                lane.setSearchGameTime(gameTime);
                lane.setSearchContextKey(key);
                searchAttemptsForTesting++;
                StartResult started = searchAndStartRecipe(lane, context, available, structureVersion);
                reserveStart(lane, started.ownsStart());
                if (!started.ownsStart()) break;
            }
        }

        for (FactoryRecipeThread lane : laneSnapshot) {
            lane.tickIdle(gameTime);
            if (lane.isTimedOut()) removeLane(lane);
        }
        if (lanes.stream().noneMatch(lane -> lane.runtime().active() || lane.isStartPending())) {
            tickIdleBaseRuntime(initialEpoch);
        }
        trimLanesToLimit();
        clearFinishedContinuations();
        recomputeFailureIfDirty();
        int activeLaneCount = activeLaneCount();
        return new FactoryTickResult(activeLaneCount, failure,
                initialEpoch != factoryStateEpoch, initialEpoch != factoryStateEpoch);
    }

    private static StartResult searchAndStartRecipe(FactoryRecipeThread lane, FactorySearchContext context,
                                                List<MachineRecipe> candidates, long structureVersion) {
        return lane.searchAndStartRecipe(context, candidates, structureVersion);
    }

    private void scheduleAsyncSearches(FactorySearchContext context, List<FactoryRecipeThread> laneSnapshot,
                                        Map<ResourceLocation, Integer> activeCounts, ServerLevel level,
                                        Set<FactoryRecipeThread> finishedThisTick) {
        asyncSearchScansForTesting++;
        if (context.maxParallelism() <= 0L) return;
        for (FactoryRecipeThread lane : laneSnapshot) {
            if (finishedThisTick.contains(lane) && !lane.hasFullSearchPending()) continue;
            scheduleAsyncSearch(context, lane, level);
        }
        if (activeCounts.isEmpty() && startReservations.isEmpty() && patternStartReservations.isEmpty()) return;
        while (lanes.size() < laneLimit) {
            if (fullSearchCandidates(context, null).isEmpty()) break;
            FactoryRecipeThread lane = FactoryRecipeThread.simple(controller, "factory-" + nextFactoryLaneId++);
            addLane(lane);
            if (!scheduleAsyncSearch(context, lane, level)
                    && !lane.isStartPending() && !lane.runtime().active()) break;
        }
    }

    private void stopEmptyLanePrefetch(FactoryRecipeThread lane) {
        if (lane.isIdle() && !pendingAsyncSearches.containsKey(lane) && !patternStartReservations.contains(lane)) {
            lane.runtime().stopEnergyPrefetch();
        }
    }

    private boolean scheduleAsyncSearch(FactorySearchContext context, FactoryRecipeThread lane,
                                        ServerLevel level) {
        int index = lanes.indexOf(lane);
        if (index < 0 || index >= laneLimit || pendingAsyncSearches.containsKey(lane)
                || patternStartReservations.contains(lane) || !lane.isIdle()) {
            return false;
        }
        RecipeSearchContextKey key = searchContextKey(context, lane);
        if (!lane.canSearch(context.gameTime(), key)) return false;
        List<MachineRecipe> candidates = fullSearchCandidates(context, lane);
        if (candidates.isEmpty()) {
            if (!searchCandidateScope(context, lane).isEmpty()) return false;
            StartResult restart = lane.tryRestartLastRecipe(context, candidates, perThreadParallelLimit,
                    context.snapshot().structure().version(), context.snapshot().capabilityVersion(),
                    context.snapshot().modifierVersion(), context.snapshot().stateVersion());
            if (restart == StartResult.DEFERRED || restart == StartResult.CANCELLED) return false;
            if (lane.hasFullSearchPending()) {
                searchAttemptsForTesting++;
                reserveStart(lane, lane.searchAndStartRecipe(context, List.of(), context.snapshot().structure().version()).ownsStart());
            }
            stopEmptyLanePrefetch(lane);
            return false;
        }
        StartResult restart = lane.tryRestartLastRecipe(context, candidates, perThreadParallelLimit,
                context.snapshot().structure().version(), context.snapshot().capabilityVersion(),
                context.snapshot().modifierVersion(), context.snapshot().stateVersion());
        if (restart.ownsStart()) {
            reserveStart(lane, true);
            markLaneStateChanged();
            return false;
        }
        if (restart == StartResult.DEFERRED || restart == StartResult.CANCELLED) return false;
        candidates = fullSearchCandidates(context, lane);
        WorkerSearchRequest workerRequest;
        long coreRecipeSetVersion = lane.coreRecipeSetVersion();
        long lifecycleEpoch = controller.lifecycleEpoch();
        try {
            workerRequest = captureWorkerSearch(context, candidates);
        } catch (RuntimeException exception) {
            if (coreRecipeSetVersion != lane.coreRecipeSetVersion() || lifecycleEpoch != controller.lifecycleEpoch()) return false;
            searchAttemptsForTesting++;
            StartResult started = lane.startSearchResult(context, candidates, context.snapshot().structure().version(),
                    FactoryRecipeThread.search(context, candidates, context.snapshot().structure().version(), lane.runtime()));
            reserveStart(lane, started.ownsStart());
            return false;
        }
        if (coreRecipeSetVersion != lane.coreRecipeSetVersion() || lifecycleEpoch != controller.lifecycleEpoch()) return false;
        AsyncSearchRequest request = new AsyncSearchRequest(context, candidates, ++nextAsyncSearchId,
                coreRecipeSetVersion, workerRequest);
        MachineAsyncCoordinator.TaskKey taskKey = new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(),
                level.getGameTime(), MachineWorkMode.ASYNC, "factory-search/" + lane.laneId(), lifecycleEpoch);
        pendingAsyncSearches.put(lane, request);
        MachineAsyncCoordinator.SubmissionResult submission = MachineAsyncCoordinator.get(level).submitDetailed(taskKey,
                new FactorySearchContinuation(lane.laneId(), context.catalogVersion(), request.searchId(), workerRequest),
                this::executeAsyncSearchStep,
                new MachineAsyncCoordinator.TaskHooks(
                        () -> taskKey.lifecycleEpoch() == controller.lifecycleEpoch()
                                && pendingAsyncSearches.get(lane) == request
                                && lane.coreRecipeSetVersion() == request.coreRecipeSetVersion(),
                        (ignored, outcome) -> {
                            if (!(outcome instanceof MachineAsyncCoordinator.TaskOutcome.Succeeded)
                                    && pendingAsyncSearches.get(lane) == request) {
                                boolean current = taskKey.lifecycleEpoch() == controller.lifecycleEpoch()
                                        && asyncSearchStillValid(lane, request, request.context().catalogVersion());
                                pendingAsyncSearches.remove(lane, request);
                                if (current && outcome instanceof MachineAsyncCoordinator.TaskOutcome.Failed failed) {
                                    lane.startSearchResult(request.context(), request.candidates(),
                                            request.context().snapshot().structure().version(),
                                            new FactoryRecipeThread.SearchResult(null, new RuntimeException(failed.cause()), false));
                                    refreshLaneAccounting(lane);
                                }
                            }
                        }));
        if (submission != MachineAsyncCoordinator.SubmissionResult.ACCEPTED) {
            pendingAsyncSearches.remove(lane, request);
            return false;
        }
        if (coreRecipeSetVersion == lane.coreRecipeSetVersion() && lifecycleEpoch == controller.lifecycleEpoch()) {
            lane.onFullSearchResult(StartResult.PENDING);
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
                || taskKey.lifecycleEpoch() != controller.lifecycleEpoch()
                || !asyncSearchStillValid(lane, request, catalogVersion)) {
            if (lane != null && request != null && request.searchId() == searchId) {
                pendingAsyncSearches.remove(lane, request);
            }
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
                    if (!pendingAsyncSearches.remove(lane, request)) return false;
                    Map<ResourceLocation, Integer> activeCounts = activeRecipeCounts();
                    if (lane.coreRecipeSetVersion() != request.coreRecipeSetVersion()) return false;
                    List<MachineRecipe> available = filterAvailableCandidates(
                            lane.candidatesFor(request.candidates(), catalogVersion), activeCounts);
                    FactoryRecipeThread.SearchResult result = request.workerRequest().result();
                    if (result == null || result.result() != null && result.result().success()
                            && !available.contains(result.result().recipe())) return false;
                    StartResult started = lane.startSearchResult(request.context(), available,
                            request.context().snapshot().structure().version(), result);
                    reserveStart(lane, started.ownsStart());
                    return true;
                }, () -> validateAsyncSearchRequest(lane, request, catalogVersion, taskKey, level),
                () -> controller.currentRuntimeSnapshot().structure().version(),
                () -> controller.currentRuntimeSnapshot().stateVersion(), catalogVersion,
                this::currentCatalogVersion, () -> MachineAsyncCoordinator.get(level).resume(taskKey)));
        return MainThreadStep.Result.pending();
    }

    private boolean validateAsyncSearchRequest(FactoryRecipeThread lane, AsyncSearchRequest request, long catalogVersion,
                                               MachineAsyncCoordinator.TaskKey taskKey, ServerLevel level) {
        boolean valid = taskKey.lifecycleEpoch() == controller.lifecycleEpoch()
                && asyncSearchStillValid(lane, request, catalogVersion);
        if (!valid) {
            pendingAsyncSearches.remove(lane, request);
            MachineAsyncCoordinator.get(level).resume(taskKey);
        }
        return valid;
    }

    private boolean asyncSearchStillValid(FactoryRecipeThread lane, AsyncSearchRequest request, long catalogVersion) {
        if (controller == null || controller.isRedstonePaused() || !lanes.contains(lane) || !lane.isIdle()
                || pendingAsyncSearches.get(lane) != request || catalogVersion != request.context().catalogVersion()
                || catalogVersion != currentCatalogVersion()
                || lane.coreRecipeSetVersion() != request.coreRecipeSetVersion()) return false;
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        return current.structure().version() == request.context().snapshot().structure().version()
                && current.capabilityVersion() == request.context().snapshot().capabilityVersion()
                && current.modifierVersion() == request.context().snapshot().modifierVersion()
                && current.stateVersion() == request.context().snapshot().stateVersion();
    }

    private long currentCatalogVersion() {
        return RecipeRegistry.catalogForPool(controller.currentRecipePoolId()).version();
    }

    private WorkerSearchRequest captureWorkerSearch(FactorySearchContext context,
                                                     List<MachineRecipe> candidates) {
        return new WorkerSearchRequest(AsyncRequirementPlanner.captureRecipeSearch(context.snapshot(), candidates,
                context.maxParallelism(), context.capabilitySnapshot(), context.modifiers(),
                context.catalogVersion(), effectiveRecipeCache));
    }

    private record AsyncSearchRequest(FactorySearchContext context, List<MachineRecipe> candidates,
                                      long searchId, long coreRecipeSetVersion, WorkerSearchRequest workerRequest) {
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


    public boolean syncCoreLanes(MachineControllerBlockEntity controller, Machine machine,
                                 List<MachineRecipe> candidates) {
        long initialEpoch = factoryStateEpoch;
        ensureBaseLane(controller);
        long catalogVersion = RecipeRegistry.catalogForPool(controller.currentRecipePoolId()).version();
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        Map<ResourceLocation, MachineRecipe> byId = new LinkedHashMap<>();
        for (MachineRecipe recipe : candidates == null ? List.<MachineRecipe>of() : candidates) {
            if (recipe != null && recipePoolId != null && recipePoolId.equals(recipe.recipePoolId())) {
                byId.putIfAbsent(recipe.id(), recipe);
            }
        }
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
                        refreshLaneAccounting(lane);
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
        long catalogVersion = RecipeRegistry.catalogForPool(controller.currentRecipePoolId()).version();
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
        FactoryRecipeThread base = FactoryRecipeThread.base(controller);
        lanes.addFirst(base);
        bindStartEligibility(base);
        lanesByRuntime.put(base.runtime(), base);
        refreshLaneAccounting(base);
        markLaneStateChanged();
        return true;
    }

    public List<CraftingRuntime> activeRuntimes() {
        return lanes.stream().map(FactoryRecipeThread::runtime).filter(CraftingRuntime::active).toList();
    }

    /** Releases contributions from every lane before controller unload or component replacement. */
    public void releaseStressContributions() {
        for (FactoryRecipeThread lane : lanes) lane.runtime().releaseStressContributions();
    }

    public void invalidateForSmartInterfaceChange() {
        Map<FactoryRecipeThread, LaneObservation> observations = new IdentityHashMap<>();
        for (FactoryRecipeThread lane : List.copyOf(lanes)) {
            observations.put(lane, observe(lane));
            lane.invalidateForSmartInterfaceChange();
        }
        for (Map.Entry<FactoryRecipeThread, LaneObservation> entry : observations.entrySet()) {
            if (!matchesObservation(entry.getKey(), entry.getValue())) markLaneStateChanged();
        }
        recomputeFailureIfDirty();
    }

    public void wakeSearches(ResourceAvailabilityNotifier.Reason reason, @Nullable Object resource) {
        if (reason == null) return;
        for (FactoryRecipeThread lane : searchWaitingLanes) {
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

    public List<ThreadSnapshot> threadSnapshots() {
        List<ThreadSnapshot> snapshots = new ArrayList<>(laneLimit);
        for (int index = 0; index < lanes.size(); index++) {
            FactoryRecipeThread lane = lanes.get(index);
            snapshots.add(threadSnapshot(index, lane));
        }
        return completeThreadSnapshots(snapshots);
    }

    private ThreadSnapshot threadSnapshot(int index, FactoryRecipeThread lane) {
        CraftingRuntime runtime = lane.runtime();
        ControllerRecipePresentation presentation = presentationFor(lane);
        String recipeId = runtime.recipeIdText();
        long parallelism = runtime.active() ? runtime.parallelism() : 1;
        ThreadSnapshot previous = laneThreadSnapshots.get(lane);
        if (previous != null && previous.index() == index && previous.laneId().equals(lane.laneId())
                && previous.baseThread() == lane.isBaseThread() && previous.coreThread() == lane.isCoreThread()
                && previous.active() == runtime.active() && previous.recipeId().equals(recipeId)
                && previous.tick() == runtime.tickCount() && previous.totalTick() == runtime.totalTick()
                && previous.parallelism() == parallelism && Objects.equals(previous.failure(), runtime.failure())
                && previous.presentation() == presentation) return previous;
        ThreadSnapshot next = new ThreadSnapshot(index, lane.laneId(), lane.isBaseThread(), lane.isCoreThread(),
                runtime.active(), recipeId, runtime.tickCount(), runtime.totalTick(), parallelism,
                runtime.failure(), presentation);
        laneThreadSnapshots.put(lane, next);
        return next;
    }

    private List<ThreadSnapshot> completeThreadSnapshots(List<ThreadSnapshot> snapshots) {
        while (snapshots.size() < laneLimit) {
            int index = snapshots.size();
            snapshots.add(new ThreadSnapshot(index, "idle-" + index, false, false, false,
                    "", 0, 0, 1, null, ControllerRecipePresentation.empty()));
        }
        return List.copyOf(snapshots);
    }

    int presentationBuildCountForTesting() {
        return presentationBuildCountForTesting;
    }

    int laneSnapshotBuildCountForTesting() {
        int count = 0;
        for (FactoryRecipeThread lane : lanes) count += lane.runtime().snapshotBuildCountForTesting();
        return count;
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
        StructureSnapshot structure = controller == null ? null : controller.currentStructureSnapshot();
        long structureVersion = structure == null ? Long.MIN_VALUE : structure.version();
        if (cachedSnapshot != null && cachedSnapshotEpoch == factoryStateEpoch
                && cachedSnapshotStructureVersion == structureVersion) return cachedSnapshot;
        List<CraftingStateSnapshot> laneSnapshots = new ArrayList<>(lanes.size());
        List<ThreadSnapshot> presentationSnapshots = new ArrayList<>(laneLimit);
        int activeCount = 0;
        for (int index = 0; index < lanes.size(); index++) {
            FactoryRecipeThread lane = lanes.get(index);
            CraftingStateSnapshot state = lane.runtime().snapshot();
            if (state.recipeId() != null || state.failure() != null) laneSnapshots.add(state);
            if (lane.runtime().active()) activeCount++;
            presentationSnapshots.add(threadSnapshot(index, lane));
        }
        int matchedStage = 0;
        int stageCount = 1;
        if (structure != null) {
            matchedStage = structure.matchedStage();
            Machine machine = structure.machine() == null ? structure.configuredMachine() : structure.machine();
            if (machine != null) {
                List<MachineStructureStage> stages = machine.structureStages();
                stageCount = stages == null || stages.isEmpty() ? 1 : stages.size();
            }
        }
        cachedSnapshot = new FactorySnapshot(false, activeCount > 0, laneSnapshots, laneLimit,
                activeCount, Math.max(1L, perThreadParallelLimit), paused, completeThreadSnapshots(presentationSnapshots), "", 0, failure,
                List.of(), matchedStage, stageCount);
        cachedSnapshotEpoch = factoryStateEpoch;
        cachedSnapshotStructureVersion = structureVersion;
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
        return lanesByRuntime.containsKey(runtime);
    }

    /** Static lane/core eligibility deliberately ignores temporary occupancy. */
    public boolean supportsPatternRecipe(MachineRecipe recipe) {
        return controller != null && supportsPatternRecipe(controller, recipe, laneLimit);
    }

    public boolean supportsPatternRecipe(MachineControllerBlockEntity owner, MachineRecipe recipe, int limit) {
        if (recipe == null || !Objects.equals(recipe.recipePoolId(), owner.currentRecipePoolId())) return false;
        for (int index = 0; index < Math.min(limit, lanes.size()); index++) {
            if (lanes.get(index).candidatesFor(List.of(recipe)).contains(recipe)) return true;
        }
        return lanes.size() < limit;
    }

    public @Nullable MachineRecipe cachedPatternRecipe(ResourceLocation recipeId) {
        for (int index = 0; index < Math.min(laneLimit, lanes.size()); index++) {
            FactoryRecipeThread lane = lanes.get(index);
            if (!lane.isIdle() || patternPreparations.containsKey(lane)) continue;
            MachineRecipe recipe = lane.cachedPatternRecipe(recipeId);
            if (recipe != null) return recipe;
        }
        return null;
    }

    public void rememberPatternStartedRecipe(PatternLane reservation) {
        FactoryRecipeThread lane = lanesByRuntime.get(reservation.runtime());
        if (lane != null && lane.runtime().active()) {
            lane.rememberPatternStartedRecipe();
            refreshLaneAccounting(lane);
            markLaneStateChanged();
        }
    }

    public boolean patternStartCurrent(PatternLane reservation) {
        FactoryRecipeThread lane = lanesByRuntime.get(reservation.runtime());
        MachineRecipe recipe = reservation.preparedStart().recipe().source();
        return lane != null && patternStartReservations.contains(lane)
                && lane.acceptsPatternRecipe(recipe) && startEligible(lane, recipe, true);
    }

    /** Selects exactly one actual lane; failure never falls back to a prefix or a second lane. */
    public @Nullable PatternLane reserveExactPatternStart(MachineRecipe recipe, long parallelism,
                                                           List<MachineCapability> requestCapabilities,
                                                           @Nullable BlockPos requestSource) {
        if (recipe == null || controller == null || paused || parallelism <= 0L) {
            PatternStartDiagnostics.reject("factory-admission", "paused", paused, "laneLimit", laneLimit);
            return null;
        }
        if (!patternQuotaAvailable(recipe)) return null;
        FactoryRecipeThread selected = null;
        for (int index = 0; index < Math.min(laneLimit, lanes.size()); index++) {
            FactoryRecipeThread lane = lanes.get(index);
            if (!lane.isIdle() || patternPreparations.containsKey(lane) || !lane.canStartPatternRecipe(recipe)) {
                PatternStartDiagnostics.reject("factory-lane-skipped", "lane", lane.laneId(), "idle", lane.isIdle(),
                        "preparing", patternPreparations.containsKey(lane));
                continue;
            }
            if (selected == null) selected = lane;
            if (lane.cachedPatternRecipe(recipe.id()) != null) { selected = lane; break; }
        }
        if (selected != null) return reservePatternStart(selected, recipe, parallelism,
                requestCapabilities, requestSource, null, true);
        if (lanes.size() >= laneLimit) {
            PatternStartDiagnostics.reject("factory-no-free-lane", "lanes", lanes.size(), "limit", laneLimit);
            return null;
        }
        FactoryRecipeThread lane = FactoryRecipeThread.simple(controller, "factory-" + nextFactoryLaneId++);
        addLane(lane);
        PatternLane reservation = reservePatternStart(lane, recipe, parallelism, requestCapabilities, requestSource, null, true);
        if (reservation == null) removeLane(lane);
        return reservation;
    }

    private boolean patternQuotaAvailable(MachineRecipe recipe) {
        int used = activeRecipeCountFor(recipe.id()) + patternRecipeCounts.getOrDefault(recipe.id(), 0);
        used += (int) patternPreparations.values().stream().filter(recipe.id()::equals).count();
        int limit = effectiveRecipe(recipe).recipeThreadLimit();
        if (used >= limit) PatternStartDiagnostics.reject("factory-recipe-thread-limit", "used", used, "limit", limit);
        return used < limit;
    }

    /** Reserves an idle lane after its runtime has successfully planned the requested pattern inputs. */
    public @Nullable PatternLane reservePatternStart(MachineRecipe recipe, long requestedParallelism,
                                                     List<MachineCapability> requestCapabilities) {
        return reservePatternStart(recipe, requestedParallelism, requestCapabilities, null);
    }

    public @Nullable PatternLane reservePatternStart(MachineRecipe recipe, long requestedParallelism,
                                                     List<MachineCapability> requestCapabilities,
                                                     @Nullable BlockPos requestSource) {
        return reservePatternStart(recipe, requestedParallelism, requestCapabilities, requestSource, null);
    }

    /** Reserves at most one lane with inputs rebuilt to the actual admitted copy count. */
    public @Nullable PatternLane reservePendingPatternStart(MachineRecipe recipe, long requestedParallelism,
                                                            LongFunction<List<MachineCapability>> capabilitiesForParallelism,
                                                            @Nullable BlockPos requestSource) {
        if (requestedParallelism <= 0L || capabilitiesForParallelism == null) return null;
        return reservePatternStart(recipe, requestedParallelism, null, requestSource, capabilitiesForParallelism);
    }

    private @Nullable PatternLane reservePatternStart(MachineRecipe recipe, long requestedParallelism,
                                                      List<MachineCapability> requestCapabilities,
                                                      @Nullable BlockPos requestSource,
                                                      @Nullable LongFunction<List<MachineCapability>> capabilitiesForParallelism) {
        if (recipe == null || controller == null || paused) return null;
        if (!patternQuotaAvailable(recipe)) return null;
        for (FactoryRecipeThread lane : lanes) {
            if (capabilitiesForParallelism != null) {
                if (patternStartReservations.contains(lane) || !lane.isIdle()
                        || !lane.candidatesFor(List.of(recipe)).contains(recipe)) continue;
                return reservePatternStart(lane, recipe, requestedParallelism, requestCapabilities,
                        requestSource, capabilitiesForParallelism);
            }
            PatternLane reservation = reservePatternStart(lane, recipe, requestedParallelism, requestCapabilities,
                    requestSource, capabilitiesForParallelism);
            if (reservation != null) return reservation;
        }
        if (lanes.size() >= laneLimit) return null;
        FactoryRecipeThread lane = FactoryRecipeThread.simple(controller, "factory-" + nextFactoryLaneId++);
        PatternLane reservation = reservePatternStart(lane, recipe, requestedParallelism, requestCapabilities,
                requestSource, capabilitiesForParallelism);
        if (reservation == null) {
            if (controller.getLevel() instanceof ServerLevel level) {
                SharedIoCoordinator.get(level).forgetExecutionLane(lane.runtime(), lane.laneId());
            }
            return null;
        }
        addLane(lane);
        return reservation;
    }

    /** Reserves enough idle lanes to cover one pattern batch, or releases every partial reservation. */
    public List<PatternLane> reservePatternStarts(MachineRecipe recipe, long requestedParallelism,
                                                  List<MachineCapability> requestCapabilities) {
        return reservePatternStarts(recipe, requestedParallelism, requestCapabilities, null);
    }

    public List<PatternLane> reservePatternStarts(MachineRecipe recipe, long requestedParallelism,
                                                  List<MachineCapability> requestCapabilities,
                                                  @Nullable BlockPos requestSource) {
        if (requestedParallelism <= 0L) return List.of();
        long maxParallelism = controller.currentRuntimeSnapshot().maxParallelism();
        List<PatternLane> reservations = new ArrayList<>();
        long remaining = requestedParallelism;
        while (remaining > 0L) {
            PatternLane reservation = reservePatternStart(recipe, Math.min(remaining, maxParallelism), requestCapabilities, requestSource);
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
            if (!lane.runtime().releasePatternStart(reservation.preparedStart())) return;
            patternStartReservations.remove(lane);
            if (!lane.runtime().active() && controller.getLevel() instanceof ServerLevel level) {
                SharedIoCoordinator.get(level).releaseExecutionReservation(lane.runtime(), lane.laneId());
            }
            refreshLaneAccounting(lane);
            markLaneStateChanged();
            return;
        }
    }

    public boolean setLaneLimit(int laneLimit) {
        int normalized = Math.min(MAX_LANES, Math.max(1, laneLimit));
        if (this.laneLimit == normalized) return false;
        if (normalized > this.laneLimit && lanes.size() < normalized) laneAdmissionPending = true;
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
        boolean changed = !lanes.isEmpty()
                || !startReservations.isEmpty() || !patternStartReservations.isEmpty()
                || coreCatalogVersion != Long.MIN_VALUE || failure != null;
        for (FactoryRecipeThread lane : List.copyOf(lanes)) removeLane(lane);
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
        cachedIndexedCandidates = List.of();
        cachedCandidateRecipePoolId = null;
    }

    public void save(CompoundTag output, HolderLookup.Provider registries) {
        output.putInt("lane_limit", laneLimit);
        output.putBoolean("paused", paused);
        output.putInt("lane_count", lanes.size());
        for (int index = 0; index < lanes.size(); index++) {
            FactoryRecipeThread lane = lanes.get(index);
            CompoundTag laneOutput = new CompoundTag();
            lane.save(laneOutput, registries);
            output.put("lane_" + index, laneOutput);
        }
    }

    public void load(CompoundTag input, MachineControllerBlockEntity controller, HolderLookup.Provider registries) {
        this.controller = controller;
        clear();
        setLaneLimit(input.contains("lane_limit") ? input.getInt("lane_limit") : laneLimit);
        boolean restoredPaused = input.getBoolean("paused");
        if (paused != restoredPaused) {
            paused = restoredPaused;
            markLaneStateChanged();
        }
        int count = Math.min(MAX_LANES, Math.max(0, input.getInt("lane_count")));
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        Machine machine = current.structure().machine() == null
                ? current.structure().configuredMachine() : current.structure().machine();
        MachineRecipeCatalog catalog = RecipeRegistry.catalogForPool(controller.currentRecipePoolId());
        Map<String, List<MachineRecipe>> coreCandidates = new LinkedHashMap<>();
        if (machine != null) {
            for (FactoryThreadSpec spec : machine.factoryThreads()) {
                coreCandidates.put(spec.name(), catalog.recipes().stream()
                        .filter(recipe -> spec.recipeIds().contains(recipe.id())).toList());
            }
        }
        Map<String, Integer> restoredCoreOccurrences = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            CompoundTag laneInput = input.getCompound("lane_" + index);
            List<MachineRecipe> candidates = laneInput.getBoolean("core")
                    ? coreCandidates.getOrDefault(laneInput.getString("name"), List.of())
                    : catalog.recipes();
            String fallbackLaneId = null;
            if (laneInput.getBoolean("core")) {
                String name = laneInput.getString("name");
                int occurrence = restoredCoreOccurrences.merge(name, 1, Integer::sum) - 1;
                fallbackLaneId = "core-" + name + (occurrence == 0 ? "" : "-" + occurrence);
            }
            FactoryRecipeThread lane = FactoryRecipeThread.load(laneInput, controller, registries, candidates,
                    fallbackLaneId);
            addLane(lane);
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
            RecipeSearchContextKey key = currentSearchContextKey(lane);
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
            if (!matchesObservation(entry.getKey(), entry.getValue())) markLaneStateChanged();
        }
    }

    public FactorySearchContext createSearchContext(ControllerRuntimeSnapshot snapshot,
                                                    List<MachineRecipe> candidates,
                                                     long maxParallelism, long gameTime) {
        MachineRecipeCatalog catalog = RecipeRegistry.catalogForPool(controller.currentRecipePoolId());
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        if (!Objects.equals(cachedCandidateRecipePoolId, recipePoolId)) {
            clearCandidateCaches();
            cachedCandidateRecipePoolId = recipePoolId;
        }
        List<MachineRecipe> candidateSnapshot = candidatesForPool(nonNullCandidates(candidates), recipePoolId);
        List<MachineRecipe> ordered = orderedCandidates(candidateSnapshot, catalog);
        Set<Item> inputItems = currentInputItems();
        if (inputItems != null && candidatesBelongToCatalog(candidateSnapshot, catalog)) {
            ordered = filterIndexedCandidates(ordered, catalog, inputItems);
        }
        return new FactorySearchContext(snapshot, ordered, controller.componentRuntime().capabilitySnapshot(),
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
                                                        Set<Item> inputItems) {
        if (ordered.isEmpty()) return ordered;
        if (cachedIndexedCandidateCatalogVersion == catalog.version()
                && cachedIndexedCandidateSource.equals(ordered)
                && cachedIndexedInputItems.equals(inputItems)) {
            return cachedIndexedCandidates;
        }
        Set<MachineRecipe> indexed = new LinkedHashSet<>(catalog.inputIndex().candidates(inputItems));
        boolean filteringRequired = false;
        for (MachineRecipe recipe : ordered) {
            if (!indexed.contains(recipe)) {
                filteringRequired = true;
                break;
            }
        }
        List<MachineRecipe> filtered = filteringRequired
                ? ordered.stream().filter(indexed::contains).toList()
                : ordered;
        cachedIndexedCandidateSource = List.copyOf(ordered);
        cachedIndexedCandidateCatalogVersion = catalog.version();
        cachedIndexedInputItems = Set.copyOf(inputItems);
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
            IItemHandler handler = capability.facet(cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet.class)
                    .map(cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet::itemHandler).orElse(null);
            if (handler == null) continue;
            supported = true;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) items.add(handler.getStackInSlot(slot).getItem());
            }
        }
        return supported && !items.isEmpty() ? items : null;
    }

    private RecipeSearchContextKey currentSearchContextKey(FactoryRecipeThread lane) {
        return currentSearchContextKey(controller.currentRuntimeSnapshot(), lane);
    }

    private RecipeSearchContextKey currentSearchContextKey(ControllerRuntimeSnapshot snapshot,
                                                            FactoryRecipeThread lane) {
        return new RecipeSearchContextKey(snapshot.structure().version(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), snapshot.stateVersion(),
                RecipeRegistry.catalogForPool(controller.currentRecipePoolId()).version(),
                lane.searchResourceEpoch(controller.resourceAvailabilityEpoch()),
                lane.coreRecipeSetVersion());
    }

    private boolean requiresFullTick(ControllerRuntimeSnapshot snapshot, List<MachineRecipe> candidates,
                                     long maxParallelism, long gameTime) {
        if (laneAdmissionPending || perThreadParallelLimit != maxParallelism || !startReservations.isEmpty()
                || !patternStartReservations.isEmpty() || !readyLanes.isEmpty() || !pendingAsyncSearches.isEmpty()) {
            return true;
        }
        for (FactoryRecipeThread lane : lanes) {
            if (lane.isStartPending() || lane.runtime().active()) return true;
            if (lane.hasFullSearchPending()) return true;
            if (lane.needsSearch(currentSearchContextKey(snapshot, lane), gameTime)
                    && candidates != null && !candidates.isEmpty()) return true;
            if (lane.idleTimeoutDue(gameTime)) return true;
        }
        return false;
    }

    private boolean canTickInstalledLanes(long maxParallelism) {
        if (laneAdmissionPending || lanes.isEmpty() || cachedActiveLaneCount != lanes.size() || perThreadParallelLimit != maxParallelism
                || !startReservations.isEmpty() || !patternStartReservations.isEmpty()
                || !pendingAsyncSearches.isEmpty() || !readyLanes.isEmpty()) return false;
        for (FactoryRecipeThread lane : lanes) {
            InstalledRecipeProgram execution = lane.runtime().installedProgram();
            if (execution == null || !execution.current()) return false;
        }
        return true;
    }

    private void tickLane(FactoryRecipeThread lane, ControllerRuntimeSnapshot snapshot) {
        LaneTickObservation before = acquireLaneTickObservation();
        try {
            refreshLaneAccounting(lane);
            before.capture(lane);
            lane.tick(snapshot);
            refreshLaneAccounting(lane);
            if (!before.matches(lane) || before.recipe != lane.runtime().recipe()) {
                markLaneStateChanged();
            }
        } finally {
            releaseLaneTickObservation(before);
        }
    }

    private FactoryTickResult tickIdleBaseRuntime(long initialEpoch) {
        if (lanes.isEmpty()) return currentTickResult(initialEpoch, false);
        FactoryRecipeThread base = lanes.getFirst();
        LaneTickObservation before = acquireLaneTickObservation();
        try {
            refreshLaneAccounting(base);
            before.capture(base);
            CapabilityTickResult result = base.runtime().tickIdle();
            refreshLaneAccounting(base);
            boolean changed = result.stateChanged() || !before.matches(base);
            if (changed) markLaneStateChanged();
            return currentTickResult(initialEpoch, changed);
        } finally {
            releaseLaneTickObservation(before);
        }
    }

    private static RecipeSearchContextKey searchContextKey(FactorySearchContext context,
                                                           FactoryRecipeThread lane) {
        return new RecipeSearchContextKey(context.snapshot().structure().version(), context.snapshot().capabilityVersion(),
                context.snapshot().modifierVersion(), context.snapshot().stateVersion(), context.catalogVersion(),
                lane.searchResourceEpoch(context.resourceAvailabilityEpoch()),
                lane.coreRecipeSetVersion());
    }

    private long currentGameTime() {
        if (controller == null || controller.getLevel() == null) return 0L;
        return controller.getLevel().getGameTime();
    }

    private Map<ResourceLocation, Integer> activeRecipeCounts() {
        return activeRecipeCounts;
    }

    private void refreshLaneAccounting(FactoryRecipeThread lane) {
        ExecutionStatus failure = lane.runtime().failure();
        if (!Objects.equals(laneFailures.get(lane), failure)) {
            if (failure == null) laneFailures.remove(lane);
            else laneFailures.put(lane, failure);
            markLaneStateChanged();
        }
        boolean active = lane.runtime().active();
        if (lane.isIdle()) searchWaitingLanes.add(lane);
        else searchWaitingLanes.remove(lane);
        MachineRecipe pending = lane.getPendingStartRecipe();
        if (!lane.isStartPending() || active) startReservations.remove(lane);
        ResourceLocation recipeId = active && lane.runtime().recipe() != null ? lane.runtime().recipe().id()
                : pending != null ? pending.id()
                : lane.getStatus() == RecipeThread.Status.FAILED ? null : startReservations.get(lane);
        ResourceLocation patternId = patternStartReservations.contains(lane) ? lane.runtime().pendingPatternRecipeId() : null;
        LaneAccounting previous = laneAccounting.get(lane);
        if (previous != null && previous.active() == active && Objects.equals(previous.recipeId(), recipeId)
                && Objects.equals(previous.patternRecipeId(), patternId)) return;
        if (previous != null) {
            decrementCount(activeRecipeCounts, previous.recipeId());
            decrementCount(patternRecipeCounts, previous.patternRecipeId());
            if (previous.active()) cachedActiveLaneCount--;
        }
        if (recipeId != null) activeRecipeCounts.merge(recipeId, 1, Integer::sum);
        if (patternId != null) patternRecipeCounts.merge(patternId, 1, Integer::sum);
        if (active) cachedActiveLaneCount++;
        laneAccounting.put(lane, new LaneAccounting(recipeId, patternId, active));
        accountingUpdatesForTesting++;
        markLaneStateChanged();
    }

    private static void decrementCount(Map<ResourceLocation, Integer> counts, ResourceLocation recipeId) {
        if (recipeId != null) counts.computeIfPresent(recipeId, (ignored, count) -> count == 1 ? null : count - 1);
    }

    int accountingUpdatesForTesting() { return accountingUpdatesForTesting; }

    int tickObservationBuildCountForTesting() { return tickObservationBuildCountForTesting; }

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

    private List<MachineRecipe> fullSearchCandidates(FactorySearchContext context, @Nullable FactoryRecipeThread lane) {
        return filterAvailableCandidates(searchCandidateScope(context, lane), activeRecipeCounts);
    }

    private List<MachineRecipe> searchCandidateScope(FactorySearchContext context, @Nullable FactoryRecipeThread lane) {
        MachineRecipeCatalog catalog = RecipeRegistry.catalogForPool(controller.currentRecipePoolId());
        List<MachineRecipe> ordered = orderedCandidates(lanes.getFirst().candidatesFor(
                nonNullCandidates(currentFinishCandidates), catalog.version()), catalog);
        return lane == null ? ordered : lane.candidatesFor(ordered, context.catalogVersion());
    }

    private EffectiveRecipe effectiveRecipe(MachineRecipe recipe) {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        return new EffectiveRecipeResolver().resolve(recipe, snapshot,
                controller.componentRuntime().modifierList());
    }

    private void reserveStart(FactoryRecipeThread lane, boolean started) {
        startReservations.remove(lane);
        MachineRecipe pending = lane.getPendingStartRecipe();
        if (started && pending != null) startReservations.put(lane, pending.id());
        refreshLaneAccounting(lane);
        if (started) markLaneStateChanged();
    }

    private void clearFinishedContinuations() {
        for (FactoryRecipeThread lane : lanes) {
            if (!lane.isStartPending() && !lane.runtime().active()) lane.setFinishContinuation(null);
        }
    }

    private void addLane(FactoryRecipeThread lane) {
        lanes.add(lane);
        bindStartEligibility(lane);
        lanesByRuntime.put(lane.runtime(), lane);
        refreshLaneAccounting(lane);
        markLaneStateChanged();
    }

    private void bindStartEligibility(FactoryRecipeThread lane) {
        lane.setStartEligibility(recipe -> startEligible(lane, recipe, false));
    }

    private boolean startEligible(FactoryRecipeThread lane, MachineRecipe recipe, boolean patternOwner) {
        int index = lanes.indexOf(lane);
        if (index < 0 || index >= laneLimit || patternStartReservations.contains(lane) && !patternOwner) return false;
        refreshLaneAccounting(lane);
        int used = activeRecipeCounts.getOrDefault(recipe.id(), 0) + patternRecipeCounts.getOrDefault(recipe.id(), 0);
        used += (int) patternPreparations.entrySet().stream()
                .filter(entry -> entry.getKey() != lane && recipe.id().equals(entry.getValue())).count();
        LaneAccounting own = laneAccounting.get(lane);
        if (own != null && recipe.id().equals(own.recipeId())) used--;
        if (patternOwner && own != null && recipe.id().equals(own.patternRecipeId())) used--;
        return used < effectiveRecipe(recipe).recipeThreadLimit();
    }

    private void removeLane(FactoryRecipeThread lane) {
        if (!lanes.contains(lane)) return;
        String laneId = lane.laneId();
        if (controller != null && controller.getLevel() instanceof ServerLevel level) {
            SharedIoCoordinator.get(level).forgetExecutionLane(lane.runtime(), laneId);
        }
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
        laneFailures.remove(lane);
        searchWaitingLanes.remove(lane);
        lanesByRuntime.remove(lane.runtime());
        LaneAccounting previous = laneAccounting.remove(lane);
        if (previous != null) {
            decrementCount(activeRecipeCounts, previous.recipeId());
            decrementCount(patternRecipeCounts, previous.patternRecipeId());
            if (previous.active()) cachedActiveLaneCount--;
        }
        startReservations.remove(lane);
        lanePresentationCaches.remove(lane);
        laneThreadSnapshots.remove(lane);
    }

    public void markLaneRuntimeChanged(CraftingRuntime runtime) {
        if (runtime == null) return;
        FactoryRecipeThread lane = lanesByRuntime.get(runtime);
        if (lane != null) {
            refreshLaneAccounting(lane);
            markLaneStateChanged();
        }
    }

    public void recomputeFailure() {
        failureDirty = true;
        recomputeFailureIfDirty();
    }

    /** Current aggregated failure without capturing lane or recipe presentation snapshots. */
    public @Nullable ExecutionStatus failure() {
        recomputeFailureIfDirty();
        return failure;
    }

    private void recomputeFailureIfDirty() {
        if (!failureDirty) return;
        failureDirty = false;
        ExecutionStatus next = null;
        if (!laneFailures.isEmpty()) {
            for (FactoryRecipeThread lane : lanes) {
                ExecutionStatus laneFailure = laneFailures.get(lane);
                if (laneFailure != null) {
                    next = laneFailure;
                    break;
                }
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
        ControllerRecipePresentation presentation = lane.runtime().recipePresentation();
        if (lanePresentationCaches.put(lane, presentation) != presentation) presentationBuildCountForTesting++;
        return presentation;
    }

    private @Nullable PatternLane reservePatternStart(FactoryRecipeThread lane, MachineRecipe recipe,
                                                        long requestedParallelism,
                                                        List<MachineCapability> requestCapabilities,
                                                        @Nullable BlockPos requestSource,
                                                        @Nullable LongFunction<List<MachineCapability>> capabilitiesForParallelism) {
        return reservePatternStart(lane, recipe, requestedParallelism, requestCapabilities, requestSource,
                capabilitiesForParallelism, false);
    }

    private @Nullable PatternLane reservePatternStart(FactoryRecipeThread lane, MachineRecipe recipe,
                                                        long requestedParallelism,
                                                        List<MachineCapability> requestCapabilities,
                                                        @Nullable BlockPos requestSource,
                                                        @Nullable LongFunction<List<MachineCapability>> capabilitiesForParallelism,
                                                        boolean exact) {
        if (patternStartReservations.contains(lane) || patternPreparations.containsKey(lane) || !lane.isIdle()
                || !lane.canStartPatternRecipe(recipe)) {
            PatternStartDiagnostics.reject("factory-lane-not-ready", "lane", lane.laneId(), "idle", lane.isIdle(),
                    "reserved", patternStartReservations.contains(lane), "preparing", patternPreparations.containsKey(lane));
            return null;
        }
        SharedIoCoordinator coordinator = controller.getLevel() instanceof ServerLevel level
                ? SharedIoCoordinator.get(level) : null;
        if (coordinator != null && !coordinator.reserveExecution(lane.runtime(), lane.laneId())) {
            PatternStartDiagnostics.reject("factory-shared-io-slot", "lane", lane.laneId());
            return null;
        }
        CraftingRuntime.PreparedStart prepared = null;
        boolean reserved = false;
        boolean installed = lanes.contains(lane);
        patternPreparations.put(lane, recipe.id());
        try {
            prepared = exact ? lane.runtime().prepareExactPatternStart(recipe, requestedParallelism, requestCapabilities, requestSource)
                    : capabilitiesForParallelism == null
                    ? lane.runtime().preparePatternStart(recipe, requestedParallelism, requestCapabilities, requestSource)
                    : lane.runtime().preparePendingPatternStart(recipe, requestedParallelism, capabilitiesForParallelism, requestSource);
            if (prepared == null) {
                PatternStartDiagnostics.reject("factory-runtime-preparation", "lane", lane.laneId());
                return null;
            }
            if (installed && (!lanes.contains(lane) || !lane.canStartPatternRecipe(recipe))) {
                PatternStartDiagnostics.reject("factory-lane-invalidated", "lane", lane.laneId());
                return null;
            }
            if (!lane.runtime().reservePatternStart()) {
                PatternStartDiagnostics.reject("factory-runtime-slot", "lane", lane.laneId());
                return null;
            }
            reserved = true;
            patternStartReservations.add(lane);
            refreshLaneAccounting(lane);
            markLaneStateChanged();
            return new PatternLane(lane.runtime(), lane.laneId(), prepared);
        } finally {
            patternPreparations.remove(lane);
            if (!reserved) {
                if (prepared != null) lane.runtime().releasePatternStart(prepared);
                if (coordinator != null) coordinator.releaseExecutionReservation(lane.runtime(), lane.laneId());
            }
        }
    }

    /** Concrete factory lane selected for one admitted pattern start. */
    public record PatternLane(CraftingRuntime runtime, String laneId, CraftingRuntime.PreparedStart preparedStart) {
    }

    private LaneTickObservation acquireLaneTickObservation() {
        LaneTickObservation observation = availableTickObservations.pollFirst();
        if (observation == null) {
            observation = new LaneTickObservation();
            tickObservationBuildCountForTesting++;
        }
        return observation;
    }

    private void releaseLaneTickObservation(LaneTickObservation observation) {
        observation.clear();
        availableTickObservations.addFirst(observation);
    }

    private LaneObservation observe(FactoryRecipeThread lane) {
        refreshLaneAccounting(lane);
        return new LaneObservation(lane.runtime().observation(), lane.getStatus(), lane.isStartPending(),
                lane.getPendingStartRecipe());
    }

    private boolean matchesObservation(FactoryRecipeThread lane, LaneObservation before) {
        refreshLaneAccounting(lane);
        return before.runtime().matches(lane.runtime()) && before.status() == lane.getStatus()
                && before.startPending() == lane.isStartPending()
                && Objects.equals(before.pendingRecipe(), lane.getPendingStartRecipe());
    }

    private FactoryTickResult currentTickResult(long initialEpoch, boolean laneStateChanged) {
        recomputeFailureIfDirty();
        return new FactoryTickResult(activeLaneCount(), failure, laneStateChanged,
                initialEpoch != factoryStateEpoch);
    }

    /**
     * One exclusively borrowed factory tick baseline.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class LaneTickObservation extends CraftingRuntime.ObservationBuffer {
        private RecipeThread.Status laneStatus;
        private boolean startPending;
        private @Nullable MachineRecipe pendingRecipe;
        private @Nullable MachineRecipe recipe;

        void capture(FactoryRecipeThread lane) {
            super.capture(lane.runtime());
            laneStatus = lane.getStatus();
            startPending = lane.isStartPending();
            pendingRecipe = lane.getPendingStartRecipe();
            recipe = lane.runtime().recipe();
        }

        boolean matches(FactoryRecipeThread lane) {
            return super.matches(lane.runtime()) && laneStatus == lane.getStatus()
                    && startPending == lane.isStartPending()
                    && Objects.equals(pendingRecipe, lane.getPendingStartRecipe());
        }

        @Override
        void clear() {
            super.clear();
            pendingRecipe = null;
            recipe = null;
        }
    }

    private record LaneObservation(CraftingRuntime.StateObservation runtime, RecipeThread.Status status,
                                   boolean startPending, @Nullable MachineRecipe pendingRecipe) {
    }

    /**
     * One lane's contribution to the incremental factory counters.
     * @author howxu <dev@howxu.cn>
     */
    private record LaneAccounting(@Nullable ResourceLocation recipeId, @Nullable ResourceLocation patternRecipeId, boolean active) { }

    /** Immutable runtime-owned lane snapshot. */
    public record ThreadSnapshot(int index, String laneId, boolean baseThread, boolean coreThread, boolean active,
                                 String recipeId, int tick, int totalTick, long parallelism,
                                 @Nullable ExecutionStatus failure, ControllerRecipePresentation presentation) {
        public ThreadSnapshot(int index, boolean baseThread, boolean coreThread, boolean active,
                              String recipeId, int tick, int totalTick, long parallelism,
                              String lastFailureUnloc) {
            this(index, index == 0 ? "base" : "factory-" + index, baseThread, coreThread, active,
                    recipeId, tick, totalTick, parallelism, legacyFailure(lastFailureUnloc),
                    ControllerRecipePresentation.empty());
        }

        public ThreadSnapshot(int index, String laneId, boolean baseThread, boolean coreThread, boolean active,
                              String recipeId, int tick, int totalTick, long parallelism,
                              String lastFailureUnloc) {
            this(index, laneId, baseThread, coreThread, active, recipeId, tick, totalTick, parallelism,
                    legacyFailure(lastFailureUnloc), ControllerRecipePresentation.empty());
        }

        public ThreadSnapshot(int index, String laneId, boolean baseThread, boolean coreThread, boolean active,
                              String recipeId, int tick, int totalTick, long parallelism,
                              @Nullable ExecutionStatus failure) {
            this(index, laneId, baseThread, coreThread, active, recipeId, tick, totalTick, parallelism,
                    failure, ControllerRecipePresentation.empty());
        }

        public ThreadSnapshot {
            laneId = laneId == null ? "" : laneId;
            recipeId = recipeId == null ? "" : recipeId;
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
                    null, ControllerRecipePresentation.empty());
        }
    }
}
