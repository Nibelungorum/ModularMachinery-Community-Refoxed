package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Plans recipe capability operations from an immutable capability snapshot.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CraftingContext {
    private List<MachineCapability> capabilities;
    private List<RecipeModifier> modifiers;

    public CraftingContext(CapabilitySnapshot snapshot) {
        this(snapshot, List.of());
    }

    public CraftingContext(CapabilitySnapshot snapshot, List<RecipeModifier> modifiers) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        capabilities = snapshot.capabilities();
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    public PlanningResult planInputs(MachineRecipe recipe, int parallelism) {
        return planInputs(recipe, parallelism, Set.of(), Set.of());
    }

    public PlanningResult planInputs(MachineRecipe recipe, int parallelism,
                                     Set<Integer> consumedAtStart, Set<Integer> retainedInputs) {
        return plan(recipe, parallelism, RecipeModifier.IOType.INPUT,
                consumedAtStart == null ? Set.of() : consumedAtStart,
                retainedInputs == null ? Set.of() : retainedInputs);
    }

    public PlanningResult planOutputs(MachineRecipe recipe, int parallelism) {
        return plan(recipe, parallelism, RecipeModifier.IOType.OUTPUT, Set.of(), Set.of());
    }

    public PlanningResult planOutputs(List<MachineOutput> outputs, int parallelism) {
        if (outputs == null) throw new IllegalArgumentException("outputs must not be null");
        return plan(outputRequirements(outputs), parallelism, true, indexes(outputs.size()));
    }

    public PlanningResult planOutputs(MachineRecipe recipe, List<MachineOutput> outputs, int parallelism) {
        if (recipe == null) throw new IllegalArgumentException("recipe must not be null");
        List<MachineRequirement> requirements = outputRequirements(outputs);
        for (MachineRequirement requirement : recipe.runtimeRequirements(modifiers)) {
            if (requirement.io() == RecipeModifier.IOType.OUTPUT
                    && !(requirement instanceof ItemRequirement) && !(requirement instanceof FluidRequirement)) {
                requirements.add(requirement);
            }
        }
        return plan(requirements, parallelism, recipe.allowPartialOutputs(), indexes(requirements.size()));
    }

    private static List<MachineRequirement> outputRequirements(List<MachineOutput> outputs) {
        if (outputs == null) throw new IllegalArgumentException("outputs must not be null");
        List<MachineRequirement> requirements = new ArrayList<>(outputs.size());
        for (MachineOutput output : outputs) {
            if (output == null || !Float.isFinite(output.chance())) {
                throw new IllegalArgumentException("outputs must contain finite, non-null values");
            }
            if (output instanceof MachineOutput.ItemOutput item) {
                ItemStack stack = item.stack();
                if (stack == null || stack.isEmpty() || stack.getCount() < 0) {
                    throw new IllegalArgumentException("item outputs must contain a non-empty stack");
                }
                requirements.add(MachineRequirement.itemOutput(stack, item.chance()));
            } else if (output instanceof MachineOutput.FluidOutput fluid) {
                FluidStack stack = fluid.stack();
                if (stack == null || stack.isEmpty() || stack.getAmount() < 0) {
                    throw new IllegalArgumentException("fluid outputs must contain a non-empty stack");
                }
                requirements.add(MachineRequirement.fluidOutput(stack, fluid.chance()));
            } else {
                throw new IllegalArgumentException("Unknown machine output: " + output);
            }
        }
        return requirements;
    }

    private static List<Integer> indexes(int size) {
        List<Integer> indexes = new ArrayList<>(size);
        for (int index = 0; index < size; index++) indexes.add(index);
        return indexes;
    }

    public CraftingPlan planStart(MachineRecipe recipe, int requestedParallelism) {
        PlanningResult result = plan(recipe, requestedParallelism, null, Set.of(), Set.of());
        return result.successful() ? result.plan() : null;
    }

    public PlanningResult planStartResult(MachineRecipe recipe, int requestedParallelism) {
        return plan(recipe, requestedParallelism, null, Set.of(), Set.of());
    }

    public void setModifiers(List<RecipeModifier> modifiers) {
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    void resetFor(CapabilitySnapshot snapshot, List<RecipeModifier> modifiers) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        capabilities = snapshot.capabilities();
        setModifiers(modifiers);
    }

    private PlanningResult plan(MachineRecipe recipe, int parallelism, RecipeModifier.IOType direction,
                                Set<Integer> consumedAtStart, Set<Integer> retainedInputs) {
        if (recipe == null) throw new IllegalArgumentException("recipe must not be null");
        List<MachineRequirement> requirements = new ArrayList<>();
        List<Integer> requirementIndexes = new ArrayList<>();
        List<MachineRequirement> recipeRequirements = recipe.runtimeRequirements(modifiers);
        for (int index = 0; index < recipeRequirements.size(); index++) {
            MachineRequirement requirement = recipeRequirements.get(index);
            if (direction != null && requirement.io() != direction) continue;
            if (consumedAtStart.contains(index)) continue;
            if (retainedInputs.contains(index) && requirement instanceof cn.howxu.mmcr.api.recipe.requirement.ItemRequirement item
                    && item.io() == RecipeModifier.IOType.INPUT && item.consumeChance() > 0F) {
                requirement = new cn.howxu.mmcr.api.recipe.requirement.ItemRequirement(item.io(), item.item(), item.count(),
                        item.stack(null), item.chance(), item.tags(), item.components(), 0F);
            }
            requirements.add(requirement);
            requirementIndexes.add(index);
        }
        return plan(requirements, parallelism,
                direction != RecipeModifier.IOType.INPUT && recipe.allowPartialOutputs(), requirementIndexes);
    }

    private PlanningResult plan(List<MachineRequirement> requirements, int parallelism, boolean allowPartialOutputs,
                                List<Integer> requirementIndexes) {
        return new RequirementPlanner().plan(requirements, capabilities,
                new PlanningContext(parallelism, 0, allowPartialOutputs), requirementIndexes);
    }
}
