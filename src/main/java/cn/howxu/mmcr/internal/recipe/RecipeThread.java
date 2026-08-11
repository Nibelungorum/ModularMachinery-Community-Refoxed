package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeSearchResult;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
    private boolean startPending;
    private @Nullable RecipeCraftingContext pendingStartContext;
    private @Nullable cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry.ResourceDomain pendingStartDomain;
    private long nextStartToken;
    private long pendingStartToken;
    private boolean tickPending;
    private @Nullable cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry.ResourceDomain pendingTickDomain;

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
        return startRecipe(next, nextContext, structureVersion);
    }

    protected boolean startRecipe(ActiveMachineRecipe next, RecipeCraftingContext nextContext, long structureVersion) {
        Identifier machineId = controller == null || controller.getFoundMachine() == null
                ? null : controller.getFoundMachine().registryName();
        if (controller != null && controller.getLevel() instanceof ServerLevel serverLevel && controller.resourceDomain() != null) {
            return requestStart(serverLevel, controller.resourceDomain(), next, nextContext, structureVersion);
        }
        int searchedParallelism = next.getParallelism();
        int granted = nextContext.commitStart(next.getRecipe(), next.getMaxParallelism());
        if (granted <= 0) {
            contextPool.returnContext(nextContext);
            lastFailureUnloc = nextContext.getLastFailureUnloc();
            status = Status.FAILED;
<<<<<<< HEAD
            return false;
        }
=======
            LOG.info("[ParallelStart] machine={} recipe={} availableParallelism={} searchedParallelism={} startFailed failure={}",
                    machineId,
                    next.getRecipe() == null ? null : next.getRecipe().id(),
                    next.getMaxParallelism(),
                    searchedParallelism,
                    lastFailureUnloc);
            return false;
        }
        next.setParallelism(granted);
        next.refreshTotalTick(nextContext);
        LOG.info("[ParallelStart] machine={} recipe={} availableParallelism={} searchedParallelism={} startedParallelism={}",
                machineId,
                next.getRecipe().id(),
                next.getMaxParallelism(),
                searchedParallelism,
                next.getParallelism());
