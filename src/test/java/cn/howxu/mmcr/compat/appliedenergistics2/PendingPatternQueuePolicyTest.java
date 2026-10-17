package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Registry-independent admission throttling behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
class PendingPatternQueuePolicyTest {
    @Test
    void budgetCountsRejectedAttemptsAndResetsNextTick() {
        var queue = new PendingPatternQueue();
        for (int attempt = 0; attempt < 1024; attempt++) {
            assertThat(queue.canAttempt(41L)).isTrue();
            assertThat(queue.offer(null, List.of(), new KeyCounter[0],
                    new PendingPatternQueue.OfferLimits(1L, 1L), 41L)).isFalse();
        }
        assertThat(queue.canAttempt(41L)).isFalse();
        assertThat(queue.canAttempt(41L)).isFalse();
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.canAttempt(41L)).isFalse();
        assertThat(queue.canAttempt(42L)).isTrue();
    }

    @Test
    void budgetDoesNotDependOnClockOriginOrAdvanceByOne() {
        var queue = new PendingPatternQueue();
        for (int attempt = 0; attempt < 1024; attempt++) {
            assertThat(queue.canAttempt(Long.MIN_VALUE)).isTrue();
        }
        assertThat(queue.canAttempt(Long.MIN_VALUE)).isFalse();
        assertThat(queue.canAttempt(9_000_000L)).isTrue();
    }
}
