package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compaction guard: patterns shaped like real multiblock shells must reach {@link RowRlePositions} and
 * not silently fall back to the packed representation, which costs roughly ninety bytes per cell.
 *
 * <p>Lives in this package so it can assert on the concrete table type.
 */
class RowRleCompactionTest {

    /** A hollow 3x3x3 shell drawn as rows, like a casing wall with an empty interior. */
    private static BlockArray hollowShell() {
        BlockArray.Builder builder = BlockArray.builder();
        builder.pattern("XXX", "X X", "XXX");
        builder.pattern("X X", "   ", "X X");
        builder.pattern("XXX", "X X", "XXX");
        builder.set('X', new BlockPredicate.Any());
        builder.noController();
        return builder.build();
    }

    @Test
    void shaped_like_a_shell_is_compacted() {
        BlockArray shell = hollowShell();

        assertThat(shell.pattern()).isInstanceOf(RowRlePositions.class);
        assertThat(shell.pattern()).hasSize(20);
        // Corners are casing; the interior cell at the pattern origin is empty.
        assertThat(shell.get(new BlockPos(1, 1, 1))).isInstanceOf(BlockPredicate.Any.class);
        assertThat(shell.get(new BlockPos(0, 0, 0))).isNull();
    }

    @Test
    void spaced_rows_with_gaps_are_compacted() {
        // Rows of isolated cells separated by spaces: the worst case for run coalescing, and the shape a
        // sparse ritual wall takes.
        BlockArray.Builder builder = BlockArray.builder();
        builder.pattern("X X X X X", " X X X X ", "X X X X X");
        builder.set('X', new BlockPredicate.Any());
        builder.noController();
        BlockArray pattern = builder.build();

        assertThat(pattern.pattern()).isInstanceOf(RowRlePositions.class);
        // 5 + 4 + 5 cells: the middle row has one fewer because it starts and ends with a gap.
        assertThat(pattern.pattern()).hasSize(14);
    }

    @Test
    void row_major_map_is_compacted_with_identity_preserved() {
        BlockPredicate casing = new BlockPredicate.Any();
        BlockPredicate controller = new BlockPredicate.Air();
        Map<BlockPos, BlockPredicate> source = new LinkedHashMap<>();
        for (int y = 0; y <= 1; y++) {
            for (int x = -3; x <= 3; x++) {
                source.put(new BlockPos(x, y, 0), y == 1 && x == 0 ? controller : casing);
            }
        }
        BlockArray pattern = new BlockArray(source);

        assertThat(pattern.pattern()).isInstanceOf(RowRlePositions.class);
        assertThat(pattern.get(new BlockPos(0, 0, 0))).isSameAs(casing);
        assertThat(pattern.get(new BlockPos(0, 1, 0))).isSameAs(controller);
        assertThat(pattern.pattern().size()).isEqualTo(14);
    }

    @Test
    void out_of_order_rows_fall_back_to_the_packed_table() {
        Map<BlockPos, BlockPredicate> source = new LinkedHashMap<>();
        source.put(new BlockPos(0, 1, 0), new BlockPredicate.Any());
        source.put(new BlockPos(0, 0, 0), new BlockPredicate.Any());
        BlockArray pattern = new BlockArray(source);

        // Rows arriving out of order keep the packed representation; correctness beats density there.
        assertThat(pattern.pattern()).isInstanceOf(PackedAirPositions.class);
        assertThat(pattern.pattern().size()).isEqualTo(2);
    }
}
