package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;

/** Public startup registration entry point for immutable recipe definitions.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeApi {
    private RecipeApi() {
    }

    public static void registerRecipe(MachineRecipeDefinition definition) {
        if (definition == null) throw new NullPointerException("definition");
        ApiRuntime.registerRecipe(definition);
    }

    public static boolean isRegistrationOpen() {
        return ApiRuntime.isRegistrationOpen();
    }
}
