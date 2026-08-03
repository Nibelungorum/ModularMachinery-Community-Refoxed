package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 多方块模板的 yaw 旋转:
 * <ul>
 *     <li>{@code rotateYCCW(pos)} = (z, y, -x)</li>
 *     <li>{@code rotateYCCWSouthUntil(pos, target)} 从 SOUTH 起步一直 YCCW 直到 target。</li>
 * </ul>
 * BuildCommand 与 StructureMatcher 共用,这样两者对同一 ctrlFacing 计算 world offset 一致。
 */
public final class BlockRotator {

    private BlockRotator() {}

    public static BlockPos rotateYCCW(BlockPos pos) {
        return new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
    }

    /**
     * 起点 SOUTH,循环 rotateYCCW 直到 facing 等于 target。
     */
    public static BlockPos rotateYCCWSouthUntil(BlockPos pos, Direction target) {
        Direction current = Direction.SOUTH;
        BlockPos r = pos;
        while (current != target) {
            current = current.getCounterClockWise();
            r = rotateYCCW(r);
        }
        return r;
    }
}
