package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

import java.util.List;

/** Immutable public definition of a registered structure or recipe modifier.
 * @author howxu <dev@howxu.cn>
 */
public record ModifierDefinition(List<RecipeModifier> modifiers) {
    public static ModifierDefinition of(String target, String ioTarget, float modifier, String operation,
            boolean affectsChance) {
        return new ModifierDefinition(List.of(new RecipeModifier(target,
                RecipeModifier.IOType.valueOf(ioTarget.toUpperCase(java.util.Locale.ROOT)), modifier,
                RecipeModifier.Operation.valueOf(operation.toUpperCase(java.util.Locale.ROOT)), affectsChance)));
    }

    public ModifierDefinition {
        modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
    }
}
