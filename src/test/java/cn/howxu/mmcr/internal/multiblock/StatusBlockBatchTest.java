package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class StatusBlockBatchTest {
    private static StatusBlockBatch batch(int size) {
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) pattern.put(new BlockPos(i, 0, 0), new BlockPredicate.Any());
        return new StatusBlockBatch(new BlockPos(9, 8, 7), new BlockArray(pattern), false, true);
    }

    @ParameterizedTest
    @CsvSource({"128,1", "129,5", "1023,5", "1024,10"})
    void reads_candidates_incrementally_then_only_visits_cached_world_positions(int size, int ticks) {
        var batch = batch(size + 1);
        List<Long> reads = new ArrayList<>(), writes = new ArrayList<>();
        for (int t = 0; reads.size() < size + 1; t++) {
            int before = reads.size();
            batch.step(pos -> { reads.add(pos); return BlockPos.getX(pos) != 9; }, writes::add);
            assertThat(reads.size() - before).isBetween(1, Math.ceilDiv(size + 1, 20));
            assertThat(t).isLessThan(20);
            batch.target(true);
        }
        assertThat(writes).isEmpty();
        for (int t = 0; t < ticks; t++) {
            int before = writes.size();
            batch.step(pos -> { throw new AssertionError("rescanned pattern"); }, writes::add);
            assertThat(writes.size() - before).isBetween(1, Math.ceilDiv(size, ticks));
        }
        assertThat(writes).containsExactlyElementsOf(reads.subList(1, reads.size()));
        assertThat(batch.busy()).isFalse();
    }

    @Test
    void reversal_keeps_cursor_and_repairs_the_already_lit_prefix() {
        var batch = batch(200);
        var lit = new HashSet<Long>();
        List<Long> writes = new ArrayList<>();
        for (int i = 0; i < 20; i++) batch.step(pos -> true, writes::add);
        batch.target(true);
        batch.step(pos -> false, lit::add);
        batch.step(pos -> false, lit::add);
        assertThat(lit).hasSize(80);
        batch.target(false);
        for (int i = 0; i < 5; i++) {
            batch.target(false);
            batch.step(pos -> false, pos -> { writes.add(pos); lit.remove(pos); });
        }
        assertThat(writes.getFirst()).isEqualTo(new BlockPos(9 + 80, 8, 7).asLong());
        assertThat(writes).hasSize(200).doesNotHaveDuplicates();
        assertThat(lit).isEmpty();
        batch.target(true);
        batch.target(false);
        assertThat(batch.busy()).isFalse();
    }

    @Test
    void idle_formation_reconciles_saved_lit_values_and_empty_members_never_queue_transitions() {
        var idle = batch(1);
        idle.step(pos -> true, pos -> { throw new AssertionError(); });
        List<Long> visited = new ArrayList<>();
        idle.step(pos -> { throw new AssertionError(); }, visited::add);
        assertThat(visited).containsExactly(new BlockPos(9, 8, 7).asLong());
        var noHits = batch(1);
        noHits.step(pos -> false, pos -> { throw new AssertionError(); });
        noHits.target(true);
        assertThat(noHits.busy()).isFalse();
    }

    @Test
    void empty_or_retired_partial_batch_does_not_keep_discovering() {
        var empty = batch(0);
        empty.step(pos -> { throw new AssertionError(); }, pos -> { throw new AssertionError(); });
        assertThat(empty.busy()).isFalse();
        var partial = batch(200);
        List<Long> captured = new ArrayList<>(), released = new ArrayList<>();
        partial.step(captured::add, released::add);
        partial.retire();
        partial.step(pos -> { throw new AssertionError(); }, released::add);
        assertThat(released).isNotEmpty().containsExactlyElementsOf(captured);
        assertThat(partial.busy()).isFalse();
    }
}
