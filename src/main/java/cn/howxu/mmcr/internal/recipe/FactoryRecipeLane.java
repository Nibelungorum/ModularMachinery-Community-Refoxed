package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;

import java.util.function.Consumer;

/**
 * Server-thread factory recipe lane. It owns one active recipe/context pair and returns the context exactly once.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRecipeLane implements FactoryRecipeScheduler.Lane {

    private final ActiveMachineRecipe recipe;
    private final RecipeCraftingContext context;
    private final Consumer<RecipeCraftingContext> contextReturner;
    private final Runnable onFinished;
    private boolean started;
    private boolean closed;
    private String lastFailureUnloc;

    public FactoryRecipeLane(ActiveMachineRecipe recipe,
                             RecipeCraftingContext context,
                             Consumer<RecipeCraftingContext> contextReturner) {
        this(recipe, context, contextReturner, () -> { });
    }

    public FactoryRecipeLane(ActiveMachineRecipe recipe,
                             RecipeCraftingContext context,
                             Consumer<RecipeCraftingContext> contextReturner,
                             Runnable onFinished) {
        if (recipe == null) throw new IllegalArgumentException("recipe null");
        if (context == null) throw new IllegalArgumentException("context null");
        if (contextReturner == null) throw new IllegalArgumentException("contextReturner null");
        if (onFinished == null) throw new IllegalArgumentException("onFinished null");
        this.recipe = recipe;
        this.context = context;
        this.contextReturner = contextReturner;
        this.onFinished = onFinished;
    }

    public ActiveMachineRecipe recipe() {
        return recipe;
    }

    public RecipeCraftingContext context() {
        return context;
    }

    public String lastFailureUnloc() {
        return lastFailureUnloc;
    }

    @Override
    public void start() {
        if (started) return;
        started = true;
        if (!recipe.canRunOnConnectedHost(context)) {
            failForInvalidModuleConnection();
            return;
        }
        int granted = context.commitStart(recipe, recipe.getMaxParallelism());
        if (granted <= 0) {
            lastFailureUnloc = context.getLastFailureUnloc();
            close();
            return;
        }
        recipe.refreshTotalTick(context);
    }

    @Override
    public boolean tick() {
        return tick(0L);
    }

    @Override
    public boolean tick(long gameTime) {
        if (closed) return true;
        int tickTime = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, gameTime));
        if (!recipe.canRunOnConnectedHost(context)) {
            failForInvalidModuleConnection();
            return recipe.getRecipe().doesCancelRecipeOnPerTickFailure();
        }
        if (recipe.isFinishPending()) {
            if (!recipe.shouldRetryFinish(tickTime)) return false;
            ActiveMachineRecipe.TickStatus status = recipe.applyTickGrant(true,
                    context.commitSynchronousOutputs(recipe.getRecipe(), recipe.getParallelism()), tickTime);
            if (status == ActiveMachineRecipe.TickStatus.FINISHED) {
                onFinished.run();
                close();
                return true;
            }
            lastFailureUnloc = context.getLastFailureUnloc();
            return false;
        }
        boolean finalTick = recipe.needsFinishCommit();
        if (finalTick && !context.simulateOutputs(recipe.getRecipe(), recipe.getParallelism())) {
            recipe.applyTickGrant(true, false, tickTime);
            lastFailureUnloc = context.getLastFailureUnloc();
            return false;
        }
        boolean resourcesGranted = context.commitSynchronousIoTick(recipe.getRecipe(), recipe.getParallelism(), recipe.inputConsumptionPlan());
        boolean outputsCommitted = resourcesGranted && finalTick
                && context.commitSynchronousOutputs(recipe.getRecipe(), recipe.getParallelism());
        ActiveMachineRecipe.TickStatus status = recipe.applyTickGrant(resourcesGranted, outputsCommitted, tickTime);
        if (status == ActiveMachineRecipe.TickStatus.FINISHED) {
            onFinished.run();
            close();
            return true;
        }
        if (status == ActiveMachineRecipe.TickStatus.CANCELLED) {
            lastFailureUnloc = context.getLastFailureUnloc();
            close();
            return true;
        }
        if (status == ActiveMachineRecipe.TickStatus.WAITING) {
            lastFailureUnloc = context.getLastFailureUnloc();
            if (recipe.getRecipe().doesCancelRecipeOnPerTickFailure()) {
                close();
                return true;
            }
        } else {
            lastFailureUnloc = null;
        }
        return false;
    }

    @Override
    public void stop() {
        close();
    }

    private void close() {
        if (closed) return;
        closed = true;
        contextReturner.accept(context);
    }

    private void failForInvalidModuleConnection() {
        recipe.doFailureAction(null);
        context.setModuleConnectionFailure();
        lastFailureUnloc = RecipeCraftingContext.FAILURE_MODULE_CONNECTION;
        if (recipe.getRecipe().doesCancelRecipeOnPerTickFailure()) close();
    }
}
