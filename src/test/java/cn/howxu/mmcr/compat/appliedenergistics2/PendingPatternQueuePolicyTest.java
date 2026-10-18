package cn.howxu.mmcr.compat.appliedenergistics2;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Registry-independent empty legacy ownership behavior.
 * @author howxu <dev@howxu.cn>
 */
class PendingPatternQueuePolicyTest {
    @Test
    void empty_legacy_owner_stays_empty_across_clock_rebases() {
        var queue = new PendingPatternQueue();
        queue.rebaseClock(Long.MIN_VALUE);
        queue.rebaseClock(Long.MAX_VALUE);
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.groups()).isEmpty();
        assertThat(queue.totalCopies()).isZero();
    }
}
