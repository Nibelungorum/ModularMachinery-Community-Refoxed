package cn.howxu.mmcr.publicapi.recipe.modifier;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced linear smart-interface mapping. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface SmartModifierSpec {
    String interfaceType(); String target(); ModifierScope scope(); boolean affectsChance();
    float minValue(); float maxValue(); float atMin(); float atMax();
    ModifierOperation operation(); IoDirection io();
    float mappedValue(float value); NumericModifierSpec toModifier(float value);
}
