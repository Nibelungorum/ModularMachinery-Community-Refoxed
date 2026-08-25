package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
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
    private boolean tickPending;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingTickDomain;
    private long nextTickToken;
    private long pendingTickToken;

    protected RecipeThread(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        this.runtime = new CraftingRuntime(controller, controller.componentRuntime());
    }

    public boolean searchAndStartRecipe(List<MachineRecipe> candidates, int availableParallelism, long structureVersion) {
        return searchAndStartRecipe(candidates, availableParallelism, structureVersion, null);
    }

    protected boolean searchAndStartRecipe(List<MachineRecipe> candidates, int availableParallelism,
                                           long structureVersion, @Nullable Identifier lockedRecipeId) {
        ControllerRuntimeSnapshot snapshot = controller.runtimeSnapshot();
        Identifier machineId = snapshot.structure().machine() == null
                ? null : controller.runtimeSnapshot().structure().machine().registryName();
        if (machineId == null || availableParallelism <= 0) return false;
        RecipeSearchResult result = new RecipeSearchTask(snapshot, machineId, structureVersion,
                availableParallelism, candidates, lockedRecipeId, controller.componentRuntime().capabilities()).compute();
        if (!result.success()) {
            controller.clearPendingConflictStart();
            onStartSearchFailed(result.failure());
            return false;
        }
        if (controller.shouldDelayConflictProneStart(result)) return false;
        return startRecipe(result.recipe(), availableParallelism, structureVersion);
    }

    protected boolean startRecipe(MachineRecipe next, int requestedParallelism, long structureVersion) {
        if (next == null || requestedParallelism <= 0) return false;
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (controller.getLevel() instanceof ServerLevel serverLevel && domain != null) {
            return requestStart(serverLevel, domain, next, requestedParallelism, structureVersion);
        }
        CraftingStatus state = runtime.start(next, requestedParallelism);
        if (!state.isCrafting()) {
            onStartFailed();
            return false;
        }
        onStarted();
        return true;
    }

    private boolean requestStart(ServerLevel level, StructureClaimRegistry.ResourceDomain domain,
                                 MachineRecipe next, int requestedParallelism, long structureVersion) {
        long token = ++nextStartToken;
        startPending = true;
        pendingStartRecipe = next;
        pendingStartDomain = domain;
        pendingStartToken = token;
        ControllerRuntimeSnapshot snapshot = controller.runtimeSnapshot();
        pendingStartStructureVersion = structureVersion;
        pendingStartCapabilityVersion = snapshot.capabilityVersion();
        pendingStartModifierVersion = snapshot.modifierVersion();
        pendingStartComponentStateVersion = snapshot.stateVersion();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.StartRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                snapshot.stateVersion(),
                requestedParallelism,
                requested -> {
                    if (!isPendingStart(token, next) || runtime.active()) return 0;
                    CraftingStatus state = runtime.start(next, requested);
                    if (!state.isCrafting()) {
                        clearPendingStart(token, next);
                        onStartFailed();
                        controller.syncRecipeRuntimeFailure(runtime);
                        return 0;
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
                () -> controller.runtimeSnapshot().structure().version(),
                () -> controller.runtimeSnapshot().stateVersion()
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
        ControllerRuntimeSnapshot snapshot = controller.runtimeSnapshot();
        if (snapshot.structure().version() != pendingStartStructureVersion
                || snapshot.capabilityVersion() != pendingStartCapabilityVersion
                || snapshot.modifierVersion() != pendingStartModifierVersion
                || snapshot.stateVersion() != pendingStartComponentStateVersion) {
            invalidatePendingStart(token, recipe);
            return false;
        }
        return true;
    }

    private void invalidatePendingStart(long token, MachineRecipe recipe) {
        clearPendingStart(token, recipe);
        runtime.invalidate();
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
    }

    public void tick() {
        if (startPending && !isPendingStart(pendingStartToken, pendingStartRecipe)) {
            clearPendingStart(pendingStartToken, pendingStartRecipe);
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
        ControllerRuntimeSnapshot snapshot = controller.runtimeSnapshot();
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
                        return true;
                    }
                    completeIfFinished(wasActive);
                    return true;
                },
                () -> validateCurrentRuntime(token, domain),
                () -> controller.runtimeSnapshot().structure().version(),
                () -> controller.runtimeSnapshot().stateVersion()
        ));
    }

    private void requestFinish(ServerLevel level, StructureClaimRegistry.ResourceDomain domain, long token) {
        tickPending = true;
        pendingTickDomain = domain;
        ControllerRuntimeSnapshot snapshot = controller.runtimeSnapshot();
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
                    return true;
                },
                () -> validateCurrentRuntime(token, domain),
                () -> controller.runtimeSnapshot().structure().version(),
                () -> controller.runtimeSnapshot().stateVersion()
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
            runtime.tick();
            clearPendingTick();
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
        if (wasActive && !runtime.active() && runtime.failure() == null) {
            onFinished();
            onRecipeFinished();
        }
    }

    public void invalidate() {
        runtime.invalidate();
        startPending = false;
        pendingStartRecipe = null;
        pendingStartDomain = null;
        pendingStartToken = 0L;
        pendingStartStructureVersion = Long.MIN_VALUE;
        pendingStartCapabilityVersion = Long.MIN_VALUE;
        pendingStartModifierVersion = Long.MIN_VALUE;
        pendingStartComponentStateVersion = Long.MIN_VALUE;
        clearPendingTick();
    }

    protected void onStartSearchFailed(@Nullable ExecutionStatus failure) {
        runtime.recordSearchFailure(failure);
    }

    protected abstract void onStarted();
    protected abstract void onFinished();
    protected void onRecipeFinished() { }
    protected void onStartFailed() { }
    protected String laneId() { return "base"; }

    public @Nullable ActiveMachineRecipe getActiveRecipe() { return runtime.activeRecipe(); }
    public Status getStatus() {
        if (runtime.active()) return runtime.finishPending() ? Status.WAITING : Status.WORKING;
        return runtime.failure() == null ? Status.IDLE : Status.FAILED;
    }
    public @Nullable String getLastFailureUnloc() { return runtime.failureUnloc(); }
    public boolean isIdle() { return !startPending && !runtime.active(); }
    public boolean isStartPending() { return startPending; }
    public @Nullable MachineRecipe getPendingStartRecipe() { return pendingStartRecipe; }
    public int usedParallelism() { return runtime.parallelism(); }
    public CraftingRuntime runtime() { return runtime; }

}
