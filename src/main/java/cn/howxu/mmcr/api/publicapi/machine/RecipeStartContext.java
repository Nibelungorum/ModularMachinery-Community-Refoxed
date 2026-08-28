package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Context supplied before a recipe consumes its start inputs.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeStartContext {
    private final MachineRecipe recipe;
    private final int requestedParallelism;
    private final int effectiveParallelism;
    private boolean cancelled;

    public RecipeStartContext(MachineRecipe recipe, int requestedParallelism, int effectiveParallelism) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        if (requestedParallelism <= 0) throw new IllegalArgumentException("requestedParallelism must be positive");
        if (effectiveParallelism <= 0) throw new IllegalArgumentException("effectiveParallelism must be positive");
        this.requestedParallelism = requestedParallelism;
        this.effectiveParallelism = effectiveParallelism;
    }

    public MachineRecipe recipe() {
        return recipe;
    }

    public Identifier recipeId() {
        return recipe.id();
    }

    public int requestedParallelism() {
        return requestedParallelism;
    }

    public int effectiveParallelism() {
        return effectiveParallelism;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean cancelled() {
        return cancelled;
    }
}
