package cn.howxu.mmcr.api.compat.create;

import net.minecraft.core.BlockPos;

/** Read-only local rotation and network load snapshot.
 * @author howxu <dev@howxu.cn>
 */
public record StressState(BlockPos position, double actualRpm, double theoreticalRpm, double generatedRpm,
                          double baseContribution, double actualContribution, double networkCapacity,
                          double networkStress, boolean connected, boolean overstressed) {
}
