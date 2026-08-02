package cn.howxu.mmcr.api.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

public record MachineRecipe(
        Identifier id,
        Identifier machineId,
        int tickTime,
        List<MachineIngredient> inputs,
        List<ItemStack> outputs
) implements Recipe<RecipeInput> {

    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(MachineRecipe::id),
            Identifier.CODEC.fieldOf("machine").forGetter(MachineRecipe::machineId),
            Codec.INT.fieldOf("tick_time").forGetter(MachineRecipe::tickTime),
            MachineIngredient.CODEC.listOf().fieldOf("inputs").forGetter(MachineRecipe::inputs),
            ItemStack.CODEC.listOf().fieldOf("outputs").forGetter(MachineRecipe::outputs)
    ).apply(instance, MachineRecipe::new));

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
        return MMCRRegistries.MACHINE_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<RecipeInput>> getType() {
        return MMCRRegistries.MACHINE_RECIPE_TYPE.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return new RecipeBookCategory();
    }
}
