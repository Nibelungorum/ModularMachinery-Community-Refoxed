package cn.howxu.mmcr.compat.create;

import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real ledger and floating reservation behavior, without Minecraft runtime state.
 * @author howxu <dev@howxu.cn>
 */
class StressContributionsTest {
    @Test
    void replacements_are_idempotent_and_lane_release_is_isolated() {
        var ledger = new StressContributions();
        var laneA = new StressSession();
        var laneB = new StressSession();
        var first = new StressContributions.Contribution(8, 64);
        ledger.replace(laneA, 3, first);
        ledger.replace(laneA, 3, first);
        ledger.replace(laneB, 3, new StressContributions.Contribution(16, 64));
        assertThat(ledger.baseStress()).isEqualTo(24);
        assertThat(ledger.accepts(laneB, 4, -64)).isFalse();
        ledger.release(laneA);
        assertThat(ledger.get(laneB, 3)).isEqualTo(new StressContributions.Contribution(16, 64));
        ledger.release(laneB);
        assertThat(ledger.baseStress()).isZero();
        assertThat(ledger.generatedRpm()).isZero();
    }

    @Test
    void rpm_conflicts_exclude_only_same_owner_and_index() {
        var ledger = new StressContributions();
        var lane = new StressSession();
        ledger.replace(lane, 1, new StressContributions.Contribution(8, 64));
        assertThat(ledger.accepts(lane, 1, -64)).isTrue();
        assertThat(ledger.accepts(lane, 2, -64)).isFalse();
        ledger.replace(lane, 2, new StressContributions.Contribution(4, 64));
        assertThat(ledger.accepts(lane, 1, -64)).isFalse();
        ledger.release(lane, 2);
        ledger.replace(lane, 1, new StressContributions.Contribution(8, -64));
        assertThat(ledger.generatedRpm()).isEqualTo(-64);
        assertThat(ledger.accepts(lane, 1, Double.NaN)).isFalse();
        assertThatThrownBy(() -> new StressContributions.Contribution(Double.MAX_VALUE, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservations_use_identity_and_copies_isolate_stress_rpm_and_owner_credits() {
        var reservations = new PlanningReservations();
        Object first = new String("network");
        Object equalButDifferent = new String("network");
        var owner = new StressSession();
        assertThat(reservations.creditStress(first, owner, 3, 64)).isTrue();
        assertThat(reservations.creditStress(first, owner, 3, 64)).isTrue();
        assertThat(reservations.creditedStress(first)).isEqualTo(64);
        assertThat(reservations.reserveStress(first, 48, 64)).isTrue();
        assertThat(reservations.reserveStress(first, 32, 64)).isFalse();
        assertThat(reservations.reservedStress(equalButDifferent)).isZero();
        assertThat(reservations.reserveGeneratedRpm(first, 64)).isTrue();
        var copy = reservations.copy();
        assertThat(copy.reserveStress(first, 16, 64)).isTrue();
        assertThat(copy.reserveGeneratedRpm(first, -64)).isFalse();
        assertThat(copy.creditStress(first, owner, 4, 32)).isTrue();
        assertThat(reservations.reservedStress(first)).isEqualTo(48);
        assertThat(reservations.creditedStress(first)).isEqualTo(64);
        assertThat(copy.creditedStress(first)).isEqualTo(96);
        assertThat(reservations.reserveStress(first, Double.POSITIVE_INFINITY, 64)).isFalse();
    }
}
