package cn.howxu.mmcr.client.controller;

import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies versioned controller screen text cache replacement.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerScreenTextCacheTest {
    private static final BlockPos POS = new BlockPos(10, 20, 30);
    private static final BlockPos SECOND_POS = new BlockPos(40, 50, 60);

    @BeforeEach
    void clearCacheEntry() {
        ControllerScreenTextCache.clear(POS);
        ControllerScreenTextCache.clear(SECOND_POS);
    }

    @Test
    void equal_revision_is_a_no_op_and_older_revision_is_rejected() {
        ControllerScreenTextSnapshot.Line first = line("test:first", "first");
        ControllerScreenTextSnapshot.Line replacement = line("test:replacement", "replacement");

        assertThat(ControllerScreenTextCache.replace(POS, 4L, List.of(first))).isTrue();
        assertThat(ControllerScreenTextCache.replace(POS, 4L, List.of(replacement))).isFalse();
        assertThat(ControllerScreenTextCache.replace(POS, 3L, List.of(replacement))).isFalse();
        assertThat(ControllerScreenTextCache.linesAt(POS)).containsExactly(first);
    }

    @Test
    void newer_revision_replaces_the_entire_snapshot_atomically() {
        ControllerScreenTextSnapshot.Line first = line("test:first", "first");
        ControllerScreenTextSnapshot.Line second = line("test:second", "second");
        List<ControllerScreenTextSnapshot.Line> replacement = new ArrayList<>(List.of(second));

        ControllerScreenTextCache.replace(POS, 1L, List.of(first));
        assertThat(ControllerScreenTextCache.replace(POS, 2L, replacement)).isTrue();
        replacement.clear();

        assertThat(ControllerScreenTextCache.linesAt(POS)).containsExactly(second);
    }

    @Test
    void cache_copies_mutable_block_positions() {
        BlockPos.MutableBlockPos mutablePosition = new BlockPos.MutableBlockPos(POS.getX(), POS.getY(), POS.getZ());

        ControllerScreenTextCache.replace(mutablePosition, 1L, List.of(line("test:line", "text")));
        mutablePosition.set(99, 98, 97);

        assertThat(ControllerScreenTextCache.linesAt(POS)).hasSize(1);
    }

    @Test
    void missing_and_cleared_positions_return_no_external_lines() {
        assertThat(ControllerScreenTextCache.linesAt(POS)).isEmpty();

        ControllerScreenTextCache.replace(POS, 1L, List.of(line("test:line", "text")));
        ControllerScreenTextCache.clear(POS);

        assertThat(ControllerScreenTextCache.linesAt(POS)).isEmpty();
    }

    @Test
    void clear_all_discards_snapshots_and_resets_revision_gate() {
        ControllerScreenTextSnapshot.Line first = line("test:first", "first");
        ControllerScreenTextSnapshot.Line second = line("test:second", "second");
        ControllerScreenTextSnapshot.Line replacement = line("test:replacement", "replacement");

        ControllerScreenTextCache.replace(POS, 8L, List.of(first));
        ControllerScreenTextCache.replace(SECOND_POS, 3L, List.of(second));

        ControllerScreenTextCache.clearAll();

        assertThat(ControllerScreenTextCache.linesAt(POS)).isEmpty();
        assertThat(ControllerScreenTextCache.linesAt(SECOND_POS)).isEmpty();
        assertThat(ControllerScreenTextCache.replace(POS, 1L, List.of(replacement))).isTrue();
        assertThat(ControllerScreenTextCache.linesAt(POS)).containsExactly(replacement);
    }

    @Test
    void clear_chunk_discards_only_chunk_snapshots_and_resets_revision_gate() {
        ControllerScreenTextSnapshot.Line first = line("test:first", "first");
        ControllerScreenTextSnapshot.Line second = line("test:second", "second");
        ControllerScreenTextSnapshot.Line replacement = line("test:replacement", "replacement");

        ControllerScreenTextCache.replace(POS, 8L, List.of(first));
        ControllerScreenTextCache.replace(SECOND_POS, 3L, List.of(second));

        ControllerScreenTextCache.clearChunk(POS.getX() >> 4, POS.getZ() >> 4);

        assertThat(ControllerScreenTextCache.linesAt(POS)).isEmpty();
        assertThat(ControllerScreenTextCache.linesAt(SECOND_POS)).containsExactly(second);
        assertThat(ControllerScreenTextCache.replace(POS, 1L, List.of(replacement))).isTrue();
        assertThat(ControllerScreenTextCache.linesAt(POS)).containsExactly(replacement);
    }

    @Test
    void returned_lines_are_immutable() {
        ControllerScreenTextCache.replace(POS, 1L, List.of(line("test:line", "text")));

        assertThat(ControllerScreenTextCache.linesAt(POS)).isUnmodifiable();
    }

    @Test
    void lane_snapshots_are_isolated_from_each_other() {
        ControllerScreenTextSnapshot.Line first = line("test:first", "first");
        ControllerScreenTextSnapshot.Line second = line("test:second", "second");

        ControllerScreenTextCache.replace(POS, "lane-0", 1L, List.of(first));
        ControllerScreenTextCache.replace(POS, "lane-1", 1L, List.of(second));

        assertThat(ControllerScreenTextCache.linesAt(POS, "lane-0")).containsExactly(first);
        assertThat(ControllerScreenTextCache.linesAt(POS, "lane-1")).containsExactly(second);
    }

    @Test
    void listeners_run_only_for_accepted_newer_replacements() {
        AtomicInteger invocations = new AtomicInteger();
        ControllerScreenTextCache.addInvalidationListener(invocations::incrementAndGet);

        assertThat(ControllerScreenTextCache.replace(POS, 1L, List.of())).isTrue();
        assertThat(ControllerScreenTextCache.replace(POS, 1L, List.of(line("test:ignored", "ignored")))).isFalse();
        assertThat(ControllerScreenTextCache.replace(POS, 0L, List.of())).isFalse();
        ControllerScreenTextCache.clear(POS);

        assertThat(invocations).hasValue(1);
    }

    private static ControllerScreenTextSnapshot.Line line(String id, String text) {
        return new ControllerScreenTextSnapshot.Line(ControllerScreenTextScope.CONTROLLER,
                Identifier.parse(id), Component.literal(text));
    }
}
