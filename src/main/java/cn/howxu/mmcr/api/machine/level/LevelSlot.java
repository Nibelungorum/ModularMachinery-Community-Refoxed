package cn.howxu.mmcr.api.machine.level;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Marks a structure pattern position that accepts any level of a type.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LevelSlot(Identifier typeId) {
    public LevelSlot {
        Objects.requireNonNull(typeId, "typeId");
    }
}
