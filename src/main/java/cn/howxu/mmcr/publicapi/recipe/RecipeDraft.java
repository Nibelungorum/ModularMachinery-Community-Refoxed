package cn.howxu.mmcr.publicapi.recipe;

import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.SmartInterfaceRequirementSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.ApiStatus;
import java.util.List;

/** Library-produced mutable draft backed by one recipe builder. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface RecipeDraft {
    RecipeDraft recipePool(ResourceLocation id);
    RecipeDraft duration(int ticks); RecipeDraft priority(int priority); RecipeDraft maxThreads(int threads);
    RecipeDraft cancelIfPerTickFails(boolean value); RecipeDraft parallelized(boolean value); RecipeDraft allowPartialOutputs(boolean value);
    RecipeDraft inputItem(Item item, int count); RecipeDraft inputItem(Ingredient item, int count);
    RecipeDraft inputItem(TagKey<Item> tag, int count); RecipeDraft inputItemTag(TagKey<Item> tag, int count);
    RecipeDraft inputItem(Ingredient item, int count, ComponentConstraints components, float consumeChance);
    RecipeDraft inputFluid(Fluid fluid, int amount); RecipeDraft inputFluid(Fluid fluid, int amount, float consumeChance);
    RecipeDraft outputFluid(Fluid fluid, int amount);
    RecipeDraft inputStress(double stress, double minRpm); RecipeDraft inputStress(double stress, double minRpm, List<String> tags);
    RecipeDraft outputStress(double stress, double rpm); RecipeDraft outputStress(double stress, double rpm, List<String> tags);
    RecipeDraft inputEnergy(long fePerTick); RecipeDraft outputEnergy(long fePerTick); RecipeDraft iFEt(long fePerTick); RecipeDraft oFEt(long fePerTick);
    RecipeDraft outputItem(Item item, int count); RecipeDraft outputItem(ItemStack stack); RecipeDraft outputItem(ItemStack stack, ComponentConstraints components);
    RecipeDraft outputChance(ItemStack stack, float chance); RecipeDraft outputChance(ItemStack stack, float chance, ComponentConstraints components);
    RecipeDraft levelRequirement(ResourceLocation typeId, ResourceLocation levelId); RecipeDraft stageRequirement(int minStage);
    RecipeDraft requiredHost(ResourceLocation id); RecipeDraft modifier(ResourceLocation id);
    RecipeDraft requirement(RequirementSpec value); RecipeDraft smartInterface(SmartInterfaceRequirementSpec value); RecipeDraft custom(CustomIoSpec value);
    RecipeDraft inputChemical(ResourceLocation id, long amount); RecipeDraft inputChemical(ResourceLocation id, long amount, float consumeChance);
    RecipeDraft inputChemicalTag(ResourceLocation id, long amount); RecipeDraft inputChemicalTag(ResourceLocation id, long amount, float consumeChance);
    RecipeDraft outputChemical(ResourceLocation id, long amount, float chance); RecipeDraft inputHeatTemperature(double temperature); RecipeDraft outputHeat(double heat);
    RecipeSpec build();
}
