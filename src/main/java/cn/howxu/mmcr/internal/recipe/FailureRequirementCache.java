package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatOutput;
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
import java.util.stream.Stream;

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
                && cached.compatibilityTypes().stream().allMatch(type -> RequirementHandlerRegistry.handlerFor(type) == type.handler())
                && cached.outputTypes().stream().allMatch(type -> OutputRegistry.typeFor(type.id()) == type)) {
            return cached.requirements();
        }
        requirements.remove(recipe);
        List<MachineRequirement> derived = recipe.runtimeRequirements();
        // Only known native modifier semantics are stable. Compatibility delegates are checked on
        // every cache hit; custom handlers/converters must still be invoked afresh.
        // Outputs may introduce requirements absent from the declared source list.
        List<MachineRequirement> dependencies = Stream.concat(recipe.requirements().stream(), derived.stream()).toList();
        boolean stableRequirements = dependencies.stream().allMatch(FailureRequirementCache::stableRequirement);
        boolean stableOutputs = recipe.machineOutputs().stream().allMatch(output ->
                output instanceof MachineOutput.ItemOutput || output instanceof MachineOutput.FluidOutput
                || output instanceof LoadedChemicalOutput && OutputRegistry.isCanonical(output)
                || output instanceof LoadedHeatOutput && OutputRegistry.isCanonical(output));
        if (stableRequirements && stableOutputs) requirements.put(recipe, new Entry(derived,
                dependencies.stream().anyMatch(LoadedChemicalRequirement.class::isInstance),
                dependencies.stream().anyMatch(LoadedHeatRequirement.class::isInstance),
                dependencies.stream().filter(requirement -> requirement instanceof LoadedChemicalRequirement
                        || requirement instanceof LoadedHeatRequirement).map(MachineRequirement::type).distinct().toList(),
                recipe.machineOutputs().stream().filter(output -> output instanceof LoadedChemicalOutput
                        || output instanceof LoadedHeatOutput).map(MachineOutput::outputType).distinct().toList()));
        return derived;
    }

    private static boolean stableRequirement(MachineRequirement requirement) {
        return requirement instanceof ItemRequirement || requirement instanceof FluidRequirement
                || requirement instanceof EnergyRequirement || requirement instanceof LevelRequirement
                || requirement instanceof StageRequirement
                || requirement instanceof LoadedChemicalRequirement && LoadedChemicalRequirement.supportsSearchSnapshot()
                    && RequirementHandlerRegistry.handlerFor(requirement.type()) == requirement.type().handler()
                || requirement instanceof LoadedHeatRequirement && LoadedHeatRequirement.supportsSearchSnapshot()
                    && RequirementHandlerRegistry.handlerFor(requirement.type()) == requirement.type().handler();
    }

    /** @author howxu <dev@howxu.cn> */
    private record Entry(List<MachineRequirement> requirements, boolean chemical, boolean heat,
                         List<RequirementType<?>> compatibilityTypes, List<OutputType<?>> outputTypes) { }

    void clear() {
        requirements.clear();
        catalogVersion = Long.MIN_VALUE;
    }
}
