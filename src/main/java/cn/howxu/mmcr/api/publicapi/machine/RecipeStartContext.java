package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Context supplied before a recipe consumes its start inputs.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeStartContext {
    private final MachineBehaviorContext machineContext;
    private final MachineRecipe recipe;
    private final int requestedParallelism;
    private final int effectiveParallelism;
    private int duration;
    private List<MachineRequirement> requirements;
    private List<MachineOutput> outputs;
    private boolean cancelled;

    public RecipeStartContext(MachineRecipe recipe, int requestedParallelism, int effectiveParallelism) {
        this(MachineBehaviorContext.empty(Objects.requireNonNull(recipe, "recipe").machineId()), recipe,
                requestedParallelism, effectiveParallelism, recipe.tickTime(), recipe.runtimeRequirements(),
                recipe.runtimeMachineOutputs());
    }

    public RecipeStartContext(MachineBehaviorContext machineContext, MachineRecipe recipe,
                              int requestedParallelism, int effectiveParallelism, int duration,
                              List<MachineRequirement> requirements, List<MachineOutput> outputs) {
        this.machineContext = Objects.requireNonNull(machineContext, "machineContext");
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        if (requestedParallelism <= 0) throw new IllegalArgumentException("requestedParallelism must be positive");
        if (effectiveParallelism <= 0) throw new IllegalArgumentException("effectiveParallelism must be positive");
        if (duration <= 0) throw new IllegalArgumentException("duration must be positive");
        this.requestedParallelism = requestedParallelism;
        this.effectiveParallelism = effectiveParallelism;
        this.duration = duration;
        this.requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        this.outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
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

    public int requestedParallelism() {
        return requestedParallelism;
    }

    public int effectiveParallelism() {
        return effectiveParallelism;
    }

    public int duration() {
        return duration;
    }

    public void setDuration(int duration) {
        if (duration <= 0) throw new IllegalArgumentException("duration must be positive");
        this.duration = duration;
    }

    public List<MachineRequirement> requirements() {
        return requirements;
    }

    public void setRequirements(List<MachineRequirement> requirements) {
        this.requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        this.outputs = outputsFromRequirements(this.requirements);
    }

    public List<MachineOutput> outputs() {
        return outputs;
    }

    public void setOutputs(List<MachineOutput> outputs) {
        List<MachineOutput> copy = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        List<MachineRequirement> replacement = outputRequirements(copy);
        List<MachineRequirement> nextRequirements = new ArrayList<>();
        boolean inserted = false;
        for (MachineRequirement requirement : requirements) {
            if (isItemOrFluidOutput(requirement)) {
                if (!inserted) {
                    nextRequirements.addAll(replacement);
                    inserted = true;
                }
            } else {
                nextRequirements.add(requirement);
            }
        }
        if (!inserted) nextRequirements.addAll(replacement);
        this.requirements = List.copyOf(nextRequirements);
        this.outputs = copy;
    }

    public ExecutionSnapshot snapshot() {
        return new ExecutionSnapshot(duration, requirements, outputs);
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean cancelled() {
        return cancelled;
    }

    private static List<MachineOutput> outputsFromRequirements(List<MachineRequirement> requirements) {
        List<MachineOutput> result = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT) {
                result.add(new MachineOutput.ItemOutput(item.resolvedStack(), item.chance()));
            } else if (requirement instanceof FluidRequirement fluid
                    && fluid.io() == RecipeModifier.IOType.OUTPUT) {
                result.add(new MachineOutput.FluidOutput(fluid.stack(), fluid.chance()));
            }
        }
        return List.copyOf(result);
    }

    private static List<MachineRequirement> outputRequirements(List<MachineOutput> outputs) {
        List<MachineRequirement> result = new ArrayList<>(outputs.size());
        for (MachineOutput output : outputs) {
            if (output instanceof MachineOutput.ItemOutput item) {
                result.add(MachineRequirement.itemOutput(item.stack(), item.chance()));
            } else if (output instanceof MachineOutput.FluidOutput fluid) {
                result.add(MachineRequirement.fluidOutput(fluid.stack(), fluid.chance()));
            }
        }
        return result;
    }

    private static boolean isItemOrFluidOutput(MachineRequirement requirement) {
        return requirement.io() == RecipeModifier.IOType.OUTPUT
                && (requirement instanceof ItemRequirement || requirement instanceof FluidRequirement);
    }

    public record ExecutionSnapshot(int duration, List<MachineRequirement> requirements,
                                    List<MachineOutput> outputs) {
        public ExecutionSnapshot {
            if (duration <= 0) throw new IllegalArgumentException("duration must be positive");
            requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
            outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        }
    }
}
