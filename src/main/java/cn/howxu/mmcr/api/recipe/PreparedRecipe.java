package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;

public final class PreparedRecipe {

    private final String registryName;
    private final String machineId;
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
                          String machineId,
                          int tickTime,
                          List<MachineIngredient> inputs,
                          List<ItemStack> outputs) {
        this(registryName, machineId, tickTime, inputs, outputs, Collections.emptyList(), 0, 1);
    }

    public PreparedRecipe(String registryName,
                          String machineId,
                          int tickTime,
                          List<MachineIngredient> inputs,
                          List<ItemStack> outputs,
                          List<RecipeModifier> modifiers,
                          int priority,
                          int maxThreads) {
        this(registryName, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, false);
    }

    public PreparedRecipe(String registryName,
                          String machineId,
                          int tickTime,
                          List<MachineIngredient> inputs,
                          List<ItemStack> outputs,
                          List<RecipeModifier> modifiers,
                          int priority,
                          int maxThreads,
                          boolean cancelRecipeOnPerTickFailure) {
        this(registryName, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, Collections.emptyList());
    }

    public PreparedRecipe(String registryName,
                          String machineId,
                          int tickTime,
                          List<MachineIngredient> inputs,
                          List<ItemStack> outputs,
                          List<RecipeModifier> modifiers,
                          int priority,
                          int maxThreads,
                          boolean cancelRecipeOnPerTickFailure,
                          List<FluidStack> fluidOutputs) {
        this(registryName, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, fluidOutputs, false);
    }

    public PreparedRecipe(String registryName,
                          String machineId,
                          int tickTime,
                          List<MachineIngredient> inputs,
                          List<ItemStack> outputs,
                          List<RecipeModifier> modifiers,
                          int priority,
                          int maxThreads,
                          boolean cancelRecipeOnPerTickFailure,
                          List<FluidStack> fluidOutputs,
                          boolean parallelized) {
        this(registryName, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, fluidOutputs, parallelized, false);
    }

    public PreparedRecipe(String registryName,
                          String machineId,
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
        this.machineId = machineId;
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

    public String getMachineId() {
        return machineId;
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
        return new MachineRecipe(
                Identifier.parse(registryName),
                Identifier.parse(machineId),
                tickTime,
                inputs,
                outputs,
                modifiers,
                priority,
                maxThreads,
                cancelRecipeOnPerTickFailure,
                fluidOutputs,
                Collections.emptyList(),
                parallelized,
                Collections.emptyList(),
                allowPartialOutputs
        );
    }
}
