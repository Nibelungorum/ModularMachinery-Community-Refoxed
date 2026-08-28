package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.recipe.MachineRecipe;

import java.util.Objects;

/**
 * Context supplied before a recipe's per-tick input plan.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeTickContext {
    private final MachineRecipe recipe;
    private final int currentTick;
    private final int totalTick;
    private final int parallelism;

    public RecipeTickContext(MachineRecipe recipe, int currentTick, int totalTick, int parallelism) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        if (currentTick < 0) throw new IllegalArgumentException("currentTick must not be negative");
        if (totalTick <= 0) throw new IllegalArgumentException("totalTick must be positive");
        if (parallelism <= 0) throw new IllegalArgumentException("parallelism must be positive");
        this.currentTick = currentTick;
        this.totalTick = totalTick;
        this.parallelism = parallelism;
    }

    public MachineRecipe recipe() {
        return recipe;
    }

    public int currentTick() {
        return currentTick;
    }

    public int totalTick() {
        return totalTick;
    }

    public int parallelism() {
        return parallelism;
    }
}
