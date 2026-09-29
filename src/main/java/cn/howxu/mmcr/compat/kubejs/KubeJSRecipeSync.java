package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import cn.howxu.mmcr.internal.sync.JeiRuntimeReloadBridge;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import net.minecraft.resources.ResourceLocation;
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

    static Map<ResourceLocation, MachineRecipe> filterRecipesWithRegisteredPools(
            Map<ResourceLocation, MachineRecipe> recipes) {
        Map<ResourceLocation, MachineRecipe> valid = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, MachineRecipe> entry : recipes.entrySet()) {
            MachineRecipe recipe = entry.getValue();
            if (!MachineRegistry.containsRecipePool(recipe.recipePoolId())) {
                MMCR.LOG.warn("Skipping KubeJS recipe {}: unknown recipe pool {} at recipe_pool",
                        entry.getKey(), recipe.recipePoolId());
                continue;
            }
            valid.put(entry.getKey(), recipe);
        }
        return valid;
    }

    public static void replaceDataPackRecipes(Iterable<RecipeHolder<?>> holders) {
        Map<ResourceLocation, MachineRecipe> recipes = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : holders) {
            if (holder.value() instanceof MachineRecipe machineRecipe) {
                ResourceLocation id = holder.id().identifier();
                if (!KubeJSContentReloadTransaction.ownsRecipe(id)
                        && !KubeJSContentReloadTransaction.ownsRecipe(machineRecipe)) {
                    recipes.put(id, machineRecipe.withId(id));
                }
            }
        }
        RuntimeContentSnapshot snapshot = RuntimeContentCoordinator.replaceKubeJSRecipesAndSnapshot(
                filterRecipesWithRegisteredPools(recipes));
        JeiRuntimeReloadBridge.reloadIfAvailable(snapshot);
    }
}
