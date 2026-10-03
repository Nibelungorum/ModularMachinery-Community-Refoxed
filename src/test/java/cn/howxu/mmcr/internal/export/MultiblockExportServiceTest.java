package cn.howxu.mmcr.internal.export;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static cn.howxu.mmcr.api.machine.definition.BlockPredicate.state;

/** Verifies exported states survive template normalization and runtime rotation.
 * @author howxu <dev@howxu.cn>
 */
class MultiblockExportServiceTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void horizontal_exports_round_trip_stair_facing_and_shape() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Direction stairFacing : Direction.Plane.HORIZONTAL) {
                for (StairsShape shape : StairsShape.values()) {
                    BlockState state = Blocks.OAK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, stairFacing)
                            .setValue(StairBlock.SHAPE, shape)
                            .setValue(StairBlock.HALF, Half.TOP)
                            .setValue(StairBlock.WATERLOGGED, true);
                    assertRoundTrip(state, facing, Direction.SOUTH);
                }
            }
        }
    }

    @Test
    void vertical_exports_round_trip_direction_and_axis_for_every_roll() {
        for (Direction facing : List.of(Direction.UP, Direction.DOWN)) {
            for (Direction roll : Direction.Plane.HORIZONTAL) {
                for (Direction direction : Direction.values()) {
                    assertRoundTrip(Blocks.END_ROD.defaultBlockState()
                            .setValue(BlockStateProperties.FACING, direction), facing, roll);
                }
                for (Direction.Axis axis : Direction.Axis.values()) {
                    assertRoundTrip(Blocks.OAK_LOG.defaultBlockState()
                            .setValue(BlockStateProperties.AXIS, axis), facing, roll);
                }
            }
        }
    }

    @Test
    void north_controller_exports_north_stairs_as_south_in_both_formats() {
        BlockState state = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        var entries = snapshot(state, new BlockPos(1, 0, 0));
        assertThat(exportedState(MultiblockExportService.renderKubeJS(entries, Direction.NORTH))
                .getValue(StairBlock.FACING)).isEqualTo(Direction.SOUTH);
        assertThat(MultiblockExportService.renderJava(entries, Direction.NORTH))
                .contains(".getValue(\"south\").orElseThrow()");
    }

    @Test
    void vertical_stairs_round_trip_when_representable_and_reject_unrepresentable_states() {
        for (Direction facing : List.of(Direction.UP, Direction.DOWN)) {
            for (Direction roll : Direction.Plane.HORIZONTAL) {
                var representable = new ArrayList<BlockState>();
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockState state = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, direction);
                    var template = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlockState(state)));
                    var rotated = (BlockPredicate.OfBlockState) BlockArrayCache.get(template, facing, roll).get(BlockPos.ZERO);
                    representable.add(rotated.state());
                }
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockState state = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, direction);
                    if (representable.contains(state)) {
                        assertRoundTrip(state, facing, roll);
                    } else {
                        var entries = snapshot(state, new BlockPos(1, 0, 0));
                        assertThatThrownBy(() -> MultiblockExportService.renderKubeJS(entries, facing, roll))
                                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Cannot normalize");
                        assertThatThrownBy(() -> MultiblockExportService.renderJava(entries, facing, roll))
                                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Cannot normalize");
                    }
                }
            }
        }
    }

    @Test
    void vertical_controller_roll_does_not_become_an_exported_state_condition() {
        BlockState controller = TestBootstrap.newController().getBlockState();
        for (Direction facing : List.of(Direction.UP, Direction.DOWN)) {
            for (Direction roll : Direction.Plane.HORIZONTAL) {
                BlockState state = controller.setValue(MachineControllerBlock.FACING, facing)
                        .setValue(MachineControllerBlock.ROLL_FACING, roll);
                var entries = List.of(new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, state, false, true),
                        new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState(), false));
                assertThat(MultiblockExportService.renderKubeJS(entries, facing, roll))
                        .contains(".controller('C')").doesNotContain("roll_facing");
                assertThat(MultiblockExportService.renderJava(entries, facing, roll))
                        .contains(".controller('C')").doesNotContain("roll_facing");
            }
        }
    }

    private static void assertRoundTrip(BlockState state, Direction facing, Direction roll) {
        BlockPos offset = BlockRotator.rotateSouthTo(new BlockPos(1, 1, 1), facing, roll);
        BlockState templateState = exportedState(MultiblockExportService.renderKubeJS(snapshot(state, offset), facing, roll));
        BlockPos templateOffset = MultiblockExportService.normalizeOffset(offset, facing, roll);
        BlockArray template = new BlockArray(Map.of(templateOffset, new BlockPredicate.OfBlockState(templateState)));
        var restored = (BlockPredicate.OfBlockState) BlockArrayCache.get(template, facing, roll).get(offset);
        assertThat(restored.state()).as("%s / %s / %s", facing, roll, state).isEqualTo(state);
    }

    private static List<MultiblockExportService.SnapshotEntry> snapshot(BlockState state, BlockPos offset) {
        return List.of(new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), false, true),
                new MultiblockExportService.SnapshotEntry(offset, state, false));
    }

    private static BlockState exportedState(String script) {
        var matcher = Pattern.compile("api\\.state\\('([^']+)'\\)").matcher(script);
        assertThat(matcher.find()).isTrue();
        return state(matcher.group(1)).blockState().orElseThrow();
    }
}
