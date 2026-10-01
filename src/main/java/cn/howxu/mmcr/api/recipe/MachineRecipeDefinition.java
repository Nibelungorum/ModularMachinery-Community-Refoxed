package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;

import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/** Immutable core machine recipe declaration with canonical requirements as its resource source.
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
        List<MachineRequirement> requirements,
        List<CustomRecipeIo> customOutputs,
        List<ResourceLocation> modifierIds,
        Set<RequiredHost> requiredHosts) {
    public MachineRecipeDefinition {
        if (id == null || recipePoolId == null) throw new IllegalArgumentException("Recipe ids must not be null");
        if (tickTime < 1) throw new IllegalArgumentException("Recipe tick time must be >= 1");
        if (priority < 0) throw new IllegalArgumentException("Recipe priority must be non-negative");
        if (maxThreads < 1) throw new IllegalArgumentException("Recipe max threads must be positive");
        requirements = MachineRequirement.copyList(requirements == null ? List.of() : requirements);
        customOutputs = List.copyOf(customOutputs == null ? List.of() : customOutputs);
        modifierIds = List.copyOf(modifierIds == null ? List.of() : modifierIds);
        requiredHosts = Set.copyOf(requiredHosts == null ? Set.of() : requiredHosts);
    }

    @Override
    public List<MachineRequirement> requirements() {
        return MachineRequirement.copyList(requirements);
    }

    public List<ItemRequirement> itemInputs() { return resources(ItemRequirement.class, true); }
    public List<FluidRequirement> fluidInputs() { return resources(FluidRequirement.class, true); }
    public List<EnergyRequirement> energyInputs() { return resources(EnergyRequirement.class, true); }
    public List<ItemRequirement> itemOutputs() { return resources(ItemRequirement.class, false); }
    public List<FluidRequirement> fluidOutputs() { return resources(FluidRequirement.class, false); }
    public List<EnergyRequirement> energyOutputs() { return resources(EnergyRequirement.class, false); }

    private <T extends MachineRequirement> List<T> resources(Class<T> type, boolean input) {
        return requirements.stream().filter(type::isInstance).map(type::cast)
                .filter(requirement -> requirement.io().isInput() == input)
                .map(requirement -> type.cast(MachineRequirement.copyOf(requirement))).toList();
    }

    public Set<ResourceLocation> requiredHostIds() {
        return requiredHosts.stream().map(RequiredHost::id).collect(Collectors.toUnmodifiableSet());
    }
}
