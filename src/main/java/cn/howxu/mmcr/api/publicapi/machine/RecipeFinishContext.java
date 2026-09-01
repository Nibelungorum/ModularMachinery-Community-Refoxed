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
    private final MachineBehaviorContext machineContext;
    private final MachineRecipe recipe;
    private final long requestedParallelism;
    private final long effectiveParallelism;
    private List<MachineOutput> outputs;
    private boolean cancelled;
    private boolean outputsDiscarded;

    public RecipeFinishContext(MachineRecipe recipe, long requestedParallelism, long effectiveParallelism,
                               List<MachineOutput> outputs) {
        this(MachineBehaviorContext.empty(Objects.requireNonNull(recipe, "recipe").machineId()), recipe,
                requestedParallelism, effectiveParallelism, outputs);
    }

    public RecipeFinishContext(MachineBehaviorContext machineContext, MachineRecipe recipe,
                               long requestedParallelism, long effectiveParallelism, List<MachineOutput> outputs) {
        this.machineContext = Objects.requireNonNull(machineContext, "machineContext");
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        if (requestedParallelism <= 0) throw new IllegalArgumentException("requestedParallelism must be positive");
        if (effectiveParallelism <= 0) throw new IllegalArgumentException("effectiveParallelism must be positive");
        this.requestedParallelism = requestedParallelism;
        this.effectiveParallelism = effectiveParallelism;
        this.outputs = new ArrayList<>(MachineOutput.copyList(Objects.requireNonNull(outputs, "outputs")));
    }

    public MachineRecipe recipe() {
        return recipe;
    }

    public MachineBehaviorContext machineContext() {
        return machineContext;
    }

    public Identifier recipeId() {
        return recipe.id();
    }

    public long requestedParallelism() {
        return requestedParallelism;
    }

    public long effectiveParallelism() {
        return effectiveParallelism;
    }

    public List<MachineOutput> outputs() {
        return outputs;
    }

    public void setOutputs(List<MachineOutput> outputs) {
        List<MachineOutput> copies = MachineOutput.copyList(Objects.requireNonNull(outputs, "outputs"));
        for (MachineOutput output : copies) {
            if (output instanceof MachineOutput.ItemOutput item && item.stack().isEmpty()
                    || output instanceof MachineOutput.FluidOutput fluid && fluid.stack().isEmpty()) {
                throw new IllegalArgumentException("Built-in recipe outputs must not be empty");
            }
        }
        this.outputs = new ArrayList<>(copies);
    }

    public void discardOutputs() {
        outputsDiscarded = true;
        outputs = new ArrayList<>();
    }

    public boolean outputsDiscarded() {
        return outputsDiscarded;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean cancelled() {
        return cancelled;
    }
}
