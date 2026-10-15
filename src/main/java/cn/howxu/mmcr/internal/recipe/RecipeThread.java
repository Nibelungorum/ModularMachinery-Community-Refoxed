package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickPhase;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
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
import cn.howxu.mmcr.internal.runtime.InstalledRecipeProgram;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduling and event adapter for one crafting runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public abstract class RecipeThread {
    public enum Status { IDLE, WORKING, WAITING, FAILED }

    /** @author howxu <dev@howxu.cn> */
    public enum StartResult {
        STARTED, PENDING, DEFERRED, NO_MATCH, FAILED, CANCELLED;

        public boolean ownsStart() { return this == STARTED || this == PENDING; }
    }

    /** @author howxu <dev@howxu.cn> */
    public enum StartOrigin { SEARCH, LAST_RECIPE }

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
    private long pendingStartUpgradeRevision;
    private long pendingStartCatalogVersion;
    private @Nullable ResourceLocation pendingStartRecipePoolId;
    private @Nullable RecipeSearchContextKey pendingStartSearchContextKey;
    private StartOrigin pendingStartOrigin = StartOrigin.SEARCH;
    private long pendingStartLifecycleEpoch;
    private boolean fullSearchPending;
    private boolean tickPending;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingTickDomain;
    private long nextTickToken;
    private long pendingTickToken;
    private long pendingTickCatalogVersion;
    private long lastTickRequestGameTime = Long.MIN_VALUE;

    private record StartSnapshot(ControllerRuntimeSnapshot runtime, long structureVersion, long catalogVersion,
                                 ResourceLocation recipePoolId, @Nullable RecipeSearchContextKey searchContextKey,
                                 StartOrigin origin, long lifecycleEpoch, long inputIsolationChangeVersion) {
    }

    private record PendingAsyncStart(ServerLevel level, StructureClaimRegistry.ResourceDomain domain,
                                     MachineRecipe recipe, long parallelism, long token, StartSnapshot snapshot) {
    }

    private @Nullable PendingAsyncStart pendingAsyncStart;
    private @Nullable RecipeStartContext.ExecutionSnapshot pendingAsyncStartExecution;
    private boolean asyncFinishPrepared;
    private boolean asyncTickCommitted;
    private @Nullable MachineAsyncCoordinator.TaskKey programCompilationTask;
    private @Nullable AtomicBoolean programCompilationValidity;

    protected RecipeThread(MachineControllerBlockEntity controller) {
        this(controller, new CraftingRuntime(controller, controller.componentRuntime()));
    }

    protected RecipeThread(MachineControllerBlockEntity controller, CraftingRuntime runtime) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
        this.controller = controller;
        this.runtime = runtime;
        runtime.bindRecipeLane("base", this::completeInstalledAttempt);
    }

    public StartResult searchAndStartRecipe(List<MachineRecipe> candidates, long availableParallelism, long structureVersion) {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        ResourceLocation machineId = machine == null ? null : machine.registryName();
        if (runtime.active() || isStartPending()) return StartResult.DEFERRED;
        if (machineId == null || availableParallelism <= 0) return StartResult.NO_MATCH;
        List<MachineRecipe> machineCandidates = candidatesForPool(candidates, controller.currentRecipePoolId());
        RecipeSearchResult result;
        try {
            result = new RecipeSearchTask(snapshot, machineId, controller.currentRecipePoolId(), structureVersion,
                    availableParallelism, machineCandidates, controller.componentRuntime().capabilitySnapshot()).compute(runtime);
        } catch (RuntimeException exception) {
            controller.clearPendingConflictStart();
            onStartSearchFailed(null);
            return StartResult.FAILED;
        }
        if (!result.success()) {
            controller.clearPendingConflictStart();
            onRecipeSearchFailed(result);
            return StartResult.NO_MATCH;
        }
        if (!searchSnapshotCurrent(snapshot)) return StartResult.DEFERRED;
        if (controller.shouldDelayConflictProneStart(result)) return StartResult.DEFERRED;
        return startRecipe(result.recipe(), availableParallelism, structureVersion);
    }

    protected StartResult searchAndStartRecipe(FactorySearchContext context, List<MachineRecipe> candidates,
                                           long structureVersion) {
        if (runtime.active() || isStartPending()) return StartResult.DEFERRED;
        if (context == null) return StartResult.NO_MATCH;
        ControllerRuntimeSnapshot snapshot = context.snapshot();
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        ResourceLocation machineId = machine == null ? null : machine.registryName();
        if (machineId == null || context.maxParallelism() <= 0) return StartResult.NO_MATCH;
        List<MachineRecipe> machineCandidates = candidatesForPool(candidates, controller.currentRecipePoolId());
        RecipeSearchResult result;
        try {
            result = new RecipeSearchTask(snapshot, machineId, controller.currentRecipePoolId(), structureVersion,
                    context.maxParallelism(), machineCandidates,
                    context.capabilitySnapshot(), MachineModifier.recipeModifiers(context.modifiers())).compute(runtime);
        } catch (RuntimeException exception) {
            controller.clearPendingConflictStart();
            onStartSearchFailed(null);
            return StartResult.FAILED;
        }
        if (!result.success()) {
            controller.clearPendingConflictStart();
            onRecipeSearchFailed(result);
            return StartResult.NO_MATCH;
        }
        if (!searchSnapshotCurrent(snapshot)) return StartResult.DEFERRED;
        if (controller.shouldDelayConflictProneStart(result)) return StartResult.DEFERRED;
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
            if (result != null && result.failure() == null && result.result() != null) runtime.stopEnergyPrefetch();
            if (result == null || result.result() == null || result.failure() != null) {
                onStartSearchFailed(null);
            } else {
                onRecipeSearchFailed(result.result());
            }
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

    protected StartResult startRecipe(MachineRecipe next, long requestedParallelism, long structureVersion) {
        return startRecipe(next, requestedParallelism, structureVersion, null, StartOrigin.SEARCH);
    }

    protected StartResult startRecipe(MachineRecipe next, long requestedParallelism, long structureVersion,
                                  @Nullable FactorySearchContext context) {
        return startRecipe(next, requestedParallelism, structureVersion, context, StartOrigin.SEARCH);
    }

    protected StartResult startRecipe(MachineRecipe next, long requestedParallelism, long structureVersion,
                                      @Nullable FactorySearchContext context, StartOrigin origin) {
        if (runtime.active() || isStartPending()) return reportStartResult(origin, StartResult.DEFERRED);
        if (context != null && !searchSnapshotCurrent(context.snapshot())) return reportStartResult(origin, StartResult.DEFERRED);
        long epoch = controller.lifecycleEpoch();
        long generation = nextStartToken;
        StartResult rejected = startCandidateFailure(next, requestedParallelism);
        if (!currentStartOwner(epoch) || generation != nextStartToken) return StartResult.CANCELLED;
        if (rejected != null) return reportStartResult(origin, rejected);
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (!reserveInstalledStart()) return reportStartResult(origin, StartResult.DEFERRED);
        if (usesFullAsyncContinuation() && domain != null && runtime.restartProgramReady(next)) {
            return enqueueAsyncFinishRestart(next, requestedParallelism, structureVersion, context, origin);
        }
        if (usesFullAsyncContinuation() && controller.getLevel() instanceof ServerLevel serverLevel && domain != null) {
            AsyncContinuation continuation = prepareAsyncStartContinuation(next, requestedParallelism, structureVersion,
                    context, origin);
            if (continuation == null) {
                releaseInstalledStart();
                return reportStartResult(origin, StartResult.DEFERRED);
            }
            MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.get(serverLevel);
            MachineAsyncCoordinator.TaskKey taskKey = new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(),
                    serverLevel.getGameTime(), controller.activeWorkMode(), asyncLaneId(), controller.lifecycleEpoch());
            PendingAsyncStart pending = pendingAsyncStart;
            MachineAsyncCoordinator.SubmissionResult submission = coordinator.submitDetailed(taskKey, continuation,
                    (key, step) -> isPendingStartGeneration(pending)
                            ? executeAsyncMainStep(key, step) : MainThreadStep.Result.value(false),
                    asyncStartTaskHooks(pending, taskKey.lifecycleEpoch()));
            return switch (submission) {
                case ACCEPTED -> reportStartResult(origin, StartResult.PENDING);
                case DUPLICATE, REJECTED -> {
                    // DUPLICATE owns the old task, never this attempt's newly allocated token.
                    rejectAsyncStart(pending);
                    yield StartResult.DEFERRED;
                }
            };
        }
        StartSnapshot snapshot = captureStartSnapshot(structureVersion, null, origin);
        long token = beginPendingStart(domain, next, snapshot);
        try {
            CraftingRuntime.StartPreparation preparation = runtime.prepareStartAttempt(next, requestedParallelism);
            if (!ownsPendingStart(token, next)) return StartResult.CANCELLED;
            if (!currentStartOwner(snapshot.lifecycleEpoch())) {
                finishPendingStart(token, next, StartResult.CANCELLED);
                return StartResult.CANCELLED;
            }
            if (preparation.result() != CraftingRuntime.PreparationResult.READY) {
                StartResult result = preparationResult(preparation);
                finishPendingStart(token, next, result);
                return result;
            }
            snapshot = recaptureRoutingAfterBeforeStart(snapshot);
            StartResult invalid = pendingStartInvalidResult(next);
            if (invalid != null) {
                finishPendingStart(token, next, invalid);
                return invalid;
            }
            if (!reserveInstalledStart()) {
                finishPendingStart(token, next, StartResult.DEFERRED);
                return StartResult.DEFERRED;
            }
            runtime.start(next, requestedParallelism, preparation.execution());
        } catch (RuntimeException exception) {
            return failStartAttempt(token, next, snapshot, exception);
        }
        return completeStartCommit(token, next, snapshot);
    }

    protected boolean isStartCandidateEligible(MachineRecipe recipe) {
        ResourceLocation pool = controller.currentRecipePoolId();
        return pool != null && pool.equals(recipe.recipePoolId());
    }

    private @Nullable StartResult startCandidateFailure(MachineRecipe next, long parallelism) {
        if (next == null || parallelism <= 0) return StartResult.NO_MATCH;
        try {
            return isStartCandidateEligible(next) ? null : StartResult.NO_MATCH;
        } catch (RuntimeException exception) {
            MMCR.LOG.warn("Recipe candidate validation failed: controller={} lane={}",
                    controller.getBlockPos(), laneId(), exception);
            return StartResult.FAILED;
        }
    }

    private StartResult reportStartResult(StartOrigin origin, StartResult result) {
        onStartAttemptResult(origin, result, searchContextKeyForStart());
        return result;
    }

    private StartSnapshot captureStartSnapshot(long structureVersion, @Nullable FactorySearchContext context,
                                               StartOrigin origin) {
        ControllerRuntimeSnapshot snapshot = context == null ? controller.currentRuntimeSnapshot() : context.snapshot();
        return new StartSnapshot(snapshot,
                snapshot.structure().version(), context == null ? currentCatalogVersion() : context.catalogVersion(),
                controller.currentRecipePoolId(), searchContextKeyForStart(), origin, controller.lifecycleEpoch(),
                controller.componentRuntime().inputIsolationChangeVersion());
    }

    protected final boolean searchSnapshotCurrent(ControllerRuntimeSnapshot captured) {
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        return captured.structure().version() == current.structure().version()
                && captured.capabilityVersion() == current.capabilityVersion()
                && captured.modifierVersion() == current.modifierVersion()
                && captured.stateVersion() == current.stateVersion();
    }

    private StartSnapshot recaptureRoutingAfterBeforeStart(StartSnapshot captured) {
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        long isolationChangeVersion = controller.componentRuntime().inputIsolationChangeVersion();
        if (isolationChangeVersion == captured.inputIsolationChangeVersion()
                || current.stateVersion() - captured.runtime().stateVersion()
                != isolationChangeVersion - captured.inputIsolationChangeVersion()
                || captured.runtime().structure().version() != current.structure().version()
                || captured.runtime().capabilityVersion() != current.capabilityVersion()
                || captured.runtime().modifierVersion() != current.modifierVersion()
                || captured.runtime().upgradeContentRevision() != current.upgradeContentRevision()
                || !captured.runtime().linkedPortPositions().equals(current.linkedPortPositions())) return captured;
        StartSnapshot refreshed = new StartSnapshot(current, captured.structureVersion(), captured.catalogVersion(),
                captured.recipePoolId(), searchContextKeyForStart(), captured.origin(), captured.lifecycleEpoch(),
                isolationChangeVersion);
        pendingStartComponentStateVersion = current.stateVersion();
        pendingStartSearchContextKey = refreshed.searchContextKey();
        return refreshed;
    }

    private boolean currentStartOwner(long epoch) {
        return epoch == controller.lifecycleEpoch() && !controller.isRemoved()
                && (!(controller.getLevel() instanceof ServerLevel level)
                || level.getBlockEntity(controller.getBlockPos()) == controller);
    }

    private StartResult completeStartCommit(long token, MachineRecipe next, StartSnapshot snapshot) {
        if (!ownsPendingStart(token, next)) return StartResult.CANCELLED;
        if (!currentStartOwner(snapshot.lifecycleEpoch())) {
            finishPendingStart(token, next, StartResult.CANCELLED);
            return StartResult.CANCELLED;
        }
        if (!runtime.active()) {
            finishPendingStart(token, next, StartResult.FAILED);
            controller.clearRecipeScreenText(laneId());
            controller.syncRecipeRuntimeFailure(runtime);
            return StartResult.FAILED;
        }
        if (runtime.recipe() != next) {
            finishPendingStart(token, next, StartResult.CANCELLED);
            return StartResult.CANCELLED;
        }
        // Payment, including ERROR_PAUSED, is final. Do not recheck unpaid versions or lane limits here.
        clearPendingStart(token, next);
        try {
            onStarted();
            installStartedProgram();
        } catch (RuntimeException exception) {
            MMCR.LOG.warn("Accepted recipe start notification failed: controller={} lane={}",
                    controller.getBlockPos(), laneId(), exception);
        } finally {
            if (nextStartToken == token && currentStartOwner(snapshot.lifecycleEpoch())
                    && runtime.active() && runtime.recipe() == next) {
                onStartAttemptResult(snapshot.origin(), StartResult.STARTED, snapshot.searchContextKey());
                controller.syncRecipeRuntimeFailure(runtime);
            }
        }
        return StartResult.STARTED;
    }

    private StartResult failStartAttempt(long token, MachineRecipe next, StartSnapshot snapshot,
                                        RuntimeException exception) {
        if (!ownsPendingStart(token, next)) return StartResult.CANCELLED;
        if (!currentStartOwner(snapshot.lifecycleEpoch())) {
            finishPendingStart(token, next, StartResult.CANCELLED);
            return StartResult.CANCELLED;
        }
        MMCR.LOG.warn("Recipe start attempt failed: controller={} lane={}", controller.getBlockPos(), laneId(), exception);
        if (runtime.active() && runtime.recipe() == next) return completeStartCommit(token, next, snapshot);
        finishPendingStart(token, next, StartResult.FAILED);
        controller.clearRecipeScreenText(laneId());
        controller.syncRecipeRuntimeFailure(runtime);
        return StartResult.FAILED;
    }

    private static StartResult preparationResult(CraftingRuntime.StartPreparation preparation) {
        return switch (preparation.result()) {
            case READY -> throw new IllegalArgumentException("READY is not a terminal preparation result");
            case DEFERRED -> StartResult.DEFERRED;
            case FAILED -> StartResult.FAILED;
            case CANCELLED -> StartResult.CANCELLED;
        };
    }

    private boolean reserveInstalledStart() {
        if (!(controller.getLevel() instanceof ServerLevel level)) return true;
        SharedIoCoordinator coordinator = SharedIoCoordinator.get(level);
        if (coordinator.executionRoundId() == 0L) return true;
        return qualifiedInstalledTick(level, coordinator) && coordinator.reserveExecution(runtime, laneId());
    }

    private void releaseInstalledStart() {
        if (controller.getLevel() instanceof ServerLevel level) {
            SharedIoCoordinator.get(level).releaseExecutionReservation(runtime, laneId());
        }
    }

    private boolean qualifiedInstalledTick(ServerLevel level, SharedIoCoordinator coordinator) {
        return !controller.isRemoved() && !controller.isRedstonePaused()
                && controller.currentStructureSnapshot().formed() && controller.currentStructureSnapshot().structureAreaLoaded()
                && level.getBlockEntity(controller.getBlockPos()) == controller
                && coordinator.controllerPermit(controller.getBlockPos()).eligible(coordinator.executionRoundId(),
                level.getGameTime(), level.tickRateManager().runsNormally());
    }

    protected final void installStartedProgram() {
        if (!(controller.getLevel() instanceof ServerLevel level)) return;
        SharedIoCoordinator coordinator = SharedIoCoordinator.get(level);
        if (coordinator.executionRoundId() == 0L || !qualifiedInstalledTick(level, coordinator)) return;
        coordinator.reserveExecution(runtime, laneId());
        if (usesFullAsyncContinuation() && runtime.installedProgram() == null && !runtime.restartProgramAuthorized()) {
            requestProgramCompilation(level);
            return;
        }
        InstalledRecipeProgram execution = runtime.ensureInstalledProgram();
        if (execution != null) coordinator.install(execution, coordinator.controllerPermit(controller.getBlockPos()));
    }

    private void requestProgramCompilation(ServerLevel level) {
        if (programCompilationTask != null) return;
        CraftingRuntime.ProgramCompilation captured = runtime.captureProgramCompilation();
        if (captured == null) return;
        MachineAsyncCoordinator.TaskKey key = new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(),
                level.getGameTime(), controller.activeWorkMode(), "program/" + laneId(), controller.lifecycleEpoch());
        programCompilationTask = key;
        AtomicBoolean valid = new AtomicBoolean(true);
        programCompilationValidity = valid;
        MachineAsyncCoordinator.SubmissionResult submitted = MachineAsyncCoordinator.get(level).submitDetailed(key,
                new ProgramCompilationContinuation(captured), (task, step) -> {
                    if (programCompilationTask != task || controller.lifecycleEpoch() != task.lifecycleEpoch()
                            || !(step instanceof CompiledInstall install)) return MainThreadStep.Result.success();
                    // Only this main-thread continuation can bind live runtime/storage.
                    InstalledRecipeProgram installed = runtime.installCompiledProgram(install.identity(), install.program());
                    programCompilationTask = null;
                    programCompilationValidity = null;
                    if (installed != null) installStartedProgram();
                    return MainThreadStep.Result.success();
                }, new MachineAsyncCoordinator.TaskHooks(
                        valid::get,
                        (ignored, outcome) -> {
                            if (programCompilationTask == key) {
                                programCompilationTask = null;
                                programCompilationValidity = null;
                            }
                        }));
        if (submitted != MachineAsyncCoordinator.SubmissionResult.ACCEPTED) {
            valid.set(false);
            programCompilationTask = null;
            programCompilationValidity = null;
        }
    }

    /** Worker-owned values only. @author howxu <dev@howxu.cn> */
    private record ProgramCompilationContinuation(CraftingRuntime.ProgramCompilation input) implements AsyncContinuation {
        @Override
        public Yield advance(AsyncExecutionContext context) {
            CompiledRecipeProgram program = input.compile();
            return Yield.mainThread(new CompiledInstall(input.identity(), program), ignored -> unused -> Yield.complete());
        }
    }

    /** Main-thread installation boundary. @author howxu <dev@howxu.cn> */
    private record CompiledInstall(CompiledRecipeProgram.ExecutionIdentity identity,
                                   CompiledRecipeProgram program) implements MainThreadStep {
        @Override
        public Result execute() { return Result.success(); }
    }

    private void cancelProgramCompilation() {
        MachineAsyncCoordinator.TaskKey key = programCompilationTask;
        if (programCompilationValidity != null) programCompilationValidity.set(false);
        programCompilationValidity = null;
        programCompilationTask = null;
        if (key != null && controller.getLevel() instanceof ServerLevel level) MachineAsyncCoordinator.get(level).cancel(key);
    }

    protected final void bindInstalledLane() {
        runtime.bindRecipeLane(laneId(), this::completeInstalledAttempt);
    }

    private void completeInstalledAttempt() {
        completeIfFinished(true);
    }

    protected @Nullable AsyncContinuation prepareAsyncStartContinuation(MachineRecipe next, long requestedParallelism,
                                                                         long structureVersion,
                                                                         @Nullable FactorySearchContext context) {
        return prepareAsyncStartContinuation(next, requestedParallelism, structureVersion, context, StartOrigin.SEARCH);
    }

    protected @Nullable AsyncContinuation prepareAsyncStartContinuation(MachineRecipe next, long requestedParallelism,
                                                                         long structureVersion,
                                                                         @Nullable FactorySearchContext context,
                                                                         StartOrigin origin) {
        PendingAsyncStart pending = preparePendingAsyncStart(next, requestedParallelism, structureVersion, context, origin);
        return pending == null ? null : AsyncCraftingExecution.start(asyncLaneId(), pending.snapshot().catalogVersion());
    }

    protected StartResult enqueueAsyncFinishRestart(MachineRecipe next, long requestedParallelism, long structureVersion,
                                                @Nullable FactorySearchContext context) {
        return enqueueAsyncFinishRestart(next, requestedParallelism, structureVersion, context, StartOrigin.SEARCH);
    }

    protected StartResult enqueueAsyncFinishRestart(MachineRecipe next, long requestedParallelism, long structureVersion,
                                                    @Nullable FactorySearchContext context, StartOrigin origin) {
        if (runtime.active() || isStartPending()) return reportStartResult(origin, StartResult.DEFERRED);
        long epoch = controller.lifecycleEpoch();
        long generation = nextStartToken;
        StartResult rejected = startCandidateFailure(next, requestedParallelism);
        if (!currentStartOwner(epoch) || generation != nextStartToken) return StartResult.CANCELLED;
        if (rejected != null) return reportStartResult(origin, rejected);
        if (!reserveInstalledStart()) return reportStartResult(origin, StartResult.DEFERRED);
        PendingAsyncStart pending = preparePendingAsyncStart(next, requestedParallelism, structureVersion, context, origin);
        if (pending == null) {
            releaseInstalledStart();
            return reportStartResult(origin, StartResult.DEFERRED);
        }
        StartResult result;
        try {
            CraftingRuntime.StartPreparation preparation = runtime.prepareStartAttempt(next, requestedParallelism);
            runtime.flushAsyncScreenText();
            if (!isPendingStartGeneration(pending)) return StartResult.CANCELLED;
            if (!currentStartOwner(pending.snapshot().lifecycleEpoch())) {
                finishPendingStart(pending.token(), pending.recipe(), StartResult.CANCELLED);
                return StartResult.CANCELLED;
            }
            if (preparation.result() != CraftingRuntime.PreparationResult.READY) {
                result = preparationResult(preparation);
                finishPendingStart(pending.token(), pending.recipe(), result);
                return result;
            }
            StartSnapshot preparedSnapshot = recaptureRoutingAfterBeforeStart(pending.snapshot());
            result = requestStart(pending.level(), pending.domain(), pending.recipe(), pending.parallelism(),
                    pending.token(), preparedSnapshot, preparation.execution(),
                    () -> {
                        if (isPendingStart(pending.token(), pending.recipe())) failAsyncStart(pending);
                    });
        } catch (RuntimeException exception) {
            return failStartAttempt(pending.token(), next, pending.snapshot(), exception);
        }
        if (result == StartResult.PENDING) {
            pendingAsyncStart = null;
            pendingAsyncStartExecution = null;
            onStartAttemptResult(origin, result, pending.snapshot().searchContextKey());
        }
        return result;
    }

    private @Nullable PendingAsyncStart preparePendingAsyncStart(MachineRecipe next, long requestedParallelism,
                                                                 long structureVersion,
                                                                 @Nullable FactorySearchContext context,
                                                                 StartOrigin origin) {
        if (next == null || requestedParallelism <= 0 || startPending || runtime.active()) return null;
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        if (recipePoolId == null || !(controller.getLevel() instanceof ServerLevel serverLevel)) return null;
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (domain == null) return null;
        StartSnapshot startSnapshot = captureStartSnapshot(structureVersion, context, origin);
        long token = beginPendingStart(domain, next, startSnapshot);
        pendingAsyncStart = new PendingAsyncStart(serverLevel, domain, next, requestedParallelism, token, startSnapshot);
        pendingAsyncStartExecution = null;
        return pendingAsyncStart;
    }

    private long beginPendingStart(@Nullable StructureClaimRegistry.ResourceDomain domain, MachineRecipe next,
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
        pendingStartUpgradeRevision = startSnapshot.runtime().upgradeContentRevision();
        pendingStartCatalogVersion = startSnapshot.catalogVersion();
        pendingStartRecipePoolId = startSnapshot.recipePoolId();
        pendingStartSearchContextKey = startSnapshot.searchContextKey();
        pendingStartOrigin = startSnapshot.origin();
        pendingStartLifecycleEpoch = startSnapshot.lifecycleEpoch();
        return token;
    }

    private StartResult requestStart(ServerLevel level, StructureClaimRegistry.ResourceDomain domain,
                                 MachineRecipe next, long requestedParallelism, long token,
                                 StartSnapshot startSnapshot, RecipeStartContext.ExecutionSnapshot preparedStart,
                                 Runnable completion) {
        if (!ownsPendingStart(token, next)) return StartResult.CANCELLED;
        StartResult invalid = pendingStartInvalidResult(next);
        if (invalid != null) {
            finishPendingStart(token, next, invalid);
            return invalid;
        }
        long lifecycleEpoch = startSnapshot.lifecycleEpoch();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.StartRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                startSnapshot.structureVersion(),
                startSnapshot.runtime().stateVersion(),
                requestedParallelism,
                requested -> {
                    try {
                        if (!isPendingStart(token, next) || runtime.active()) return 0L;
                        if (!reserveInstalledStart()) {
                            finishPendingStart(token, next, StartResult.DEFERRED);
                            completion.run();
                            return 0L;
                        }
                        runtime.start(next, requested, preparedStart);
                    } catch (RuntimeException exception) {
                        StartResult result = failStartAttempt(token, next, startSnapshot, exception);
                        completion.run();
                        return result == StartResult.STARTED ? runtime.parallelism() : 0L;
                    }
                    if (!ownsPendingStart(token, next)) {
                        completion.run();
                        return 0L;
                    }
                    if (!currentStartOwner(lifecycleEpoch)) {
                        finishPendingStart(token, next, StartResult.CANCELLED);
                        completion.run();
                        return 0L;
                    }
                    if (!runtime.active()) {
                        finishPendingStart(token, next, StartResult.FAILED);
                        controller.clearRecipeScreenText(laneId());
                        controller.syncRecipeRuntimeFailure(runtime);
                        completion.run();
                        return 0L;
                    }
                    return runtime.parallelism();
                },
                granted -> {
                    if (ownsPendingStart(token, next)) completeStartCommit(token, next, startSnapshot);
                },
                () -> {
                    try {
                        boolean valid = isPendingStart(token, next) && domain.equals(controller.resourceDomain());
                        if (!valid) completion.run();
                        return valid;
                    } catch (RuntimeException exception) {
                        failStartAttempt(token, next, startSnapshot, exception);
                        completion.run();
                        return false;
                    }
                },
                () -> controller.currentRuntimeSnapshot().structure().version(),
                () -> controller.currentRuntimeSnapshot().stateVersion(),
                startSnapshot.catalogVersion(),
                this::currentCatalogVersion,
                completion
        ));
        return StartResult.PENDING;
    }

    private void failAsyncStart(PendingAsyncStart pending) {
        if (pending == null || !isPendingStartGeneration(pending)) return;
        if (runtime.active() && runtime.recipe() == pending.recipe()) {
            completeStartCommit(pending.token(), pending.recipe(), pending.snapshot());
            return;
        }
        boolean current = pending.snapshot().lifecycleEpoch() == controller.lifecycleEpoch();
        finishPendingStart(pending.token(), pending.recipe(), StartResult.FAILED);
        if (!current) return;
        controller.clearRecipeScreenText(laneId());
        controller.syncRecipeRuntimeFailure(runtime);
    }

    private void rejectAsyncStart(PendingAsyncStart pending) {
        if (pending == null) return;
        finishPendingStart(pending.token(), pending.recipe(), StartResult.DEFERRED);
    }

    private void finishPendingStart(long token, MachineRecipe recipe, StartResult result) {
        if (!startPending || pendingStartToken != token || pendingStartRecipe != recipe) return;
        StartOrigin origin = pendingStartOrigin;
        RecipeSearchContextKey key = pendingStartSearchContextKey;
        long epoch = pendingStartLifecycleEpoch;
        boolean current = currentStartOwner(epoch);
        clearPendingStart(token, recipe);
        if (!runtime.active()) releaseInstalledStart();
        if (current) {
            onStartAttemptResult(origin, result, key);
            if (nextStartToken == token && currentStartOwner(epoch)) {
                controller.syncRecipeRuntimeFailure(runtime);
            }
        }
    }

    private boolean isPendingStart(long token, MachineRecipe recipe) {
        if (!ownsPendingStart(token, recipe)) return false;
        StartResult invalid = pendingStartInvalidResult(recipe);
        if (invalid == null) return true;
        finishPendingStart(token, recipe, invalid);
        return false;
    }

    private @Nullable StartResult pendingStartInvalidResult(MachineRecipe recipe) {
        if (!currentStartOwner(pendingStartLifecycleEpoch)) return StartResult.CANCELLED;
        if (controller.isRedstonePaused() || !Objects.equals(pendingStartDomain, controller.resourceDomain())) {
            return StartResult.DEFERRED;
        }
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        if (pendingStartRecipePoolId == null
                || !pendingStartRecipePoolId.equals(controller.currentRecipePoolId())
                || !pendingStartRecipePoolId.equals(recipe.recipePoolId()) || !isStartCandidateEligible(recipe)) {
            return StartResult.NO_MATCH;
        }
        if (currentCatalogVersion() != pendingStartCatalogVersion) {
            return StartResult.NO_MATCH;
        }
        if (snapshot.structure().version() != pendingStartStructureVersion
                || snapshot.capabilityVersion() != pendingStartCapabilityVersion
                || snapshot.modifierVersion() != pendingStartModifierVersion
                || snapshot.stateVersion() != pendingStartComponentStateVersion
                || snapshot.upgradeContentRevision() != pendingStartUpgradeRevision
                || controller.getLevel() instanceof ServerLevel
                && (!snapshot.structure().formed() || !snapshot.structure().structureAreaLoaded())) {
            return StartResult.DEFERRED;
        }
        return null;
    }

    private boolean ownsPendingStart(long token, MachineRecipe recipe) {
        return startPending && pendingStartToken == token && pendingStartRecipe == recipe;
    }

    private boolean isPendingStartGeneration(@Nullable PendingAsyncStart pending) {
        return pending != null && ownsPendingStart(pending.token(), pending.recipe());
    }

    private MachineAsyncCoordinator.TaskHooks asyncStartTaskHooks(PendingAsyncStart pending, long lifecycleEpoch) {
        return new MachineAsyncCoordinator.TaskHooks(
                () -> lifecycleEpoch == controller.lifecycleEpoch() && isPendingStartGeneration(pending),
                (ignored, outcome) -> {
                    if (!(outcome instanceof MachineAsyncCoordinator.TaskOutcome.Succeeded)
                            && isPendingStartGeneration(pending)) {
                        if (lifecycleEpoch != controller.lifecycleEpoch()) {
                            finishPendingStart(pending.token(), pending.recipe(), StartResult.CANCELLED);
                        } else if (outcome instanceof MachineAsyncCoordinator.TaskOutcome.Cancelled) rejectAsyncStart(pending);
                        else failAsyncStart(pending);
                    }
                });
    }

    private long currentCatalogVersion() {
        return RecipeRegistry.catalogForPool(controller.currentRecipePoolId()).version();
    }

    private void invalidatePendingStart(long token, MachineRecipe recipe) {
        finishPendingStart(token, recipe, StartResult.DEFERRED);
        controller.clearRecipeScreenText(laneId());
        controller.syncRecipeRuntimeFailure(runtime);
    }

    private void clearPendingStart(long token, MachineRecipe recipe) {
        if (!startPending || pendingStartToken != token || pendingStartRecipe != recipe) return;
        if (pendingAsyncStart != null && pendingAsyncStart.token() == token && pendingAsyncStart.recipe() == recipe) {
            pendingAsyncStart = null;
            pendingAsyncStartExecution = null;
        }
        startPending = false;
        pendingStartRecipe = null;
        pendingStartDomain = null;
        pendingStartToken = 0L;
        pendingStartStructureVersion = Long.MIN_VALUE;
        pendingStartCapabilityVersion = Long.MIN_VALUE;
        pendingStartModifierVersion = Long.MIN_VALUE;
        pendingStartComponentStateVersion = Long.MIN_VALUE;
        pendingStartUpgradeRevision = Long.MIN_VALUE;
        pendingStartCatalogVersion = Long.MIN_VALUE;
        pendingStartRecipePoolId = null;
        pendingStartSearchContextKey = null;
        pendingStartOrigin = StartOrigin.SEARCH;
        pendingStartLifecycleEpoch = Long.MIN_VALUE;
    }

    private void invalidatePendingStartForCatalog(long token, MachineRecipe recipe) {
        finishPendingStart(token, recipe, StartResult.NO_MATCH);
        controller.clearRecipeScreenText(laneId());
        onPendingStartCatalogChanged();
        controller.syncRecipeRuntimeFailure(runtime);
    }

    public void tick() {
        tick(null);
    }

    public void tick(@Nullable ControllerRuntimeSnapshot tickSnapshot) {
        if (startPending && !isPendingStart(pendingStartToken, pendingStartRecipe)) {
            clearPendingStart(pendingStartToken, pendingStartRecipe);
            controller.clearRecipeScreenText(laneId());
        }
        if (!runtime.active()) return;
        if (controller.getLevel() instanceof ServerLevel level && controller.resourceDomain() != null) {
            SharedIoCoordinator coordinator = SharedIoCoordinator.get(level);
            if (coordinator.executionRoundId() != 0L) {
                if (!qualifiedInstalledTick(level, coordinator)) return;
                InstalledRecipeProgram execution = runtime.installedProgram();
                if (execution == null || !coordinator.refreshExecution(execution,
                        coordinator.controllerPermit(controller.getBlockPos()))) {
                    if (!runtime.versionsCurrent()) {
                        boolean wasActive = runtime.active();
                        runtime.invalidateForCatalogChange();
                        completeIfFinished(wasActive);
                        return;
                    }
                    if (tickPending) return;
                    if (!runtime.finishPending() && usesFullAsyncContinuation() && runtime.installedProgram() == null
                            && !runtime.restartProgramAuthorized()) {
                        requestProgramCompilation(level);
                        return;
                    }
                    execution = runtime.ensureInstalledProgram();
                    if (execution == null) return;
                    coordinator.reserveExecution(runtime, laneId());
                    if (!coordinator.install(execution, coordinator.controllerPermit(controller.getBlockPos()))) return;
                }
                if (tickPending) return;
                if (usesAsyncPlanning()) {
                    if (!runtime.finishPending()) runtime.prepareInstalledTick(coordinator.executionRoundId());
                } else {
                    coordinator.executeInstalled(execution, level);
                }
                return;
            }
        }
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
                                    structureVersion, stateVersion, catalogVersion, intent),
                            () -> failAsyncTick(token, lifecycleEpoch));
                    if (!enqueued) failAsyncTick(token, lifecycleEpoch);
                    return enqueued;
                },
                () -> validateAsyncTickRequest(token, domain, lifecycleEpoch, structureVersion, stateVersion, catalogVersion),
                  () -> controller.currentStructureSnapshot().version(),
                  () -> controller.componentRuntime().stateVersion(),
                   catalogVersion,
                  this::currentCatalogVersion,
                  () -> { }
          ));
    }

    private boolean commitTickWorksetIntent(long token, StructureClaimRegistry.ResourceDomain domain,
                                            long lifecycleEpoch, long structureVersion, long stateVersion,
                                            long catalogVersion, AsyncRequirementPlanner.PlanResult intent) {
        if (!validateAsyncTickRequest(token, domain, lifecycleEpoch, structureVersion, stateVersion,
                catalogVersion)) return false;
        boolean committed = runtime.commitAsyncTick(intent);
        if (committed && runtime.completeAsyncTickAfterInputs()) {
            runtime.completeAsyncTickAfterRecipe();
        } else if (!committed) {
            runtime.discardAsyncTickPreparation();
        }
        finishAsyncTick();
        return committed;
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
                    boolean valid = validateAsyncTickRequest(token, domain, lifecycleEpoch,
                            structureVersion, snapshot.stateVersion(), catalogVersion);
                    if (!valid) {
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
            discardInvalidAsyncStart(key, step);
            if (!isAsyncStartStep(step) && key.lifecycleEpoch() == controller.lifecycleEpoch()
                    && pendingAsyncStart == null) finishAsyncTick();
            return MainThreadStep.Result.failure(new IllegalStateException("Async continuation validation failed"));
        }
        if (step instanceof MainThreadStep.Lifecycle lifecycle) {
            if (lifecycle.kind() == MainThreadStep.Kind.BEFORE_START) {
                PendingAsyncStart pending = pendingAsyncStart;
                if (pending == null) {
                    return MainThreadStep.Result.failure(new IllegalStateException("Missing async start lifecycle"));
                }
                CraftingRuntime.StartPreparation preparation;
                try {
                    preparation = runtime.prepareStartAttempt(pending.recipe(), pending.parallelism());
                } catch (RuntimeException exception) {
                    failStartAttempt(pending.token(), pending.recipe(), pending.snapshot(), exception);
                    return MainThreadStep.Result.value(false);
                }
                if (!isPendingStartGeneration(pending)) return MainThreadStep.Result.value(false);
                if (preparation.result() != CraftingRuntime.PreparationResult.READY) {
                    finishPendingStart(pending.token(), pending.recipe(), preparationResult(preparation));
                    return MainThreadStep.Result.value(false);
                }
                pendingAsyncStartExecution = preparation.execution();
                pendingAsyncStart = new PendingAsyncStart(pending.level(), pending.domain(), pending.recipe(),
                        pending.parallelism(), pending.token(), recaptureRoutingAfterBeforeStart(pending.snapshot()));
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
                try {
                    if (requestStart(pending.level(), pending.domain(), pending.recipe(), pending.parallelism(), pending.token(),
                            pending.snapshot(), preparedStart, () -> MachineAsyncCoordinator.get(pending.level()).resume(key))
                            == StartResult.PENDING) return MainThreadStep.Result.pending();
                } catch (RuntimeException exception) {
                    failStartAttempt(pending.token(), pending.recipe(), pending.snapshot(), exception);
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
        discardInvalidAsyncStart(key, step);
        if (!isAsyncStartStep(step) && key.lifecycleEpoch() == controller.lifecycleEpoch()
                && pendingAsyncStart == null) finishAsyncTick();
        return MainThreadStep.Result.failure(new IllegalStateException("Async continuation became stale"));
    }

    private void discardInvalidAsyncStart(MachineAsyncCoordinator.TaskKey key, MainThreadStep step) {
        if (!isAsyncStartStep(step)) return;
        PendingAsyncStart pending = pendingAsyncStart;
        if (pending == null || pending.snapshot().lifecycleEpoch() != key.lifecycleEpoch()) return;
        if (key.lifecycleEpoch() != controller.lifecycleEpoch()) {
            finishPendingStart(pending.token(), pending.recipe(), StartResult.CANCELLED);
            return;
        }
        pendingAsyncStart = null;
        pendingAsyncStartExecution = null;
        if (currentCatalogVersion() != pending.snapshot().catalogVersion()
                || !isStartCandidateEligible(pending.recipe())) {
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
                    && snapshot.runtime().upgradeContentRevision() == current.upgradeContentRevision()
                    && snapshot.recipePoolId().equals(controller.currentRecipePoolId())
                    && snapshot.recipePoolId().equals(pending.recipe().recipePoolId())
                    && isStartCandidateEligible(pending.recipe());
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
                    boolean valid = validateAsyncTickRequest(token, domain, lifecycleEpoch,
                            snapshot.structure().version(), snapshot.stateVersion(), catalogVersion);
                    if (!valid) {
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
                    boolean valid = validateAsyncTickRequest(token, domain, lifecycleEpoch,
                            structureVersion, stateVersion, catalogVersion);
                    if (!valid) {
                        MachineAsyncCoordinator.get(level).complete(key);
                    }
                    return valid;
                }, () -> controller.currentStructureSnapshot().version(),
                () -> controller.componentRuntime().stateVersion(), catalogVersion, this::currentCatalogVersion,
                () -> MachineAsyncCoordinator.get(level).complete(key)));
    }

    private boolean validateAsyncTickRequest(long token, StructureClaimRegistry.ResourceDomain domain,
                                            long lifecycleEpoch, long structureVersion, long stateVersion,
                                            long catalogVersion) {
        boolean valid = lifecycleEpoch == controller.lifecycleEpoch()
                && validateCurrentRuntime(token, domain)
                && catalogVersion == currentCatalogVersion()
                && structureVersion == controller.currentStructureSnapshot().version()
                && stateVersion == controller.componentRuntime().stateVersion();
        if (!valid) failAsyncTick(token, lifecycleEpoch);
        return valid;
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
            if (runtime.failure() == null && runtime.ledger().state().outputPaid()) {
                controller.clearRecipeScreenText(laneId());
                try {
                    onRecipeFinished();
                } finally {
                    // This completion belongs to the output-paid execution, even if its next start throws.
                    onFinished();
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
        clearFullSearchRequest();
        cancelProgramCompilation();
        releaseInstalledStart();
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
        pendingStartUpgradeRevision = Long.MIN_VALUE;
        pendingStartCatalogVersion = Long.MIN_VALUE;
        pendingStartRecipePoolId = null;
        pendingStartSearchContextKey = null;
        pendingStartOrigin = StartOrigin.SEARCH;
        pendingStartLifecycleEpoch = Long.MIN_VALUE;
          clearPendingTick();
    }

    /** Clears deferred async work without invalidating a recipe that may resume after a pause. */
    public void cancelAsyncState() {
        cancelProgramCompilation();
        runtime.cancelInstalledProgram();
        releaseInstalledStart();
        pendingAsyncStart = null;
        pendingAsyncStartExecution = null;
        asyncFinishPrepared = false;
        clearPendingStartUnconditionally();
        clearPendingTick();
    }

    public void discardForRecipePoolChange() {
        clearFullSearchRequest();
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

    protected void onRecipeSearchFailed(RecipeSearchResult result) {
        onStartSearchFailed(result.failure());
    }

    public final boolean hasFullSearchPending() { return fullSearchPending; }
    public final void requestFullSearch() { fullSearchPending = true; }

    public final void onFullSearchResult(StartResult result) {
        if (result != StartResult.DEFERRED) clearFullSearchRequest();
    }

    protected final void clearFullSearchRequest() { fullSearchPending = false; }

    public final void reportLastRecipeStartResult(StartResult result) {
        if (result == StartResult.FAILED || result == StartResult.NO_MATCH) requestFullSearch();
        if (result == StartResult.STARTED) clearFullSearchRequest();
        onLastRecipeStartResult(result);
    }

    protected void onLastRecipeStartResult(StartResult result) { }

    protected void onStartAttemptResult(StartOrigin origin, StartResult result,
                                        @Nullable RecipeSearchContextKey contextKey) {
        if (result == StartResult.STARTED) clearFullSearchRequest();
        if (origin == StartOrigin.LAST_RECIPE) reportLastRecipeStartResult(result);
        else if (result == StartResult.FAILED) onStartFailed(contextKey);
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
