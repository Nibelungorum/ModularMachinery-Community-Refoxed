package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeCatalog;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
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
    private long lastRecipeStructureVersion = Long.MIN_VALUE;
    private long lastRecipeCapabilityVersion = Long.MIN_VALUE;
    private long lastRecipeModifierVersion = Long.MIN_VALUE;
    private long lastRecipeComponentStateVersion = Long.MIN_VALUE;
    private long lastRecipeCatalogVersion = Long.MIN_VALUE;
    private boolean restartPending;
    private @Nullable PendingAsyncSearch pendingAsyncSearch;
    private long nextAsyncSearchId;

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
        if (lastRecipe != null && !recipeBelongsToCurrentMachine(lastRecipe)) {
            clearLastRecipe();
        } else if (lastRecipe != null && lastRecipeCatalogVersion != Long.MIN_VALUE) {
            MachineRecipeCatalog catalog = RecipeRegistry.catalogForMachine(currentMachine());
            MachineRecipe current = catalog.recipes().stream()
                    .filter(candidate -> lastRecipe.id().equals(candidate.id()))
                    .findFirst().orElse(null);
            if (lastRecipeCatalogVersion != catalog.version()
                    && (current == null || !ActiveMachineRecipe.sameDefinition(lastRecipe, current, registryAccess()))) {
                clearLastRecipe();
            }
        }
        if (controlsControllerRuntime) {
            controller.onNormalRecipeThreadFinished(runtime.active() || isStartPending());
        }
    }

    @Override
    protected void onRecipeFinished() {
        markRecipeFinished();
        MachineRecipe restartRecipe = consumeRestartRecipe(RecipeRegistry.catalogForMachine(currentMachine()).recipes(),
                controller.getMaxParallelism(), controller.currentRuntimeSnapshot().structure().version());
        if (restartRecipe == null || !canRestartNow(restartRecipe)) return;
        if (controller.activeWorkMode() == MachineWorkMode.ASYNC) {
            enqueueAsyncFinishRestart(restartRecipe, controller.getMaxParallelism(),
                    controller.currentRuntimeSnapshot().structure().version(), null);
        } else {
            startRecipe(restartRecipe, controller.getMaxParallelism(),
                    controller.currentRuntimeSnapshot().structure().version());
        }
    }

    private boolean canRestartNow(MachineRecipe recipe) {
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        Machine machine = currentMachine();
        RecipeSearchResult result = new RecipeSearchTask(snapshot, machine.registryName(), controller.currentRecipePoolId(),
                snapshot.structure().version(), controller.getMaxParallelism(), List.of(recipe),
                controller.componentRuntime().capabilities()).compute();
        if (result.success()) return true;
        runtime.prepareAsyncStart(recipe, controller.getMaxParallelism());
        runtime.flushAsyncScreenText();
        onStartSearchFailed(result.failure());
        return false;
    }

    @Override
    protected void onRecipeFailure() {
        if (controlsControllerRuntime) controller.onNormalRecipeThreadFinished(false);
    }

    @Override
    protected void onStartSearchFailed(@Nullable ExecutionStatus failure) {
        super.onStartSearchFailed(failure);
        if (controlsControllerRuntime) controller.onNormalRecipeThreadSearchFailed();
    }

    public void rememberStartedRecipe() {
        MachineRecipe recipe = runtime.recipe();
        if (recipe == null) return;
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        lastRecipe = recipe;
        lastRecipeStructureVersion = snapshot.structure().version();
        lastRecipeCapabilityVersion = snapshot.capabilityVersion();
        lastRecipeModifierVersion = snapshot.modifierVersion();
        lastRecipeComponentStateVersion = snapshot.stateVersion();
        lastRecipeCatalogVersion = RecipeRegistry.catalogForMachine(currentMachine()).version();
    }

    public void markRecipeFinished() {
        restartPending = lastRecipe != null;
    }

    public boolean tryRestartLastRecipe(List<MachineRecipe> candidates, long availableParallelism,
                                        long structureVersion) {
        MachineRecipe retryRecipe = consumeRestartRecipe(candidates, availableParallelism, structureVersion);
        return retryRecipe != null && startRecipe(retryRecipe, availableParallelism, structureVersion);
    }

    /** Starts one ordinary-controller candidate search from immutable main-thread captures. */
    public boolean searchAndStartAsyncRecipe(List<MachineRecipe> candidates, long availableParallelism,
                                             long structureVersion) {
        if (controller.activeWorkMode() != MachineWorkMode.ASYNC || !(controller.getLevel() instanceof ServerLevel level)) {
            return searchAndStartRecipe(candidates, availableParallelism, structureVersion);
        }
        if (hasPendingAsyncSearch()) return false;
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        Machine machine = currentMachine();
        if (machine == null || availableParallelism <= 0L) return false;
        long catalogVersion = RecipeRegistry.catalogForMachine(machine).version();
        AsyncRequirementPlanner.RecipeSearchRequest request;
        try {
            request = AsyncRequirementPlanner.captureRecipeSearch(snapshot, candidatesForMachine(candidates), availableParallelism,
                    controller.componentRuntime().capabilities(),
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
                () -> clearPendingAsyncSearch(pending, false))) return true;
        clearPendingAsyncSearch(pending, false);
        return false;
    }

    /** Returns whether a current ordinary-controller search owns the single in-flight slot. */
    public boolean hasPendingAsyncSearch() {
        PendingAsyncSearch pending = pendingAsyncSearch;
        if (pending == null) return false;
        if (isPendingAsyncSearchCurrent(pending)) return true;
        clearPendingAsyncSearch(pending, true);
        return false;
    }

    public @Nullable MachineRecipe consumeRestartRecipe(List<MachineRecipe> candidates, long availableParallelism,
                                                         long structureVersion) {
        if (!restartPending) return null;
        restartPending = false;
        MachineRecipe retryRecipe = lastRecipe;
        ControllerRuntimeSnapshot snapshot = controller.currentRuntimeSnapshot();
        boolean canRestart = retryRecipe != null && availableParallelism > 0
                && lastRecipeStructureVersion == structureVersion
                && lastRecipeCapabilityVersion == snapshot.capabilityVersion()
                && lastRecipeModifierVersion == snapshot.modifierVersion()
                && lastRecipeComponentStateVersion == snapshot.stateVersion()
                && recipeBelongsToCurrentMachine(retryRecipe)
                && candidatesForMachine(candidates).contains(retryRecipe);
        return canRestart ? retryRecipe : null;
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
                && RecipeRegistry.catalogForMachine(currentMachine).version() == pending.catalogVersion();
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

    private @Nullable net.minecraft.core.HolderLookup.Provider registryAccess() {
        return controller.getLevel() == null ? null : controller.getLevel().registryAccess();
    }

    private void clearLastRecipe() {
        lastRecipe = null;
        lastRecipeStructureVersion = Long.MIN_VALUE;
        lastRecipeCapabilityVersion = Long.MIN_VALUE;
        lastRecipeModifierVersion = Long.MIN_VALUE;
        lastRecipeComponentStateVersion = Long.MIN_VALUE;
        lastRecipeCatalogVersion = Long.MIN_VALUE;
        restartPending = false;
    }
}
