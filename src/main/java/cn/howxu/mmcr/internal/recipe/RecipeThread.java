package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
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
    private boolean tickPending;
    private @Nullable StructureClaimRegistry.ResourceDomain pendingTickDomain;

    protected RecipeThread(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        this.runtime = new CraftingRuntime(controller);
    }

    public boolean searchAndStartRecipe(List<MachineRecipe> candidates, int availableParallelism, long structureVersion) {
        return searchAndStartRecipe(candidates, availableParallelism, structureVersion, null);
    }

    protected boolean searchAndStartRecipe(List<MachineRecipe> candidates, int availableParallelism,
                                           long structureVersion, @Nullable Identifier lockedRecipeId) {
        Identifier machineId = controller.runtimeSnapshot().structure().machine() == null
                ? null : controller.runtimeSnapshot().structure().machine().registryName();
        if (machineId == null || availableParallelism <= 0) return false;
        RecipeSearchResult result = new RecipeSearchTask(controller.runtimeSnapshot(), machineId, structureVersion,
                availableParallelism, candidates, lockedRecipeId).compute();
        if (!result.success()) {
            onStartSearchFailed(result.failureUnloc());
            return false;
        }
        return startRecipe(result.recipe(), structureVersion);
    }

    protected boolean startRecipe(MachineRecipe next, long structureVersion) {
        if (next == null) return false;
        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (controller.getLevel() instanceof ServerLevel serverLevel && domain != null) {
            return requestStart(serverLevel, domain, next, structureVersion);
        }
        CraftingStatus state = runtime.start(next, runtimeParallelism(next));
        if (!state.isCrafting()) {
            onStartFailed();
            return false;
        }
        onStarted();
        return true;
    }

    private int runtimeParallelism(MachineRecipe recipe) {
        int controllerLimit = controller.getMaxParallelism();
        int recipeLimit = recipe.maxThreads() <= 0 ? controllerLimit : recipe.maxThreads();
        return Math.max(1, Math.min(controllerLimit, recipeLimit));
    }

    private boolean requestStart(ServerLevel level, StructureClaimRegistry.ResourceDomain domain,
                                 MachineRecipe next, long structureVersion) {
        long token = ++nextStartToken;
        startPending = true;
        pendingStartRecipe = next;
        pendingStartDomain = domain;
        pendingStartToken = token;
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.StartRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                runtimeParallelism(next),
                requested -> {
                    if (!isPendingStart(token, next)) return 0;
                    CraftingStatus state = runtime.start(next, requested);
                    if (!state.isCrafting()) {
                        clearPendingStart(token, next);
                        onStartFailed();
                        return 0;
                    }
                    return runtime.parallelism();
                },
                granted -> {
                    if (!isPendingStart(token, next)) return;
                    clearPendingStart(token, next);
                    onStarted();
                },
                () -> isPendingStart(token, next) && domain.equals(controller.resourceDomain()),
                () -> controller.runtimeSnapshot().structure().version()
        ));
        return true;
    }

    private boolean isPendingStart(long token, MachineRecipe recipe) {
        return startPending && pendingStartToken == token && pendingStartRecipe == recipe
                && pendingStartDomain != null && pendingStartDomain.equals(controller.resourceDomain());
    }

    private void clearPendingStart(long token, MachineRecipe recipe) {
        if (!isPendingStart(token, recipe)) return;
        startPending = false;
        pendingStartRecipe = null;
        pendingStartDomain = null;
        pendingStartToken = 0L;
    }

    public void tick() {
        if (startPending && !isCurrentDomain(pendingStartDomain)) {
            startPending = false;
            pendingStartRecipe = null;
            pendingStartDomain = null;
            pendingStartToken = 0L;
        }
        if (!runtime.active()) return;
        if (tickPending && !isCurrentDomain(pendingTickDomain)) {
            tickPending = false;
            pendingTickDomain = null;
        }
        if (tickPending) return;

        StructureClaimRegistry.ResourceDomain domain = controller.resourceDomain();
        if (controller.getLevel() instanceof ServerLevel level && domain != null) {
            if (runtime.finishPending()) {
                requestFinish(level, domain);
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
        long structureVersion = controller.runtimeSnapshot().structure().version();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.TickRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                () -> {
                    if (!isCurrentRuntime(domain)) return false;
                    boolean wasActive = runtime.active();
                    runtime.tick();
                    if (runtime.finishPending()) requestFinish(level, domain);
                    completeIfFinished(wasActive);
                    return true;
                },
                () -> isCurrentRuntime(domain),
                () -> controller.runtimeSnapshot().structure().version()
        ));
    }

    private void requestFinish(ServerLevel level, StructureClaimRegistry.ResourceDomain domain) {
        tickPending = true;
        pendingTickDomain = domain;
        long structureVersion = controller.runtimeSnapshot().structure().version();
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.FinishRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                () -> {
                    if (!isCurrentRuntime(domain)) return false;
                    boolean wasActive = runtime.active();
                    runtime.finish();
                    completeIfFinished(wasActive);
                    return true;
                },
                () -> isCurrentRuntime(domain),
                () -> controller.runtimeSnapshot().structure().version()
        ));
    }

    private boolean isCurrentRuntime(StructureClaimRegistry.ResourceDomain domain) {
        return runtime.active() && domain.equals(controller.resourceDomain()) && runtime.versionsCurrent();
    }

    private void completeIfFinished(boolean wasActive) {
        tickPending = false;
        pendingTickDomain = null;
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
        tickPending = false;
        pendingTickDomain = null;
    }

    protected void onStartSearchFailed(@Nullable String failureUnloc) {
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

    private boolean isCurrentDomain(@Nullable StructureClaimRegistry.ResourceDomain domain) {
        return domain != null && domain.equals(controller.resourceDomain());
    }
}
