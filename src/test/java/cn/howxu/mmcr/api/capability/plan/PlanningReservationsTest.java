package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies virtual planning reservations stay separate from committed storage state.
 *
 * @author howxu <dev@howxu.cn>
 */
class PlanningReservationsTest {
    @Test
    void value_reservations_are_virtual_and_copyable() {
        LongValueStorage storage = new LongValueStorage(10L, 10L, null);
        storage.setAmount(5L);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveValue(storage, 3L, false)).isTrue();
        assertThat(storage.amount()).isEqualTo(5L);
        assertThat(reservations.valueAvailable(storage, false)).isEqualTo(2L);

        PlanningReservations copy = reservations.copy();
        assertThat(copy.valueAvailable(storage, false)).isEqualTo(2L);
        assertThat(reservations.reserveValue(storage, 2L, false)).isTrue();
        assertThat(reservations.valueAvailable(storage, false)).isZero();
        assertThat(copy.valueAvailable(storage, false)).isEqualTo(2L);
    }

    @Test
    void failed_reservations_do_not_change_virtual_state() {
        LongValueStorage storage = new LongValueStorage(10L, 5L, null);
        storage.setAmount(5L);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveValue(storage, 6L, false)).isFalse();
        assertThat(reservations.valueAvailable(storage, false)).isEqualTo(5L);
        assertThat(reservations.reserveValue(storage, 6L, true)).isFalse();
        assertThat(reservations.valueAvailable(storage, true)).isEqualTo(5L);
    }
}
