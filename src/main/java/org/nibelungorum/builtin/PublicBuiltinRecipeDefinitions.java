package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRRegisterRecipesEvent;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

/** Public built-in recipe subscriber.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public final class PublicBuiltinRecipeDefinitions {
    private PublicBuiltinRecipeDefinitions() {
    }

    public static java.util.Map<net.minecraft.resources.Identifier, MachineRecipeDefinition> recipeDefinitions() {
        return PublicBuiltinDefinitions.recipeDefinitions();
    }

    @SubscribeEvent
    public static void register(MMCRRegisterRecipesEvent event) {
        recipeDefinitions().values().forEach(event::registerRecipe);
    }

}
