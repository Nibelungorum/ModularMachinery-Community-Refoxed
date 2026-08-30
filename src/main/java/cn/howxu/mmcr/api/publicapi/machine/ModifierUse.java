package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Immutable declaration of a modifier use and its replacement predicate.
 * @author howxu <dev@howxu.cn>
 */
public record ModifierUse(Identifier modifierId, BlockPredicate replacement) {
    public ModifierUse {
        Objects.requireNonNull(modifierId, "modifierId");
        Objects.requireNonNull(replacement, "replacement");
    }

    public static ModifierUse of(Identifier modifierId, BlockPredicate replacement) {
        return new ModifierUse(modifierId, replacement);
    }
}
