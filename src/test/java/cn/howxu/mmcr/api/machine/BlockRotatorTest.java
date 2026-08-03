package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mmce 标准: yaw YCCW 旋转 90° = (x, y, z) → (z, y, -x)。
 * 起点 SOUTH,旋转到 ctrlFacing 后写盘;StructureMatcher 用相同 ctrlFacing 旋转检查。
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
    void rotateYCCWSouthUntil_SOUTH_is_identity() {
        assertThat(BlockRotator.rotateYCCWSouthUntil(new BlockPos(1, 0, 1), Direction.SOUTH))
                .isEqualTo(new BlockPos(1, 0, 1));
    }

    @Test
    void rotateYCCWSouthUntil_WEST_rotates_90_CCW_thrice() {
        // 起点 SOUTH 转 to WEST: 累计 3 步 YCCW。
        BlockPos src = new BlockPos(1, 0, 1);
        assertThat(BlockRotator.rotateYCCWSouthUntil(src, Direction.WEST))
                .isEqualTo(BlockRotator.rotateYCCW(
                        BlockRotator.rotateYCCW(
                                BlockRotator.rotateYCCW(src))));
    }

    @Test
    void rotateYCCWSouthUntil_step_count_matches() {
        // SOUTH → EAST:1 步,(x,y,z)=(1,0,0)→(z,y,-x)=(0,0,-1)
        assertThat(BlockRotator.rotateYCCWSouthUntil(new BlockPos(1, 0, 0), Direction.EAST))
                .isEqualTo(new BlockPos(0, 0, -1));
        // SOUTH → NORTH:2 步
        assertThat(BlockRotator.rotateYCCWSouthUntil(new BlockPos(1, 0, 0), Direction.NORTH))
                .isEqualTo(new BlockPos(-1, 0, 0));
        // SOUTH → WEST:3 步 (-1,0,0)→(0,0,1)
        assertThat(BlockRotator.rotateYCCWSouthUntil(new BlockPos(1, 0, 0), Direction.WEST))
                .isEqualTo(new BlockPos(0, 0, 1));
    }
}
