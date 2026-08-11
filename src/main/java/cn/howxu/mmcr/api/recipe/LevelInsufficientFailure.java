package cn.howxu.mmcr.api.recipe;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A recipe's required machine level is unavailable or too low.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LevelInsufficientFailure(Identifier typeId, Identifier requiredLevelId,
                                       @Nullable Identifier actualLevelId) {
    public LevelInsufficientFailure {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(requiredLevelId, "requiredLevelId");
    }
}
