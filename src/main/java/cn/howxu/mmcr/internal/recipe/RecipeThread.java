package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * State holder for one server-side recipe execution thread.
 *
 * @author howxu <dev@howxu.cn>
 */
public abstract class RecipeThread {
    private static final Logger LOG = LoggerFactory.getLogger(RecipeThread.class);

    public enum Status { IDLE, WORKING, WAITING, FAILED }

    protected @Nullable MachineControllerBlockEntity controller;
    protected final RecipeCraftingContextPool contextPool;
    protected @Nullable ActiveMachineRecipe activeRecipe;
    protected @Nullable RecipeCraftingContext context;
    protected Status status = Status.IDLE;
    protected @Nullable String lastFailureUnloc;

    protected RecipeThread(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool) {
        this.controller = controller;
        this.contextPool = contextPool;
    }

    public boolean searchAndStartRecipe(List<MachineRecipe> candidates, int availableParallelism, long structureVersion) {
        Identifier machineId = controller == null || controller.getFoundMachine() == null
                ? null : controller.getFoundMachine().registryName();
        if (machineId == null || availableParallelism <= 0) return false;
        RecipeSearchResult result = new RecipeSearchTask(controller, machineId, structureVersion,
                availableParallelism, candidates, contextPool).compute();
        if (!result.success()) {
            lastFailureUnloc = result.levelFailure() == null
                    ? result.failureUnloc()
                    : "gui.mmcr.controller.failure.level_insufficient";
            status = Status.FAILED;
            return false;
        }
        ActiveMachineRecipe next = result.activeRecipe();
        RecipeCraftingContext nextContext = result.context();
        int searchedParallelism = next.getParallelism();
        if (!next.canStartCrafting(nextContext) || !next.start(nextContext)) {
            contextPool.returnContext(nextContext);
            lastFailureUnloc = nextContext.getLastFailureUnloc();
            status = Status.FAILED;
            LOG.info("[ParallelStart] machine={} recipe={} availableParallelism={} searchedParallelism={} startFailed failure={}",
                    machineId,
                    next.getRecipe() == null ? null : next.getRecipe().id(),
                    availableParallelism,
                    searchedParallelism,
                    lastFailureUnloc);
            return false;
        }
        LOG.info("[ParallelStart] machine={} recipe={} availableParallelism={} searchedParallelism={} startedParallelism={}",
                machineId,
                next.getRecipe().id(),
                availableParallelism,
                searchedParallelism,
                next.getParallelism());
        activeRecipe = next;
        context = nextContext;
        status = Status.WORKING;
        lastFailureUnloc = null;
        controller.clearLastFailureOnRecipeStart();
        onStarted();
        return true;
    }

    public void tick() {
        if (activeRecipe == null || context == null) return;
        ActiveMachineRecipe.TickStatus tickStatus = activeRecipe.tick(context);
        if (tickStatus == ActiveMachineRecipe.TickStatus.FINISHED) {
            onFinished();
            contextPool.returnContext(context);
            activeRecipe = null;
            context = null;
            status = Status.IDLE;
        } else if (tickStatus == ActiveMachineRecipe.TickStatus.CANCELLED) {
            lastFailureUnloc = context.getLastFailureUnloc();
            contextPool.returnContext(context);
            activeRecipe = null;
            context = null;
            status = Status.FAILED;
        } else if (tickStatus == ActiveMachineRecipe.TickStatus.WAITING) {
            lastFailureUnloc = context.getLastFailureUnloc();
            status = Status.WAITING;
        } else {
            status = Status.WORKING;
        }
    }

    public void invalidate() {
        if (context != null) contextPool.returnContext(context);
        activeRecipe = null;
        context = null;
        status = Status.IDLE;
    }

    public void bindController(MachineControllerBlockEntity controller) {
        if (this.controller == controller) return;
        this.controller = controller;
        if (activeRecipe != null && context == null && controller != null) {
            context = contextPool.borrow(activeRecipe, controller);
        }
    }

    protected abstract void onStarted();
    protected abstract void onFinished();

    public @Nullable ActiveMachineRecipe getActiveRecipe() { return activeRecipe; }
    public Status getStatus() { return status; }
    public @Nullable String getLastFailureUnloc() { return lastFailureUnloc; }
    public boolean isIdle() { return activeRecipe == null && context == null; }
    public int usedParallelism() { return activeRecipe == null ? 0 : activeRecipe.getParallelism(); }
}
