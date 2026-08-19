package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;

/** Public startup registration entry point for immutable recipe definitions.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeApi {
    private RecipeApi() {
    }

    public static void registerRecipe(MachineRecipeDefinition definition) {
        if (definition == null) throw new NullPointerException("definition");
        PublicApiBootstrap.registerRecipe(definition);
    }

    public static boolean isRegistrationOpen() {
        return PublicApiBootstrap.isRegistrationOpen();
    }
}
