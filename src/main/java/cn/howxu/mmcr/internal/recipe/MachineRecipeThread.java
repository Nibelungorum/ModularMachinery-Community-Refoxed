package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeCatalog;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Recipe thread used by a normal machine controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeThread extends RecipeThread {
    private final boolean controlsControllerRuntime;
    private @Nullable MachineRecipe lastRecipe;
    private long lastRecipeCatalogVersion = Long.MIN_VALUE;
    private boolean restartPending;
    private @Nullable PendingAsyncSearch pendingAsyncSearch;
    private long nextAsyncSearchId;

    /** @author howxu <dev@howxu.cn> */
    private record PendingAsyncSearch(AsyncRequirementPlanner.RecipeSearchRequest request,
                                      MachineAsyncCoordinator.TaskKey taskKey, long catalogVersion) {
    }

    public MachineRecipeThread(MachineControllerBlockEntity controller) {
        super(controller);
        controlsControllerRuntime = false;
    }

    public MachineRecipeThread(MachineControllerBlockEntity controller, CraftingRuntime runtime) {
        super(controller, runtime);
        controlsControllerRuntime = true;
    }

    @Override
    protected void onStarted() {
        rememberStartedRecipe();
        if (controlsControllerRuntime) controller.onNormalRecipeThreadStarted();
    }

    @Override
    protected void onFinished() {
        if (controlsControllerRuntime) {
            controller.onNormalRecipeThreadFinished(runtime.active() || controller.hasPendingNormalRecipeStart(), true);
        }
    }

    @Override
    protected void onRecipeFinished() {
        markRecipeFinished();
        if (controlsControllerRuntime && controller.activeWorkMode() == MachineWorkMode.SYNC) {
            controller.tryRestartNormalRecipe();
            return;
        }
        MachineRecipe restartRecipe = peekRestartRecipe(currentRecipeCatalog().recipes(),
                controller.getMaxParallelism(), controller.currentRuntimeSnapshot().structure().version());
        if (restartRecipe == null) {
            if (restartPending || hasFullSearchPending()) reportLastRecipeStartResult(StartResult.NO_MATCH);
            return;
        }
        if (controller.activeWorkMode() == MachineWorkMode.ASYNC) {
            enqueueAsyncFinishRestart(restartRecipe, controller.getMaxParallelism(),
                    controller.currentRuntimeSnapshot().structure().version(), null, StartOrigin.LAST_RECIPE);
        } else {
            startRecipe(restartRecipe, controller.getMaxParallelism(),
                    controller.currentRuntimeSnapshot().structure().version(), null, StartOrigin.LAST_RECIPE);
        }
    }

    @Override
    protected void onRecipeFailure() {
        if (controlsControllerRuntime) controller.onNormalRecipeThreadFinished(false, false);
    }

    @Override
    protected void onStartSearchFailed(@Nullable ExecutionStatus failure) {
        super.onStartSearchFailed(failure);
        if (controlsControllerRuntime) controller.onNormalRecipeThreadSearchFailed();
    }

    @Override
    protected void onStartFailed() {
        if (controlsControllerRuntime) controller.onNormalRecipeThreadSearchFailed();
    }

    public void rememberStartedRecipe() {
        MachineRecipe recipe = runtime.recipe();
        if (recipe == null) return;
        lastRecipe = recipe;
        lastRecipeCatalogVersion = currentRecipeCatalog().version();
        restartPending = false;
        clearFullSearchRequest();
        installStartedProgram();
    }

    public void markRecipeFinished() {
        restartPending = lastRecipe != null;
    }

    public StartResult tryRestartLastRecipe(List<MachineRecipe> candidates, long availableParallelism,
                                        long structureVersion) {
        if (!restartPending || hasFullSearchPending()) return StartResult.NO_MATCH;
        MachineRecipe retryRecipe = peekRestartRecipe(candidates, availableParallelism, structureVersion);
        if (retryRecipe == null) {
            reportLastRecipeStartResult(StartResult.NO_MATCH);
            return StartResult.NO_MATCH;
        }
        return startRecipe(retryRecipe, availableParallelism,
                controller.currentRuntimeSnapshot().structure().version(), null, StartOrigin.LAST_RECIPE);
    }

    @Override
    protected void onLastRecipeStartResult(StartResult result) {
        switch (result) {
            case STARTED, NO_MATCH, FAILED, CANCELLED -> restartPending = false;
            case PENDING, DEFERRED -> { }
        }
    }

    /** Starts one ordinary-controller candidate search from immutable main-thread captures. */
    public StartResult searchAndStartAsyncRecipe(List<MachineRecipe> candidates, long availableParallelism,
                                             long structureVersion) {
        if (controller.activeWorkMode() != MachineWorkMode.ASYNC || !(controller.getLevel() instanceof ServerLevel level)) {
            return searchAndStartRecipe(candidates, availableParallelism, structureVersion);
        }
        if (runtime.active() || isStartPending()) return StartResult.DEFERRED;
        if (hasPendingAsyncSearch()) return StartResult.PENDING;
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        Machine machine = currentMachine();
        if (machine == null || availableParallelism <= 0L) return StartResult.NO_MATCH;
        long catalogVersion = currentRecipeCatalog().version();
        AsyncRequirementPlanner.RecipeSearchRequest request;
        try {
            request = AsyncRequirementPlanner.captureRecipeSearch(snapshot, candidatesForMachine(candidates), availableParallelism,
                    controller.componentRuntime().capabilitySnapshot(),
                    controller.componentRuntime().modifierList(), catalogVersion, effectiveRecipeCache);
        } catch (RuntimeException exception) {
            return searchAndStartRecipe(candidates, availableParallelism, structureVersion);
        }
        long searchId = ++nextAsyncSearchId;
        MachineAsyncCoordinator.TaskKey taskKey = new MachineAsyncCoordinator.TaskKey(controller.getBlockPos(),
                level.getGameTime(), MachineWorkMode.ASYNC, asyncSearchLaneId(), controller.lifecycleEpoch());
        PendingAsyncSearch pending = new PendingAsyncSearch(request, taskKey, catalogVersion);
        pendingAsyncSearch = pending;
        if (submitAsyncRecipeSearch(taskKey, request, catalogVersion, searchId, asyncSearchLaneId(),
                () -> clearPendingAsyncSearch(pending, false))) return StartResult.PENDING;
        clearPendingAsyncSearch(pending, false);
        return StartResult.DEFERRED;
    }

    /** Returns whether a current ordinary-controller search owns the single in-flight slot. */
    public boolean hasPendingAsyncSearch() {
        PendingAsyncSearch pending = pendingAsyncSearch;
        if (pending == null) return false;
        if (isPendingAsyncSearchCurrent(pending)) return true;
        clearPendingAsyncSearch(pending, true);
        return false;
    }

    public @Nullable MachineRecipe peekRestartRecipe(List<MachineRecipe> candidates, long availableParallelism,
                                                         long structureVersion) {
        if (!restartPending || hasFullSearchPending()) return null;
        MachineRecipe retryRecipe = resolveCurrentLastRecipe(candidatesForMachine(candidates));
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        boolean canRestart = retryRecipe != null && availableParallelism > 0
                && snapshot.structure().formed() && snapshot.structure().structureAreaLoaded()
                && snapshot.moduleConnectionStatus().canRunRecipe(retryRecipe.requiredHostIds())
                && AsyncRequirementPlanner.capturedRequirementFailure(snapshot, retryRecipe) == null;
        return canRestart ? retryRecipe : null;
    }

    /** Compatibility entry point; inspecting a candidate no longer consumes its restart opportunity. */
    public @Nullable MachineRecipe consumeRestartRecipe(List<MachineRecipe> candidates, long availableParallelism,
                                                        long structureVersion) {
        return peekRestartRecipe(candidates, availableParallelism, structureVersion);
    }

    public boolean hasRestartPending() {
        return restartPending;
    }

    @Override
    public void cancelAsyncState() {
        clearPendingAsyncSearch(pendingAsyncSearch, true);
        super.cancelAsyncState();
    }

    @Override
    public void invalidate() {
        clearPendingAsyncSearch(pendingAsyncSearch, true);
        super.invalidate();
        clearLastRecipe();
    }

    @Override
    protected void onDiscardedForRecipePoolChange() {
        clearLastRecipe();
    }

    private String asyncSearchLaneId() {
        return "normal-search/" + asyncLaneId();
    }

    private boolean isPendingAsyncSearchCurrent(PendingAsyncSearch pending) {
        if (pending.taskKey().lifecycleEpoch() != controller.lifecycleEpoch()
                || !asyncSearchLaneId().equals(pending.taskKey().laneId()) || runtime.active() || isStartPending()) {
            return false;
        }
        ControllerRuntimeSnapshot current = controller.currentRuntimeSnapshot();
        ControllerRuntimeSnapshot captured = pending.request().snapshot();
        Machine currentMachine = currentMachine();
        Machine capturedMachine = captured.structure().machine() == null
                ? captured.structure().configuredMachine() : captured.structure().machine();
        return current.structure().formed() && current.structure().structureAreaLoaded()
                && current.structure().version() == captured.structure().version()
                && current.capabilityVersion() == captured.capabilityVersion()
                && current.modifierVersion() == captured.modifierVersion()
                && current.stateVersion() == captured.stateVersion()
                && currentMachine != null && capturedMachine != null
                && currentMachine.registryName().equals(capturedMachine.registryName())
                && currentRecipeCatalog().version() == pending.catalogVersion();
    }

    private void clearPendingAsyncSearch(@Nullable PendingAsyncSearch pending, boolean cancelTask) {
        if (pending == null || pendingAsyncSearch != pending) return;
        pendingAsyncSearch = null;
        if (cancelTask && controller.getLevel() instanceof ServerLevel level) {
            MachineAsyncCoordinator.get(level).cancel(pending.taskKey());
        }
    }

    private List<MachineRecipe> candidatesForMachine(List<MachineRecipe> candidates) {
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        if (recipePoolId == null || candidates == null) return List.of();
        return candidates.stream().filter(recipe -> recipe != null && recipePoolId.equals(recipe.recipePoolId())).toList();
    }

    private boolean recipeBelongsToCurrentMachine(MachineRecipe recipe) {
        ResourceLocation recipePoolId = controller.currentRecipePoolId();
        return recipePoolId != null && recipePoolId.equals(recipe.recipePoolId());
    }

    private Machine currentMachine() {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        return snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
    }

    private MachineRecipeCatalog currentRecipeCatalog() {
        return RecipeRegistry.catalogForPool(controller.currentRecipePoolId());
    }

    private @Nullable MachineRecipe resolveCurrentLastRecipe(List<MachineRecipe> candidates) {
        if (lastRecipe == null) return null;
        if (!recipeBelongsToCurrentMachine(lastRecipe)) {
            clearLastRecipe();
            requestFullSearch();
            return null;
        }
        MachineRecipeCatalog catalog = currentRecipeCatalog();
        if (lastRecipeCatalogVersion != catalog.version()) {
            MachineRecipe current = catalog.recipes().stream()
                    .filter(recipe -> lastRecipe.id().equals(recipe.id())).findFirst().orElse(null);
            if (current == null || !ActiveMachineRecipe.sameDefinition(lastRecipe, current, registryAccess())) {
                clearLastRecipe();
                requestFullSearch();
                return null;
            }
            lastRecipe = current;
            lastRecipeCatalogVersion = catalog.version();
        }
        return candidates.contains(lastRecipe) ? lastRecipe : null;
    }

    private @Nullable HolderLookup.Provider registryAccess() {
        return controller.getLevel() == null ? null : controller.getLevel().registryAccess();
    }

    private void clearLastRecipe() {
        lastRecipe = null;
        lastRecipeCatalogVersion = Long.MIN_VALUE;
        restartPending = false;
    }
}
