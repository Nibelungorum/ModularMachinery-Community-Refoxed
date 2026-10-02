package cn.howxu.mmcr.compat.ars_nouveau.loaded;

import cn.howxu.mmcr.util.IOType;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies native source accounting, fixed directions and legacy restoration.
 *
 * @author howxu <dev@howxu.cn>
 */
class SourcePortStorageTest {
    @Test
    void simulationAndBoundedExecutionConserveSource() {
        AtomicInteger changes = new AtomicInteger();
        SourcePortStorage storage = new SourcePortStorage(changes::incrementAndGet);

        assertThat(storage.move(900L, true, true)).isEqualTo(900L);
        assertThat(storage.amount()).isZero();
        assertThat(changes).hasValue(0);
        assertThat(storage.move(900L, true, false)).isEqualTo(900L);
        assertThat(storage.move(350L, false, true)).isEqualTo(350L);
        assertThat(storage.amount()).isEqualTo(900);
        assertThat(changes).hasValue(1);
        assertThat(storage.move(350L, false, false)).isEqualTo(350L);
        assertThat(storage.amount()).isEqualTo(550);
        long accepted = storage.move(Long.MAX_VALUE, true, false);
        assertThat(550L + accepted).isEqualTo(storage.amount());
        assertThat(storage.amount()).isEqualTo(storage.capacity());
        assertThat(storage.move(1L, true, false)).isZero();
        assertThat(changes).hasValue(3);
        long extracted = storage.move(Long.MAX_VALUE, false, false);
        assertThat(extracted).isEqualTo(550L + accepted);
        assertThat(storage.amount()).isZero();
        assertThat(storage.move(1L, false, false)).isZero();
        assertThat(changes).hasValue(4);
    }

    @Test
    void nativeInputRejectsExtractionAndNativeOutputRejectsReception() {
        AtomicInteger changes = new AtomicInteger();
        SourcePortStorage inputStorage = new SourcePortStorage(changes::incrementAndGet);
        SourcePortStorage outputStorage = new SourcePortStorage(changes::incrementAndGet);
        outputStorage.setAmount(600L);
        DirectionalSourceHandler input = new DirectionalSourceHandler(inputStorage, IOType.INPUT);
        DirectionalSourceHandler output = new DirectionalSourceHandler(outputStorage, IOType.OUTPUT);

        assertThat(input.receiveSource(300, true)).isEqualTo(300);
        assertThat(inputStorage.amount()).isZero();
        assertThat(input.receiveSource(300, false)).isEqualTo(300);
        assertThat(input.extractSource(100, true)).isZero();
        assertThat(input.extractSource(100, false)).isZero();
        assertThat(input.canReceive()).isTrue();
        assertThat(input.canExtract()).isFalse();
        assertThat(input.canAcceptSource(100)).isTrue();
        assertThat(input.canProvideSource(100)).isFalse();
        assertThat(input.getMaxReceive()).isEqualTo(inputStorage.capacity());
        assertThat(input.getMaxExtract()).isZero();
        assertThat(input.getSource()).isEqualTo(300);

        assertThat(output.receiveSource(100, true)).isZero();
        assertThat(output.receiveSource(100, false)).isZero();
        assertThat(output.extractSource(200, true)).isEqualTo(200);
        assertThat(outputStorage.amount()).isEqualTo(600);
        assertThat(output.extractSource(200, false)).isEqualTo(200);
        assertThat(output.canReceive()).isFalse();
        assertThat(output.canExtract()).isTrue();
        assertThat(output.canAcceptSource(100)).isFalse();
        assertThat(output.canProvideSource(100)).isTrue();
        assertThat(output.getMaxReceive()).isZero();
        assertThat(output.getMaxExtract()).isEqualTo(outputStorage.capacity());
        assertThat(output.getSource()).isEqualTo(400);
        assertThat(changes).hasValue(3);
    }

