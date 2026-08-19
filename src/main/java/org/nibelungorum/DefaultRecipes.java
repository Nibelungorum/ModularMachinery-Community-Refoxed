package org.nibelungorum;

import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import org.nibelungorum.builtin.PublicBuiltinDefinitions;

import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * Public built-in recipe declarations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DefaultRecipes {
    private DefaultRecipes() {
    }

    public static Map<Identifier, MachineRecipeDefinition> definitions() {
        return PublicBuiltinDefinitions.recipeDefinitions();
    }

    public static void registerStatic(java.util.List<cn.howxu.mmcr.api.recipe.MachineRecipe> recipes) {
        LegacyDefaultRecipes.registerStatic(recipes);
    }

    public static java.util.List<cn.howxu.mmcr.api.recipe.MachineRecipe> gameTestRecipes() {
        return LegacyDefaultRecipes.gameTestRecipes();
    }
}
