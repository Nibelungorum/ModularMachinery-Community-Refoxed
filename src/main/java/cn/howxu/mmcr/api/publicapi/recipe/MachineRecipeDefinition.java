package cn.howxu.mmcr.api.publicapi.recipe;

import cn.howxu.mmcr.api.publicapi.machine.LevelRequirement;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;

/** Immutable public machine recipe declaration.
 * @author howxu <dev@howxu.cn>
 */
public record MachineRecipeDefinition(
        Identifier id,
        Identifier machineId,
        int tickTime,
        int priority,
        int maxThreads,
        boolean cancelRecipeOnPerTickFailure,
        boolean parallelized,
        boolean allowPartialOutputs,
        List<ItemInput> itemInputs,
        List<FluidInput> fluidInputs,
        List<EnergyInput> energyInputs,
        List<ItemOutput> itemOutputs,
        List<FluidOutput> fluidOutputs,
        List<EnergyInput> energyOutputs,
        List<RecipeRequirement> requirements,
        List<RecipeModifierValue> modifiers,
        List<LevelRequirement> levelRequirements,
        Set<RequiredHost> requiredHosts) {
    public MachineRecipeDefinition {
        if (id == null || machineId == null) throw new IllegalArgumentException("Recipe ids must not be null");
        if (tickTime < 1) throw new IllegalArgumentException("Recipe tick time must be >= 1");
        if (priority < 0) throw new IllegalArgumentException("Recipe priority must be non-negative");
        if (maxThreads < 1) throw new IllegalArgumentException("Recipe max threads must be positive");
        itemInputs = List.copyOf(itemInputs == null ? List.of() : itemInputs);
        fluidInputs = List.copyOf(fluidInputs == null ? List.of() : fluidInputs);
        energyInputs = List.copyOf(energyInputs == null ? List.of() : energyInputs);
        itemOutputs = List.copyOf(itemOutputs == null ? List.of() : itemOutputs);
        fluidOutputs = List.copyOf(fluidOutputs == null ? List.of() : fluidOutputs);
        energyOutputs = List.copyOf(energyOutputs == null ? List.of() : energyOutputs);
        requirements = List.copyOf(requirements == null ? List.of() : requirements);
        modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
        levelRequirements = List.copyOf(levelRequirements == null ? List.of() : levelRequirements);
        requiredHosts = Set.copyOf(requiredHosts == null ? Set.of() : requiredHosts);
    }

    public Set<Identifier> requiredHostIds() {
        return requiredHosts.stream().map(RequiredHost::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
