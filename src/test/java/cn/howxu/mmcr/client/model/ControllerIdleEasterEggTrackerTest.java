package cn.howxu.mmcr.client.model;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerIdleEasterEggTrackerTest {
    private static final BlockPos POS = new BlockPos(4, 8, 15);

    @Test
    void successful_check_plays_one_complete_animation_then_restores_idle() {
        ControllerIdleEasterEggTracker tracker = new ControllerIdleEasterEggTracker(() -> 0.0D);
        tracker.track(POS, 0L);

        assertThat(tracker.tick(11999L, ignored -> true)).isEmpty();
        assertThat(tracker.tick(12000L, ignored -> true)).containsExactly(POS);
        assertThat(tracker.isActive(POS, 12000L)).isTrue();
        assertThat(tracker.tick(12103L, ignored -> true)).isEmpty();
        assertThat(tracker.tick(12104L, ignored -> true)).containsExactly(POS);
        assertThat(tracker.isActive(POS, 12104L)).isFalse();
    }

    @Test
    void untracked_controller_cannot_keep_an_active_easter_egg() {
        ControllerIdleEasterEggTracker tracker = new ControllerIdleEasterEggTracker(() -> 0.0D);
        tracker.track(POS, 0L);
        tracker.tick(12000L, ignored -> true);

        tracker.untrack(POS);

        assertThat(tracker.isActive(POS, 12000L)).isFalse();
    }

    @Test
    void delayed_tick_reports_one_change_when_expiry_and_next_trigger_are_both_overdue() {
        ControllerIdleEasterEggTracker tracker = new ControllerIdleEasterEggTracker(() -> 0.0D);
        tracker.track(POS, 0L);
        tracker.tick(12000L, ignored -> true);

        assertThat(tracker.tick(25000L, ignored -> true)).containsExactly(POS);
        assertThat(tracker.isActive(POS, 25000L)).isTrue();
    }
}