    @Test
    void nativeDirectionTraitsRemainFixedAtFullAndEmptyStates() {
        SourcePortStorage storage = new SourcePortStorage(() -> { });
        DirectionalSourceHandler input = new DirectionalSourceHandler(storage, IOType.INPUT);
        DirectionalSourceHandler output = new DirectionalSourceHandler(storage, IOType.OUTPUT);

        assertThat(output.canExtract()).isTrue();
        assertThat(output.canProvideSource(1)).isFalse();
        assertThat(input.receiveSource(Integer.MAX_VALUE, false)).isEqualTo(storage.capacity());
        assertThat(input.canReceive()).isTrue();
        assertThat(input.canAcceptSource(1)).isFalse();
        assertThat(input.canAcceptSource(0)).isFalse();
        assertThat(output.canProvideSource(0)).isFalse();
        assertThat(output.extractSource(Integer.MAX_VALUE, false)).isEqualTo(storage.capacity());
        assertThat(storage.amount()).isZero();
    }

    @Test
    void legacyOutputCanRestoreArsExtractionButCannotBeAnOrdinaryDestination() {
        AtomicInteger changes = new AtomicInteger();
        SourcePortStorage storage = new SourcePortStorage(changes::incrementAndGet);
        storage.setAmount(600L);
        LegacySourceTile output = new LegacySourceTile(storage, IOType.OUTPUT);

        assertThat(output.canAcceptSource()).isFalse();
        assertThat(output.canProvideSource()).isTrue();
        assertThat(output.getTransferRate()).isEqualTo(storage.capacity());
        assertThat(output.getMaxSource() - output.getSource()).isZero();
        assertThat(output.removeSource(200, true)).isEqualTo(200);
        assertThat(storage.amount()).isEqualTo(600);
        assertThat(output.removeSource(200)).isEqualTo(400);
        assertThat(output.getMaxSource() - output.getSource()).isZero();
        assertThat(output.addSource(200)).isEqualTo(600);
        assertThat(output.addSource(100, true)).isZero();
        assertThat(output.addSource(100, false)).isZero();
        assertThat(storage.amount()).isEqualTo(600);
        assertThat(changes).hasValue(3);
        assertThat(output.removeSource(Integer.MAX_VALUE, false)).isEqualTo(600);
        assertThat(output.canProvideSource()).isFalse();
        assertThat(output.canAcceptSource()).isFalse();
        assertThat(output.getMaxSource() - output.getSource()).isZero();
        assertThat(output.addSource(600)).isEqualTo(600);
        assertThat(storage.amount()).isEqualTo(600);
    }

    @Test
    void legacyOutputRestorationIsBoundedByRealCapacity() {
        SourcePortStorage storage = new SourcePortStorage(() -> { });
        storage.setAmount(600L);
        LegacySourceTile output = new LegacySourceTile(storage, IOType.OUTPUT);

        assertThat(output.removeSource(200)).isEqualTo(400);
        assertThat(output.addSource(Integer.MAX_VALUE)).isEqualTo(storage.capacity());
        assertThat(storage.amount()).isEqualTo(storage.capacity());
        assertThat(output.addSource(1)).isEqualTo(storage.capacity());
        assertThat(output.getMaxSource() - output.getSource()).isZero();
    }

    @Test
    void legacyInputAdvertisesOnlyRemainingCapacity() {
        AtomicInteger changes = new AtomicInteger();
        SourcePortStorage storage = new SourcePortStorage(changes::incrementAndGet);
        storage.setAmount(600L);
        LegacySourceTile input = new LegacySourceTile(storage, IOType.INPUT);

        assertThat(input.getSource()).isZero();
        assertThat(input.getMaxSource() - input.getSource()).isEqualTo(storage.capacity() - 600);
        assertThat(input.canAcceptSource()).isTrue();
        assertThat(input.canProvideSource()).isFalse();
        assertThat(input.getTransferRate()).isZero();
        assertThat(input.removeSource(200)).isZero();
        assertThat(input.removeSource(200, true)).isZero();
        assertThat(input.removeSource(200, false)).isZero();
        assertThat(input.addSource(200, true)).isEqualTo(200);
        assertThat(storage.amount()).isEqualTo(600);
        assertThat(changes).hasValue(1);
        assertThat(input.addSource(200)).isZero();
        assertThat(storage.amount()).isEqualTo(800);
        assertThat(input.getMaxSource()).isEqualTo(storage.capacity() - 800);
        assertThat(input.addSource(Integer.MAX_VALUE, true)).isEqualTo(storage.capacity() - 800);
        assertThat(storage.amount()).isEqualTo(800);
        assertThat(input.addSource(Integer.MAX_VALUE, false)).isEqualTo(storage.capacity() - 800);
        assertThat(input.getMaxSource()).isZero();
        assertThat(input.canAcceptSource()).isFalse();
        assertThat(input.addSource(1, false)).isZero();
        assertThat(changes).hasValue(3);
    }

