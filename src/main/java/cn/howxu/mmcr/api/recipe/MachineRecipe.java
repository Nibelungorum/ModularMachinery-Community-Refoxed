package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.registry.ModRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MachineRecipe implements Recipe<RecipeInput> {

    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(MachineRecipe::id),
            Identifier.CODEC.fieldOf("machine").forGetter(MachineRecipe::machineId),
            Codec.INT.fieldOf("tick_time").forGetter(MachineRecipe::tickTime),
            MachineIngredient.CODEC.listOf().fieldOf("inputs").forGetter(MachineRecipe::inputs),
            ItemStack.CODEC.listOf().fieldOf("outputs").forGetter(MachineRecipe::outputs),
            FluidStack.CODEC.listOf().optionalFieldOf("fluid_outputs", Collections.emptyList()).forGetter(MachineRecipe::fluidOutputs),
            RecipeModifier.CODEC.listOf().optionalFieldOf("modifiers", Collections.emptyList()).forGetter(MachineRecipe::modifiers),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MachineRecipe::priority),
            Codec.INT.optionalFieldOf("max_threads", 1).forGetter(MachineRecipe::maxThreads),
            Codec.BOOL.optionalFieldOf("cancelIfPerTickFails", false).forGetter(MachineRecipe::doesCancelRecipeOnPerTickFailure)
    ).apply(instance, MachineRecipe::create));

    private final Identifier id;
    private final Identifier machineId;
    private final int tickTime;
    private final List<MachineIngredient> inputs;
    private final List<ItemStack> outputs;
    private final List<FluidStack> fluidOutputs;
    private final List<RecipeModifier> modifiers;
    private final int priority;
    private final int maxThreads;
    private final boolean cancelRecipeOnPerTickFailure;

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<ItemStack> outputs) {
        this(id, machineId, tickTime, inputs, outputs, Collections.emptyList(), 0, 1);
    }

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<ItemStack> outputs,
                         List<RecipeModifier> modifiers,
                         int priority,
                         int maxThreads) {
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, false);
    }

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<ItemStack> outputs,
                         List<RecipeModifier> modifiers,
                         int priority,
                         int maxThreads,
                         boolean cancelRecipeOnPerTickFailure) {
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, Collections.emptyList());
    }

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<ItemStack> outputs,
                         List<RecipeModifier> modifiers,
                         int priority,
                         int maxThreads,
                         boolean cancelRecipeOnPerTickFailure,
                         List<FluidStack> fluidOutputs) {
        if (id == null) {
            throw new IllegalArgumentException("Recipe id must not be null");
        }
        if (machineId == null) {
            throw new IllegalArgumentException("Recipe machineId must not be null");
        }
        this.id = id;
        this.machineId = machineId;
        this.tickTime = Math.max(1, tickTime);
        this.inputs = inputs == null ? Collections.emptyList() : List.copyOf(inputs);
        this.outputs = outputs == null ? Collections.emptyList() : List.copyOf(outputs);
        this.fluidOutputs = fluidOutputs == null ? Collections.emptyList() : List.copyOf(fluidOutputs);
        this.modifiers = modifiers == null ? Collections.emptyList() : List.copyOf(modifiers);
        this.priority = priority;
        this.maxThreads = Math.max(1, maxThreads);
        this.cancelRecipeOnPerTickFailure = cancelRecipeOnPerTickFailure;
    }

    private static MachineRecipe create(Identifier id,
                                        Identifier machineId,
                                        int tickTime,
                                        List<MachineIngredient> inputs,
                                        List<ItemStack> outputs,
                                        List<FluidStack> fluidOutputs,
                                        List<RecipeModifier> modifiers,
                                        int priority,
                                        int maxThreads,
                                        boolean cancelRecipeOnPerTickFailure) {
        return new MachineRecipe(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, fluidOutputs);
    }

    public Identifier id() {
        return id;
    }

    public Identifier machineId() {
        return machineId;
    }

    public int tickTime() {
        return tickTime;
    }

    public List<MachineIngredient> inputs() {
        return inputs;
    }

    public List<ItemStack> outputs() {
        return outputs;
    }

    public List<FluidStack> fluidOutputs() {
        return fluidOutputs;
    }

    public List<RecipeModifier> modifiers() {
        return modifiers;
    }

    public int priority() {
        return priority;
    }

    public int maxThreads() {
        return maxThreads;
    }

    public boolean doesCancelRecipeOnPerTickFailure() {
        return cancelRecipeOnPerTickFailure;
    }

    public Identifier getRegistryName() {
        return id;
    }

    public Identifier getOwningMachineIdentifier() {
        return machineId;
    }

    public int getRecipeTotalTickTime() {
        return tickTime;
    }

    public int getConfiguredPriority() {
        return priority;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return true;
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst().copy();
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
        return ModRecipeTypes.MACHINE_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<RecipeInput>> getType() {
        return ModRecipeTypes.MACHINE_RECIPE_TYPE.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return new RecipeBookCategory();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MachineRecipe that)) return false;
        return tickTime == that.tickTime
                && priority == that.priority
                && maxThreads == that.maxThreads
                && id.equals(that.id)
                && machineId.equals(that.machineId)
                && inputs.equals(that.inputs)
                && outputs.equals(that.outputs)
                && fluidOutputs.equals(that.fluidOutputs)
                && modifiers.equals(that.modifiers)
                && cancelRecipeOnPerTickFailure == that.cancelRecipeOnPerTickFailure;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, machineId, tickTime, inputs, outputs, fluidOutputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure);
    }
}
