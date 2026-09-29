package cn.howxu.mmcr.api.publicapi.recipe;

import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/** Immutable public machine recipe declaration.
 * @author howxu <dev@howxu.cn>
 */
public record MachineRecipeDefinition(
        ResourceLocation id,
        ResourceLocation recipePoolId,
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
        List<CustomRecipeIo> customOutputs,
        List<ResourceLocation> modifierIds,
        Set<RequiredHost> requiredHosts) {
    public MachineRecipeDefinition {
        if (id == null || recipePoolId == null) throw new IllegalArgumentException("Recipe ids must not be null");
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
        customOutputs = List.copyOf(customOutputs == null ? List.of() : customOutputs);
        modifierIds = List.copyOf(modifierIds == null ? List.of() : modifierIds);
        requiredHosts = Set.copyOf(requiredHosts == null ? Set.of() : requiredHosts);
    }

    public Set<ResourceLocation> requiredHostIds() {
        return requiredHosts.stream().map(RequiredHost::id).collect(Collectors.toUnmodifiableSet());
    }
}
