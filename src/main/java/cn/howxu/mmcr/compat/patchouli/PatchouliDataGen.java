package cn.howxu.mmcr.compat.patchouli;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.ModItems;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

/**
 * Generates the optional guide acquisition recipe
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatchouliDataGen {
    private PatchouliDataGen() {
    }

    public static void generate(HolderLookup.Provider registries, RecipeOutput output) {
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded("patchouli")) return;

        JsonObject components = new JsonObject();
        components.addProperty("patchouli:book", "mmcr:modular_guide");
        JsonObject stack = new JsonObject();
        stack.addProperty("id", "patchouli:guide_book");
        stack.add("components", components);
        ItemStackTemplate result = ItemStackTemplate.CODEC.parse(
                registries.createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow();

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM),
                        RecipeCategory.MISC, result)
                .requires(Items.BOOK)
                .requires(ModItems.MODULARIUM.get())
                .unlockedBy("has_modularium", InventoryChangeTrigger.TriggerInstance
                        .hasItems(ModItems.MODULARIUM.get()))
                .save(output.withConditions(new ModLoadedCondition("patchouli")),
                        ResourceKey.create(Registries.RECIPE, MMCR.id("modular_guide")));
    }
}
