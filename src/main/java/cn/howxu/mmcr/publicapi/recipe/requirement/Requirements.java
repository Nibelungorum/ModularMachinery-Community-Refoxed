package cn.howxu.mmcr.publicapi.recipe.requirement;

import cn.howxu.mmcr.internal.api.facade.recipe.RequirementFactories;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementExtensionAdapter;
import cn.howxu.mmcr.publicapi.recipe.*;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

/** Requirement factories preserving strict and canonical constructor semantics.
 * @author howxu <dev@howxu.cn> */
public final class Requirements {
    private Requirements() {}
    public static AirRequirementSpec airInput(long airPerTick, float minPressure) { return airInput(airPerTick, minPressure, List.of()); }
    public static AirRequirementSpec airInput(long airPerTick, float minPressure, List<String> tags) { return RequirementFactories.airInput(airPerTick, minPressure, tags); }
    public static AirRequirementSpec airOutput(long airPerTick) { return airOutput(airPerTick, List.of()); }
    public static AirRequirementSpec airOutput(long airPerTick, List<String> tags) { return RequirementFactories.airOutput(airPerTick, tags); }
    public static StressRequirementSpec stressInput(double stress, double minRpm) { return stressInput(stress, minRpm, List.of()); }
    public static StressRequirementSpec stressInput(double stress, double minRpm, List<String> tags) { return RequirementFactories.stressInput(stress, minRpm, tags); }
    public static StressRequirementSpec stressOutput(double stress, double rpm) { return stressOutput(stress, rpm, List.of()); }
    public static StressRequirementSpec stressOutput(double stress, double rpm, List<String> tags) { return RequirementFactories.stressOutput(stress, rpm, tags); }
    public static <R> RequirementSpec extension(RequirementKind<R> kind, R payload) { return RequirementExtensionAdapter.requirement(kind, payload); }
    public static ItemRequirementSpec item(IoDirection io, Ingredient item, int count, ItemStack stack) { return RequirementFactories.item(io, item, count, stack); }
    public static ItemRequirementSpec item(IoDirection io, Ingredient item, int count, ItemStack stack, List<String> tags) { return RequirementFactories.item(io, item, count, stack, tags); }
    public static ItemRequirementSpec item(IoDirection io, Ingredient item, int count, ItemStack stack, float chance, List<String> tags) { return RequirementFactories.item(io, item, count, stack, chance, tags); }
    public static ItemRequirementSpec item(IoDirection io, Ingredient item, int count, ItemStack stack, float chance, List<String> tags, ComponentConstraints components, float consumeChance) { return RequirementFactories.item(io, item, count, stack, chance, tags, components, consumeChance); }
    public static ItemRequirementSpec item(IoDirection io, Ingredient item, int count, ItemStack stack, float chance, ComponentConstraints components, float consumeChance) { return RequirementFactories.item(io, item, count, stack, chance, components, consumeChance); }
    public static ItemRequirementSpec itemInput(ItemInputSpec input) { return RequirementFactories.itemInput(input); }
    public static ItemRequirementSpec itemOutput(ItemOutputSpec output) { return RequirementFactories.itemOutput(output); }
    public static ItemRequirementSpec itemInput(Item item, int count) { return itemInput(IoValues.itemInput(item, count)); }
    public static ItemRequirementSpec itemInput(Ingredient item, int count) { return itemInput(IoValues.itemInput(item, count)); }
    public static ItemRequirementSpec itemOutput(ItemStack stack) { return itemOutput(IoValues.itemOutput(stack)); }
    public static ItemRequirementSpec itemOutput(ItemStack stack, float chance) { return itemOutput(IoValues.itemOutput(stack, chance)); }
    public static FluidRequirementSpec fluid(IoDirection io, FluidIngredient fluid, int amount, FluidStack stack) { return RequirementFactories.fluid(io, fluid, amount, stack); }
    public static FluidRequirementSpec fluid(IoDirection io, FluidIngredient fluid, int amount, FluidStack stack, List<String> tags) { return RequirementFactories.fluid(io, fluid, amount, stack, tags); }
    public static FluidRequirementSpec fluid(IoDirection io, FluidIngredient fluid, int amount, FluidStack stack, float chance, List<String> tags) { return RequirementFactories.fluid(io, fluid, amount, stack, chance, tags); }
    public static FluidRequirementSpec fluid(IoDirection io, FluidIngredient fluid, int amount, FluidStack stack, float chance, List<String> tags, float consumeChance) { return RequirementFactories.fluid(io, fluid, amount, stack, chance, tags, consumeChance); }
    public static FluidRequirementSpec fluid(IoDirection io, FluidIngredient fluid, int amount, FluidStack stack, float chance, float consumeChance) { return RequirementFactories.fluid(io, fluid, amount, stack, chance, consumeChance); }
    public static FluidRequirementSpec fluidInput(FluidInputSpec input) { return RequirementFactories.fluidInput(input); }
    public static FluidRequirementSpec fluidOutput(FluidOutputSpec output) { return RequirementFactories.fluidOutput(output); }
    public static FluidRequirementSpec fluidInput(Fluid fluid, int amount) { return fluidInput(IoValues.fluidInput(fluid, amount)); }
    public static FluidRequirementSpec fluidOutput(FluidStack stack) { return fluidOutput(IoValues.fluidOutput(stack)); }
    public static FluidRequirementSpec fluidOutput(FluidStack stack, float chance) { return fluidOutput(IoValues.fluidOutput(stack, chance)); }
    public static EnergyRequirementSpec energy(long rate) { return RequirementFactories.energy(rate); }
    public static EnergyRequirementSpec energy(long rate, List<String> tags) { return RequirementFactories.energy(rate, tags); }
    public static EnergyRequirementSpec energy(IoDirection io, long rate) { return RequirementFactories.energy(io, rate); }
    public static EnergyRequirementSpec energy(IoDirection io, long rate, List<String> tags) { return RequirementFactories.energy(io, rate, tags); }
    public static LevelRequirementSpec level(ResourceLocation typeId, ResourceLocation levelId) { return RequirementFactories.level(typeId, levelId); }
    public static LevelRequirementSpec level(IoDirection io, ResourceLocation typeId, ResourceLocation levelId) { return RequirementFactories.level(io, typeId, levelId); }
    public static StageRequirementSpec stage(int minStage) { return RequirementFactories.stage(minStage); }
    public static StageRequirementSpec stage(IoDirection io, int minStage) { return RequirementFactories.stage(io, minStage); }
    public static SmartInterfaceRequirementSpec smartInterface(IoDirection io, String type, float min, float max) { return RequirementFactories.smartInterface(io, type, min, max); }
    public static SmartInterfaceRequirementSpec smartInput(String type, float value) { return RequirementFactories.smartInput(type, value); }
    public static SmartInterfaceRequirementSpec smartInput(String type, float min, float max) { return RequirementFactories.smartInput(type, min, max); }
    public static SmartInterfaceRequirementSpec smartOutput(String type, float value) { return RequirementFactories.smartOutput(type, value); }
}
