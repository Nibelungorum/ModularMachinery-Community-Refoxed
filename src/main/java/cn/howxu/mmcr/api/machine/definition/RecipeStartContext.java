package cn.howxu.mmcr.api.machine.definition;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
    private final long requestedParallelism;
    private final long effectiveParallelism;
    private int duration;
    private List<MachineRequirement> requirements;
    private List<MachineOutput> outputs;
    private boolean cancelled;

    public RecipeStartContext(MachineRecipe recipe, long requestedParallelism, long effectiveParallelism) {
        this(MachineBehaviorContext.empty(), recipe,
                requestedParallelism, effectiveParallelism,
                Math.max(1, IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyDuration(
                        recipe.modifiers(), recipe.tickTime()))),
                MachineRequirement.copyList(recipe.runtimeRequirements()),
                recipe.runtimeMachineOutputs());
    }

    public RecipeStartContext(MachineBehaviorContext machineContext, MachineRecipe recipe,
                              long requestedParallelism, long effectiveParallelism, int duration,
                              List<MachineRequirement> requirements, List<MachineOutput> outputs) {
        this.machineContext = Objects.requireNonNull(machineContext, "machineContext");
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        if (requestedParallelism <= 0) throw new IllegalArgumentException("requestedParallelism must be positive");
        if (effectiveParallelism <= 0) throw new IllegalArgumentException("effectiveParallelism must be positive");
        if (duration <= 0) throw new IllegalArgumentException("duration must be positive");
        this.requestedParallelism = requestedParallelism;
        this.effectiveParallelism = effectiveParallelism;
        this.duration = duration;
        this.requirements = MachineRequirement.copyList(Objects.requireNonNull(requirements, "requirements"));
        this.outputs = MachineOutput.copyList(Objects.requireNonNull(outputs, "outputs"));
    }

    public MachineRecipe recipe() {
        return recipe;
    }

    public MachineBehaviorContext machineContext() {
        return machineContext;
    }

    public ResourceLocation recipeId() {
        return recipe.id();
    }

    public long requestedParallelism() {
        return requestedParallelism;
    }

    public long effectiveParallelism() {
        return effectiveParallelism;
    }

    public int duration() {
        return duration;
    }

    public void setDuration(int duration) {
        if (duration <= 0) throw new IllegalArgumentException("duration must be positive");
        this.duration = duration;
    }

    /**
     * Replaces the count of an input whose ingredient resolves to exactly {@code item}.
     *
     * @return whether an input was replaced
     */
    public boolean replaceExactItemInputCount(Item item, int expectedCount, int replacementCount) {
        Objects.requireNonNull(item, "item");
        if (expectedCount < 1 || replacementCount < 1) throw new IllegalArgumentException("item counts must be positive");
        List<MachineRequirement> next = new ArrayList<>(requirements.size());
        boolean replaced = false;
        for (MachineRequirement requirement : requirements) {
            if (!replaced && requirement instanceof cn.howxu.mmcr.api.recipe.requirement.ItemRequirement input
                    && input.io() == IOType.INPUT && input.count() == expectedCount
                    && input.item() != null && input.item().getItems().length == 1
                    && input.item().getItems()[0].getItem() == item) {
                next.add(new cn.howxu.mmcr.api.recipe.requirement.ItemRequirement(input.io(), input.item(),
                        replacementCount, input.stack(), input.chance(), input.tags(), input.components(), input.consumeChance()));
                replaced = true;
            } else {
                next.add(requirement);
            }
        }
        if (replaced) setRequirements(next);
        return replaced;
    }

    public List<MachineRequirement> requirements() {
        return MachineRequirement.copyList(requirements);
    }

    public void setRequirements(List<MachineRequirement> requirements) {
        if (outputs.stream().anyMatch(output -> !(output instanceof MachineOutput.ItemOutput)
                && !(output instanceof MachineOutput.FluidOutput))) {
            throw new IllegalStateException("RecipeStartContext cannot derive registered custom outputs from requirements");
        }
        this.requirements = MachineRequirement.copyList(Objects.requireNonNull(requirements, "requirements"));
        this.outputs = outputsFromRequirements(this.requirements);
    }

    public List<MachineOutput> outputs() {
        return outputs;
    }

    public void setOutputs(List<MachineOutput> outputs) {
        List<MachineOutput> copy = MachineOutput.copyList(Objects.requireNonNull(outputs, "outputs"));
        List<MachineRequirement> internalRequirements = requirements;
        List<MachineRequirement> replacement = outputRequirements(copy, internalRequirements);
        List<MachineRequirement> nextRequirements = new ArrayList<>();
        int outputIndex = 0;
        for (MachineRequirement requirement : internalRequirements) {
            if (!OutputRegistry.matchesOutputRequirement(requirement)) {
                nextRequirements.add(requirement);
                continue;
            }
            if (outputIndex < replacement.size()) nextRequirements.add(replacement.get(outputIndex));
            outputIndex++;
        }
        while (outputIndex < replacement.size()) nextRequirements.add(replacement.get(outputIndex++));
        this.requirements = MachineRequirement.copyList(nextRequirements);
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
            if (requirement instanceof ItemRequirement item
                    && item.io() == RecipeModifier.IOType.OUTPUT) {
                result.add(new MachineOutput.ItemOutput(item.stack(), item.chance(), item.components()));
            } else if (requirement instanceof FluidRequirement fluid
                    && fluid.io() == RecipeModifier.IOType.OUTPUT) {
                result.add(new MachineOutput.FluidOutput(fluid.stack(), fluid.chance()));
            }
        }
        return List.copyOf(result);
    }

    private static List<MachineRequirement> outputRequirements(List<MachineOutput> outputs,
                                                               List<MachineRequirement> templates) {
        List<MachineRequirement> result = new ArrayList<>(outputs.size());
        int templateIndex = 0;
        for (MachineOutput output : outputs) {
            MachineRequirement template = null;
            while (templateIndex < templates.size()) {
                MachineRequirement candidate = templates.get(templateIndex++);
                if (OutputRegistry.matchesOutputRequirement(candidate)) {
                    template = candidate;
                    break;
                }
            }
            List<String> tags = template == null ? List.of() : template.tags();
            MachineRequirement requirement = OutputRegistry.toRequirement(output, tags);
            if (requirement == null || requirement.io() != RecipeModifier.IOType.OUTPUT) {
                throw new IllegalArgumentException("Output type must produce an output requirement: " + output.outputType().id());
            }
            result.add(requirement);
        }
        return result;
    }

    public record ExecutionSnapshot(int duration, List<MachineRequirement> requirements,
                                    List<MachineOutput> outputs) {
        public ExecutionSnapshot {
            if (duration <= 0) throw new IllegalArgumentException("duration must be positive");
            requirements = MachineRequirement.copyList(Objects.requireNonNull(requirements, "requirements"));
            outputs = MachineOutput.copyList(Objects.requireNonNull(outputs, "outputs"));
        }
    }
}
