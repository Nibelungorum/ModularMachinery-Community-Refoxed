package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.Identifier;
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
    private @Nullable RecipeSearchContextKey pendingStartSearchContextKey;
    private boolean tickPending;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingTickDomain;
    private long nextTickToken;
    private long pendingTickToken;

    protected RecipeThread(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        this.runtime = new CraftingRuntime(controller, controller.componentRuntime());
    }

    public boolean searchAndStartRecipe(List<MachineRecipe> candidates, long availableParallelism, long structureVersion) {
        return searchAndStartRecipe(candidates, availableParallelism, structureVersion, null);
    }

    protected boolean searchAndStartRecipe(List<MachineRecipe> candidates, long availableParallelism,
                                           long structureVersion, @Nullable Identifier lockedRecipeId) {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        Identifier machineId = machine == null ? null : machine.registryName();
        if (machineId == null || availableParallelism <= 0) return false;
        RecipeSearchResult result;
        try {
            result = new RecipeSearchTask(snapshot, machineId, structureVersion,
                    availableParallelism, candidates, lockedRecipeId, controller.componentRuntime().capabilities()).compute();
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
                                           long structureVersion, @Nullable Identifier lockedRecipeId) {
        if (context == null) return false;
        ControllerRuntimeSnapshot snapshot = context.snapshot();
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        Identifier machineId = machine == null ? null : machine.registryName();
        if (machineId == null || context.maxParallelism() <= 0) return false;
        RecipeSearchResult result;
        try {
            result = new RecipeSearchTask(snapshot, machineId, structureVersion,
                    context.maxParallelism(), candidates, lockedRecipeId,
                    context.capabilities(), context.modifiers()).compute();
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

    protected boolean startRecipe(MachineRecipe next, long requestedParallelism, long structureVersion) {
        return startRecipe(next, requestedParallelism, structureVersion, null);
    }

    protected boolean startRecipe(MachineRecipe next, long requestedParallelism, long structureVersion,
                                  @Nullable FactorySearchContext context) {
        if (next == null || requestedParallelism <= 0) return false;
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (controller.getLevel() instanceof ServerLevel serverLevel && domain != null) {
            return requestStart(serverLevel, domain, next, requestedParallelism, structureVersion, context);
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

    private boolean requestStart(ServerLevel level, StructureClaimRegistry.ResourceDomain domain,
                                  MachineRecipe next, long requestedParallelism, long structureVersion,
                                 @Nullable FactorySearchContext context) {
        long token = ++nextStartToken;
        startPending = true;
        pendingStartRecipe = next;
        pendingStartDomain = domain;
        pendingStartToken = token;
        ControllerRuntimeSnapshot snapshot = context == null ? controller.currentRuntimeSnapshot() : context.snapshot();
        pendingStartStructureVersion = structureVersion;
        pendingStartCapabilityVersion = snapshot.capabilityVersion();
        pendingStartModifierVersion = snapshot.modifierVersion();
        pendingStartComponentStateVersion = snapshot.stateVersion();
        pendingStartCatalogVersion = context == null ? currentCatalogVersion() : context.catalogVersion();
        pendingStartSearchContextKey = searchContextKeyForStart();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.StartRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                snapshot.stateVersion(),
                requestedParallelism,
                requested -> {
                    if (!isPendingStart(token, next) || runtime.active()) return 0L;
                    CraftingStatus state = runtime.start(next, requested);
                    if (!state.isCrafting()) {
                        controller.clearRecipeScreenText(laneId());
                        RecipeSearchContextKey failureKey = pendingStartSearchContextKey;
                        clearPendingStart(token, next);
                        onStartFailed(failureKey);
                        controller.syncRecipeRuntimeFailure(runtime);
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
                 () -> isPendingStart(token, next) && domain.equals(controller.resourceDomain()),
                 () -> controller.currentRuntimeSnapshot().structure().version(),
                 () -> controller.currentRuntimeSnapshot().stateVersion(),
                 pendingStartCatalogVersion,
                 this::currentCatalogVersion
         ));
        return true;
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
        if (currentCatalogVersion() != pendingStartCatalogVersion) {
            invalidatePendingStartForCatalog(token, recipe);
            return false;
        }
        return true;
    }

    private long currentCatalogVersion() {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        return RecipeRegistry.catalog(machine == null ? null : machine.registryName()).version();
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
        pendingStartSearchContextKey = null;
    }

    private void invalidatePendingStartForCatalog(long token, MachineRecipe recipe) {
        clearPendingStart(token, recipe);
        runtime.invalidate();
        controller.clearRecipeScreenText(laneId());
        onPendingStartCatalogChanged();
        controller.syncRecipeRuntimeFailure(runtime);
    }

    public void tick() {
        if (startPending && !isPendingStart(pendingStartToken, pendingStartRecipe)) {
            clearPendingStart(pendingStartToken, pendingStartRecipe);
            controller.clearRecipeScreenText(laneId());
        }
        if (!runtime.active()) return;
        if (tickPending && !validateCurrentRuntime(pendingTickToken, pendingTickDomain)) clearPendingTick();
        if (tickPending) return;

        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (controller.getLevel() instanceof ServerLevel level && domain != null) {
            if (runtime.finishPending()) {
                if (!runtime.shouldRetryFinish()) return;
                long token = ++nextTickToken;
                pendingTickToken = token;
                requestFinish(level, domain, token);
            } else {
                requestTick(level, domain);
            }
            return;
        }
        boolean wasActive = runtime.active();
        runtime.tick();
        if (runtime.finishPending()) runtime.finish();
        completeIfFinished(wasActive);
    }

    private void requestTick(ServerLevel level, StructureClaimRegistry.ResourceDomain domain) {
        tickPending = true;
        pendingTickDomain = domain;
        long token = ++nextTickToken;
        pendingTickToken = token;
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        long structureVersion = snapshot.structure().version();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.TickRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                snapshot.stateVersion(),
                () -> {
                    if (!validateCurrentRuntime(token, domain)) return false;
                    boolean wasActive = runtime.active();
                    runtime.tick();
                    if (runtime.finishPending()) {
                        if (runtime.shouldRetryFinish()) requestFinish(level, domain, token);
                        else clearPendingTick();
                        controller.syncRecipeRuntimeFailure(runtime);
                        return true;
                    }
                    completeIfFinished(wasActive);
                    controller.syncRecipeRuntimeFailure(runtime);
                    return true;
                },
                 () -> validateCurrentRuntime(token, domain),
                 () -> controller.currentRuntimeSnapshot().structure().version(),
                 () -> controller.currentRuntimeSnapshot().stateVersion()
         ));
    }

    private void requestFinish(ServerLevel level, StructureClaimRegistry.ResourceDomain domain, long token) {
        tickPending = true;
        pendingTickDomain = domain;
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        long structureVersion = snapshot.structure().version();
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
                 () -> validateCurrentRuntime(token, domain),
                 () -> controller.currentRuntimeSnapshot().structure().version(),
                 () -> controller.currentRuntimeSnapshot().stateVersion(),
                 () -> controller.notifyResourceAvailability(ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY, null)
         ));
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
    }

    private void completeIfFinished(boolean wasActive) {
        clearPendingTick();
        if (wasActive && !runtime.active()) {
            if (runtime.failure() == null) {
                onFinished();
                onRecipeFinished();
            } else {
                onRecipeFailure();
            }
            controller.clearRecipeScreenText(laneId());
        }
    }

    public void invalidate() {
        runtime.invalidate();
        controller.clearRecipeScreenText(laneId());
        startPending = false;
        pendingStartRecipe = null;
        pendingStartDomain = null;
        pendingStartToken = 0L;
        pendingStartStructureVersion = Long.MIN_VALUE;
        pendingStartCapabilityVersion = Long.MIN_VALUE;
          pendingStartModifierVersion = Long.MIN_VALUE;
          pendingStartComponentStateVersion = Long.MIN_VALUE;
          pendingStartCatalogVersion = Long.MIN_VALUE;
          pendingStartSearchContextKey = null;
          clearPendingTick();
    }

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
