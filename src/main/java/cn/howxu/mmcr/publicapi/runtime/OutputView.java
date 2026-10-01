package cn.howxu.mmcr.publicapi.runtime;
import cn.howxu.mmcr.publicapi.recipe.modifier.RecipeAdjustmentSpec;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced runtime output. Stack getters are copies; submit replacements through setOutputs.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface OutputView {
    ResourceLocation kindId(); String serializedId(); float chance(); long amount();
    OutputView copy(); OutputView withChance(float chance); OutputView applyModifiers(List<RecipeAdjustmentSpec> modifiers);
}
