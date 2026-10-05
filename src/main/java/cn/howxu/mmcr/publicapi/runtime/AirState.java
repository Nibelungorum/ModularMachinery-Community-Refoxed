package cn.howxu.mmcr.publicapi.runtime;

import java.util.Objects;
import net.minecraft.core.BlockPos;

/** Immutable signed state of one air interface; pressure is not additive across interfaces.
 * @author howxu <dev@howxu.cn>
 */
public record AirState(BlockPos position, int air, int volume, float dangerPressure, float criticalPressure) {
    public AirState {
        position = Objects.requireNonNull(position, "position").immutable();
        if (volume <= 0) throw new IllegalArgumentException("volume must be positive");
    }

    public float pressure() { return (float) air / volume; }

    public long outputCapacity() { return Math.max(0L, (long) Math.floor((double) dangerPressure * volume) - air); }
}
