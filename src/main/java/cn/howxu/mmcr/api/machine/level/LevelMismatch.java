package cn.howxu.mmcr.api.machine.level;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Describes two different levels found in slots of the same type.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LevelMismatch(Identifier typeId, MachineLevel expected, MachineLevel actual, BlockPos worldPos) {
    public LevelMismatch {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(worldPos, "worldPos");
    }
}
