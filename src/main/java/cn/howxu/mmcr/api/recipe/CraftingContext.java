package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public PlanningResult planInputs(MachineRecipe recipe, long parallelism) {
        return planInputs(recipe, parallelism, Set.of(), Set.of());
    }

    public PlanningResult planInputs(MachineRecipe recipe, long parallelism,
                                     Set<Integer> consumedAtStart, Set<Integer> retainedInputs) {
        return plan(recipe, parallelism, RecipeModifier.IOType.INPUT,
                consumedAtStart == null ? Set.of() : consumedAtStart,
                retainedInputs == null ? Set.of() : retainedInputs);
    }

    public PlanningResult planInputs(List<MachineRequirement> requirements, long parallelism,
                                     Set<Integer> consumedAtStart, Set<Integer> retainedInputs) {
        return plan(requirements, parallelism, RecipeModifier.IOType.INPUT,
                consumedAtStart == null ? Set.of() : consumedAtStart,
                retainedInputs == null ? Set.of() : retainedInputs, Map.of());
    }

    public PlanningResult planOutputs(MachineRecipe recipe, long parallelism) {
        return plan(recipe, parallelism, RecipeModifier.IOType.OUTPUT, Set.of(), Set.of());
    }

    public PlanningResult planOutputs(List<MachineOutput> outputs, long parallelism) {
        if (outputs == null) throw new IllegalArgumentException("outputs must not be null");
        return planSelected(outputRequirements(outputs), parallelism, partialOutputPolicies(outputs.size()), indexes(outputs.size()));
    }

    public PlanningResult planOutputs(MachineRecipe recipe, List<MachineOutput> outputs, long parallelism) {
        if (recipe == null) throw new IllegalArgumentException("recipe must not be null");
        IndexedRequirements replacement = replacePhysicalOutputs(recipe.runtimeRequirements(modifiers), outputs);
        List<MachineRequirement> requirements = new ArrayList<>();
        List<Integer> requirementIndexes = new ArrayList<>();
        for (int index = 0; index < replacement.requirements().size(); index++) {
            MachineRequirement requirement = replacement.requirements().get(index);
            if (requirement.io() != RecipeModifier.IOType.OUTPUT) continue;
            requirements.add(requirement);
            requirementIndexes.add(replacement.indexes().get(index));
        }
        return planSelected(requirements, parallelism,
                outputPoliciesForIndexes(requirementIndexes, recipe.allowPartialOutputs()), requirementIndexes);
    }

    private static List<MachineRequirement> outputRequirements(List<MachineOutput> outputs) {
        if (outputs == null) throw new IllegalArgumentException("outputs must not be null");
        List<MachineRequirement> requirements = new ArrayList<>(outputs.size());
        for (MachineOutput output : outputs) {
            requirements.add(outputRequirement(output, null));
        }
        return requirements;
    }

    private static List<Integer> indexes(int size) {
        List<Integer> indexes = new ArrayList<>(size);
        for (int index = 0; index < size; index++) indexes.add(index);
        return indexes;
    }

    public CraftingPlan planStart(MachineRecipe recipe, long requestedParallelism) {
        PlanningResult result = plan(recipe, requestedParallelism, null, Set.of(), Set.of());
        return result.successful() ? result.plan() : null;
    }

    public PlanningResult planStartResult(MachineRecipe recipe, long requestedParallelism) {
        return plan(recipe, requestedParallelism, null, Set.of(), Set.of());
    }

    public PlanningResult planStartRequirements(List<MachineRequirement> requirements, long requestedParallelism,
                                                 boolean allowPartialOutputs) {
        return planRequirements(requirements, requestedParallelism,
                partialOutputPolicies(requirements, allowPartialOutputs));
    }

    public PlanningResult planRequirements(List<MachineRequirement> requirements, long parallelism,
                                           Map<Integer, OutputPolicy> outputPolicies) {
        return plan(requirements, parallelism, null, Set.of(), Set.of(), outputPolicies);
    }

    public PlanningResult planInputRequirements(List<MachineRequirement> requirements, long parallelism,
                                                 Set<Integer> consumedAtStart, Set<Integer> retainedInputs) {
        return planInputs(requirements, parallelism, consumedAtStart, retainedInputs);
    }

    public PlanningResult planOutputRequirements(List<MachineRequirement> requirements, long parallelism,
                                                  boolean allowPartialOutputs) {
        return plan(requirements, parallelism, RecipeModifier.IOType.OUTPUT, Set.of(), Set.of(),
                partialOutputPolicies(requirements, allowPartialOutputs));
    }

    public PlanningResult planOutputRequirements(List<MachineRequirement> requirements, List<MachineOutput> outputs,
                                                  long parallelism, boolean allowPartialOutputs) {
        IndexedRequirements replacement = replacePhysicalOutputs(requirements, outputs);
        return planSelected(replacement.requirements(), replacement.indexes(), parallelism,
                RecipeModifier.IOType.OUTPUT,
                outputPoliciesForIndexes(replacement.indexes(), allowPartialOutputs));
    }

    public void setModifiers(List<RecipeModifier> modifiers) {
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    void resetFor(CapabilitySnapshot snapshot, List<RecipeModifier> modifiers) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        capabilities = snapshot.capabilities();
        setModifiers(modifiers);
    }

    private PlanningResult plan(MachineRecipe recipe, long parallelism, RecipeModifier.IOType direction,
                                Set<Integer> consumedAtStart, Set<Integer> retainedInputs) {
        if (recipe == null) throw new IllegalArgumentException("recipe must not be null");
        List<MachineRequirement> recipeRequirements = recipe.runtimeRequirements(modifiers);
        return plan(recipeRequirements, parallelism, direction, consumedAtStart, retainedInputs,
                partialOutputPolicies(recipeRequirements, recipe.allowPartialOutputs()));
    }

    private PlanningResult plan(List<MachineRequirement> source, long parallelism, RecipeModifier.IOType direction,
                                Set<Integer> consumedAtStart, Set<Integer> retainedInputs,
                                Map<Integer, OutputPolicy> outputPolicies) {
        if (source == null) throw new IllegalArgumentException("requirements must not be null");
        List<MachineRequirement> requirements = new ArrayList<>();
        List<Integer> requirementIndexes = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            MachineRequirement requirement = source.get(index);
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
        return planSelected(requirements, parallelism, outputPolicies, requirementIndexes);
    }

    private PlanningResult planSelected(List<MachineRequirement> requirements, long parallelism,
                                        Map<Integer, OutputPolicy> outputPolicies, List<Integer> requirementIndexes) {
        if (requirements == null || requirementIndexes == null || requirements.size() != requirementIndexes.size()) {
            throw new IllegalArgumentException("requirements and indexes must match");
        }
        return new RequirementPlanner().plan(requirements, capabilities,
                new PlanningContext(parallelism, 0, outputPolicies), requirementIndexes);
    }

    private PlanningResult planSelected(List<MachineRequirement> source, List<Integer> sourceIndexes,
                                         long parallelism, RecipeModifier.IOType direction,
                                        Map<Integer, OutputPolicy> outputPolicies) {
        if (source == null || sourceIndexes == null || source.size() != sourceIndexes.size()) {
            throw new IllegalArgumentException("requirements and indexes must match");
        }
        List<MachineRequirement> requirements = new ArrayList<>();
        List<Integer> requirementIndexes = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            MachineRequirement requirement = source.get(index);
            if (direction != null && requirement.io() != direction) continue;
            requirements.add(requirement);
            requirementIndexes.add(sourceIndexes.get(index));
        }
        return planSelected(requirements, parallelism, outputPolicies, requirementIndexes);
    }

    private static Map<Integer, OutputPolicy> partialOutputPolicies(int size) {
        return outputPoliciesForIndexes(indexes(size), true);
    }

    private static Map<Integer, OutputPolicy> partialOutputPolicies(List<MachineRequirement> requirements,
                                                                     boolean allowPartialOutputs) {
        if (requirements == null) throw new IllegalArgumentException("requirements must not be null");
        Map<Integer, OutputPolicy> policies = new LinkedHashMap<>();
        for (int index = 0; index < requirements.size(); index++) {
            if (requirements.get(index).io() == RecipeModifier.IOType.OUTPUT) {
                policies.put(index, allowPartialOutputs ? OutputPolicy.ALLOW_PARTIAL : OutputPolicy.REQUIRE_FULL);
            }
        }
        return Map.copyOf(policies);
    }

    private static Map<Integer, OutputPolicy> outputPoliciesForIndexes(List<Integer> indexes,
                                                                         boolean allowPartialOutputs) {
        Map<Integer, OutputPolicy> policies = new LinkedHashMap<>();
        for (Integer index : indexes) {
            policies.put(index, allowPartialOutputs ? OutputPolicy.ALLOW_PARTIAL : OutputPolicy.REQUIRE_FULL);
        }
        return Map.copyOf(policies);
    }

    private static IndexedRequirements replacePhysicalOutputs(List<MachineRequirement> base,
                                                              List<MachineOutput> outputs) {
        if (base == null) throw new IllegalArgumentException("requirements must not be null");
        List<MachineOutput> copiedOutputs = MachineOutput.copyList(
                Objects.requireNonNull(outputs, "outputs"));
        List<MachineRequirement> result = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        int outputIndex = 0;
        int extraOutputIndex = 0;
        for (int index = 0; index < base.size(); index++) {
            MachineRequirement requirement = base.get(index);
            if (!isItemOrFluidOutput(requirement)) {
                result.add(requirement);
                indexes.add(index);
                continue;
            }
            if (outputIndex < copiedOutputs.size()) {
                result.add(outputRequirement(copiedOutputs.get(outputIndex++), requirement));
                indexes.add(index);
            } else {
                outputIndex++;
            }
        }
        while (outputIndex < copiedOutputs.size()) {
            result.add(outputRequirement(copiedOutputs.get(outputIndex++), null));
            indexes.add(base.size() + extraOutputIndex++);
        }
        return new IndexedRequirements(List.copyOf(result), List.copyOf(indexes));
    }

    private static MachineRequirement outputRequirement(MachineOutput output, MachineRequirement template) {
        if (output == null || !Float.isFinite(output.chance())) {
            throw new IllegalArgumentException("outputs must contain finite, non-null values");
        }
        List<String> tags = template == null ? List.of() : template.tags();
        if (output instanceof MachineOutput.ItemOutput item) {
            ItemStack stack = item.stack();
            if (stack == null || stack.isEmpty() || stack.getCount() < 0) {
                throw new IllegalArgumentException("item outputs must contain a non-empty stack");
            }
            return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, item.stack(), item.chance(), tags);
        }
        if (!(output instanceof MachineOutput.FluidOutput fluid)) {
            throw new IllegalArgumentException("Unknown machine output: " + output);
        }
        FluidStack stack = fluid.stack();
        if (stack == null || stack.isEmpty() || stack.getAmount() < 0) {
            throw new IllegalArgumentException("fluid outputs must contain a non-empty stack");
        }
        return new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, fluid.stack(), fluid.chance(), tags);
    }

    private record IndexedRequirements(List<MachineRequirement> requirements, List<Integer> indexes) {
    }

    private static boolean isItemOrFluidOutput(MachineRequirement requirement) {
        return requirement.io() == RecipeModifier.IOType.OUTPUT
                && (requirement instanceof ItemRequirement || requirement instanceof FluidRequirement);
    }
}
