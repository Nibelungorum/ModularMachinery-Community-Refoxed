package cn.howxu.mmcr.publicapi.network;

import cn.howxu.mmcr.api.data.view.DataStorage;
import cn.howxu.mmcr.api.network.view.MachineReference;
import cn.howxu.mmcr.api.network.view.RequestFailureReason;
import cn.howxu.mmcr.api.network.view.RequestInfo;
import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import cn.howxu.mmcr.internal.api.facade.network.NetworkAdapters;
import cn.howxu.mmcr.publicapi.data.DataKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Open addon callbacks retain peer identity, missing storage and live writes.
 * @author howxu <dev@howxu.cn>
 */
class NetworkBoundaryTest {
    @Test
    void plain_handler_writes_authoritative_receiver_and_preserves_nullable_sender() {
        var nativeStore = new cn.howxu.mmcr.api.data.DataStorage();
        var storage = DataStorage.view(nativeStore);
        var peer = new MachineReference(ResourceLocation.parse("test:producer"), 42L);
        var request = new RequestInfo(ResourceLocation.parse("test:report"), peer);
        RequestPayload payload = RequestPayload.of(Map.of("power", DataKey.of(3.5D)));
        RequestHandler handler = (body, info, sender, receiver) -> {
            assertNull(sender);
            assertEquals(peer.type(), info.peer().type());
            receiver.set("power_" + info.peer().hash(), body.get("power").orElseThrow());
        };
        NetworkAdapters.toCore(handler).process(NetworkAdapters.unwrap(payload), request, null, storage);
        assertEquals(3.5D, nativeStore.get("power_42").orElseThrow().doubleValue());
        assertSame(storage, StorageAdapters.unwrap(StorageAdapters.wrap(storage)));
        assertEquals(payload.values(), NetworkAdapters.wrap(NetworkAdapters.unwrap(payload)).values());
        assertThrows(UnsupportedOperationException.class, () -> payload.values().clear());
    }

    @Test
    void all_failed_delivery_values_cross_an_open_handler_without_inventing_storage() {
        AtomicInteger calls = new AtomicInteger();
        var target = new MachineReference(ResourceLocation.parse("test:target"), 19L);
        var info = new RequestInfo(ResourceLocation.parse("test:request"), target);
        var body = NetworkAdapters.unwrap(RequestPayload.of(Map.of()));
        FailureHandler handler = (payload, details, storage, reason) -> {
            assertNull(storage);
            assertEquals(target.hash(), details.peer().hash());
            assertEquals(target.type(), details.peer().type());
            assertNotNull(reason);
            calls.incrementAndGet();
        };
        var adapted = NetworkAdapters.toCore(handler);
        for (RequestFailureReason reason : RequestFailureReason.values()) {
            assertEquals(RequestFailure.valueOf(reason.name()), NetworkAdapters.wrap(reason));
            adapted.fail(body, info, null, reason);
        }
        assertEquals(RequestFailure.values().length, calls.get());
        FailureHandler throwing = (payload, details, storage, reason) -> { throw new IllegalStateException("addon failure"); };
        assertThrows(IllegalStateException.class, () -> NetworkAdapters.toCore(throwing)
                .fail(body, info, null, RequestFailureReason.UNREACHABLE));
    }

    @Test
    void network_settings_round_trip_core_validation_and_immutable_allowlist() {
        var first = NetworkSettings.of(1, 2, Set.of());
        ResourceLocation allowed = ResourceLocation.parse("test:allowed");
        var next = first.withAllowedMachine(allowed);
        assertTrue(first.allowedMachineIds().isEmpty());
        assertEquals(Set.of(allowed), NetworkAdapters.toCore(next).allowedMachineIds());
        assertThrows(UnsupportedOperationException.class, () -> next.allowedMachineIds().clear());
        assertThrows(IllegalArgumentException.class, () -> NetworkSettings.of(-1, 2, Set.of()));
    }
}
