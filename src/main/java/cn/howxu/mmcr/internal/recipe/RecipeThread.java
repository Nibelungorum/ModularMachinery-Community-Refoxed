package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickPhase;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.machine.definition.RecipeStartContext;
import cn.howxu.mmcr.internal.async.AsyncExecutionContext;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.async.MainThreadStep;
import cn.howxu.mmcr.internal.async.AsyncContinuation;
import cn.howxu.mmcr.internal.runtime.AsyncCraftingExecution;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Scheduling and event adapter for one crafting runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public abstract class RecipeThread {
    public enum Status { IDLE, WORKING, WAITING, FAILED }

    protected final MachineControllerBlockEntity controller;
    protected final CraftingRuntime runtime;
    protected final EffectiveRecipeSet.Cache effectiveRecipeCache = new EffectiveRecipeSet.Cache();
    private boolean startPending;
    private @Nullable MachineRecipe pendingStartRecipe;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingStartDomain;
    private long nextStartToken;
    private long pendingStartToken;
    private long pendingStartStructureVersion;
    private long pendingStartCapabilityVersion;
    private long pendingStartModifierVersion;
    private long pendingStartComponentStateVersion;
    private long pendingStartCatalogVersion;
    private @Nullable ResourceLocation pendingStartRecipePoolId;
    private @Nullable RecipeSearchContextKey pendingStartSearchContextKey;
    private boolean tickPending;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingTickDomain;
    private long nextTickToken;
    private long pendingTickToken;
    private long pendingTickCatalogVersion;
    private long lastTickRequestGameTime = Long.MIN_VALUE;

    private record StartSnapshot(ControllerRuntimeSnapshot runtime, long structureVersion, long catalogVersion,
                                 ResourceLocation recipePoolId, @Nullable RecipeSearchContextKey searchContextKey) {
    }

    private record PendingAsyncStart(ServerLevel level, StructureClaimRegistry.ResourceDomain domain,
                                     MachineRecipe recipe, long parallelism, long token, StartSnapshot snapshot) {
    }

    private @Nullable PendingAsyncStart pendingAsyncStart;
    private @Nullable RecipeStartContext.ExecutionSnapshot pendingAsyncStartExecution;
    private boolean asyncFinishPrepared;
    private boolean asyncTickCommitted;

    protected RecipeThread(MachineControllerBlockEntity controller) {
        this(controller, new CraftingRuntime(controller, controller.componentRuntime()));
    }

    protected RecipeThread(MachineControllerBlockEntity controller, CraftingRuntime runtime) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
        this.controller = controller;
        this.runtime = runtime;
    }

    public boolean searchAndStartRecipe(List<MachineRecipe> candidates, long availableParallelism, long structureVersion) {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        ResourceLocation machineId = machine == null ? null : machine.registryName();
        if (machineId == null || availableParallelism <= 0) return false;
        List<MachineRecipe> machineCandidates = candidatesForPool(candidates, controller.currentRecipePoolId());
        RecipeSearchResult result;
        try {
            result = new RecipeSearchTask(snapshot, machineId, controller.currentRecipePoolId(), structureVersion,
                    availableParallelism, machineCandidates, controller.componentRuntime().capabilities()).compute();
        } catch (RuntimeException exception) {
            controller.clearPendingConflictStart();
            onStartSearchFailed(null);
            return false;
        }
        if (!result.success()) {
            controller.clearPendingConflictStart();
            onStartSearchFailed(result.failure());
            return false;
        }
        if (controller.shouldDelayConflictProneStart(result)) return false;
        return startRecipe(result.recipe(), availableParallelism, structureVersion);
    }

    protected boolean searchAndStartRecipe(FactorySearchContext context, List<MachineRecipe> candidates,
                                           long structureVersion) {
        if (context == null) return false;
        ControllerRuntimeSnapshot snapshot = context.snapshot();
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        ResourceLocation machineId = machine == null ? null : machine.registryName();
        if (machineId == null || context.maxParallelism() <= 0) return false;
        List<MachineRecipe> machineCandidates = candidatesForPool(candidates, controller.currentRecipePoolId());
        RecipeSearchResult result;
        try {
            result = new RecipeSearchTask(snapshot, machineId, controller.currentRecipePoolId(), structureVersion,
                    context.maxParallelism(), machineCandidates,
                    context.capabilities(), MachineModifier.recipeModifiers(context.modifiers())).compute();
        } catch (RuntimeException exception) {
            controller.clearPendingConflictStart();
            onStartSearchFailed(null);
            return false;
        }
        if (!result.success()) {
            controller.clearPendingConflictStart();
            onStartSearchFailed(result.failure());
            return false;
        }
        if (controller.shouldDelayConflictProneStart(result)) return false;
        return startRecipe(result.recipe(), context.maxParallelism(), structureVersion, context);
    }

    /** Submits a pure, immutable candidate search and applies it only after server-thread validation. */
    protected boolean submitAsyncRecipeSearch(MachineAsyncCoordinator.TaskKey taskKey,
                                              AsyncRequirementPlanner.RecipeSearchRequest request,
                                              long catalogVersion, long searchId, String searchLaneId,
                                              Runnable clearPendingSearch) {
        if (!(controller.getLevel() instanceof ServerLevel level)) return false;
        AsyncRecipeSearch search = new AsyncRecipeSearch(request);
        return MachineAsyncCoordinator.get(level).submitDetailed(taskKey,
                new AsyncRecipeSearchContinuation(search, catalogVersion, searchId, searchLaneId),
                (key, step) -> applyAsyncRecipeSearch(key, step, search, catalogVersion, searchId,
                        searchLaneId, clearPendingSearch),
                new MachineAsyncCoordinator.TaskHooks(
                        () -> isAsyncRecipeSearchCurrent(taskKey, request, catalogVersion, searchLaneId),
                        (ignored, outcome) -> {
                            if (!(outcome instanceof MachineAsyncCoordinator.TaskOutcome.Succeeded)) {
                                clearPendingSearch.run();
                            }
                        })) == MachineAsyncCoordinator.SubmissionResult.ACCEPTED;
    }

    private MainThreadStep.Result applyAsyncRecipeSearch(MachineAsyncCoordinator.TaskKey taskKey, MainThreadStep step,
                                                          AsyncRecipeSearch search, long catalogVersion, long searchId,
                                                          String searchLaneId, Runnable clearPendingSearch) {
        if (!(step instanceof MainThreadStep.FactorySearch completed)
                || completed.searchId() != searchId || !searchLaneId.equals(completed.laneId())
                || !isAsyncRecipeSearchCurrent(taskKey, search.request(), catalogVersion, searchLaneId)) {
            clearPendingSearch.run();
            return MainThreadStep.Result.success();
        }
        clearPendingSearch.run();
        AsyncRequirementPlanner.RecipeSearchResult result = search.result();
        if (result == null || result.failure() != null || result.result() == null || !result.result().success()) {
            controller.clearPendingConflictStart();
            onStartSearchFailed(result == null || result.result() == null ? null : result.result().failure());
            return MainThreadStep.Result.success();
        }
        if (result.requiresMainThreadReplan()) {
            searchAndStartRecipe(search.request().candidates(), search.request().maxParallelism(),
                    search.request().structureVersion());
            return MainThreadStep.Result.success();
        }
        if (controller.shouldDelayConflictProneStart(result.result())) return MainThreadStep.Result.success();
        startRecipe(result.result().recipe(), search.request().maxParallelism(), search.request().structureVersion());
        return MainThreadStep.Result.success();
    }

    private boolean isAsyncRecipeSearchCurrent(MachineAsyncCoordinator.TaskKey taskKey,
                                               AsyncRequirementPlanner.RecipeSearchRequest request,
                                               long catalogVersion, String searchLaneId) {
        if (!controller.getBlockPos().equals(taskKey.controllerPos()) || taskKey.lifecycleEpoch() != controller.lifecycleEpoch()
                || taskKey.workMode() != MachineWorkMode.ASYNC || !searchLaneId.equals(taskKey.laneId())
                || runtime.active() || isStartPending()) return false;
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        ControllerRuntimeSnapshot captured = request.snapshot();
        Machine currentMachine = current.structure().machine() == null
                ? current.structure().configuredMachine() : current.structure().machine();
        Machine capturedMachine = captured.structure().machine() == null
                ? captured.structure().configuredMachine() : captured.structure().machine();
        return current.structure().formed() && current.structure().structureAreaLoaded()
                && current.structure().version() == captured.structure().version()
                && current.capabilityVersion() == captured.capabilityVersion()
                && current.modifierVersion() == captured.modifierVersion()
                && current.stateVersion() == captured.stateVersion()
                && currentMachine != null && capturedMachine != null
                && currentMachine.registryName().equals(capturedMachine.registryName())
                && currentCatalogVersion() == catalogVersion;
    }

    private static final class AsyncRecipeSearch {
        private final AsyncRequirementPlanner.RecipeSearchRequest request;
        private volatile @Nullable AsyncRequirementPlanner.RecipeSearchResult result;

        private AsyncRecipeSearch(AsyncRequirementPlanner.RecipeSearchRequest request) {
            this.request = request;
        }

        private AsyncRequirementPlanner.RecipeSearchRequest request() { return request; }
        private @Nullable AsyncRequirementPlanner.RecipeSearchResult result() { return result; }
    }

    private record AsyncRecipeSearchContinuation(AsyncRecipeSearch search, long catalogVersion, long searchId,
                                                 String searchLaneId) implements AsyncContinuation {

        @Override
            public Yield advance(AsyncExecutionContext context) {
                search.result = search.request().search();
                return Yield.mainThread(new MainThreadStep.FactorySearch(searchLaneId, catalogVersion, searchId),
                        ignored -> ignoredContext -> Yield.complete());
            }
        }

    private static List<MachineRecipe> candidatesForPool(List<MachineRecipe> candidates, ResourceLocation recipePoolId) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (recipePoolId == null) return List.of();
        return candidates.stream().filter(recipe -> recipe != null
                && recipePoolId.equals(recipe.recipePoolId())).toList();
    }

    protected boolean startRecipe(MachineRecipe next, long requestedParallelism, long structureVersion) {
        return startRecipe(next, requestedParallelism, structureVersion, null);
    }

    protected boolean startRecipe(MachineRecipe next, long requestedParallelism, long structureVersion,
                                  @Nullable FactorySearchContext context) {
        if (next == null || requestedParallelism <= 0) return false;
        ControllerRuntimeSnapshot currentSnapshot = controller.currentRuntimeSnapshot();
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        if (recipePoolId == null || !recipePoolId.equals(next.recipePoolId())) return false;
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (usesFullAsyncContinuation() && controller.getLevel() instanceof ServerLevel serverLevel && domain != null) {
            AsyncContinuation continuation = prepareAsyncStartContinuation(next, requestedParallelism, structureVersion,
                    context);
            if (continuation == null) return false;
            MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.get(serverLevel);
            MachineAsyncCoordinator.TaskKey taskKey = new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(),
                    serverLevel.getGameTime(), controller.activeWorkMode(), asyncLaneId(), controller.lifecycleEpoch());
            PendingAsyncStart pending = pendingAsyncStart;
            MachineAsyncCoordinator.SubmissionResult submission = coordinator.submitDetailed(taskKey, continuation,
                    this::executeAsyncMainStep, asyncStartTaskHooks(pending, taskKey.lifecycleEpoch()));
            if (submission == MachineAsyncCoordinator.SubmissionResult.ACCEPTED) return true;
            if (submission == MachineAsyncCoordinator.SubmissionResult.REJECTED) {
                rejectAsyncStart(pendingAsyncStart);
                return false;
            }
            failAsyncStart(pendingAsyncStart);
            return false;
        }
        CraftingStatus state = runtime.start(next, requestedParallelism);
        if (!state.isCrafting()) {
            controller.clearRecipeScreenText(laneId());
            onStartFailed(searchContextKeyForStart());
            return false;
        }
        onStarted();
        return true;
    }

    protected @Nullable AsyncContinuation prepareAsyncStartContinuation(MachineRecipe next, long requestedParallelism,
                                                                         long structureVersion,
                                                                         @Nullable FactorySearchContext context) {
        PendingAsyncStart pending = preparePendingAsyncStart(next, requestedParallelism, structureVersion, context);
        return pending == null ? null : AsyncCraftingExecution.start(asyncLaneId(), pending.snapshot().catalogVersion());
    }

    protected boolean enqueueAsyncFinishRestart(MachineRecipe next, long requestedParallelism, long structureVersion,
                                                @Nullable FactorySearchContext context) {
        PendingAsyncStart pending = preparePendingAsyncStart(next, requestedParallelism, structureVersion, context);
        if (pending == null) return false;
        RecipeStartContext.ExecutionSnapshot preparedStart = runtime.prepareAsyncStart(next, requestedParallelism);
        runtime.flushAsyncScreenText();
        if (preparedStart == null) {
            failAsyncStart(pending);
            return false;
        }
        boolean enqueued = requestStart(pending.level(), pending.domain(), pending.recipe(), pending.parallelism(),
                pending.token(), pending.snapshot(), preparedStart,
                () -> {
                    if (isPendingStart(pending.token(), pending.recipe())) failAsyncStart(pending);
                });
        if (enqueued) {
            pendingAsyncStart = null;
            pendingAsyncStartExecution = null;
        } else {
            failAsyncStart(pending);
        }
        return enqueued;
    }

    private @Nullable PendingAsyncStart preparePendingAsyncStart(MachineRecipe next, long requestedParallelism,
                                                                 long structureVersion,
                                                                 @Nullable FactorySearchContext context) {
        if (next == null || requestedParallelism <= 0 || pendingAsyncStart != null) return null;
        ControllerRuntimeSnapshot currentSnapshot = controller.currentRuntimeSnapshot();
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        if (recipePoolId == null || !recipePoolId.equals(next.recipePoolId())
                || !(controller.getLevel() instanceof ServerLevel serverLevel)) return null;
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (domain == null) return null;
        ControllerRuntimeSnapshot startRuntime = context == null ? currentSnapshot : context.snapshot();
        StartSnapshot startSnapshot = new StartSnapshot(startRuntime, structureVersion,
                context == null ? currentCatalogVersion() : context.catalogVersion(), recipePoolId,
                searchContextKeyForStart());
        long token = beginPendingStart(domain, next, startSnapshot);
        pendingAsyncStart = new PendingAsyncStart(serverLevel, domain, next, requestedParallelism, token, startSnapshot);
        pendingAsyncStartExecution = null;
        return pendingAsyncStart;
    }

    private long beginPendingStart(StructureClaimRegistry.ResourceDomain domain, MachineRecipe next,
                                   StartSnapshot startSnapshot) {
        long token = ++nextStartToken;
        startPending = true;
        pendingStartRecipe = next;
        pendingStartDomain = domain;
        pendingStartToken = token;
        pendingStartStructureVersion = startSnapshot.structureVersion();
        pendingStartCapabilityVersion = startSnapshot.runtime().capabilityVersion();
        pendingStartModifierVersion = startSnapshot.runtime().modifierVersion();
        pendingStartComponentStateVersion = startSnapshot.runtime().stateVersion();
        pendingStartCatalogVersion = startSnapshot.catalogVersion();
        pendingStartRecipePoolId = startSnapshot.recipePoolId();
        pendingStartSearchContextKey = startSnapshot.searchContextKey();
        return token;
    }

    private boolean requestStart(ServerLevel level, StructureClaimRegistry.ResourceDomain domain,
                                 MachineRecipe next, long requestedParallelism, long token,
                                 StartSnapshot startSnapshot, RecipeStartContext.ExecutionSnapshot preparedStart,
                                 Runnable completion) {
        if (!isPendingStart(token, next)) return false;
        long lifecycleEpoch = controller.lifecycleEpoch();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.StartRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                startSnapshot.structureVersion(),
                startSnapshot.runtime().stateVersion(),
                requestedParallelism,
                requested -> {
                    if (!isPendingStart(token, next) || runtime.active()) return 0L;
                    CraftingStatus state = runtime.start(next, requested, preparedStart);
                    if (!state.isCrafting()) {
                        controller.clearRecipeScreenText(laneId());
                        RecipeSearchContextKey failureKey = pendingStartSearchContextKey;
                        clearPendingStart(token, next);
                        onStartFailed(failureKey);
                        controller.syncRecipeRuntimeFailure(runtime);
                        completion.run();
                        return 0L;
                    }
                    return runtime.parallelism();
                },
                granted -> {
                    if (!isPendingStart(token, next) || !runtime.active()) return;
                    clearPendingStart(token, next);
                    onStarted();
                    controller.syncRecipeRuntimeFailure(runtime);
                },
                () -> {
                    boolean valid = lifecycleEpoch == controller.lifecycleEpoch()
                            && isPendingStart(token, next) && domain.equals(controller.resourceDomain());
                    if (!valid) completion.run();
                    return valid;
                },
                () -> controller.currentRuntimeSnapshot().structure().version(),
                () -> controller.currentRuntimeSnapshot().stateVersion(),
                pendingStartCatalogVersion,
                this::currentCatalogVersion,
                completion
        ));
        return true;
    }

    private void failAsyncStart(PendingAsyncStart pending) {
        if (pending == null || !isPendingStartGeneration(pending)) return;
        RecipeSearchContextKey failureKey = pending.snapshot().searchContextKey();
        if (pendingAsyncStart == pending) {
            pendingAsyncStart = null;
            pendingAsyncStartExecution = null;
        }
        clearPendingStart(pending.token(), pending.recipe());
        controller.clearRecipeScreenText(laneId());
        onStartFailed(failureKey);
        controller.syncRecipeRuntimeFailure(runtime);
    }

    private void rejectAsyncStart(PendingAsyncStart pending) {
        if (pending == null) return;
        pendingAsyncStart = null;
        pendingAsyncStartExecution = null;
        clearPendingStart(pending.token(), pending.recipe());
    }

    private boolean isPendingStart(long token, MachineRecipe recipe) {
        if (!startPending || pendingStartToken != token || pendingStartRecipe != recipe) return false;
        if (controller.isRedstonePaused()) {
            invalidatePendingStart(token, recipe);
            return false;
        }
        if (pendingStartDomain == null || !pendingStartDomain.equals(controller.resourceDomain())) {
            invalidatePendingStart(token, recipe);
            return false;
        }
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        if (snapshot.structure().version() != pendingStartStructureVersion
                || snapshot.capabilityVersion() != pendingStartCapabilityVersion
                || snapshot.modifierVersion() != pendingStartModifierVersion
                || snapshot.stateVersion() != pendingStartComponentStateVersion) {
            invalidatePendingStart(token, recipe);
            return false;
        }
        if (pendingStartRecipePoolId == null
                || !pendingStartRecipePoolId.equals(controller.currentRecipePoolId())
                || !pendingStartRecipePoolId.equals(recipe.recipePoolId())) {
            invalidatePendingStartForCatalog(token, recipe);
            return false;
        }
        if (currentCatalogVersion() != pendingStartCatalogVersion) {
            invalidatePendingStartForCatalog(token, recipe);
            return false;
        }
        return true;
    }

    private boolean isPendingStartGeneration(PendingAsyncStart pending) {
        return startPending && pendingStartToken == pending.token() && pendingStartRecipe == pending.recipe();
    }

    private MachineAsyncCoordinator.TaskHooks asyncStartTaskHooks(PendingAsyncStart pending, long lifecycleEpoch) {
        return new MachineAsyncCoordinator.TaskHooks(
                () -> lifecycleEpoch == controller.lifecycleEpoch() && isPendingStartGeneration(pending),
                (ignored, outcome) -> {
                    if (!(outcome instanceof MachineAsyncCoordinator.TaskOutcome.Succeeded)
                            && lifecycleEpoch == controller.lifecycleEpoch() && isPendingStartGeneration(pending)) {
                        failAsyncStart(pending);
                    }
                });
    }

    private long currentCatalogVersion() {
        return RecipeRegistry.catalogForPool(controller.currentRecipePoolId()).version();
    }

    private void invalidatePendingStart(long token, MachineRecipe recipe) {
        clearPendingStart(token, recipe);
        runtime.invalidate();
        controller.clearRecipeScreenText(laneId());
        controller.syncRecipeRuntimeFailure(runtime);
    }

    private void clearPendingStart(long token, MachineRecipe recipe) {
        if (!startPending || pendingStartToken != token || pendingStartRecipe != recipe) return;
        startPending = false;
        pendingStartRecipe = null;
        pendingStartDomain = null;
        pendingStartToken = 0L;
        pendingStartStructureVersion = Long.MIN_VALUE;
        pendingStartCapabilityVersion = Long.MIN_VALUE;
        pendingStartModifierVersion = Long.MIN_VALUE;
        pendingStartComponentStateVersion = Long.MIN_VALUE;
        pendingStartCatalogVersion = Long.MIN_VALUE;
        pendingStartRecipePoolId = null;
        pendingStartSearchContextKey = null;
        if (!runtime.active()) runtime.completeStressHandoff(false);
    }

    private void invalidatePendingStartForCatalog(long token, MachineRecipe recipe) {
        clearPendingStart(token, recipe);
        runtime.invalidate();
        controller.clearRecipeScreenText(laneId());
        onPendingStartCatalogChanged();
        controller.syncRecipeRuntimeFailure(runtime);
    }

    public void tick() {
        tick(null);
    }

    public void tick(@Nullable ControllerRuntimeSnapshot tickSnapshot) {
        runtime.expireStressHandoff();
        if (startPending && !isPendingStart(pendingStartToken, pendingStartRecipe)) {
            clearPendingStart(pendingStartToken, pendingStartRecipe);
            controller.clearRecipeScreenText(laneId());
        }
        if (!runtime.active()) return;
        runtime.beginStressHandoff();
        if (!usesAsyncPlanning()) {
            tickSynchronously();
            return;
        }
        if (!usesFullAsyncContinuation() && runtime.finishPending()) {
            finishSynchronously();
            return;
        }
        if (tickPending && !validateCurrentRuntime(pendingTickToken, pendingTickDomain)) clearPendingTick();
        if (tickPending) return;

        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (controller.getLevel() instanceof ServerLevel level && domain != null) {
            if (runtime.finishPending()) {
                if (!runtime.shouldRetryFinish()) return;
                long token = beginPendingTick(domain);
                requestFinish(level, domain, token);
            } else {
                requestTick(level, domain, tickSnapshot);
            }
            return;
        }
        boolean wasActive = runtime.active();
        runtime.tick();
        if (runtime.finishPending()) runtime.finish();
        completeIfFinished(wasActive);
    }

    private boolean usesAsyncPlanning() {
        return controller.activeWorkMode() != MachineWorkMode.SYNC;
    }

    private boolean usesFullAsyncContinuation() {
        return controller.activeWorkMode() == MachineWorkMode.ASYNC;
    }

    private void tickSynchronously() {
        boolean wasActive = runtime.active();
        runtime.tick();
        if (runtime.finishPending()) runtime.finish();
        completeIfFinished(wasActive);
    }

    private void finishSynchronously() {
        boolean wasActive = runtime.active();
        runtime.finish();
        completeIfFinished(wasActive);
    }

    private void requestTick(ServerLevel level, StructureClaimRegistry.ResourceDomain domain,
                             @Nullable ControllerRuntimeSnapshot tickSnapshot) {
        long gameTime = level.getGameTime();
        if (lastTickRequestGameTime == gameTime) return;
        // Cancellation and completion must not allow another preparation in the same world tick.
        lastTickRequestGameTime = gameTime;
        long token = beginPendingTick(domain);
        ControllerRuntimeSnapshot runtimeSnapshot = tickSnapshot == null
                ? controller.currentRuntimeSnapshot() : tickSnapshot;
        long structureVersion = runtimeSnapshot.structure().version();
        long stateVersion = runtimeSnapshot.stateVersion();
        long catalogVersion = currentCatalogVersion();
        long lifecycleEpoch = controller.lifecycleEpoch();
        AsyncRequirementPlanner.PreparedPlan preparedPlan = runtime.prepareAsyncTickPlan(runtimeSnapshot);
        if (preparedPlan == null) {
            finishAsyncTick();
            return;
        }
        MachineAsyncCoordinator.TaskKey taskKey = new MachineAsyncCoordinator.TaskKey(
                controller.getBlockPos(), gameTime, controller.activeWorkMode(),
                asyncLaneId(), lifecycleEpoch);
        if (!requiresWorkerPlanning(preparedPlan)) {
            AsyncRequirementPlanner.PlanResult mainThreadPlan = new AsyncRequirementPlanner.PlanResult(
                    List.of(), preparedPlan.initialMainThreadRequirements());
            MachineAsyncCoordinator.SubmissionResult submission = MachineAsyncCoordinator.get(level).submitMainThread(
                    taskKey, new MainThreadStep.TickTransitionCommit(asyncLaneId(), catalogVersion, mainThreadPlan),
                    this::executeAsyncMainStep, tickTaskHooks(token, lifecycleEpoch));
            if (submission != MachineAsyncCoordinator.SubmissionResult.ACCEPTED) {
                failAsyncTick(token, lifecycleEpoch);
            }
            return;
        }
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.TickRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                stateVersion,
                () -> {
                    boolean enqueued = SharedIoCoordinator.get(level).enqueueTickWork(level, domain, taskKey,
                            preparedPlan, intent -> commitTickWorksetIntent(token, domain, lifecycleEpoch,
                                    catalogVersion, intent), () -> failAsyncTick(token, lifecycleEpoch));
                    if (!enqueued) failAsyncTick(token, lifecycleEpoch);
                    return enqueued;
                },
                () -> {
                    boolean valid = lifecycleEpoch == controller.lifecycleEpoch()
                            && validateCurrentRuntime(token, domain);
                      if (!valid) clearPendingTick();
                      return valid;
                },
                  () -> controller.currentStructureSnapshot().version(),
                  () -> controller.componentRuntime().stateVersion(),
                   catalogVersion,
                  this::currentCatalogVersion,
                  () -> { }
          ));
    }

    private void commitTickWorksetIntent(long token, StructureClaimRegistry.ResourceDomain domain,
                                         long lifecycleEpoch, long catalogVersion,
                                         AsyncRequirementPlanner.PlanResult intent) {
        if (lifecycleEpoch != controller.lifecycleEpoch() || !validateCurrentRuntime(token, domain)
                || catalogVersion != currentCatalogVersion()) {
            clearPendingTick();
            return;
        }
        boolean committed = runtime.commitAsyncTick(intent);
        if (committed && runtime.completeAsyncTickAfterInputs()) {
            runtime.completeAsyncTickAfterRecipe();
        } else if (!committed) {
            runtime.discardAsyncTickPreparation();
        }
        finishAsyncTick();
    }

    private void requestFinish(ServerLevel level, StructureClaimRegistry.ResourceDomain domain, long token) {
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.get(level);
        MachineAsyncCoordinator.TaskKey key = new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(),
                level.getGameTime(), controller.activeWorkMode(), asyncLaneId(), controller.lifecycleEpoch());
        if (coordinator.submitDetailed(key, AsyncCraftingExecution.finish(asyncLaneId(), pendingTickCatalogVersion),
                this::executeAsyncMainStep, tickTaskHooks(token, key.lifecycleEpoch()))
                != MachineAsyncCoordinator.SubmissionResult.ACCEPTED) clearPendingTick();
    }

    private void enqueueFinish(ServerLevel level, StructureClaimRegistry.ResourceDomain domain, long token,
                               MachineAsyncCoordinator.TaskKey key, long catalogVersion) {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        long structureVersion = snapshot.structure().version();
        long lifecycleEpoch = controller.lifecycleEpoch();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.FinishRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                snapshot.stateVersion(),
                () -> {
                    if (!validateCurrentRuntime(token, domain)) return false;
                    boolean wasActive = runtime.active();
                    runtime.finish();
                    completeIfFinished(wasActive);
                    controller.syncRecipeRuntimeFailure(runtime);
                    return true;
                 },
                 () -> {
                    boolean runtimeValid = lifecycleEpoch == controller.lifecycleEpoch()
                            && validateCurrentRuntime(token, domain);
                    boolean valid = catalogVersion == currentCatalogVersion() && runtimeValid;
                    if (!valid) {
                        clearPendingTick();
                        MachineAsyncCoordinator.get(level).resume(key);
                    }
                    return valid;
                },
                 () -> controller.currentRuntimeSnapshot().structure().version(),
                 () -> controller.currentRuntimeSnapshot().stateVersion(),
                 catalogVersion,
                 this::currentCatalogVersion,
                 () -> {
                     controller.notifyResourceAvailability(ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY, null);
                     AsyncContinuation restart = consumeAsyncFinishRestart();
                     MachineAsyncCoordinator.get(level).resume(key, restart == null
                             ? MainThreadStep.Result.success() : MainThreadStep.Result.value(restart));
                 }
        ));
    }

    private long beginPendingTick(StructureClaimRegistry.ResourceDomain domain) {
        tickPending = true;
        asyncTickCommitted = false;
        pendingTickDomain = domain;
        pendingTickToken = ++nextTickToken;
        pendingTickCatalogVersion = currentCatalogVersion();
        return pendingTickToken;
    }

    private MainThreadStep.Result executeAsyncMainStep(MachineAsyncCoordinator.TaskKey key, MainThreadStep step) {
        if (!validateAsyncMainStep(key, step)) {
            discardInvalidAsyncStart(step);
            if (pendingAsyncStart == null) finishAsyncTick();
            return MainThreadStep.Result.failure(new IllegalStateException("Async continuation validation failed"));
        }
        if (step instanceof MainThreadStep.Lifecycle lifecycle) {
            if (lifecycle.kind() == MainThreadStep.Kind.BEFORE_START) {
                PendingAsyncStart pending = pendingAsyncStart;
                if (pending == null) {
                    return MainThreadStep.Result.failure(new IllegalStateException("Missing async start lifecycle"));
                }
                pendingAsyncStartExecution = runtime.prepareAsyncStart(pending.recipe(), pending.parallelism());
                if (pendingAsyncStartExecution == null) {
                    failAsyncStart(pending);
                    return MainThreadStep.Result.value(false);
                }
            } else if (lifecycle.kind() == MainThreadStep.Kind.BEFORE_FINISH) {
                asyncFinishPrepared = runtime.prepareAsyncFinish();
            }
            return validatedAsyncResult(key, step, MainThreadStep.Result.success());
        }
        if (step instanceof MainThreadStep.CapabilityTick capabilityTick) {
            if (capabilityTick.phase() == CapabilityTickPhase.AFTER_INPUTS) {
                boolean completed = asyncTickCommitted && runtime.completeAsyncTickAfterInputs();
                if (!completed) finishAsyncTick();
                return validatedAsyncResult(key, step, MainThreadStep.Result.value(completed));
            }
            if (capabilityTick.phase() == CapabilityTickPhase.AFTER_RECIPE) {
                runtime.completeAsyncTickAfterRecipe();
                MainThreadStep.Result result = validatedAsyncResult(key, step, MainThreadStep.Result.success());
                if (result instanceof MainThreadStep.Result.Failure) return result;
                finishAsyncTick();
                return result;
            }
            if (!runtime.executeAsyncCapabilityTick(capabilityTick.phase())) {
                runtime.discardAsyncTickPreparation();
                finishAsyncTick();
                return MainThreadStep.Result.value(false);
            }
            return validatedAsyncResult(key, step, MainThreadStep.Result.success());
        }
        if (step instanceof MainThreadStep.ScreenTextFlush screenTextFlush) {
            runtime.flushAsyncScreenText();
            return validatedAsyncResult(key, step, MainThreadStep.Result.success());
        }
        if (step instanceof MainThreadStep.SharedIoRequest request) {
            if (request.kind() == MainThreadStep.Kind.BEFORE_START) {
                PendingAsyncStart pending = pendingAsyncStart;
                RecipeStartContext.ExecutionSnapshot preparedStart = pendingAsyncStartExecution;
                pendingAsyncStart = null;
                pendingAsyncStartExecution = null;
                if (pending == null) {
                    return MainThreadStep.Result.failure(new IllegalStateException("Missing async start continuation"));
                }
                if (preparedStart == null) return MainThreadStep.Result.success();
                if (requestStart(pending.level(), pending.domain(), pending.recipe(), pending.parallelism(), pending.token(),
                        pending.snapshot(), preparedStart, () -> MachineAsyncCoordinator.get(pending.level()).resume(key))) {
                    return MainThreadStep.Result.pending();
                }
                return MainThreadStep.Result.value(false);
            }
            if (request.kind() == MainThreadStep.Kind.BEFORE_FINISH) {
                if (controller.getLevel() instanceof ServerLevel level && pendingTickDomain != null) {
                    if (!asyncFinishPrepared) {
                        clearPendingTick();
                        controller.syncRecipeRuntimeFailure(runtime);
                        return MainThreadStep.Result.success();
                    }
                    asyncFinishPrepared = false;
                    enqueueFinish(level, pendingTickDomain, pendingTickToken, key, request.catalogVersion());
                    return MainThreadStep.Result.pending();
                }
                return MainThreadStep.Result.failure(new IllegalStateException("Missing async finish continuation"));
            }
        }
        if (step instanceof MainThreadStep.TickTransitionCommit transition) {
            if (controller.getLevel() instanceof ServerLevel level && pendingTickDomain != null) {
                enqueueAsyncTickTransition(level, pendingTickDomain, pendingTickToken, key,
                        transition.catalogVersion(), transition.intent());
                return MainThreadStep.Result.pending();
            }
            return MainThreadStep.Result.failure(new IllegalStateException("Missing async tick transition context"));
        }
        if (step instanceof MainThreadStep.UnsupportedRequirement unsupported) {
            if (controller.getLevel() instanceof ServerLevel level && pendingTickDomain != null) {
                enqueueAsyncTickIntent(level, pendingTickDomain, pendingTickToken, key,
                        unsupported.catalogVersion(), unsupported.intent());
                return MainThreadStep.Result.pending();
            }
            return MainThreadStep.Result.failure(new IllegalStateException("Missing async tick fallback context"));
        }
        if (step instanceof MainThreadStep.IntentCommit intent) {
            if (controller.getLevel() instanceof ServerLevel level && pendingTickDomain != null) {
                enqueueAsyncTickIntent(level, pendingTickDomain, pendingTickToken, key, intent.catalogVersion(), intent.intent());
                return MainThreadStep.Result.pending();
            }
            return MainThreadStep.Result.failure(new IllegalStateException("Missing async intent context"));
        }
        MainThreadStep.Result result = step.execute();
        return validatedAsyncResult(key, step, result);
    }

    private MainThreadStep.Result validatedAsyncResult(MachineAsyncCoordinator.TaskKey key, MainThreadStep step,
                                                        MainThreadStep.Result result) {
        if (result instanceof MainThreadStep.Result.Pending || validateAsyncMainStep(key, step)) return result;
        discardInvalidAsyncStart(step);
        if (pendingAsyncStart == null) finishAsyncTick();
        return MainThreadStep.Result.failure(new IllegalStateException("Async continuation became stale"));
    }

    private void discardInvalidAsyncStart(MainThreadStep step) {
        if (!isAsyncStartStep(step)) return;
        PendingAsyncStart pending = pendingAsyncStart;
        if (pending == null) return;
        pendingAsyncStart = null;
        pendingAsyncStartExecution = null;
        if (currentCatalogVersion() != pending.snapshot().catalogVersion()) {
            invalidatePendingStartForCatalog(pending.token(), pending.recipe());
        } else {
            invalidatePendingStart(pending.token(), pending.recipe());
        }
    }

    private static boolean isAsyncStartStep(MainThreadStep step) {
        return step instanceof MainThreadStep.SharedIoRequest request && request.kind() == MainThreadStep.Kind.BEFORE_START
                || step instanceof MainThreadStep.Lifecycle lifecycle && lifecycle.kind() == MainThreadStep.Kind.BEFORE_START
                || step instanceof MainThreadStep.ScreenTextFlush screenTextFlush
                && screenTextFlush.source() == MainThreadStep.Kind.BEFORE_START;
    }

    private boolean validateAsyncMainStep(MachineAsyncCoordinator.TaskKey key, MainThreadStep step) {
        if (!controller.getBlockPos().equals(key.controllerPos())
                || key.lifecycleEpoch() != controller.lifecycleEpoch()) return false;
        if ((step instanceof MainThreadStep.SharedIoRequest request && request.kind() == MainThreadStep.Kind.BEFORE_START)
                || (step instanceof MainThreadStep.Lifecycle lifecycle && lifecycle.kind() == MainThreadStep.Kind.BEFORE_START)
                || (step instanceof MainThreadStep.ScreenTextFlush screenTextFlush
                && screenTextFlush.source() == MainThreadStep.Kind.BEFORE_START)) {
            PendingAsyncStart pending = pendingAsyncStart;
            if (pending == null || controller.isRedstonePaused() || !pending.domain().equals(controller.resourceDomain())) {
                return false;
            }
            StartSnapshot snapshot = pending.snapshot();
            ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
            long catalogVersion = step instanceof MainThreadStep.SharedIoRequest request
                    ? request.catalogVersion() : step instanceof MainThreadStep.Lifecycle lifecycle
                    ? lifecycle.catalogVersion() : ((MainThreadStep.ScreenTextFlush) step).catalogVersion();
            return catalogVersion == currentCatalogVersion()
                    && snapshot.catalogVersion() == currentCatalogVersion()
                    && snapshot.structureVersion() == current.structure().version()
                    && snapshot.runtime().capabilityVersion() == current.capabilityVersion()
                    && snapshot.runtime().modifierVersion() == current.modifierVersion()
                    && snapshot.runtime().stateVersion() == current.stateVersion()
                    && snapshot.recipePoolId().equals(controller.currentRecipePoolId())
                    && snapshot.recipePoolId().equals(pending.recipe().recipePoolId());
        }
        long catalogVersion = step instanceof MainThreadStep.TickTransitionCommit transition ? transition.catalogVersion()
                : step instanceof MainThreadStep.IntentCommit intent ? intent.catalogVersion()
                : step instanceof MainThreadStep.UnsupportedRequirement unsupported ? unsupported.catalogVersion()
                : step instanceof MainThreadStep.SharedIoRequest request ? request.catalogVersion()
                : step instanceof MainThreadStep.Lifecycle lifecycle ? lifecycle.catalogVersion()
                : step instanceof MainThreadStep.CapabilityTick capabilityTick ? capabilityTick.catalogVersion()
                : step instanceof MainThreadStep.ScreenTextFlush screenTextFlush ? screenTextFlush.catalogVersion()
                : Long.MIN_VALUE;
        if (catalogVersion == Long.MIN_VALUE) return true;
        boolean runtimeValid = validateCurrentRuntime(pendingTickToken, pendingTickDomain);
        return catalogVersion == currentCatalogVersion() && runtimeValid;
    }

    private void enqueueAsyncTickIntent(ServerLevel level, StructureClaimRegistry.ResourceDomain domain, long token,
                                        MachineAsyncCoordinator.TaskKey key, long catalogVersion,
                                        AsyncRequirementPlanner.PlanResult intent) {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        long lifecycleEpoch = controller.lifecycleEpoch();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.TickRequest(domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()), snapshot.structure().version(),
                snapshot.stateVersion(), () -> {
                    asyncTickCommitted = runtime.commitAsyncTick(intent);
                    return true;
                }, () -> {
                    boolean valid = lifecycleEpoch == controller.lifecycleEpoch()
                            && validateCurrentRuntime(token, domain);
                    if (!valid) {
                        clearPendingTick();
                        MachineAsyncCoordinator.get(level).resume(key);
                    }
                    return valid;
                }, () -> controller.currentRuntimeSnapshot().structure().version(),
                () -> controller.currentRuntimeSnapshot().stateVersion(), catalogVersion, this::currentCatalogVersion,
                 () -> MachineAsyncCoordinator.get(level).resume(key)));
    }

    private void enqueueAsyncTickTransition(ServerLevel level, StructureClaimRegistry.ResourceDomain domain, long token,
                                            MachineAsyncCoordinator.TaskKey key, long catalogVersion,
                                            AsyncRequirementPlanner.PlanResult intent) {
        long structureVersion = controller.currentStructureSnapshot().version();
        long stateVersion = controller.componentRuntime().stateVersion();
        long lifecycleEpoch = controller.lifecycleEpoch();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.TickRequest(domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()), structureVersion,
                stateVersion, () -> {
                    boolean committed = runtime.commitAsyncTick(intent);
                    if (committed && runtime.completeAsyncTickAfterInputs()) {
                        runtime.completeAsyncTickAfterRecipe();
                    } else if (!committed) {
                        runtime.discardAsyncTickPreparation();
                    }
                    finishAsyncTick();
                    MachineAsyncCoordinator.get(level).complete(key);
                    return true;
                }, () -> {
                    boolean valid = lifecycleEpoch == controller.lifecycleEpoch()
                            && validateCurrentRuntime(token, domain);
                    if (!valid) {
                        clearPendingTick();
                        MachineAsyncCoordinator.get(level).complete(key);
                    }
                    return valid;
                }, () -> controller.currentStructureSnapshot().version(),
                () -> controller.componentRuntime().stateVersion(), catalogVersion, this::currentCatalogVersion,
                () -> MachineAsyncCoordinator.get(level).complete(key)));
    }

    private boolean validateCurrentRuntime(long token, @Nullable StructureClaimRegistry.ResourceDomain domain) {
        if (!tickPending || pendingTickToken != token) return false;
        if (controller.isRedstonePaused()) {
            clearPendingTick();
            return false;
        }
        if (!runtime.active()) {
            clearPendingTick();
            return false;
        }
        if (currentCatalogVersion() != pendingTickCatalogVersion) {
            boolean wasActive = runtime.active();
            runtime.invalidateForCatalogChange();
            completeIfFinished(wasActive);
            controller.syncRecipeRuntimeFailure(runtime);
            return false;
        }
        if (!runtime.versionsCurrent()) {
            boolean wasActive = runtime.active();
            runtime.tick();
            completeIfFinished(wasActive);
            controller.syncRecipeRuntimeFailure(runtime);
            return false;
        }
        if (domain == null || !domain.equals(controller.resourceDomain())) {
            clearPendingTick();
            return false;
        }
        return true;
    }

    private void clearPendingTick() {
        tickPending = false;
        pendingTickDomain = null;
        pendingTickToken = 0L;
        pendingTickCatalogVersion = Long.MIN_VALUE;
        asyncTickCommitted = false;
    }

    private void finishAsyncTick() {
        boolean wasActive = runtime.active();
        clearPendingTick();
        completeIfFinished(wasActive);
        controller.syncRecipeRuntimeFailure(runtime);
    }

    private void failAsyncTick(long token, long lifecycleEpoch) {
        if (lifecycleEpoch != controller.lifecycleEpoch() || !tickPending || pendingTickToken != token) return;
        runtime.discardAsyncTickPreparation();
        finishAsyncTick();
    }

    static boolean requiresWorkerPlanning(AsyncRequirementPlanner.PreparedPlan plan) {
        return !plan.requirements().isEmpty();
    }

    private MachineAsyncCoordinator.TaskHooks tickTaskHooks(long token, long lifecycleEpoch) {
        return new MachineAsyncCoordinator.TaskHooks(
                () -> lifecycleEpoch == controller.lifecycleEpoch()
                        && tickPending && pendingTickToken == token,
                (ignored, outcome) -> {
                    if (!(outcome instanceof MachineAsyncCoordinator.TaskOutcome.Succeeded)) {
                        failAsyncTick(token, lifecycleEpoch);
                    }
                });
    }

    boolean tickPendingForTesting() {
        return tickPending;
    }

    private void completeIfFinished(boolean wasActive) {
        clearPendingTick();
        if (wasActive && !runtime.active()) {
            if (runtime.failure() == null) {
                controller.clearRecipeScreenText(laneId());
                try {
                    onRecipeFinished();
                    onFinished();
                } finally {
                    runtime.completeStressHandoff(isStartPending());
                }
            } else {
                onRecipeFailure();
                controller.clearRecipeScreenText(laneId());
            }
        }
    }

    protected @Nullable AsyncContinuation consumeAsyncFinishRestart() {
        return null;
    }

    public void invalidate() {
        runtime.invalidate();
        controller.clearRecipeScreenText(laneId());
        pendingAsyncStart = null;
        pendingAsyncStartExecution = null;
        asyncFinishPrepared = false;
        startPending = false;
        pendingStartRecipe = null;
        pendingStartDomain = null;
        pendingStartToken = 0L;
        pendingStartStructureVersion = Long.MIN_VALUE;
        pendingStartCapabilityVersion = Long.MIN_VALUE;
        pendingStartModifierVersion = Long.MIN_VALUE;
        pendingStartComponentStateVersion = Long.MIN_VALUE;
        pendingStartCatalogVersion = Long.MIN_VALUE;
        pendingStartRecipePoolId = null;
        pendingStartSearchContextKey = null;
          clearPendingTick();
    }

    /** Clears deferred async work without invalidating a recipe that may resume after a pause. */
    public void cancelAsyncState() {
        pendingAsyncStart = null;
        pendingAsyncStartExecution = null;
        asyncFinishPrepared = false;
        clearPendingStartUnconditionally();
        clearPendingTick();
        if (!runtime.active()) runtime.completeStressHandoff(false);
    }

    public void discardForRecipePoolChange() {
        cancelAsyncState();
        clearPendingStartUnconditionally();
        runtime.invalidate();
        controller.clearRecipeScreenText(laneId());
        onDiscardedForRecipePoolChange();
    }

    private void clearPendingStartUnconditionally() {
        if (startPending) clearPendingStart(pendingStartToken, pendingStartRecipe);
    }

    protected void onDiscardedForRecipePoolChange() { }

    public void invalidateForSmartInterfaceChange() {
        runtime.invalidateForSmartInterfaceChange();
        if (runtime.active()) return;
        if (startPending) clearPendingStart(pendingStartToken, pendingStartRecipe);
        clearPendingTick();
        controller.clearRecipeScreenText(laneId());
    }

    protected void onStartSearchFailed(@Nullable ExecutionStatus failure) {
        runtime.recordSearchFailure(failure);
    }

    protected abstract void onStarted();
    protected abstract void onFinished();
    protected void onRecipeFinished() { }
    protected void onRecipeFailure() { }
    protected void onStartFailed() { }
    protected void onStartFailed(@Nullable RecipeSearchContextKey contextKey) { onStartFailed(); }
    protected void onPendingStartCatalogChanged() { }
    protected @Nullable RecipeSearchContextKey searchContextKeyForStart() { return null; }
    protected String laneId() { return "base"; }
    public final String asyncLaneId() { return laneId(); }

    public Status getStatus() {
        if (runtime.active()) return runtime.finishPending() ? Status.WAITING : Status.WORKING;
        return runtime.failure() == null ? Status.IDLE : Status.FAILED;
    }
    public boolean isIdle() { return !startPending && !runtime.active(); }
    public boolean isStartPending() { return startPending; }
    public @Nullable MachineRecipe getPendingStartRecipe() { return pendingStartRecipe; }
    public long usedParallelism() { return runtime.parallelism(); }
    public CraftingRuntime runtime() { return runtime; }

}
