package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Syncs KubeJS datapack recipes into MMCR's runtime recipe registry.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class KubeJSRecipeSync {
    private KubeJSRecipeSync() {
    }

    public static void replaceDynamicRecipes(Iterable<RecipeHolder<?>> holders) {
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : holders) {
            if (holder.value() instanceof MachineRecipe machineRecipe) {
                recipes.put(holder.id().identifier(), machineRecipe.withId(holder.id().identifier()));
            }
        }
        RecipeRegistry.replaceDynamic(recipes);
    }
}
