package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

import java.util.List;

/** Immutable public definition of a registered structure or recipe modifier.
 * @author howxu <dev@howxu.cn>
 */
public record ModifierDefinition(List<RecipeModifier> modifiers) {
    public ModifierDefinition {
        modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
    }
}
