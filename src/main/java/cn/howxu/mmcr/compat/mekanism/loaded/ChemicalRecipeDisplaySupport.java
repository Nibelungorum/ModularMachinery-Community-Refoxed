package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.client.recipe.RecipeDisplayEntry;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.List;

/** Loaded-only native chemical lookup; no viewer API escapes this boundary.
 * @author howxu <dev@howxu.cn>
 */
public final class ChemicalRecipeDisplaySupport {
    private ChemicalRecipeDisplaySupport() { }

    public static RecipeDisplayEntry display(LoadedChemicalRequirement requirement) {
        ChemicalIngredient ingredient = requirement.ingredient();
        List<ResourceLocation> candidates;
        if (ingredient.kind() == ChemicalIngredient.Kind.CHEMICAL) {
            candidates = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                            ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ingredient.id()))
                    .map(holder -> List.of(holder.key().location())).orElseGet(List::of);
        } else {
            TagKey<Chemical> tag = TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ingredient.id());
            candidates = MekanismAPI.CHEMICAL_REGISTRY.getTag(tag).stream()
                    .flatMap(holders -> holders.stream())
                    .map(holder -> MekanismAPI.CHEMICAL_REGISTRY.getKey(holder.value()))
                    .sorted().toList();
        }
        return new RecipeDisplayEntry(requirement.io(), requirement.type().id(), RecipeDisplayEntry.Kind.CHEMICAL,
                requirement, ingredient.amount(), requirement.io() == IOType.INPUT
                ? requirement.consumeChance() : requirement.chance(), List.of(), List.of(), candidates,
                DataComponentPredicateSet.EMPTY, !candidates.isEmpty(), List.of(Component.literal(ingredient.id().toString())));
    }

    public static Component temperatureLabel(double kelvin, boolean input) {
        var unit = MekanismTemperatureDisplay.configuredUnit();
        return Component.translatable(input ? "jei.mmcr.machine_recipe.mekanism_temperature"
                        : "jei.mmcr.machine_recipe.heat_output",
                MekanismTemperatureDisplay.fromKelvin(kelvin, unit), MekanismTemperatureDisplay.symbol(unit));
    }
}
