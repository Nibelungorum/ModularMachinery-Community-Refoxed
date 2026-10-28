package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reuses source-only requirement views for current native candidates, never dynamic wakeup predicates.
 *
 * @author howxu <dev@howxu.cn>
 */
final class FailureRequirementCache {
    private final Map<MachineRecipe, List<MachineRequirement>> requirements = new IdentityHashMap<>();
    private long catalogVersion = Long.MIN_VALUE;

    void prepare(List<MachineRecipe> candidates, long version) {
        if (catalogVersion != version) {
            requirements.clear();
            catalogVersion = version;
        }
        Set<MachineRecipe> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        retained.addAll(candidates);
        requirements.keySet().removeIf(recipe -> !retained.contains(recipe));
    }

    List<MachineRequirement> resolve(MachineRecipe recipe) {
        List<MachineRequirement> cached = requirements.get(recipe);
        if (cached != null) return cached;
        List<MachineRequirement> derived = recipe.runtimeRequirements();
        // Only reserved core types have known, immutable modifier/conversion semantics. Custom and
        // compatibility handlers must be invoked afresh, even when they return core requirements.
        boolean stableRequirements = recipe.requirements().stream().allMatch(requirement ->
                requirement instanceof ItemRequirement || requirement instanceof FluidRequirement
                || requirement instanceof EnergyRequirement || requirement instanceof LevelRequirement
                || requirement instanceof StageRequirement);
        boolean stableOutputs = recipe.machineOutputs().stream().allMatch(output ->
                output instanceof MachineOutput.ItemOutput || output instanceof MachineOutput.FluidOutput);
        if (stableRequirements && stableOutputs) requirements.put(recipe, derived);
        return derived;
    }

    void clear() {
        requirements.clear();
        catalogVersion = Long.MIN_VALUE;
    }
}
