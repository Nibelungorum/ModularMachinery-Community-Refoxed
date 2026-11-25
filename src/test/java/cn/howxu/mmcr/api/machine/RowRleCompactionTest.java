package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RowRleCompactionTest {
    @ParameterizedTest
    @EnumSource(Direction.class)
    void packingPreservesEncounterOrderCoordinatesAndGaps(Direction step) {
        BlockPos start = new BlockPos(1, 0, 0);
        BlockPredicate air = new BlockPredicate.Air();
        Map<BlockPos, BlockPredicate> input = new LinkedHashMap<>();
        input.put(start, air);
        input.put(start.relative(step), air);
        input.put(start.east(3), new BlockPredicate.Any());
        Map<BlockPos, Character> glyphs = new LinkedHashMap<>();
        input.forEach((pos, predicate) -> glyphs.put(pos, predicate == air ? '空' : 'X'));

        BlockArray pattern = new BlockArray(input, Map.of(), glyphs);
        assertThat(pattern.pattern()).containsExactlyEntriesOf(input);
        assertThat(pattern.symbolsByPosition()).containsExactlyEntriesOf(glyphs);
        PositionCursor<BlockPredicate> cursor = pattern.positions().cursor();
        int index = 0;
        for (var entry : input.entrySet()) {
            cursor.seek(index++);
            assertThat(cursor.position()).isEqualTo(entry.getKey());
            assertThat(cursor.value()).isSameAs(entry.getValue());
            assertThat(pattern.get(entry.getKey())).isSameAs(entry.getValue());
        }
        assertThat(pattern.get(start.relative(step, 2))).isNull();
    }

    @Test
    void solidRunsStayCompactWithoutLosingIndividualCandidatePositions() {
        BlockPredicate wall = new BlockPredicate.Any();
        BlockPredicate other = new BlockPredicate.Any();
        BlockArray row = BlockArray.builder().noController().pattern("AABBA").set('A', wall).set('B', other).build();
        List<Integer> lengths = new ArrayList<>();
        row.forRuns((pos, step, length, value) -> lengths.add(length));
        assertThat(lengths).containsExactly(2, 2, 1);
        assertThat(row.get(BlockPos.ZERO)).isSameAs(other);

        // A non-row-major base selects the general storage path. Appending a solid run must not
        // reclassify its positions as skippable air when the two representations are merged.
        Map<BlockPos, BlockPredicate> base = new LinkedHashMap<>();
        base.put(BlockPos.ZERO.above(2), wall);
        base.put(BlockPos.ZERO.above(), wall);
        var merged = new BlockArray(base).positions().union(row.positions(), pos -> new IllegalArgumentException());
        var candidates = new ArrayList<BlockPos>();
        merged.visitPoints((pos, value) -> candidates.add(pos));
        assertThat(candidates).containsExactlyElementsOf(merged.keySet());
    }

    @Test
    void cursorCanSeekAcrossSparseRowsWithoutFillingTheGaps() {
        Map<BlockPos, BlockPredicate> cells = new LinkedHashMap<>();
        var wall = new BlockPredicate.Any();
        var air = new BlockPredicate.Air();
        var first = new BlockPos(-8, -5, -3000);
        var last = new BlockPos(7, 1000, -1);
        cells.put(first, wall);
        cells.put(first.east(), wall);
        cells.put(new BlockPos(2, -5, 1000), air);
        cells.put(last, wall);
        BlockArray template = new BlockArray(cells);
        var positions = List.copyOf(cells.keySet());
        var cursor = template.positions().cursor();
        for (int i : new int[]{3, 0, 2, 1}) {
            cursor.seek(i);
            assertThat(cursor.position()).isEqualTo(positions.get(i));
            assertThat(cursor.value()).isSameAs(cells.get(positions.get(i)));
        }
        assertThat(template.get(first.south())).isNull();
        assertThat(template.get(last.below())).isNull();
    }

    @Test
    void nullableGlyphRemainsPresentInRotatedMapsAndUsesTheWireBlank() {
        Map<BlockPos, Character> glyphs = new LinkedHashMap<>();
        glyphs.put(BlockPos.ZERO.east(), null);
        var source = new BlockArray(Map.of(BlockPos.ZERO.east(), new BlockPredicate.Any()), Map.of(), glyphs);
        var rotated = BlockArrayCache.get(source, Direction.NORTH);
        assertThat(rotated.symbolsByPosition()).containsEntry(BlockPos.ZERO.west(), null);
        Map<BlockPos, Character> wire = new LinkedHashMap<>();
        rotated.forSymbolRuns((pos, step, length, value) -> wire.put(pos, value));
        assertThat(wire).containsOnly(Map.entry(BlockPos.ZERO.west(), ' '));
    }

    @Test
    void rotatedSymbolRunsDescribeTheSameCoordinatesAsTheMap() {
        var pattern = BlockArray.builder().noController().pattern("AAA B").set('A', new BlockPredicate.Any())
                .set('B', new BlockPredicate.Any()).build();
        for (Direction face : Direction.values()) {
            BlockArray rotated = BlockArrayCache.get(pattern, face, Direction.WEST);
            Map<BlockPos, Character> runs = new LinkedHashMap<>();
            rotated.forSymbolRuns((pos, step, length, symbol) -> {
                for (int i = 0; i < length; i++) runs.put(pos.relative(step, i), symbol);
            });
            Map<BlockPos, Character> expected = new LinkedHashMap<>();
            pattern.symbolsByPosition().forEach((p, v) -> expected.put(BlockRotator.rotateSouthTo(p, face, Direction.WEST), v));
            assertThat(runs).containsExactlyEntriesOf(expected);
        }
    }
}
