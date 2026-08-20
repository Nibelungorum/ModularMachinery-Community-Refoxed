package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.RecipeApi;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import net.neoforged.bus.api.Event;

/** Event used to register public recipe definitions during startup.
 * @author howxu <dev@howxu.cn>
 */
public final class MMCRRegisterRecipesEvent extends Event {
    public void registerRecipe(MachineRecipeDefinition definition) {
        RecipeApi.registerRecipe(definition);
    }
}
