package cn.howxu.mmcr.compat.appliedenergistics2;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PendingPatternScheduler;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class PendingPatternSchedulerPolicyTest {
    @Test
    void independentThirdInterfaceCannotEraseTheWaitingCompetitorsPriority() {
        var cursor = new PendingPatternScheduler.InterfaceCursor();
        BlockPos first = BlockPos.ZERO;
        BlockPos waiting = new BlockPos(1, 0, 0);
        BlockPos independent = new BlockPos(2, 0, 0);
        var ports = Set.of(first, waiting, independent);
        cursor.order(ports);
        cursor.started(first);
        cursor.started(independent);
        for (int busyTick = 0; busyTick < 40; busyTick++) {
            assertThat(cursor.order(ports)).startsWith(waiting);
            cursor.started(independent);
        }
        cursor.started(waiting);
        assertThat(cursor.order(ports)).containsExactly(first, independent, waiting);
    }

    @Test
    void partialGroupDoesNotReclaimPriorityAfterACompleteBusyPass() {
        var cursor = new PendingPatternScheduler.GroupCursor();
        var groups = List.of(1L, 2L);
        cursor.next(groups);
        cursor.attempted(1L);
        for (int busyTick = 0; busyTick < 40; busyTick++) {
            assertThat(cursor.next(groups)).containsExactly(2L, 1L);
        }
        cursor.attempted(2L);
        assertThat(cursor.next(groups)).containsExactly(1L, 2L);
    }

    @Test
    void competingInterfacesKeepPriorityUntilTheyActuallyStart() {
        var cursor = new PendingPatternScheduler.InterfaceCursor();
        BlockPos first = BlockPos.ZERO;
        BlockPos second = new BlockPos(1, 0, 0);
        var ports = Set.of(first, second);
        assertThat(cursor.order(ports)).containsExactly(first, second);
        cursor.started(first);
        for (int busyTick = 0; busyTick < 40; busyTick++) {
            assertThat(cursor.order(ports)).containsExactly(second, first);
        }
        cursor.started(second);
        assertThat(cursor.order(ports)).containsExactly(first, second);
    }

    @Test
    void runnableGroupBehindNineBlockedGroupsGetsATurn() {
        var cursor = new PendingPatternScheduler.GroupCursor();
        List<Long> groups = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        for (long id : cursor.next(groups).subList(0, 8)) cursor.attempted(id);
        assertThat(cursor.next(groups)).startsWith(9L, 10L);
        assertThat(cursor.next(List.of(1L, 2L, 3L, 4L, 5L))).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void sameTickNeverStartsEvenWhenFull() {
        assertThat(PendingPatternScheduler.ready(10, 10, 10, 64, 64, 1)).isFalse();
        assertThat(PendingPatternScheduler.ready(11, 10, 10, 64, 64, 100)).isTrue();
    }

    @Test
    void vanillaFourTickArrivalsDoNotTriggerQuietFlush() {
        for (long now = 1; now < 12; now++) {
            long last = now / 4 * 4;
            assertThat(PendingPatternScheduler.ready(now, 0, last, 3, 64, 100)).isFalse();
        }
        assertThat(PendingPatternScheduler.ready(13, 0, 8, 3, 64, 100)).isTrue();
    }

    @Test
    void hardDeadlineSurvivesContinuousArrivals() {
        assertThat(PendingPatternScheduler.ready(39, 0, 39, 10, 64, 100)).isFalse();
        assertThat(PendingPatternScheduler.ready(40, 0, 40, 11, 64, 100)).isTrue();
    }

    @Test
    void shortRecipeCanStartNextTickButNoCapacityCannotMeanFull() {
        assertThat(PendingPatternScheduler.ready(1, 0, 0, 1, 64, 2)).isTrue();
        assertThat(PendingPatternScheduler.ready(100, 0, 0, 1, 0, 2)).isFalse();
    }
}
