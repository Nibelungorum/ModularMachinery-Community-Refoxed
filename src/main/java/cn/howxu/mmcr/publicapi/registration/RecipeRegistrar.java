package cn.howxu.mmcr.publicapi.registration;

import cn.howxu.mmcr.publicapi.recipe.RecipeDraft;
import cn.howxu.mmcr.publicapi.recipe.RecipeSpec;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-provided static recipe registration window.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface RecipeRegistrar {
    void registerRecipe(RecipeSpec recipe);
    void registerRecipe(ResourceLocation id, Consumer<RecipeDraft> configuration);
    Map<ResourceLocation, RecipeSpec> recipes();
}
