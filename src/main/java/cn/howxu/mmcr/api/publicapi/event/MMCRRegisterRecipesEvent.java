package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.RecipeApi;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import net.neoforged.bus.api.Event;

import java.util.Objects;

/** Event used to register public recipe definitions during startup.
 * @author howxu <dev@howxu.cn>
 */
public final class MMCRRegisterRecipesEvent extends Event {
    private boolean frozen;

    public void registerRecipe(MachineRecipeDefinition definition) {
        if (frozen) throw new IllegalStateException("Machine recipes are frozen");
        Objects.requireNonNull(definition, "definition");
        RecipeApi.registerRecipe(definition);
    }

    public void freeze() {
        frozen = true;
    }
}
