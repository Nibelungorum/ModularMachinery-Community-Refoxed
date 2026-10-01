package cn.howxu.mmcr.publicapi.data;

import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import cn.howxu.mmcr.api.data.view.DataStorage;
import cn.howxu.mmcr.api.data.view.DataRepositoryContext;
import cn.howxu.mmcr.api.data.view.DataValueType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** External implementations retain native reservation and storage semantics.
 * @author howxu <dev@howxu.cn>
 */
class RepositoryBridgeTest {
    @Test
    void external_source_and_destination_commit_through_the_native_reservation() {
        var source = new ExternalBalance();
        var nativeStore = new cn.howxu.mmcr.api.data.DataStorage();
        var destination = StorageAdapters.wrap(DataStorage.view(nativeStore));
        var id = ResourceLocation.parse("test:external_transaction");
        Repository repository = new Repository() {
            public ResourceLocation id() { return id; }
            public RepositoryRequest request(RepositoryContext context) {
                return RepositoryRequest.available(id, BlockPos.ZERO, "balance", DataKind.LONG, DataKey.of(12L),
                        new Reservation() {
                            public boolean commit() {
                                source.take();
                                destination.set("balance", DataKey.of(12L));
                                return true;
                            }
                            public void cancel() { }
                        });
            }
        };
        var reservation = StorageAdapters.toCore(repository).request(
                new DataRepositoryContext(id, BlockPos.ZERO, "balance", DataValueType.LONG)).reservation().orElseThrow();
        assertEquals(12L, source.balance);
        assertFalse(destination.contains("balance"));
        assertTrue(reservation.commit());
        assertEquals(0L, source.balance);
        assertEquals(12L, destination.get("balance").orElseThrow().longValue());
    }

    /** External resource committed by its reservation.
     * @author howxu <dev@howxu.cn>
     */
    private static final class ExternalBalance {
        private long balance = 12L;
        void take() { balance = 0L; }
    }

    @Test
    void user_reservation_retains_immediate_writes_change_notifications_and_cancellation() {
        AtomicInteger notifications = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        var nativeStore = new cn.howxu.mmcr.api.data.DataStorage(ignored -> notifications.incrementAndGet());
        DataStore destination = StorageAdapters.wrap(DataStorage.view(nativeStore));
        ResourceLocation id = ResourceLocation.parse("test:repository");
        Repository repository = new Repository() {
            public ResourceLocation id() { return id; }
            public RepositoryRequest request(RepositoryContext context) {
                return RepositoryRequest.available(id(), context.controllerPos(), context.key(), context.requestedType(),
                        DataKey.of(12L), new Reservation() {
                            public boolean commit() {
                                if (destination.contains(context.key())) return false;
                                destination.set(context.key(), DataKey.of(12L));
                                return true;
                            }
                            public void cancel() { cancellations.incrementAndGet(); }
                        });
            }
        };
        var request = StorageAdapters.toCore(repository).request(
                new DataRepositoryContext(id, BlockPos.ZERO, "balance", DataValueType.LONG));
        var reservation = request.reservation().orElseThrow();
        assertFalse(destination.contains("balance"));
        assertTrue(reservation.commit());
        assertFalse(reservation.commit());
        assertEquals(1, notifications.get());
        assertEquals(12L, destination.get("balance").orElseThrow().longValue());
        reservation.cancel();
        assertEquals(1, cancellations.get());
    }

    @Test
    void nested_values_are_typed_immutable_and_round_trip_without_object_handles() {
        DataKey value = DataKey.map(Map.of("list", DataKey.list(List.of(DataKey.of(7L), DataKey.of("value")))));
        DataKey roundTrip = StorageAdapters.wrap(StorageAdapters.unwrap(value));
        assertEquals(value, roundTrip);
        var list = roundTrip.asMap().orElseThrow().get("list").asList().orElseThrow();
        assertEquals("value", list.get(1).stringValue());
        assertThrows(UnsupportedOperationException.class, () -> list.add(DataKey.of(false)));
        assertThrows(IllegalStateException.class, list.getFirst()::stringValue);
        assertThrows(IllegalArgumentException.class, () -> DataKey.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> RepositoryRequest.unavailable(
                ResourceLocation.parse("test:repository"), BlockPos.ZERO, "value", DataKind.INT, DataKey.of(1L)));
    }
}
