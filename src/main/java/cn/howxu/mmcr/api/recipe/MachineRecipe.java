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
            RecipeModifier.CODEC.listOf().optionalFieldOf("modifiers", Collections.emptyList()).forGetter(MachineRecipe::modifiers),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MachineRecipe::priority),
            Codec.INT.optionalFieldOf("max_threads", 1).forGetter(MachineRecipe::maxThreads)
    ).apply(instance, MachineRecipe::new));

    private final Identifier id;
    private final Identifier machineId;
    private final int tickTime;
    private final List<MachineIngredient> inputs;
    private final List<ItemStack> outputs;
    private final List<RecipeModifier> modifiers;
    private final int priority;
    private final int maxThreads;

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
        this.modifiers = modifiers == null ? Collections.emptyList() : List.copyOf(modifiers);
        this.priority = priority;
        this.maxThreads = Math.max(1, maxThreads);
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

    public List<RecipeModifier> modifiers() {
        return modifiers;
    }

    public int priority() {
        return priority;
    }

    public int maxThreads() {
        return maxThreads;
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
                && modifiers.equals(that.modifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads);
    }
}
