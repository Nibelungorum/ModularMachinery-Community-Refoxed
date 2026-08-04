package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public final class PreparedRecipe {

    private final String registryName;
    private final String machineId;
    private int tickTime;
    private final List<MachineIngredient> inputs;
    private final List<ItemStack> outputs;
    private final List<RecipeModifier> modifiers;
    private int priority;
    private int maxThreads;

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
        this.registryName = registryName;
        this.machineId = machineId;
        this.tickTime = Math.max(1, tickTime);
        this.inputs = inputs == null ? Collections.emptyList() : List.copyOf(inputs);
        this.outputs = outputs == null ? Collections.emptyList() : List.copyOf(outputs);
        this.modifiers = modifiers == null ? Collections.emptyList() : List.copyOf(modifiers);
        this.priority = priority;
        this.maxThreads = Math.max(1, maxThreads);
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

    public List<RecipeModifier> getModifiers() {
        return modifiers;
    }

    public int getPriority() {
        return priority;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setTickTime(int tickTime) {
        this.tickTime = Math.max(1, tickTime);
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = Math.max(1, maxThreads);
    }

    public MachineRecipe toMachineRecipe() {
        return new MachineRecipe(
                net.minecraft.resources.Identifier.parse(registryName),
                net.minecraft.resources.Identifier.parse(machineId),
                tickTime,
                inputs,
                outputs,
                modifiers,
                priority,
                maxThreads
        );
    }
}
