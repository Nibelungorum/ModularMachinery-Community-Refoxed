package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;

/** Public startup registration entry point for immutable recipe definitions.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeApi {
    private RecipeApi() {
    }

    /**
     * Registers a recipe definition during the startup registration window.
     *
     * @param definition definition to register
     * @throws NullPointerException if {@code definition} is null
     * @throws ApiRegistrationException if the window is not open or the ID is duplicated
     */
    public static void registerRecipe(MachineRecipeDefinition definition) {
        if (definition == null) throw new NullPointerException("definition");
        ApiRuntime.registerRecipe(definition);
    }

    /**
     * Returns whether startup registration is currently accepting recipe definitions.
     *
     * @return {@code true} while the startup registration window is open
     */
    public static boolean isRegistrationOpen() {
        return ApiRuntime.isRegistrationOpen();
    }
}
