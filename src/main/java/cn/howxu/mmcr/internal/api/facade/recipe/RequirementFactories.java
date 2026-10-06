package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.ItemInputSpec;
import cn.howxu.mmcr.publicapi.recipe.ItemOutputSpec;
import cn.howxu.mmcr.publicapi.recipe.FluidInputSpec;
import cn.howxu.mmcr.publicapi.recipe.FluidOutputSpec;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import cn.howxu.mmcr.publicapi.recipe.requirement.*;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import static cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters.wrap;
import static cn.howxu.mmcr.internal.api.facade.recipe.ModifierAdapters.io;

/** Constructor delegation without merging strict and lenient overloads. @author howxu <dev@howxu.cn> */
public final class RequirementFactories {
    private RequirementFactories() {}
    public static SourceRequirementSpec source(IoDirection direction, long amount, List<String> tags) { return (SourceRequirementSpec) wrap(new SourceRequirement(io(direction), amount, tags)); }
    public static ManaRequirementSpec mana(IoDirection direction, long amount, List<String> tags) { return (ManaRequirementSpec) wrap(new ManaRequirement(io(direction), amount, tags)); }
    public static AirRequirementSpec airInput(long airPerTick, float minPressure, List<String> tags) { return (AirRequirementSpec) wrap(AirRequirement.input(airPerTick, minPressure, tags)); }
    public static AirRequirementSpec airOutput(long airPerTick, List<String> tags) { return (AirRequirementSpec) wrap(AirRequirement.output(airPerTick, tags)); }
    public static StressRequirementSpec stressInput(double stress, double minRpm, List<String> tags) { return (StressRequirementSpec) wrap(StressRequirement.input(stress, minRpm, tags)); }
    public static StressRequirementSpec stressOutput(double stress, double rpm, List<String> tags) { return (StressRequirementSpec) wrap(StressRequirement.output(stress, rpm, tags)); }
    public static ItemRequirementSpec item(IoDirection io, Ingredient item, int count, ItemStack stack) { return (ItemRequirementSpec) wrap(new ItemRequirement(io(io), item, count, stack)); }
    public static ItemRequirementSpec item(IoDirection io, Ingredient item, int count, ItemStack stack, List<String> tags) { return (ItemRequirementSpec) wrap(new ItemRequirement(io(io), item, count, stack, tags)); }
    public static ItemRequirementSpec item(IoDirection io, Ingredient item, int count, ItemStack stack, float chance, List<String> tags) { return (ItemRequirementSpec) wrap(new ItemRequirement(io(io), item, count, stack, chance, tags)); }
    public static ItemRequirementSpec item(IoDirection io, Ingredient item, int count, ItemStack stack, float chance, List<String> tags, ComponentConstraints components, float consumeChance) { return (ItemRequirementSpec) wrap(new ItemRequirement(io(io), item, count, stack, chance, tags, ComponentAdapters.unwrap(components), consumeChance)); }
    public static ItemRequirementSpec item(IoDirection io, Ingredient item, int count, ItemStack stack, float chance, ComponentConstraints components, float consumeChance) { return (ItemRequirementSpec) wrap(new ItemRequirement(io(io), item, count, stack, chance, ComponentAdapters.unwrap(components), consumeChance)); }
    public static ItemRequirementSpec itemInput(ItemInputSpec input) { return (ItemRequirementSpec) wrap(ItemRequirement.input(RecipeIoAdapters.unwrap(input))); }
    public static ItemRequirementSpec itemOutput(ItemOutputSpec output) { return (ItemRequirementSpec) wrap(ItemRequirement.output(RecipeIoAdapters.unwrap(output))); }
    public static FluidRequirementSpec fluid(IoDirection io, FluidIngredient fluid, int amount, FluidStack stack) { return (FluidRequirementSpec) wrap(new FluidRequirement(io(io), fluid, amount, stack)); }
    public static FluidRequirementSpec fluid(IoDirection io, FluidIngredient fluid, int amount, FluidStack stack, List<String> tags) { return (FluidRequirementSpec) wrap(new FluidRequirement(io(io), fluid, amount, stack, tags)); }
    public static FluidRequirementSpec fluid(IoDirection io, FluidIngredient fluid, int amount, FluidStack stack, float chance, List<String> tags) { return (FluidRequirementSpec) wrap(new FluidRequirement(io(io), fluid, amount, stack, chance, tags)); }
    public static FluidRequirementSpec fluid(IoDirection io, FluidIngredient fluid, int amount, FluidStack stack, float chance, List<String> tags, float consumeChance) { return (FluidRequirementSpec) wrap(new FluidRequirement(io(io), fluid, amount, stack, chance, tags, consumeChance)); }
    public static FluidRequirementSpec fluid(IoDirection io, FluidIngredient fluid, int amount, FluidStack stack, float chance, float consumeChance) { return (FluidRequirementSpec) wrap(new FluidRequirement(io(io), fluid, amount, stack, chance, consumeChance)); }
    public static FluidRequirementSpec fluidInput(FluidInputSpec input) { return (FluidRequirementSpec) wrap(FluidRequirement.input(RecipeIoAdapters.unwrap(input))); }
    public static FluidRequirementSpec fluidOutput(FluidOutputSpec output) { return (FluidRequirementSpec) wrap(FluidRequirement.output(RecipeIoAdapters.unwrap(output))); }
    public static EnergyRequirementSpec energy(long rate) { return (EnergyRequirementSpec) wrap(new EnergyRequirement(rate)); }
    public static EnergyRequirementSpec energy(long rate, List<String> tags) { return (EnergyRequirementSpec) wrap(new EnergyRequirement(rate, tags)); }
    public static EnergyRequirementSpec energy(IoDirection io, long rate) { return (EnergyRequirementSpec) wrap(new EnergyRequirement(io(io), rate)); }
    public static EnergyRequirementSpec energy(IoDirection io, long rate, List<String> tags) { return (EnergyRequirementSpec) wrap(new EnergyRequirement(io(io), rate, tags)); }
    public static LevelRequirementSpec level(ResourceLocation typeId, ResourceLocation levelId) { return (LevelRequirementSpec) wrap(new LevelRequirement(typeId, levelId)); }
    public static LevelRequirementSpec level(IoDirection io, ResourceLocation typeId, ResourceLocation levelId) { return (LevelRequirementSpec) wrap(new LevelRequirement(io(io), typeId, levelId)); }
    public static StageRequirementSpec stage(int minStage) { return (StageRequirementSpec) wrap(new StageRequirement(minStage)); }
    public static StageRequirementSpec stage(IoDirection io, int minStage) { return (StageRequirementSpec) wrap(new StageRequirement(io(io), minStage)); }
    public static SmartInterfaceRequirementSpec smartInterface(IoDirection io, String type, float min, float max) { return (SmartInterfaceRequirementSpec) wrap(new SmartInterfaceRequirement(io(io), type, min, max)); }
    public static SmartInterfaceRequirementSpec smartInput(String type, float value) { return (SmartInterfaceRequirementSpec) wrap(SmartInterfaceRequirement.input(type, value)); }
    public static SmartInterfaceRequirementSpec smartInput(String type, float min, float max) { return (SmartInterfaceRequirementSpec) wrap(SmartInterfaceRequirement.input(type, min, max)); }
    public static SmartInterfaceRequirementSpec smartOutput(String type, float value) { return (SmartInterfaceRequirementSpec) wrap(SmartInterfaceRequirement.output(type, value)); }
}
