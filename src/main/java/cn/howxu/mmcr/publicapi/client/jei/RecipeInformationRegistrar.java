package cn.howxu.mmcr.publicapi.client.jei;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-provided localized information registration window.
 * Component arguments are deep-snapshotted on registration and on reads; immutable scalar arguments retain their values.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface RecipeInformationRegistrar {
    void registerRecipePool(ResourceLocation id, String translationKey, Object... arguments);
    void registerRecipe(ResourceLocation id, String translationKey, Object... arguments);
    List<RecipeInformation> entries();
}
