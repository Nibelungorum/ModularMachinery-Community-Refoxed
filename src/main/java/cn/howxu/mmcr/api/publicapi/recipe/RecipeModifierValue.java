package cn.howxu.mmcr.api.publicapi.recipe;

import java.util.Objects;

/** Immutable public recipe modifier value mapped by the public recipe adapter.
 * @author howxu <dev@howxu.cn>
 */
public record RecipeModifierValue(String target, RecipeIo io, float value, Operation operation, boolean affectsChance) {
    public enum Operation { ADD, MULTIPLY }

    public RecipeModifierValue {
        target = target == null ? "" : target;
        Objects.requireNonNull(io, "io");
        Objects.requireNonNull(operation, "operation");
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Modifier value must be finite");
    }
}
