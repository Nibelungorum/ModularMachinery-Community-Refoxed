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

    @Test
    void rotateSouthTo_preserves_existing_horizontal_behavior() {
        BlockPos left = new BlockPos(-1, 0, 0);
        BlockPos front = new BlockPos(0, 0, 1);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertThat(BlockRotator.rotateSouthTo(left, facing))
                    .isEqualTo(BlockRotator.rotateYCCWSouthUntil(left, facing));
            assertThat(BlockRotator.rotateSouthTo(front, facing))
                    .isEqualTo(BlockRotator.rotateYCCWSouthUntil(front, facing));
        }
    }

    @Test
    void rotateSouthTo_uses_front_and_roll_basis_for_up_and_down_controller() {
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 0, 1), Direction.UP))
                .isEqualTo(new BlockPos(0, 1, 0));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 1, 0), Direction.UP))
                .isEqualTo(new BlockPos(0, 0, 1));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.UP))
                .isEqualTo(new BlockPos(-1, 0, 0));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, -1, 1), Direction.UP))
                .isEqualTo(new BlockPos(0, 1, -1));

        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 0, 1), Direction.DOWN))
                .isEqualTo(new BlockPos(0, -1, 0));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 1, 0), Direction.DOWN))
                .isEqualTo(new BlockPos(0, 0, 1));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.DOWN))
                .isEqualTo(new BlockPos(1, 0, 0));
    }

    @Test
    void rotateSouthTo_uses_roll_facing_for_up_controller() {
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.UP, Direction.SOUTH))
                .isEqualTo(new BlockPos(-1, 0, 0));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.UP, Direction.EAST))
                .isEqualTo(new BlockPos(0, 0, 1));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.UP, Direction.NORTH))
                .isEqualTo(new BlockPos(1, 0, 0));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.UP, Direction.WEST))
                .isEqualTo(new BlockPos(0, 0, -1));
    }

    @Test
    void normalizeFromFace_reverses_rotateSouthTo_for_all_faces() {
        BlockPos[] samples = {
                new BlockPos(0, 0, 0),
                new BlockPos(1, 0, 0),
                new BlockPos(0, 1, 0),
                new BlockPos(0, 0, 1),
                new BlockPos(-2, 3, 4)
        };

        for (Direction facing : Direction.values()) {
            for (BlockPos sample : samples) {
                BlockPos rotated = BlockRotator.rotateSouthTo(sample, facing);
                assertThat(BlockRotator.normalizeFromFace(rotated, facing))
                        .as("face=%s sample=%s", facing, sample)
                        .isEqualTo(sample);
            }
        }
    }

    @Test
    void rotateSouthTo_uses_front_and_roll_basis_for_vertical_controllers() {
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 0, 1), Direction.UP, Direction.SOUTH))
                .isEqualTo(new BlockPos(0, 1, 0));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 1, 0), Direction.UP, Direction.SOUTH))
                .isEqualTo(new BlockPos(0, 0, 1));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.UP, Direction.SOUTH))
                .isEqualTo(new BlockPos(-1, 0, 0));

        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 0, 1), Direction.DOWN, Direction.SOUTH))
                .isEqualTo(new BlockPos(0, -1, 0));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 1, 0), Direction.DOWN, Direction.SOUTH))
                .isEqualTo(new BlockPos(0, 0, 1));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.DOWN, Direction.SOUTH))
                .isEqualTo(new BlockPos(1, 0, 0));
    }

    @Test
    void normalizeFromFaceWithRoll_reverses_roll_aware_rotation_for_all_faces() {
        BlockPos[] samples = {
                new BlockPos(0, 0, 0),
                new BlockPos(1, 0, 0),
                new BlockPos(0, 1, 0),
                new BlockPos(0, 0, 1),
                new BlockPos(-2, 3, 4)
        };

        for (Direction facing : Direction.values()) {
            for (Direction roll : Direction.Plane.HORIZONTAL) {
                for (BlockPos sample : samples) {
                    Direction normalizedRoll = BlockRotator.normalizedRoll(facing, roll);
                    BlockPos rotated = BlockRotator.rotateSouthTo(sample, facing, normalizedRoll);
                    assertThat(BlockRotator.normalizeFromFace(rotated, facing, normalizedRoll))
                            .as("face=%s roll=%s sample=%s", facing, normalizedRoll, sample)
                            .isEqualTo(sample);
                }
            }
        }
    }
}
