package cn.howxu.mmcr.publicapi.recipe.modifier;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced numeric modifier. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface NumericModifierSpec extends ModifierSpec {
    String target(); ModifierScope scope(); double value(); ModifierOperation operation(); boolean affectsChance();
}
