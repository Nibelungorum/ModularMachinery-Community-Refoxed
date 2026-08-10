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
            lastFailureUnloc = result.failureUnloc();
            status = Status.FAILED;
            return false;
        }
        ActiveMachineRecipe next = result.activeRecipe();
        RecipeCraftingContext nextContext = result.context();
        if (controller.getLevel() instanceof ServerLevel serverLevel && controller.resourceDomain() != null) {
            return requestStart(serverLevel, controller.resourceDomain(), next, nextContext, structureVersion);
        }
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
        onStarted();
        return true;
    }

    private boolean requestStart(ServerLevel level, cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry.ResourceDomain domain,
                                 ActiveMachineRecipe next, RecipeCraftingContext nextContext, long structureVersion) {
        startPending = true;
        pendingStartContext = nextContext;
        SharedIoCoordinator.get(level).enqueue(new SharedIoCoordinator.StartRequest(
                domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), laneId()),
                structureVersion,
                next.getMaxParallelism(),
                requested -> {
                    if (!startPending) return 0;
                    int granted = nextContext.commitStart(next.getRecipe(), requested);
                    if (granted <= 0) {
                        startPending = false;
                        pendingStartContext = null;
                        contextPool.returnContext(nextContext);
                    }
                    return granted;
                },
                granted -> {
                    startPending = false;
                    pendingStartContext = null;
                    next.setParallelism(granted);
                    next.refreshTotalTick(nextContext);
                    activeRecipe = next;
                    context = nextContext;
                    status = Status.WORKING;
                    lastFailureUnloc = null;
                    onStarted();
                },
                () -> startPending && controller != null && controller.resourceDomain() != null && controller.resourceDomain().equals(domain),
                controller::getStructureVersion
        ));
        return true;
    }

    public void tick() {
        if (startPending && pendingStartContext != null && !pendingStartContext.isStructureVersionCurrent()) {
            contextPool.returnContext(pendingStartContext);
            pendingStartContext = null;
            startPending = false;
        }
        if (activeRecipe == null || context == null) return;
        ActiveMachineRecipe.TickStatus tickStatus = activeRecipe.tick(context);
        if (tickStatus == ActiveMachineRecipe.TickStatus.FINISHED) {
            onFinished();
            contextPool.returnContext(context);
            activeRecipe = null;
            context = null;
            status = Status.IDLE;
        } else if (tickStatus == ActiveMachineRecipe.TickStatus.WAITING) {
            lastFailureUnloc = context.getLastFailureUnloc();
            status = Status.WAITING;
        } else {
            status = Status.WORKING;
        }
    }

    public void invalidate() {
        if (context != null) contextPool.returnContext(context);
        if (pendingStartContext != null) contextPool.returnContext(pendingStartContext);
        activeRecipe = null;
        context = null;
        pendingStartContext = null;
        status = Status.IDLE;
        startPending = false;
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
}
