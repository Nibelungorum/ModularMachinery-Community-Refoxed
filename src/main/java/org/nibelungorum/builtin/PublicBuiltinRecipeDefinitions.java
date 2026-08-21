package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Public built-in recipe subscriber.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = PublicBuiltinRegistration.MOD_ID)
public final class PublicBuiltinRecipeDefinitions {
    private PublicBuiltinRecipeDefinitions() {
    }

    public static java.util.Map<net.minecraft.resources.Identifier, MachineRecipeDefinition> recipeDefinitions() {
        return PublicBuiltinDefinitions.recipeDefinitions();
    }

    @SubscribeEvent
    public static void register(MMCRMachineRecipesEvent event) {
        java.util.Map<net.minecraft.resources.Identifier, MachineRecipeDefinition> recipes = recipeDefinitions();
        PublicBuiltinRegistration.logger().debug("Registering {} built-in recipes", recipes.size());
        recipes.forEach((id, recipe) -> {
            if (!event.recipes().containsKey(id)) event.registerRecipe(recipe);
        });
    }

}
