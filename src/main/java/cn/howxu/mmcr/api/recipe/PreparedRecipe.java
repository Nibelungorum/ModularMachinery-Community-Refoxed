package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import java.util.ArrayList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class PreparedRecipe {

    private final String registryName;
    private final String recipePoolId;
    private int tickTime;
    private final List<MachineIngredient> inputs;
    private final List<ItemStack> outputs;
    private final List<FluidStack> fluidOutputs;
    private final List<RecipeModifier> modifiers;
    private int priority;
    private int maxThreads;
    private boolean cancelRecipeOnPerTickFailure;
    private boolean parallelized;
    private boolean allowPartialOutputs;

    public PreparedRecipe(String registryName,
                          String recipePoolId,
                          int tickTime,
                          List<MachineIngredient> inputs,
                          List<ItemStack> outputs) {
        this(registryName, recipePoolId, tickTime, inputs, outputs, Collections.emptyList(), 0, 1);
    }

    public PreparedRecipe(String registryName,
                          String recipePoolId,
                          int tickTime,
                          List<MachineIngredient> inputs,
                          List<ItemStack> outputs,
                          List<RecipeModifier> modifiers,
                          int priority,
                          int maxThreads) {
        this(registryName, recipePoolId, tickTime, inputs, outputs, modifiers, priority, maxThreads, false);
    }

    public PreparedRecipe(String registryName,
                          String recipePoolId,
                          int tickTime,
                          List<MachineIngredient> inputs,
                          List<ItemStack> outputs,
                          List<RecipeModifier> modifiers,
                          int priority,
                          int maxThreads,
                          boolean cancelRecipeOnPerTickFailure) {
        this(registryName, recipePoolId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, Collections.emptyList());
    }

    public PreparedRecipe(String registryName,
                          String recipePoolId,
                          int tickTime,
                          List<MachineIngredient> inputs,
                          List<ItemStack> outputs,
                          List<RecipeModifier> modifiers,
                          int priority,
                          int maxThreads,
                          boolean cancelRecipeOnPerTickFailure,
                          List<FluidStack> fluidOutputs) {
        this(registryName, recipePoolId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, fluidOutputs, false);
    }

    public PreparedRecipe(String registryName,
                          String recipePoolId,
                          int tickTime,
                          List<MachineIngredient> inputs,
                          List<ItemStack> outputs,
                          List<RecipeModifier> modifiers,
                          int priority,
                          int maxThreads,
                          boolean cancelRecipeOnPerTickFailure,
                          List<FluidStack> fluidOutputs,
                          boolean parallelized) {
        this(registryName, recipePoolId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, fluidOutputs, parallelized, false);
    }

    public PreparedRecipe(String registryName,
                          String recipePoolId,
                          int tickTime,
                          List<MachineIngredient> inputs,
                          List<ItemStack> outputs,
                          List<RecipeModifier> modifiers,
                          int priority,
                          int maxThreads,
                          boolean cancelRecipeOnPerTickFailure,
                          List<FluidStack> fluidOutputs,
                          boolean parallelized,
                          boolean allowPartialOutputs) {
        this.registryName = registryName;
        this.recipePoolId = recipePoolId;
        this.tickTime = Math.max(1, tickTime);
        this.inputs = inputs == null ? Collections.emptyList() : List.copyOf(inputs);
        this.outputs = outputs == null ? Collections.emptyList() : List.copyOf(outputs);
        this.fluidOutputs = fluidOutputs == null ? Collections.emptyList() : List.copyOf(fluidOutputs);
        this.modifiers = modifiers == null ? Collections.emptyList() : List.copyOf(modifiers);
        this.priority = priority;
        this.maxThreads = maxThreads;
        this.cancelRecipeOnPerTickFailure = cancelRecipeOnPerTickFailure;
        this.parallelized = parallelized;
        this.allowPartialOutputs = allowPartialOutputs;
    }

    public String getRegistryName() {
        return registryName;
    }

    public String getRecipePoolId() {
        return recipePoolId;
    }

    public int getTickTime() {
        return tickTime;
    }

    public List<MachineIngredient> getInputs() {
        return inputs;
    }

    public List<ItemStack> getOutputs() {
        return outputs;
    }

    public List<FluidStack> getFluidOutputs() {
        return fluidOutputs;
    }

    public List<RecipeModifier> getModifiers() {
        return modifiers;
    }

    public int getPriority() {
        return priority;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public boolean doesCancelRecipeOnPerTickFailure() {
        return cancelRecipeOnPerTickFailure;
    }

    public boolean isParallelized() {
        return parallelized;
    }

    public boolean allowPartialOutputs() {
        return allowPartialOutputs;
    }

    public void setTickTime(int tickTime) {
        this.tickTime = Math.max(1, tickTime);
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public void setCancelRecipeOnPerTickFailure(boolean cancelRecipeOnPerTickFailure) {
        this.cancelRecipeOnPerTickFailure = cancelRecipeOnPerTickFailure;
    }

    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    public void setAllowPartialOutputs(boolean allowPartialOutputs) {
        this.allowPartialOutputs = allowPartialOutputs;
    }

    public MachineRecipe toMachineRecipe() {
        List<MachineRequirement> requirements = new ArrayList<>();
        inputs.stream().map(MachineRequirement::fromInput).forEach(requirements::add);
        outputs.stream().map(output -> MachineRequirement.itemOutput(output, 1F)).forEach(requirements::add);
        fluidOutputs.stream().map(output -> MachineRequirement.fluidOutput(output, 1F)).forEach(requirements::add);
        List<MachineOutput> canonicalOutputs = new ArrayList<>();
        outputs.forEach(output -> canonicalOutputs.add(new MachineOutput.ItemOutput(output, 1F)));
        fluidOutputs.forEach(output -> canonicalOutputs.add(new MachineOutput.FluidOutput(output, 1F)));
        return MachineRecipe.fromCanonical(ResourceLocation.parse(registryName), ResourceLocation.parse(recipePoolId), tickTime,
                requirements, canonicalOutputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure,
                parallelized, allowPartialOutputs, Set.of());
    }
}
