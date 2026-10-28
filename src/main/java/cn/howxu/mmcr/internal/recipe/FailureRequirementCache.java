package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
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
    private final Map<MachineRecipe, Entry> requirements = new IdentityHashMap<>();
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
        Entry cached = requirements.get(recipe);
        if (cached != null && (!cached.chemical() || LoadedChemicalRequirement.supportsSearchSnapshot())
                && (!cached.heat() || LoadedHeatRequirement.supportsSearchSnapshot())
                && cached.compatibilityTypes().stream().allMatch(type -> RequirementHandlerRegistry.handlerFor(type) == type.handler())) {
            return cached.requirements();
        }
        requirements.remove(recipe);
        List<MachineRequirement> derived = recipe.runtimeRequirements();
        // Only known native modifier semantics are stable. Compatibility delegates are checked on
        // every cache hit; custom handlers/converters must still be invoked afresh.
        boolean stableRequirements = recipe.requirements().stream().allMatch(requirement ->
                requirement instanceof ItemRequirement || requirement instanceof FluidRequirement
                || requirement instanceof EnergyRequirement || requirement instanceof LevelRequirement
                || requirement instanceof StageRequirement
                || requirement instanceof LoadedChemicalRequirement && LoadedChemicalRequirement.supportsSearchSnapshot()
                    && RequirementHandlerRegistry.handlerFor(requirement.type()) == requirement.type().handler()
                || requirement instanceof LoadedHeatRequirement && LoadedHeatRequirement.supportsSearchSnapshot()
                    && RequirementHandlerRegistry.handlerFor(requirement.type()) == requirement.type().handler());
        boolean stableOutputs = recipe.machineOutputs().stream().allMatch(output ->
                output instanceof MachineOutput.ItemOutput || output instanceof MachineOutput.FluidOutput);
        if (stableRequirements && stableOutputs) requirements.put(recipe, new Entry(derived,
                recipe.requirements().stream().anyMatch(LoadedChemicalRequirement.class::isInstance),
                recipe.requirements().stream().anyMatch(LoadedHeatRequirement.class::isInstance),
                recipe.requirements().stream().filter(requirement -> requirement instanceof LoadedChemicalRequirement
                        || requirement instanceof LoadedHeatRequirement).map(MachineRequirement::type).distinct().toList()));
        return derived;
    }

    /** @author howxu <dev@howxu.cn> */
    private record Entry(List<MachineRequirement> requirements, boolean chemical, boolean heat,
                         List<RequirementType<?>> compatibilityTypes) { }

    void clear() {
        requirements.clear();
        catalogVersion = Long.MIN_VALUE;
    }
}