    @Test
    void forceSettersCannotChangeExternalState() {
        AtomicInteger changes = new AtomicInteger();
        SourcePortStorage storage = new SourcePortStorage(changes::incrementAndGet);
        storage.setAmount(600L);
        int capacity = storage.capacity();
        for (IOType ioType : IOType.values()) {
            DirectionalSourceHandler handler = new DirectionalSourceHandler(storage, ioType);
            LegacySourceTile legacy = new LegacySourceTile(storage, ioType);

            handler.setSource(0);
            handler.setSource(Integer.MAX_VALUE);
            handler.setMaxSource(0);
            handler.setMaxSource(Integer.MAX_VALUE);
            assertThat(legacy.setSource(0)).isEqualTo(legacy.getSource());
            assertThat(legacy.setSource(Integer.MAX_VALUE)).isEqualTo(legacy.getSource());
            assertThat(storage.amount()).isEqualTo(600);
            assertThat(handler.getSource()).isEqualTo(600);
            assertThat(handler.getSourceCapacity()).isEqualTo(capacity);
            assertThat(handler.getMaxSource()).isEqualTo(capacity);
        }
        assertThat(changes).hasValue(1);
        storage.setAmount(350L);
        assertThat(storage.amount()).isEqualTo(350);
        assertThat(changes).hasValue(2);
    }

    @Test
    void unchangedAndSimulatedAccessDoesNotNotify() {
        AtomicInteger changes = new AtomicInteger();
        SourcePortStorage storage = new SourcePortStorage(changes::incrementAndGet);
        storage.setAmount(600L);
        storage.setAmount(600L);

        assertThat(storage.move(200L, true, true)).isEqualTo(200L);
        assertThat(storage.move(200L, false, true)).isEqualTo(200L);
        for (long amount : new long[]{0L, -1L, Long.MIN_VALUE}) {
            assertThat(storage.move(amount, true, false)).isZero();
            assertThat(storage.move(amount, false, false)).isZero();
        }
        assertThat(storage.amount()).isEqualTo(600);
        assertThat(changes).hasValue(1);
        storage.setAmount(Long.MAX_VALUE);
        assertThat(storage.amount()).isEqualTo(storage.capacity());
        storage.setAmount(Long.MAX_VALUE);
        assertThat(storage.move(1L, true, false)).isZero();
        assertThat(changes).hasValue(2);
        storage.setAmount(Long.MIN_VALUE);
        assertThat(storage.amount()).isZero();
        storage.setAmount(-1L);
        assertThat(storage.move(1L, false, false)).isZero();
        assertThat(changes).hasValue(3);
    }

    @Test
    void nativeAndLegacyViewsShareTheSameStableStorageIdentity() {
        SourcePortStorage storage = new SourcePortStorage(() -> { });
        Object identity = storage.identity();
        assertThat(identity).isInstanceOf(SourceStorage.class);
        SourceStorage nativeStorage = (SourceStorage) identity;
        DirectionalSourceHandler input = new DirectionalSourceHandler(storage, IOType.INPUT);
        DirectionalSourceHandler output = new DirectionalSourceHandler(storage, IOType.OUTPUT);
        LegacySourceTile legacyOutput = new LegacySourceTile(storage, IOType.OUTPUT);

        assertThat(input.receiveSource(600, false)).isEqualTo(600);
        assertThat(nativeStorage.getSource()).isEqualTo(600);
        assertThat(output.getSource()).isEqualTo(600);
        assertThat(legacyOutput.removeSource(200)).isEqualTo(400);
        assertThat(input.getSource()).isEqualTo(400);
        assertThat(nativeStorage.getSource()).isEqualTo(400);
        assertThat(output.extractSource(100, false)).isEqualTo(100);
        assertThat(legacyOutput.getSource()).isEqualTo(300);
        storage.setAmount(900L);
        assertThat(nativeStorage.getSource()).isEqualTo(900);
        assertThat(input.getSource()).isEqualTo(900);
        assertThat(legacyOutput.getSource()).isEqualTo(900);
        assertThat(storage.identity()).isSameAs(identity);
        assertThat(new SourcePortStorage(() -> { }).identity()).isNotSameAs(identity);
    }
}
