package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies virtual planning reservations stay separate from committed storage state.
 *
 * @author howxu <dev@howxu.cn>
 */
class PlanningReservationsTest {
    @Test
    void empty_reads_and_copies_leave_reservation_maps_unallocated() {
        LongValueStorage storage = new LongValueStorage(10L, 5L, null);
        storage.setAmount(4L);
        Object network = new Object();
        Object key = new Object();
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.valueAvailable(storage, false)).isEqualTo(4L);
        assertThat(reservations.valueAvailable(storage, true)).isEqualTo(6L);
        assertThat(reservations.outputAvailable(network, key, 7L)).isEqualTo(7L);
        PlanningReservations copy = reservations.copy();
        assertThat(copy.valueAvailable(storage, false)).isEqualTo(4L);
        assertThat(copy.outputAvailable(network, key, 7L)).isEqualTo(7L);
        assertThat(reservations).extracting("resources", "outputReservations", "values")
                .containsExactly(null, null, null);
        assertThat(copy).extracting("resources", "outputReservations", "values")
                .containsExactly(null, null, null);

        assertThat(copy.reserveValue(storage, 2L, false)).isTrue();
        assertThat(copy.reserveOutput(network, key, 3L)).isTrue();
        assertThat(reservations.valueAvailable(storage, false)).isEqualTo(4L);
        assertThat(reservations.outputAvailable(network, key, 7L)).isEqualTo(7L);
        assertThat(reservations).extracting("resources", "outputReservations", "values")
                .containsExactly(null, null, null);
    }

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
        assertThat(copy.reserveValue(storage, 1L, false)).isTrue();
        assertThat(copy.valueAvailable(storage, false)).isEqualTo(1L);
        assertThat(reservations.valueAvailable(storage, false)).isEqualTo(2L);
        assertThat(reservations.reserveValue(storage, 2L, false)).isTrue();
        assertThat(reservations.valueAvailable(storage, false)).isZero();
        assertThat(copy.valueAvailable(storage, false)).isEqualTo(1L);
        assertThat(reservations).extracting("resources", "outputReservations").containsExactly(null, null);
        assertThat(copy).extracting("resources", "outputReservations").containsExactly(null, null);
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
        assertThat(reservations.reserveValue(storage, 0L, false)).isFalse();
        assertThat(reservations.reserveValueTotal(storage, Long.MIN_VALUE, true)).isFalse();
        assertThat(reservations.reserveValueTotal(storage, 6L, true)).isFalse();
        assertThat(reservations).extracting("resources", "outputReservations", "values")
                .containsExactly(null, null, null);
    }

    @Test
    void output_reservations_are_keyed_by_network_and_copyable() {
        Object network = new Object();
        Object key = new Object();
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.outputAvailable(network, key, 5L)).isEqualTo(5L);
        assertThat(reservations.reserveOutput(network, key, 4L)).isTrue();
        assertThat(reservations.outputAvailable(network, key, 5L)).isEqualTo(1L);

        PlanningReservations copy = reservations.copy();
        assertThat(copy.outputAvailable(network, key, 5L)).isEqualTo(1L);
        assertThat(copy.reserveOutput(network, key, 1L)).isTrue();
        assertThat(copy.outputAvailable(network, key, 5L)).isZero();
        assertThat(reservations.outputAvailable(network, key, 5L)).isEqualTo(1L);
        assertThat(reservations).extracting("resources", "values").containsExactly(null, null);
        assertThat(copy).extracting("resources", "values").containsExactly(null, null);
    }

    @Test
    void output_reservations_validate_arguments() {
        Object network = new Object();
        Object key = new Object();
        PlanningReservations reservations = new PlanningReservations();

        assertThatThrownBy(() -> reservations.outputAvailable(null, key, 5L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservations.outputAvailable(network, null, 5L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservations.outputAvailable(network, key, -1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservations.reserveOutput(null, key, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservations.reserveOutput(network, null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(reservations.reserveOutput(network, key, 0L)).isFalse();
        assertThat(reservations.reserveOutput(network, key, -1L)).isFalse();
        assertThat(reservations).extracting("resources", "outputReservations", "values")
                .containsExactly(null, null, null);
    }

    @Test
    void output_reservations_aggregate_equal_keys_and_isolate_identities() {
        Object firstNetwork = new String("network");
        Object secondNetwork = new String("network");
        String firstKey = new String("iron");
        String equalKey = new String("iron");
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveOutput(firstNetwork, firstKey, 4L)).isTrue();
        assertThat(reservations.outputAvailable(firstNetwork, equalKey, 10L)).isEqualTo(6L);
        assertThat(reservations.reserveOutput(firstNetwork, equalKey, 3L)).isTrue();
        assertThat(reservations.outputAvailable(firstNetwork, firstKey, 10L)).isEqualTo(3L);
        assertThat(reservations.outputAvailable(secondNetwork, firstKey, 10L)).isEqualTo(10L);
        PlanningReservations copy = reservations.copy();
        assertThat(copy.outputAvailable(firstNetwork, equalKey, 10L)).isEqualTo(3L);
        assertThat(copy.reserveOutput(secondNetwork, equalKey, 2L)).isTrue();
        assertThat(copy.outputAvailable(firstNetwork, firstKey, 10L)).isEqualTo(3L);
        assertThat(reservations.outputAvailable(secondNetwork, firstKey, 10L)).isEqualTo(10L);
    }

    @Test
    void output_reservations_reject_reservation_overflow() {
        Object network = new Object();
        Object key = new Object();
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveOutput(network, key, Long.MAX_VALUE)).isTrue();
        assertThat(reservations.reserveOutput(network, key, 1L)).isFalse();
        assertThat(reservations.outputAvailable(network, key, Long.MAX_VALUE)).isZero();
    }

    @Test
    void total_value_reservations_bypass_only_the_transfer_limit_and_copies_stay_isolated() {
        LongValueStorage storage = new LongValueStorage(10L, 2L, null);
        LongValueStorage other = new LongValueStorage(10L, 2L, null);
        storage.setAmount(6L);
        other.setAmount(6L);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveValue(storage, 4L, false)).isFalse();
        assertThat(reservations.reserveValueTotal(storage, 4L, false)).isTrue();
        PlanningReservations copy = reservations.copy();
        assertThat(copy.reserveValueTotal(storage, 5L, true)).isTrue();
        assertThat(copy.valueAvailable(storage, false)).isEqualTo(7L);
        assertThat(reservations.valueAvailable(storage, false)).isEqualTo(2L);
        assertThat(copy.valueAvailable(other, false)).isEqualTo(6L);
        assertThat(storage.amount()).isEqualTo(6L);
    }

    @Test
    void overflowing_value_reads_and_failed_writes_preserve_existing_reservations() {
        LongValueStorage storage = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, null);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveValueTotal(storage, Long.MAX_VALUE, true)).isTrue();
        storage.setAmount(Long.MAX_VALUE);
        assertThat(reservations.valueAvailable(storage, false)).isZero();
        assertThat(reservations.reserveValue(storage, 1L, false)).isFalse();
        assertThat(reservations.reserveValueTotal(storage, 1L, true)).isFalse();
        PlanningReservations copy = reservations.copy();
        storage.setAmount(0L);
        assertThat(copy.valueAvailable(storage, false)).isEqualTo(Long.MAX_VALUE);
        assertThat(copy.reserveValueTotal(storage, Long.MAX_VALUE, false)).isTrue();
        assertThat(copy.valueAvailable(storage, false)).isZero();
        assertThat(reservations.valueAvailable(storage, false)).isEqualTo(Long.MAX_VALUE);
    }
}
