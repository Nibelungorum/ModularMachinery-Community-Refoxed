package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.MMCR;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Public built-in recipe subscriber.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID)
public final class PublicBuiltinRecipeDefinitions {
    private PublicBuiltinRecipeDefinitions() {
    }

    public static java.util.Map<net.minecraft.resources.Identifier, MachineRecipeDefinition> recipeDefinitions() {
        return PublicBuiltinDefinitions.recipeDefinitions();
    }

    @SubscribeEvent
    public static void register(MMCRMachineRecipesEvent event) {
        recipeDefinitions().values().forEach(event::registerRecipe);
    }

}
