package cn.howxu.mmcr.publicapi.recipe.modifier;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced raw recipe adjustment. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface RecipeAdjustmentSpec {
    String target(); IoDirection io(); float value(); ModifierOperation operation(); boolean affectsChance();
    RecipeAdjustmentSpec multiply(float value); RecipeAdjustmentSpec add(float value);
}
