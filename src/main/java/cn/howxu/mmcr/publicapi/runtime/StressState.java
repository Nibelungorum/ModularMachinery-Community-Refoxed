package cn.howxu.mmcr.publicapi.runtime;

import java.util.Objects;
import net.minecraft.core.BlockPos;

/** Immutable state of one stress interface; network values are not additive across interfaces.
 * @author howxu <dev@howxu.cn>
 */
public record StressState(BlockPos position, double actualRpm, double theoreticalRpm, double generatedRpm,
        double baseContribution, double actualContribution, double networkCapacity, double networkStress,
        boolean connected, boolean overstressed) {
    public StressState {
        position = Objects.requireNonNull(position, "position").immutable();
    }
}
