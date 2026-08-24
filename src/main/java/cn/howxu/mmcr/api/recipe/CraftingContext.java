package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;

import java.util.ArrayList;
import java.util.List;

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
        return plan(recipe, parallelism, RecipeModifier.IOType.INPUT);
    }

    public PlanningResult planOutputs(MachineRecipe recipe, int parallelism) {
        return plan(recipe, parallelism, RecipeModifier.IOType.OUTPUT);
    }

    public CraftingPlan planStart(MachineRecipe recipe, int requestedParallelism) {
        PlanningResult result = plan(recipe, requestedParallelism, null);
        return result.successful() ? result.plan() : null;
    }

    public void setModifiers(List<RecipeModifier> modifiers) {
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    void resetFor(CapabilitySnapshot snapshot, List<RecipeModifier> modifiers) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        capabilities = snapshot.capabilities();
        setModifiers(modifiers);
    }

    private PlanningResult plan(MachineRecipe recipe, int parallelism, RecipeModifier.IOType direction) {
        if (recipe == null) throw new IllegalArgumentException("recipe must not be null");
        List<MachineRequirement> requirements = new ArrayList<>();
        List<Integer> requirementIndexes = new ArrayList<>();
        List<MachineRequirement> recipeRequirements = recipe.runtimeRequirements(modifiers);
        for (int index = 0; index < recipeRequirements.size(); index++) {
            MachineRequirement requirement = recipeRequirements.get(index);
            if (direction != null && requirement.io() != direction) continue;
            requirements.add(requirement);
            requirementIndexes.add(index);
        }
        return new RequirementPlanner().plan(requirements, capabilities,
                new PlanningContext(parallelism, 0, direction == RecipeModifier.IOType.OUTPUT && recipe.allowPartialOutputs()),
                requirementIndexes);
    }
}
