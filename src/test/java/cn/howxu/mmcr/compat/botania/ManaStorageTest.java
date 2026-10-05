package cn.howxu.mmcr.compat.botania;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies bounded movement, simulation and actual-change notifications.
 *
 * @author howxu <dev@howxu.cn>
 */
class ManaStorageTest {
    @Test
    void simulationDoesNotMoveManaAndLargeExtractionCannotWrap() {
        AtomicInteger changes = new AtomicInteger();
        ManaStorage storage = new ManaStorage(changes::incrementAndGet);
        storage.setAmount(700L);
        changes.set(0);
        assertThat(storage.move(Long.MAX_VALUE, false, true)).isEqualTo(700L);
        assertThat(storage.amount()).isEqualTo(700);
        assertThat(changes).hasValue(0);
        assertThat(storage.move(Long.MAX_VALUE, false, false)).isEqualTo(700L);
        assertThat(storage.amount()).isZero();
        assertThat(changes).hasValue(1);
    }

    @Test
    void insertionOnlyAcceptsRemainingSpaceAndNoOpsNeverNotify() {
        AtomicInteger changes = new AtomicInteger();
        ManaStorage storage = new ManaStorage(changes::incrementAndGet);
        storage.setAmount(storage.capacity() - 17L);
        changes.set(0);
        assertThat(storage.move(Long.MAX_VALUE, true, true)).isEqualTo(17L);
        assertThat(storage.amount()).isEqualTo(storage.capacity() - 17);
        assertThat(changes).hasValue(0);
        assertThat(storage.move(Long.MAX_VALUE, true, false)).isEqualTo(17L);
        assertThat(storage.move(100L, true, false)).isZero();
        assertThat(storage.move(-1L, false, false)).isZero();
        assertThat(storage.move(Long.MIN_VALUE, true, false)).isZero();
        storage.setAmount(storage.amount());
        storage.setAmount(Long.MAX_VALUE);
        assertThat(storage.amount()).isEqualTo(storage.capacity());
        assertThat(changes).hasValue(1);
        storage.setAmount(Long.MIN_VALUE);
        storage.setAmount(-1L);
        assertThat(storage.amount()).isZero();
        assertThat(changes).hasValue(2);
        assertThat(storage.identity()).isSameAs(storage);
    }
}
