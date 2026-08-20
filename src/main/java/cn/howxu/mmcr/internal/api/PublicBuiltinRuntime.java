package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import org.nibelungorum.builtin.PublicBuiltinDefinitions;

/** Installs public built-in declarations into the internal runtime models.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PublicBuiltinRuntime {
    private PublicBuiltinRuntime() {
    }

    public static void registerStructures(DynamicContentReloadService.Candidate candidate) {
        PublicBuiltinDefinitions.structureDefinitions().values().stream()
                .map(PublicMachineAdapter::toStructureDefinition)
                .forEach(candidate::registerStructure);
    }

    public static void registerRecipes() {
        PublicBuiltinDefinitions.recipeDefinitions().values().stream()
                .map(PublicRecipeAdapter::toRecipe)
                .filter(recipe -> !cn.howxu.mmcr.api.recipe.RecipeRegistry.containsStatic(recipe.id()))
                .forEach(cn.howxu.mmcr.api.recipe.RecipeRegistry::register);
    }
}
