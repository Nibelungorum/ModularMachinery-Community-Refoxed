package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import org.nibelungorum.builtin.PublicBuiltinDefinitions;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;

/** Installs public built-in structure declarations into the reloadable runtime registry.
 * @author howxu <dev@howxu.cn>
 */
public final class PublicBuiltinRuntime {
    private PublicBuiltinRuntime() {
    }

    public static void registerStructures(DynamicContentReloadService.Candidate candidate) {
        // Migrated structure declarations are installed here; legacy runtime structures remain explicit.
    }

    public static void registerRecipes() {
        PublicBuiltinDefinitions.recipeDefinitions().values().stream()
                .map(PublicRecipeAdapter::toRecipe)
                .filter(recipe -> RecipeRegistry.getRecipe(recipe.id()) == null)
                .forEach(RecipeRegistry::register);
    }
}
