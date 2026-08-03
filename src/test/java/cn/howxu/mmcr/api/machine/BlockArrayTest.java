package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class BlockArrayTest {

    @BeforeAll static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test void empty_has_zero_dims() {
        var arr = new BlockArray(Map.of());
        assertThat(arr.width()).isZero();
        assertThat(arr.height()).isZero();
        assertThat(arr.length()).isZero();
        assertThat(arr.isEmpty()).isTrue();
    }

    @Test void single_block_has_1x1x1_dims() {
        var arr = new BlockArray(Map.of(
                new BlockPos(0, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE)));
        assertThat(arr.width()).isEqualTo(1);
        assertThat(arr.height()).isEqualTo(1);
        assertThat(arr.length()).isEqualTo(1);
        assertThat(arr.isEmpty()).isFalse();
    }

    @Test void multi_block_dims_correct() {
        Map<BlockPos, BlockPredicate> m = new HashMap<>();
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 2; y++)
                for (int z = 0; z < 4; z++)
                    m.put(new BlockPos(x, y, z), new BlockPredicate.OfBlock(Blocks.STONE));
        var arr = new BlockArray(m);
        assertThat(arr.width()).isEqualTo(3);
        assertThat(arr.height()).isEqualTo(2);
        assertThat(arr.length()).isEqualTo(4);
    }

    @Test void offset_positions_use_inclusive_extent() {
        var arr = new BlockArray(Map.of(
                new BlockPos(-2, 4, 10), new BlockPredicate.Any(),
                new BlockPos(1, 6, 14), new BlockPredicate.Any()));
        assertThat(arr.width()).isEqualTo(4);
        assertThat(arr.height()).isEqualTo(3);
        assertThat(arr.length()).isEqualTo(5);
    }

    @Test void get_returns_predicate_at_pos() {
        var pred = new BlockPredicate.OfBlock(Blocks.STONE);
        var arr = new BlockArray(Map.of(new BlockPos(0, 0, 0), pred));
        assertThat(arr.get(new BlockPos(0, 0, 0))).isEqualTo(pred);
        assertThat(arr.get(new BlockPos(1, 0, 0))).isNull();
    }

    @Test void builder_groups_input_as_y_layers_from_arr_columns() {
        var x = new BlockPredicate.OfBlock(Blocks.STONE);
        var i = new BlockPredicate.OfBlock(Blocks.OAK_PLANKS);
        var c = new BlockPredicate.OfBlock(Blocks.DIRT);
        var arr = BlockArray.builder()
                .pattern(
                        "XXX", "XIX", "XXX",
                        "XXX", "I I", "XXX",
                        "XXX", "XCX", "XXX")
                .set('X', x)
                .set('I', i)
                .set('C', c)
                .build();

        assertThat(arr.get(BlockPos.ZERO)).isEqualTo(c);
        assertThat(arr.get(new BlockPos(-1, -1, -2))).isEqualTo(x);
        assertThat(arr.get(new BlockPos(0, -1, -1))).isEqualTo(x);
        assertThat(arr.get(new BlockPos(1, -1, 0))).isEqualTo(x);
        assertThat(arr.get(new BlockPos(0, 0, -2))).isEqualTo(i);
        assertThat(arr.get(new BlockPos(-1, 0, -1))).isEqualTo(i);
        assertThat(arr.get(new BlockPos(1, 0, -1))).isEqualTo(i);
        assertThat(arr.get(new BlockPos(0, 0, -1))).isNull();
    }

    /**
     * pattern 默认按 SOUTH 摆;controller FACING = SOUTH 时 pattern 原貌对应世界坐标。
     */
    @Test void matcher_matches_perfect_structure() {
        Map<BlockPos, BlockPredicate> m = new HashMap<>();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                m.put(new BlockPos(x, 0, z), new BlockPredicate.OfBlock(Blocks.STONE));
        var arr = new BlockArray(m);

        var ctrl = new BlockPos(2, 2, 2);
        Map<BlockPos, Block> blocks = new HashMap<>();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                blocks.put(ctrl.offset(x, 0, z), Blocks.STONE);
        assertThat(StructureMatcher.matches(arr, LevelStub.create(blocks), ctrl, Direction.SOUTH)).isTrue();
    }

    @Test void matcher_rejects_wrong_block() {
        var arr = new BlockArray(Map.of(
                new BlockPos(0, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE),
                new BlockPos(0, 0, 1), new BlockPredicate.OfBlock(Blocks.DIRT)));
        var level = LevelStub.create(Map.of(
                new BlockPos(0, 0, 0), Blocks.STONE,
                new BlockPos(0, 0, 1), Blocks.COBBLESTONE));

        assertThat(StructureMatcher.matches(arr, level, BlockPos.ZERO, Direction.NORTH)).isFalse();
    }

    @Test void matcher_rotates_structure_positions_from_controller_facing() {
        var arr = new BlockArray(Map.of(
                new BlockPos(-1, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE),
                new BlockPos(0, 0, 1), new BlockPredicate.OfBlock(Blocks.DIRT)));
        var ctrl = new BlockPos(10, 2, 10);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var level = LevelStub.create(Map.of(
                    ctrl.offset(BlockRotator.rotateYCCWSouthUntil(new BlockPos(-1, 0, 0), facing)), Blocks.STONE,
                    ctrl.offset(BlockRotator.rotateYCCWSouthUntil(new BlockPos(0, 0, 1), facing)), Blocks.DIRT));
            assertThat(StructureMatcher.matches(arr, level, ctrl, facing)).isTrue();
        }
    }

    @Test void block_rotator_treats_raw_multiblock_template_as_south_facing() {
        BlockPos left = new BlockPos(-1, 0, 0);
        BlockPos front = new BlockPos(0, 0, 1);

        assertThat(BlockRotator.rotateYCCWSouthUntil(left, Direction.SOUTH)).isEqualTo(new BlockPos(-1, 0, 0));
        assertThat(BlockRotator.rotateYCCWSouthUntil(front, Direction.SOUTH)).isEqualTo(new BlockPos(0, 0, 1));
        assertThat(BlockRotator.rotateYCCWSouthUntil(left, Direction.NORTH)).isEqualTo(new BlockPos(1, 0, 0));
        assertThat(BlockRotator.rotateYCCWSouthUntil(front, Direction.NORTH)).isEqualTo(new BlockPos(0, 0, -1));
        assertThat(BlockRotator.rotateYCCWSouthUntil(left, Direction.EAST)).isEqualTo(new BlockPos(0, 0, 1));
        assertThat(BlockRotator.rotateYCCWSouthUntil(front, Direction.EAST)).isEqualTo(new BlockPos(1, 0, 0));
        assertThat(BlockRotator.rotateYCCWSouthUntil(left, Direction.WEST)).isEqualTo(new BlockPos(0, 0, -1));
        assertThat(BlockRotator.rotateYCCWSouthUntil(front, Direction.WEST)).isEqualTo(new BlockPos(-1, 0, 0));
    }
}
