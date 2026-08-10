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
        if (!recipe.start(context)) {
            lastFailureUnloc = context.getLastFailureUnloc();
            close();
        }
    }

    @Override
    public boolean tick() {
        return tick(0L);
    }

    @Override
    public boolean tick(long gameTime) {
        if (closed) return true;
        ActiveMachineRecipe.TickStatus status = recipe.tick(context, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, gameTime)));
        if (status == ActiveMachineRecipe.TickStatus.FINISHED || status == ActiveMachineRecipe.TickStatus.CANCELLED) {
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
