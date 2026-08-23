package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .pattern("XXX", "XIX", "XXX")
                .pattern("XXX", "I I", "XXX")
                .pattern("XXX", "XCX", "XXX")
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

    @Test void unicode_symbol_survives_build_and_structure_matching() {
        var casing = new BlockPredicate.OfBlock(Blocks.STONE);
        var controller = new BlockPredicate.OfBlock(Blocks.DIRT);
        var pattern = BlockArray.builder()
                .pattern("中C")
                .set('中', casing)
                .set('C', controller)
                .build();
        BlockPos controllerPos = new BlockPos(5, 5, 5);

        assertThat(pattern.symbolsByPosition().values()).contains('中');
        assertThat(StructureMatcher.matches(pattern, LevelStub.create(Map.of(
                controllerPos, Blocks.DIRT,
                controllerPos.offset(-1, 0, 0), Blocks.STONE)),
                controllerPos, Direction.SOUTH)).isTrue();
    }

    @Test void builder_accepts_arbitrary_width_height_and_depth() {
        var x = new BlockPredicate.OfBlock(Blocks.STONE);
        var c = new BlockPredicate.OfBlock(Blocks.DIRT);

        var arr = BlockArray.builder()
                .pattern("XXXX", "X  X")
                .pattern("X  X", "XC X")
                .set('X', x)
                .set('C', c)
                .build();

        assertThat(arr.get(BlockPos.ZERO)).isEqualTo(c);
        assertThat(arr.get(new BlockPos(-1, -1, -1))).isEqualTo(x);
        assertThat(arr.get(new BlockPos(2, -1, -1))).isEqualTo(x);
        assertThat(arr.get(new BlockPos(-1, 0, 0))).isEqualTo(x);
        assertThat(arr.get(new BlockPos(2, 0, 0))).isEqualTo(x);
        assertThat(arr.get(new BlockPos(0, 0, -1))).isNull();
    }

    @Test void builder_allows_explicit_controller_symbol() {
        var casing = new BlockPredicate.OfBlock(Blocks.STONE);
        var controller = new BlockPredicate.OfBlock(Blocks.DIRT);

        var arr = BlockArray.builder()
                .pattern("AAA", "XBX", "XBX", "XDX")
                .pattern("AAA", "B B", "B B", "DED")
                .pattern("AAA", "XBX", "XBX", "XDX")
                .set('A', casing)
                .set('B', casing)
                .set('D', casing)
                .set('E', controller)
                .controller('E')
                .build();

        assertThat(arr.get(BlockPos.ZERO)).isEqualTo(controller);
        assertThat(arr.get(new BlockPos(0, 0, 0))).isEqualTo(controller);
        assertThat(arr.get(new BlockPos(0, -3, -1))).isEqualTo(casing);
        assertThat(arr.get(new BlockPos(0, -2, 0))).isNull();
    }

    @Test void builder_rejects_pattern_slices_with_inconsistent_row_widths() {
        assertThatThrownBy(() -> BlockArray.builder()
                .pattern("XX", "XXX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same width");
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

    @Test void block_array_cache_rotates_and_reuses_pattern_for_facing() {
        BlockArrayCache.clearForTesting();
        var stone = new BlockPredicate.OfBlock(Blocks.STONE);
        var arr = new BlockArray(Map.of(new BlockPos(1, 0, 0), stone));

        BlockArray east = BlockArrayCache.get(arr, Direction.EAST);
        BlockArray eastAgain = BlockArrayCache.get(arr, Direction.EAST);

        assertThat(east.get(new BlockPos(0, 0, -1))).isSameAs(stone);
        assertThat(eastAgain).isSameAs(east);
    }

    @Test void block_array_and_rotation_cache_preserve_declaration_order() {
        BlockArrayCache.clearForTesting();
        var first = new BlockPos(0, 0, 1);
        var second = new BlockPos(-1, 0, 0);
        var third = new BlockPos(0, 1, 0);
        var stone = new BlockPredicate.OfBlock(Blocks.STONE);
        Map<BlockPos, BlockPredicate> ordered = new LinkedHashMap<>();
        ordered.put(first, stone);
        ordered.put(second, stone);
        ordered.put(third, stone);

        BlockArray array = new BlockArray(ordered);
        BlockArray east = BlockArrayCache.get(array, Direction.EAST);

        assertThat(array.pattern().keySet()).containsExactly(first, second, third);
        assertThat(east.pattern().keySet()).containsExactly(
                BlockRotator.rotateSouthTo(first, Direction.EAST),
                BlockRotator.rotateSouthTo(second, Direction.EAST),
                BlockRotator.rotateSouthTo(third, Direction.EAST));
    }

    @Test void block_array_cache_rotates_block_state_predicates() {
        var southState = Blocks.DISPENSER.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.SOUTH);
        var array = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlockState(southState)));

        for (Direction facing : List.of(Direction.EAST, Direction.NORTH, Direction.WEST)) {
            BlockArray rotated = BlockArrayCache.get(array, facing);
            Rotation rotation = switch (facing) {
                case EAST -> Rotation.COUNTERCLOCKWISE_90;
                case NORTH -> Rotation.CLOCKWISE_180;
                case WEST -> Rotation.CLOCKWISE_90;
                default -> throw new AssertionError(facing);
            };

            assertThat(((BlockPredicate.OfBlockState) rotated.get(BlockPos.ZERO)).state())
                    .isEqualTo(southState.rotate(rotation));
        }
    }

    @Test void block_array_cache_recursively_rotates_any_of_state_predicates() {
        var southState = Blocks.DISPENSER.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.SOUTH);
        var plainBlock = new BlockPredicate.OfBlock(Blocks.STONE);
        var array = new BlockArray(Map.of(BlockPos.ZERO,
                new BlockPredicate.AnyOf(List.of(new BlockPredicate.OfBlockState(southState), plainBlock))));

        BlockPredicate.AnyOf rotated = (BlockPredicate.AnyOf) BlockArrayCache.get(array, Direction.EAST)
                .get(BlockPos.ZERO);

        assertThat(((BlockPredicate.OfBlockState) rotated.children().getFirst()).state())
                .isEqualTo(southState.rotate(Rotation.COUNTERCLOCKWISE_90));
        assertThat(rotated.children().get(1)).isSameAs(plainBlock);
    }

    @Test void block_array_cache_keeps_vertical_state_direction_vertical() {
        var upState = Blocks.DISPENSER.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP);
        var array = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlockState(upState)));

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPredicate.OfBlockState rotated = (BlockPredicate.OfBlockState) BlockArrayCache.get(array, facing)
                    .get(BlockPos.ZERO);
            assertThat(rotated.state().getValue(DirectionalBlock.FACING)).isEqualTo(Direction.UP);
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

    @Test void tagged_stores_multiple_tags_at_position() {
        var stone = new BlockPredicate.OfBlock(Blocks.STONE);
        var arr = new BlockArray(Map.of(new BlockPos(1, 0, 0), stone))
                .tagged(new BlockPos(1, 0, 0), "input_a", "fast");

        assertThat(arr.tagsAt(new BlockPos(1, 0, 0))).containsExactly("input_a", "fast");
        assertThat(arr.tagsAt(new BlockPos(0, 0, 0))).isEmpty();
    }

    @Test void cache_preserves_tags_for_non_zero_relative_position() {
        BlockArrayCache.clearForTesting();
        var stone = new BlockPredicate.OfBlock(Blocks.STONE);
        BlockArray arr = new BlockArray(Map.of(new BlockPos(1, 0, 0), stone))
                .tagged(new BlockPos(1, 0, 0), "input_a");

        BlockArray rotated = BlockArrayCache.get(arr, Direction.EAST);
        assertThat(rotated.tagsAt(new BlockPos(0, 0, -1))).contains("input_a");
    }

    @Test
    void vertical_roll_uses_same_coordinate_transform_as_block_array() {
        Identifier id = Identifier.fromNamespaceAndPath("mmcr", "compiled_vertical_replacement");
        BlockPos rawPos = new BlockPos(1, 0, 0);
        var replacement = new SingleBlockModifierReplacement("speed", new BlockPredicate.Any(), List.of(), ItemStack.EMPTY);
        var machine = new DynamicMachine(id, "Compiled Vertical Replacement",
                new BlockArray(Map.of(rawPos, new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(id), PortRequirementSpec.none(), List.of(),
                Map.of(rawPos, List.of(replacement)));

        for (Direction facing : List.of(Direction.UP, Direction.DOWN)) {
            for (Direction rollFacing : Direction.Plane.HORIZONTAL) {
                BlockPos expected = BlockRotator.rotateSouthTo(rawPos, facing, rollFacing);
                assertThat(machine.rotatedModifierReplacements(facing, rollFacing)).containsKey(expected);
            }
        }
    }
}
