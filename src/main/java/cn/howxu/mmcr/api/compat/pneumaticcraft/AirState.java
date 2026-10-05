package cn.howxu.mmcr.api.compat.pneumaticcraft;

import java.util.Objects;
import net.minecraft.core.BlockPos;

/** Immutable snapshot of the official handler's signed air store.
 * @author howxu <dev@howxu.cn>
 */
public record AirState(BlockPos position, int air, int volume, float dangerPressure, float criticalPressure) {
    public AirState {
        position = Objects.requireNonNull(position, "position").immutable();
        if (volume <= 0) throw new IllegalArgumentException("air volume must be positive");
    }

    public float pressure() {
        return (float) air / volume;
    }

    public long outputCapacity() {
        return Math.max(0L, (long) Math.floor((double) dangerPressure * volume) - (long) air);
    }
}
