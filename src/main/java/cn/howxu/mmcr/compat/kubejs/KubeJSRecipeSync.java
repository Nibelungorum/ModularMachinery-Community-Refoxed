package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import cn.howxu.mmcr.internal.sync.JeiRuntimeReloadBridge;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
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

    public static void replaceDataPackRecipes(Iterable<RecipeHolder<?>> holders) {
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : holders) {
            if (holder.value() instanceof MachineRecipe machineRecipe) {
                Identifier id = holder.id().identifier();
                boolean ownedById = KubeJSContentReloadTransaction.ownsRecipe(id);
                boolean ownedByValue = KubeJSContentReloadTransaction.ownsRecipe(machineRecipe);
                MMCR.LOG.info("[MMCR/Temp][KubeJS] holderId={}, valueId={}, machineId={}, ownedById={}, ownedByValue={}",
                        id, machineRecipe.id(), machineRecipe.machineId(), ownedById, ownedByValue);
                if (!ownedById && !ownedByValue) {
                    recipes.put(id, machineRecipe.withId(id));
                }
            }
        }
        MMCR.LOG.info("[MMCR/Temp][KubeJS] publishing data-pack recipe ids={}", recipes.keySet());
        RuntimeContentSnapshot snapshot = RuntimeContentCoordinator.replaceDataPackRecipesAndSnapshot(recipes);
        JeiRuntimeReloadBridge.reloadIfAvailable(snapshot);
    }
}
