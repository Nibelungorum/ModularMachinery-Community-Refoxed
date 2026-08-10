package cn.howxu.mmcr.api.machine.level;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Typed position in a machine structure that accepts any registered level.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LevelSlot(Identifier typeId) {
    public LevelSlot {
        Objects.requireNonNull(typeId, "typeId");
    }
}
