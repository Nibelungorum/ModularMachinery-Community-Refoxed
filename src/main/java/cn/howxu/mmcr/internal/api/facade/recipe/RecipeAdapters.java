package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.publicapi.recipe.*;
import cn.howxu.mmcr.publicapi.recipe.modifier.RecipeAdjustmentSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.runtime.RecipeView;
import cn.howxu.mmcr.publicapi.runtime.OutputView;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

/** Typed declaration and runtime adapters; wrapping never rebuilds either model.
 * @author howxu <dev@howxu.cn> */
public final class RecipeAdapters {
    private RecipeAdapters() {}
    public static RecipeDraft recipe(ResourceLocation id) { return new RecipeDraftFacade(MachineRecipeBuilder.recipe(id)); }
    public static RecipeDraft wrap(MachineRecipeBuilder value) { return new RecipeDraftFacade(value); }
    public static RecipeSpec wrap(MachineRecipeDefinition value) { return new DefinitionView(value); }
    public static MachineRecipeDefinition unwrap(RecipeSpec value) { if (value instanceof DefinitionView view) return view.delegate; throw new IllegalArgumentException("Recipe declaration must be library-produced"); }
    public static RecipeView wrap(MachineRecipe value) { return new RuntimeView(value); }
    public static MachineRecipe unwrap(RecipeView value) { if (value instanceof RuntimeView view) return view.delegate; throw new IllegalArgumentException("Recipe view must be library-produced"); }
    private record DefinitionView(MachineRecipeDefinition delegate) implements RecipeSpec {
        public ResourceLocation id() { return delegate.id(); }
        public ResourceLocation recipePoolId() { return delegate.recipePoolId(); }
        public int tickTime() { return delegate.tickTime(); }
        public int priority() { return delegate.priority(); }
        public int maxThreads() { return delegate.maxThreads(); }
        public boolean cancelRecipeOnPerTickFailure() { return delegate.cancelRecipeOnPerTickFailure(); }
        public boolean parallelized() { return delegate.parallelized(); }
        public boolean allowPartialOutputs() { return delegate.allowPartialOutputs(); }
        public List<ItemInputSpec> itemInputs() { return delegate.itemInputs().stream().map(RecipeIoAdapters::input).toList(); }
        public List<FluidInputSpec> fluidInputs() { return delegate.fluidInputs().stream().map(RecipeIoAdapters::input).toList(); }
        public List<EnergyRateSpec> energyInputs() { return delegate.energyInputs().stream().map(RecipeIoAdapters::wrap).toList(); }
        public List<ItemOutputSpec> itemOutputs() { return delegate.itemOutputs().stream().map(RecipeIoAdapters::output).toList(); }
        public List<FluidOutputSpec> fluidOutputs() { return delegate.fluidOutputs().stream().map(RecipeIoAdapters::output).toList(); }
        public List<EnergyRateSpec> energyOutputs() { return delegate.energyOutputs().stream().map(RecipeIoAdapters::wrap).toList(); }
        public List<RequirementSpec> requirements() { return RequirementAdapters.wrap(delegate.requirements()); }
        public List<CustomIoSpec> customOutputs() { return delegate.customOutputs().stream().map(RecipeIoAdapters::wrap).toList(); }
        public List<ResourceLocation> modifierIds() { return delegate.modifierIds(); }
        public Set<HostConstraint> requiredHosts() { return delegate.requiredHosts().stream().map(RecipeIoAdapters::wrap).collect(Collectors.toUnmodifiableSet()); }
        public Set<ResourceLocation> requiredHostIds() { return delegate.requiredHostIds(); }
    }
    private record RuntimeView(MachineRecipe delegate) implements RecipeView {
        public ResourceLocation id() { return delegate.id(); }
        public ResourceLocation recipePoolId() { return delegate.recipePoolId(); }
        public int tickTime() { return delegate.tickTime(); }
        public int priority() { return delegate.priority(); }
        public int maxThreads() { return delegate.maxThreads(); }
        public boolean cancelRecipeOnPerTickFailure() { return delegate.doesCancelRecipeOnPerTickFailure(); }
        public boolean parallelized() { return delegate.isParallelized(); }
        public boolean allowPartialOutputs() { return delegate.allowPartialOutputs(); }
        public List<RequirementSpec> requirements() { return RequirementAdapters.wrap(delegate.requirements()); }
        public List<OutputView> outputs() { return OutputAdapters.wrap(delegate.machineOutputs()); }
        public List<RecipeAdjustmentSpec> modifiers() { return delegate.modifiers().stream().map(ModifierAdapters::wrap).toList(); }
        public Set<ResourceLocation> requiredHostIds() { return delegate.requiredHostIds(); }
        public List<RequirementSpec> runtimeRequirements() { return RequirementAdapters.wrap(delegate.runtimeRequirements()); }
        public List<RequirementSpec> runtimeRequirements(List<RecipeAdjustmentSpec> modifiers) { return RequirementAdapters.wrap(delegate.runtimeRequirements(ModifierAdapters.adjustments(modifiers))); }
        public List<RequirementSpec> runtimeRequirements(List<RecipeAdjustmentSpec> modifiers, double energyMultiplier, double outputMultiplier) { return RequirementAdapters.wrap(delegate.runtimeRequirements(ModifierAdapters.adjustments(modifiers), energyMultiplier, outputMultiplier)); }
        public List<OutputView> runtimeMachineOutputs() { return OutputAdapters.wrap(delegate.runtimeMachineOutputs()); }
        public List<OutputView> runtimeMachineOutputs(List<RecipeAdjustmentSpec> modifiers) { return OutputAdapters.wrap(delegate.runtimeMachineOutputs(ModifierAdapters.adjustments(modifiers))); }
    }
}
