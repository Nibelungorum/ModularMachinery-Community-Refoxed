package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Context supplied before a recipe commits its outputs.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeFinishContext {
    private final MachineRecipe recipe;
    private final int requestedParallelism;
    private final int effectiveParallelism;
    private List<MachineOutput> outputs;
    private boolean cancelled;

    public RecipeFinishContext(MachineRecipe recipe, int requestedParallelism, int effectiveParallelism,
                               List<MachineOutput> outputs) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        if (requestedParallelism <= 0) throw new IllegalArgumentException("requestedParallelism must be positive");
        if (effectiveParallelism <= 0) throw new IllegalArgumentException("effectiveParallelism must be positive");
        this.requestedParallelism = requestedParallelism;
        this.effectiveParallelism = effectiveParallelism;
        setOutputs(outputs);
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

    public List<MachineOutput> outputs() {
        return outputs;
    }

    public void setOutputs(List<MachineOutput> outputs) {
        this.outputs = new ArrayList<>(Objects.requireNonNull(outputs, "outputs"));
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean cancelled() {
        return cancelled;
    }
}
