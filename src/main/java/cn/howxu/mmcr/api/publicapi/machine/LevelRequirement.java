package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Immutable public machine level requirement.
 * @author howxu <dev@howxu.cn>
 */
public record LevelRequirement(Identifier typeId, Identifier levelId) {
    public LevelRequirement {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(levelId, "levelId");
    }
}
