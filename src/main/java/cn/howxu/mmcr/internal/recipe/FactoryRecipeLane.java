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
    private boolean started;
    private boolean closed;
    private String lastFailureUnloc;

    public FactoryRecipeLane(ActiveMachineRecipe recipe,
                             RecipeCraftingContext context,
                             Consumer<RecipeCraftingContext> contextReturner) {
        if (recipe == null) throw new IllegalArgumentException("recipe null");
        if (context == null) throw new IllegalArgumentException("context null");
        if (contextReturner == null) throw new IllegalArgumentException("contextReturner null");
        this.recipe = recipe;
        this.context = context;
        this.contextReturner = contextReturner;
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
        int granted = context.commitStart(recipe.getRecipe(), recipe.getMaxParallelism());
        if (granted <= 0) {
            lastFailureUnloc = context.getLastFailureUnloc();
            close();
            return;
        }
        recipe.setParallelism(granted);
        recipe.refreshTotalTick(context);
    }

    @Override
    public boolean tick() {
        return tick(0L);
    }

    @Override
    public boolean tick(long gameTime) {
        if (closed) return true;
<<<<<<< HEAD
        ActiveMachineRecipe.TickStatus status = recipe.tick(context, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, gameTime)));
        if (status == ActiveMachineRecipe.TickStatus.FINISHED || status == ActiveMachineRecipe.TickStatus.CANCELLED) {
            lastFailureUnloc = context.getLastFailureUnloc();
=======
        int tickTime = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, gameTime));
        if (recipe.isFinishPending()) {
            if (!recipe.shouldRetryFinish(tickTime)) return false;
            ActiveMachineRecipe.TickStatus status = recipe.applyTickGrant(true,
                    context.commitSynchronousOutputs(recipe.getRecipe(), recipe.getParallelism()), tickTime);
            if (status == ActiveMachineRecipe.TickStatus.FINISHED) {
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
        boolean resourcesGranted = context.commitSynchronousIoTick(recipe.getRecipe(), recipe.getParallelism());
        boolean outputsCommitted = resourcesGranted && finalTick
                && context.commitSynchronousOutputs(recipe.getRecipe(), recipe.getParallelism());
        ActiveMachineRecipe.TickStatus status = recipe.applyTickGrant(resourcesGranted, outputsCommitted, tickTime);
        if (status == ActiveMachineRecipe.TickStatus.FINISHED) {
>>>>>>> feat/shared-multiblock-io
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
}
