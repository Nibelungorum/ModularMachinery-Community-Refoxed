package cn.howxu.mmcr.api.machine.level;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Describes an unresolved or inconsistent level slot.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LevelMismatch(ResourceLocation typeId, MachineLevel expected, MachineLevel actual, BlockPos worldPos) {
    public LevelMismatch {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(worldPos, "worldPos");
    }
}
