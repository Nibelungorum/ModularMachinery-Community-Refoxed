package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.publicapi.recipe.*;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.SmartInterfaceRequirementSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import java.util.List;

/** One-state builder delegation. @author howxu <dev@howxu.cn> */
final class RecipeDraftFacade implements RecipeDraft {
    private final MachineRecipeBuilder delegate;
    RecipeDraftFacade(MachineRecipeBuilder delegate) { this.delegate = delegate; }
    public RecipeDraft recipePool(ResourceLocation id) { delegate.recipePool(id); return this; }
    public RecipeDraft duration(int ticks) { delegate.duration(ticks); return this; }
    public RecipeDraft priority(int priority) { delegate.priority(priority); return this; }
    public RecipeDraft maxThreads(int threads) { delegate.maxThreads(threads); return this; }
    public RecipeDraft cancelIfPerTickFails(boolean value) { delegate.cancelIfPerTickFails(value); return this; }
    public RecipeDraft parallelized(boolean value) { delegate.parallelized(value); return this; }
    public RecipeDraft allowPartialOutputs(boolean value) { delegate.allowPartialOutputs(value); return this; }
    public RecipeDraft inputItem(Item item, int count) { delegate.inputItem(item, count); return this; }
    public RecipeDraft inputItem(Ingredient item, int count) { delegate.inputItem(item, count); return this; }
    public RecipeDraft inputItem(TagKey<Item> tag, int count) { delegate.inputItem(tag, count); return this; }
    public RecipeDraft inputItemTag(TagKey<Item> tag, int count) { delegate.inputItemTag(tag, count); return this; }
    public RecipeDraft inputItem(Ingredient item, int count, ComponentConstraints components, float consumeChance) { delegate.inputItem(item, count, ComponentAdapters.unwrap(components), consumeChance); return this; }
    public RecipeDraft inputFluid(Fluid fluid, int amount) { delegate.inputFluid(fluid, amount); return this; }
    public RecipeDraft inputFluid(Fluid fluid, int amount, float consumeChance) { delegate.inputFluid(fluid, amount, consumeChance); return this; }
    public RecipeDraft outputFluid(Fluid fluid, int amount) { delegate.outputFluid(fluid, amount); return this; }
    public RecipeDraft inputAir(long airPerTick, float minPressure) { delegate.inputAir(airPerTick, minPressure); return this; }
    public RecipeDraft inputAir(long airPerTick, float minPressure, List<String> tags) { delegate.inputAir(airPerTick, minPressure, tags); return this; }
    public RecipeDraft outputAir(long airPerTick) { delegate.outputAir(airPerTick); return this; }
    public RecipeDraft outputAir(long airPerTick, List<String> tags) { delegate.outputAir(airPerTick, tags); return this; }
    public RecipeDraft inputStress(double stress, double minRpm) { delegate.inputStress(stress, minRpm); return this; }
    public RecipeDraft inputStress(double stress, double minRpm, List<String> tags) { delegate.inputStress(stress, minRpm, tags); return this; }
    public RecipeDraft outputStress(double stress, double rpm) { delegate.outputStress(stress, rpm); return this; }
    public RecipeDraft outputStress(double stress, double rpm, List<String> tags) { delegate.outputStress(stress, rpm, tags); return this; }
    public RecipeDraft inputSource(long amount) { delegate.inputSource(amount); return this; }
    public RecipeDraft outputSource(long amount) { delegate.outputSource(amount); return this; }
    public RecipeDraft inputMana(long amount) { delegate.inputMana(amount); return this; }
    public RecipeDraft outputMana(long amount) { delegate.outputMana(amount); return this; }
    public RecipeDraft inputEnergy(long rate) { delegate.inputEnergy(rate); return this; }
    public RecipeDraft outputEnergy(long rate) { delegate.outputEnergy(rate); return this; }
    public RecipeDraft iFEt(long rate) { delegate.iFEt(rate); return this; }
    public RecipeDraft oFEt(long rate) { delegate.oFEt(rate); return this; }
    public RecipeDraft outputItem(Item item, int count) { delegate.outputItem(item, count); return this; }
    public RecipeDraft outputItem(ItemStack stack) { delegate.outputItem(stack); return this; }
    public RecipeDraft outputItem(ItemStack stack, ComponentConstraints components) { delegate.outputItem(stack, ComponentAdapters.unwrap(components)); return this; }
    public RecipeDraft outputChance(ItemStack stack, float chance) { delegate.outputChance(stack, chance); return this; }
    public RecipeDraft outputChance(ItemStack stack, float chance, ComponentConstraints components) { delegate.outputChance(stack, chance, ComponentAdapters.unwrap(components)); return this; }
    public RecipeDraft levelRequirement(ResourceLocation typeId, ResourceLocation levelId) { delegate.levelRequirement(typeId, levelId); return this; }
    public RecipeDraft stageRequirement(int minStage) { delegate.stageRequirement(minStage); return this; }
    public RecipeDraft requiredHost(ResourceLocation id) { delegate.requiredHost(id); return this; }
    public RecipeDraft modifier(ResourceLocation id) { delegate.modifier(id); return this; }
    public RecipeDraft requirement(RequirementSpec value) { delegate.requirement(RequirementAdapters.unwrap(value)); return this; }
    public RecipeDraft smartInterface(SmartInterfaceRequirementSpec value) { delegate.smartInterface((SmartInterfaceRequirement) RequirementAdapters.unwrap(value)); return this; }
    public RecipeDraft custom(CustomIoSpec value) { delegate.custom(RecipeIoAdapters.unwrap(value)); return this; }
    public RecipeDraft inputChemical(ResourceLocation id, long amount) { delegate.inputChemical(id, amount); return this; }
    public RecipeDraft inputChemical(ResourceLocation id, long amount, float chance) { delegate.inputChemical(id, amount, chance); return this; }
    public RecipeDraft inputChemicalTag(ResourceLocation id, long amount) { delegate.inputChemicalTag(id, amount); return this; }
    public RecipeDraft inputChemicalTag(ResourceLocation id, long amount, float chance) { delegate.inputChemicalTag(id, amount, chance); return this; }
    public RecipeDraft outputChemical(ResourceLocation id, long amount, float chance) { delegate.outputChemical(id, amount, chance); return this; }
    public RecipeDraft inputHeatTemperature(double temperature) { delegate.inputHeatTemperature(temperature); return this; }
    public RecipeDraft outputHeat(double heat) { delegate.outputHeat(heat); return this; }
    public RecipeSpec build() { return RecipeAdapters.wrap(delegate.build()); }
}
