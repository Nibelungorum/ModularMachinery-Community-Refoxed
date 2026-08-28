package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.publicapi.machine.RecipeFinishContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeStartContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeTickContext;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehaviorContext;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * KubeJS bridge for collecting machine behavior callbacks during startup.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineBehaviorBuilderJS {
    private final MachineBehavior.Kind kind;
    private final RecipeBehavior.Builder recipeBuilder;
    private final TickBehavior.Builder tickBuilder;

    public MachineBehaviorBuilderJS(MachineBehavior.Kind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
        recipeBuilder = kind == MachineBehavior.Kind.RECIPE ? RecipeBehavior.builder() : null;
        tickBuilder = kind == MachineBehavior.Kind.TICK ? TickBehavior.builder() : null;
    }

    public MachineBehaviorBuilderJS idleStart(Consumer<MachineBehaviorContext> callback) {
        requireRecipe();
        recipeBuilder.idleStart(Objects.requireNonNull(callback, "callback")::accept);
        return this;
    }

    public MachineBehaviorBuilderJS idleEnd(Consumer<MachineBehaviorContext> callback) {
        requireRecipe();
        recipeBuilder.idleEnd(Objects.requireNonNull(callback, "callback")::accept);
        return this;
    }

    public MachineBehaviorBuilderJS beforeStart(Consumer<RecipeStartContext> callback) {
        requireRecipe();
        recipeBuilder.beforeStart(Objects.requireNonNull(callback, "callback")::accept);
        return this;
    }

    public MachineBehaviorBuilderJS recipeTick(Consumer<RecipeTickContext> callback) {
        requireRecipe();
        recipeBuilder.recipeTick(Objects.requireNonNull(callback, "callback")::accept);
        return this;
    }

    public MachineBehaviorBuilderJS beforeFinish(Consumer<RecipeFinishContext> callback) {
        requireRecipe();
        recipeBuilder.beforeFinish(Objects.requireNonNull(callback, "callback")::accept);
        return this;
    }

    public MachineBehaviorBuilderJS serverTick(Consumer<TickBehaviorContext> callback) {
        requireTick();
        tickBuilder.serverTick(Objects.requireNonNull(callback, "callback")::accept);
        return this;
    }

    public MachineBehavior build() {
        return kind == MachineBehavior.Kind.RECIPE ? recipeBuilder.build() : tickBuilder.build();
    }

    private void requireRecipe() {
        if (kind != MachineBehavior.Kind.RECIPE) {
            throw new IllegalStateException("Recipe callbacks require recipe behavior");
        }
    }

    private void requireTick() {
        if (kind != MachineBehavior.Kind.TICK) {
            throw new IllegalStateException("serverTick requires tick behavior");
        }
    }
}