>>>>>>> feat/shared-multiblock-io
        activeRecipe = next;
        context = nextContext;
        status = Status.WORKING;
        lastFailureUnloc = null;
        controller.clearLastFailureOnRecipeStart();
        onStarted();
        return true;
    }

    private boolean requestStart(ServerLevel level, cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry.ResourceDomain domain,
                                 ActiveMachineRecipe next, RecipeCraftingContext nextContext, long structureVersion) {
        long startToken = ++nextStartToken;
        startPending = true;
        pendingStartContext = nextContext;
        pendingStartDomain = domain;
        pendingStartToken = startToken;
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.StartRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                next.getMaxParallelism(),
                requested -> {
                    if (!isPendingStart(startToken, nextContext)) return 0;
                    int granted = nextContext.commitStart(next.getRecipe(), requested);
                    if (granted <= 0) {
                        clearPendingStart(startToken, nextContext);
                        contextPool.returnContext(nextContext);
                    }
                    return granted;
                },
                granted -> {
                    if (!isPendingStart(startToken, nextContext)) return;
                    clearPendingStart(startToken, nextContext);
                    next.setParallelism(granted);
                    next.refreshTotalTick(nextContext);
                    activeRecipe = next;
                    context = nextContext;
                    status = Status.WORKING;
                    lastFailureUnloc = null;
                    onStarted();
                },
                () -> isPendingStart(startToken, nextContext) && controller != null
                        && controller.resourceDomain() != null && controller.resourceDomain().equals(domain),
                controller::getStructureVersion
        ));
        return true;
    }

    private boolean isPendingStart(long startToken, RecipeCraftingContext startContext) {
        return startPending && pendingStartToken == startToken && pendingStartContext == startContext;
    }

    private void clearPendingStart(long startToken, RecipeCraftingContext startContext) {
        if (!isPendingStart(startToken, startContext)) return;
        startPending = false;
        pendingStartContext = null;
        pendingStartDomain = null;
        pendingStartToken = 0L;
    }

    public void tick() {
        if (startPending && pendingStartContext != null
                && (!pendingStartContext.isStructureVersionCurrent() || !isCurrentDomain(pendingStartDomain))) {
            RecipeCraftingContext pendingContext = pendingStartContext;
            clearPendingStart(pendingStartToken, pendingContext);
            contextPool.returnContext(pendingContext);
        }
        if (activeRecipe == null || context == null) return;
        if (tickPending && !isCurrentDomain(pendingTickDomain)) {
            tickPending = false;
            pendingTickDomain = null;
        }
        if (tickPending) return;
        if (controller != null && controller.getLevel() instanceof ServerLevel level && controller.resourceDomain() != null) {
            if (activeRecipe.isFinishPending()) {
                requestFinishIfReady(level, controller.resourceDomain(), activeRecipe, context, controller.getStructureVersion());
                return;
            }
            requestTick(level, controller.resourceDomain(), activeRecipe, context, controller.getStructureVersion());
            return;
        }
        boolean resourcesGranted = context.commitSynchronousIoTick(activeRecipe.getRecipe(), activeRecipe.getParallelism());
        boolean outputsCommitted = resourcesGranted && activeRecipe.needsFinishCommit()
                && context.commitSynchronousOutputs(activeRecipe.getRecipe(), activeRecipe.getParallelism());
        applyTick(activeRecipe, context, resourcesGranted, outputsCommitted, 0);
    }

    private void requestTick(ServerLevel level, cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry.ResourceDomain domain,
                              ActiveMachineRecipe recipe, RecipeCraftingContext recipeContext, long structureVersion) {
        int gameTime = (int) level.getGameTime();
        tickPending = true;
        pendingTickDomain = domain;
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.TickRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                () -> {
                    if (!isActive(recipe, recipeContext, domain)) return false;
                    if (recipe.needsFinishCommit()
                            && !recipeContext.simulateOutputs(recipe.getRecipe(), recipe.getParallelism())) {
                        applyTick(recipe, recipeContext, false, false, gameTime);
                        tickPending = true;
                        return false;
                    }
                    boolean granted = recipeContext.coordinatorIoTick(recipe.getRecipe(), recipe.getParallelism()).getAsBoolean();
                    if (!granted) {
                        applyTick(recipe, recipeContext, false, false, gameTime);
                        tickPending = true;
                        return false;
                    }
                    if (granted && recipe.needsFinishCommit()) {
                        recipe.beginFinishCommit();
                        requestFinish(level, domain, recipe, recipeContext, structureVersion, gameTime);
                    }
                    if (granted && !recipe.needsFinishCommit()) applyTick(recipe, recipeContext, true, false, gameTime);
                    return true;
                },
                () -> isActive(recipe, recipeContext, domain),
                controller::getStructureVersion
        ));
    }

    private void requestFinish(ServerLevel level, cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry.ResourceDomain domain,
                                ActiveMachineRecipe recipe, RecipeCraftingContext recipeContext, long structureVersion, int gameTime) {
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.FinishRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                () -> {
                    if (!isActive(recipe, recipeContext, domain)) return false;
                    applyTick(recipe, recipeContext, true,
                            recipeContext.coordinatorOutputs(recipe.getRecipe(), recipe.getParallelism()).getAsBoolean(), gameTime);
                    return true;
                },
                () -> isActive(recipe, recipeContext, domain),
                controller::getStructureVersion
        ));
    }

    private void requestFinishIfReady(ServerLevel level, cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry.ResourceDomain domain,
                                      ActiveMachineRecipe recipe, RecipeCraftingContext recipeContext, long structureVersion) {
        int gameTime = (int) level.getGameTime();
        if (!recipe.shouldRetryFinish(gameTime)) {
            status = Status.WAITING;
            return;
        }
        tickPending = true;
        pendingTickDomain = domain;
        requestFinish(level, domain, recipe, recipeContext, structureVersion, gameTime);
    }

    private boolean isActive(ActiveMachineRecipe recipe, RecipeCraftingContext recipeContext,
                             cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry.ResourceDomain domain) {
        return activeRecipe == recipe && context == recipeContext && controller != null
                && domain.equals(controller.resourceDomain());
    }

    private void applyTick(ActiveMachineRecipe recipe, RecipeCraftingContext recipeContext,
                            boolean resourcesGranted, boolean outputsCommitted, int gameTime) {
        tickPending = false;
        pendingTickDomain = null;
        ActiveMachineRecipe.TickStatus tickStatus = recipe.applyTickGrant(resourcesGranted, outputsCommitted, gameTime);
        if (tickStatus == ActiveMachineRecipe.TickStatus.FINISHED) {
            onFinished();
            contextPool.returnContext(recipeContext);
            activeRecipe = null;
            context = null;
            status = Status.IDLE;
        } else if (tickStatus == ActiveMachineRecipe.TickStatus.CANCELLED) {
<<<<<<< HEAD
            lastFailureUnloc = context.getLastFailureUnloc();
            contextPool.returnContext(context);
            activeRecipe = null;
            context = null;
            status = Status.FAILED;
        } else if (tickStatus == ActiveMachineRecipe.TickStatus.WAITING) {
=======
>>>>>>> feat/shared-multiblock-io
            lastFailureUnloc = context.getLastFailureUnloc();
            contextPool.returnContext(context);
            activeRecipe = null;
            context = null;
            status = Status.FAILED;
        } else if (tickStatus == ActiveMachineRecipe.TickStatus.WAITING) {
            lastFailureUnloc = recipeContext.getLastFailureUnloc();
            status = Status.WAITING;
        } else {
            status = Status.WORKING;
        }
    }

    public void invalidate() {
        if (context != null) contextPool.returnContext(context);
        if (pendingStartContext != null) {
            RecipeCraftingContext pendingContext = pendingStartContext;
            clearPendingStart(pendingStartToken, pendingContext);
            contextPool.returnContext(pendingContext);
        }
        activeRecipe = null;
        context = null;
        tickPending = false;
        pendingTickDomain = null;
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
    protected String laneId() { return "base"; }

    public @Nullable ActiveMachineRecipe getActiveRecipe() { return activeRecipe; }
    public Status getStatus() { return status; }
    public @Nullable String getLastFailureUnloc() { return lastFailureUnloc; }
    public boolean isIdle() { return !startPending && activeRecipe == null && context == null; }
    public boolean isStartPending() { return startPending; }
    public int usedParallelism() { return activeRecipe == null ? 0 : activeRecipe.getParallelism(); }

    private boolean isCurrentDomain(@Nullable cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry.ResourceDomain domain) {
        return domain != null && controller != null && domain.equals(controller.resourceDomain());
    }
}
