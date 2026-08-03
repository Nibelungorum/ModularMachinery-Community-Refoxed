package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mmce 标准: yaw YCCW 旋转 90° = (x, y, z) → (z, y, -x)。
 * 起点 NORTH,旋转到 ctrlFacing 后写盘;StructureMatcher 用相同 ctrlFacing 旋转检查。
 *
 * 单测两个互相操作的写法(BuildCommand 与 StructureMatcher)round-trip。
 */
class BlockRotatorTest {

    @Test
    void rotateYCCW_90_quarter_turn() {
        // mmce: rotateYCCW(pos) = (z, y, -x)
        BlockPos src = new BlockPos(1, 0, 0);
        assertThat(BlockRotator.rotateYCCW(src)).isEqualTo(new BlockPos(0, 0, -1));
    }

    @Test
    void rotateYCCW_four_times_returns_origin() {
        BlockPos src = new BlockPos(-1, 2, 1);
        BlockPos r0 = src;
        BlockPos r1 = BlockRotator.rotateYCCW(r0);
        BlockPos r2 = BlockRotator.rotateYCCW(r1);
        BlockPos r3 = BlockRotator.rotateYCCW(r2);
        BlockPos r4 = BlockRotator.rotateYCCW(r3);
        assertThat(r4).isEqualTo(src);
        // 每一步也都不等自身
        assertThat(r1).isNotEqualTo(src);
    }

    @Test
    void rotateYCCWNorthUntil_NORTH_is_identity() {
        assertThat(BlockRotator.rotateYCCWNorthUntil(new BlockPos(1, 0, 1), Direction.NORTH))
                .isEqualTo(new BlockPos(1, 0, 1));
    }

    @Test
    void rotateYCCWNorthUntil_EAST_rotates_90_CCW_thrice() {
        // 起点 NORTH 转 to EAST: 累计 3 步 YCCW。
        BlockPos src = new BlockPos(1, 0, 1);
        assertThat(BlockRotator.rotateYCCWNorthUntil(src, Direction.EAST))
                .isEqualTo(BlockRotator.rotateYCCW(
                        BlockRotator.rotateYCCW(
                                BlockRotator.rotateYCCW(src))));
    }

    @Test
    void rotateYCCWNorthUntil_step_count_matches() {
        // NORTH → WEST:1 步,(x,y,z)=(1,0,0)→(z,y,-x)=(0,0,-1)
        assertThat(BlockRotator.rotateYCCWNorthUntil(new BlockPos(1, 0, 0), Direction.WEST))
                .isEqualTo(new BlockPos(0, 0, -1));
        // NORTH → SOUTH:2 步
        assertThat(BlockRotator.rotateYCCWNorthUntil(new BlockPos(1, 0, 0), Direction.SOUTH))
                .isEqualTo(new BlockPos(-1, 0, 0));
        // NORTH → EAST:3 步 (-1,0,0)→(0,0,1)
        assertThat(BlockRotator.rotateYCCWNorthUntil(new BlockPos(1, 0, 0), Direction.EAST))
                .isEqualTo(new BlockPos(0, 0, 1));
    }
}
