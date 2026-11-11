package cn.howxu.mmcr.publicapi.recipe.modifier;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced function mapping from a smart-interface value to a modifier number. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface SmartModifierSpec {
    String interfaceType(); String target(); ModifierScope scope(); boolean affectsChance();
    ModifierOperation operation(); IoDirection io();
    double mappedValue(float value); NumericModifierSpec toModifier(float value);
}
