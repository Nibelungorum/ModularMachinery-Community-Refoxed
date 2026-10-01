package cn.howxu.mmcr.publicapi.recipe;

import cn.howxu.mmcr.internal.api.facade.recipe.RecipeIoAdapters;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

/** Validated recipe IO declaration factories. @author howxu <dev@howxu.cn> */
public final class IoValues {
    private IoValues() {}
    public static ItemInputSpec itemInput(Item item, int count) { return RecipeIoAdapters.itemInput(item, count); }
    public static ItemInputSpec itemInput(Ingredient item, int count) { return RecipeIoAdapters.itemInput(item, count); }
    public static ItemInputSpec itemInput(Ingredient item, int count, ComponentConstraints components, float consumeChance) { return RecipeIoAdapters.itemInput(item, count, components, consumeChance); }
    public static ItemOutputSpec itemOutput(Item item, int count) { return RecipeIoAdapters.itemOutput(item, count); }
    public static ItemOutputSpec itemOutput(ItemStack stack) { return RecipeIoAdapters.itemOutput(stack); }
    public static ItemOutputSpec itemOutput(ItemStack stack, float chance) { return RecipeIoAdapters.itemOutput(stack, chance); }
    public static ItemOutputSpec itemOutput(ItemStack stack, ComponentConstraints components) { return RecipeIoAdapters.itemOutput(stack, components); }
    public static ItemOutputSpec itemOutput(ItemStack stack, float chance, ComponentConstraints components) { return RecipeIoAdapters.itemOutput(stack, chance, components); }
    public static FluidInputSpec fluidInput(Fluid fluid, int amount) { return RecipeIoAdapters.fluidInput(fluid, amount); }
    public static FluidInputSpec fluidInput(FluidIngredient fluid, int amount) { return RecipeIoAdapters.fluidInput(fluid, amount); }
    public static FluidInputSpec fluidInput(FluidIngredient fluid, int amount, float consumeChance) { return RecipeIoAdapters.fluidInput(fluid, amount, consumeChance); }
    public static FluidOutputSpec fluidOutput(Fluid fluid, int amount) { return RecipeIoAdapters.fluidOutput(fluid, amount); }
    public static FluidOutputSpec fluidOutput(FluidStack stack) { return RecipeIoAdapters.fluidOutput(stack); }
    public static FluidOutputSpec fluidOutput(FluidStack stack, float chance) { return RecipeIoAdapters.fluidOutput(stack, chance); }
    public static EnergyRateSpec energyRate(long fePerTick) { return RecipeIoAdapters.energyRate(fePerTick); }
    public static HostConstraint requiredHost(ResourceLocation id) { return RecipeIoAdapters.requiredHost(id); }
    public static CustomIoSpec customIo(ResourceLocation typeId, IoDirection io, JsonElement payload) { return RecipeIoAdapters.customIo(typeId, io, payload); }
}
