package cn.howxu.mmcr.publicapi.network;

import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.network.RequestProcess;
import cn.howxu.mmcr.api.network.view.MachineReference;
import cn.howxu.mmcr.api.network.view.RequestFailed;
import cn.howxu.mmcr.api.network.view.RequestFailureReason;
import cn.howxu.mmcr.api.network.view.RequestInfo;
import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import cn.howxu.mmcr.internal.api.facade.network.NetworkAdapters;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.data.DataStore;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

/** Readonly callback views invoke the registered core callback against authoritative storage.
 * @author howxu <dev@howxu.cn>
 */
class NetworkReverseCallbackTest {
    private static final ResourceLocation REQUEST = ResourceLocation.parse("test:reverse_request");
    private static final ResourceLocation PEER = ResourceLocation.parse("test:reverse_peer");

    @Test
    void delivered_callback_retains_peer_typed_payload_storage_identity_and_deferred_data_commit() {
        AtomicInteger notifications = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        var sender = new DataStorage();
        var receiver = new DataStorage(ignored -> notifications.incrementAndGet());
        DataStore publicSender = store(sender);
        DataStore publicReceiver = store(receiver);
        RequestPayload payload = RequestPayload.of(Map.of("values", DataKey.list(List.of(DataKey.of(7L), DataKey.of("typed")))));
        for (boolean commit : List.of(false, true)) {
            var transaction = DataStorage.Transaction.create();
                RequestProcess registered = (body, request, source, target) -> {
                    calls.incrementAndGet();
                    assertSame(NetworkAdapters.unwrap(payload).toInternal(), body);
                    assertSame(sender, source);
                    assertSame(receiver, target);
                    assertEquals(REQUEST, request.requestId());
                    assertEquals(PEER, request.peer().type());
                    assertEquals(42L, request.peer().hash());
                    var values = body.get("values").orElseThrow().asList().orElseThrow();
                    assertEquals(7L, values.getFirst().longValue());
                    assertEquals("typed", values.getLast().stringValue());
                    target.set("received", values.getFirst(), transaction);
                };
                NetworkAdapters.toPublic(registered).process(payload, details(), publicSender, publicReceiver);
                assertFalse(publicReceiver.contains("received"));
                assertEquals(0, notifications.get());
                if (commit) transaction.commit();
            assertEquals(commit, publicReceiver.contains("received"));
        }
        assertEquals(2, calls.get());
        assertEquals(1, notifications.get());
    }

    @Test
    void reverse_callback_bytecode_uses_typed_core_access_and_payload_keeps_native_identity() throws Exception {
        var nativeBody = cn.howxu.mmcr.api.network.RequestBody.of(Map.of("value", DataValue.of(9L)));
        var view = cn.howxu.mmcr.api.network.view.RequestBody.fromInternal(nativeBody);
        assertSame(nativeBody, view.toInternal());
        var payload = NetworkAdapters.wrap(view);
        NetworkAdapters.toPublic((RequestProcess) (body, request, sender, receiver) -> assertSame(nativeBody, body))
                .process(payload, details(), null, null);
        var typedCalls = new HashSet<String>();
        try (var stream = NetworkAdapters.class.getResourceAsStream("NetworkAdapters.class")) {
            assertNotNull(stream);
            ClassNode model = new ClassNode();
            new ClassReader(stream.readAllBytes()).accept(model, 0);
            for (var method : model.methods) {
                for (var element : method.instructions) {
                    if (!(element instanceof MethodInsnNode invocation)) continue;
                    assertNotEquals("bridgeValue", invocation.name);
                    if (invocation.name.equals("toInternal")) {
                        typedCalls.add(invocation.owner + invocation.desc);
                    }
                }
            }
        }
        assertTrue(typedCalls.contains("cn/howxu/mmcr/api/network/view/RequestBody()Lcn/howxu/mmcr/api/network/RequestBody;"));
        assertTrue(typedCalls.contains("cn/howxu/mmcr/api/data/view/DataStorage()Lcn/howxu/mmcr/api/data/DataStorage;"));
        assertTrue(typedCalls.contains("cn/howxu/mmcr/api/network/view/MachineReference()Lcn/howxu/mmcr/api/network/MachineReference;"));
    }

    @Test
    void delivered_callback_preserves_each_nullable_storage_and_propagates_original_exception() {
        var nativeStorage = new DataStorage();
        var publicStorage = store(nativeStorage);
        RequestPayload payload = RequestPayload.of(Map.of());
        AtomicInteger calls = new AtomicInteger();
        RequestProcess noSender = (body, request, sender, receiver) -> {
            assertNull(sender);
            assertSame(nativeStorage, receiver);
            calls.incrementAndGet();
        };
        NetworkAdapters.toPublic(noSender).process(payload, details(), null, publicStorage);
        RequestProcess noReceiver = (body, request, sender, receiver) -> {
            assertSame(nativeStorage, sender);
            assertNull(receiver);
            calls.incrementAndGet();
        };
        NetworkAdapters.toPublic(noReceiver).process(payload, details(), publicStorage, null);
        var failure = new IllegalStateException("registered handler failure");
        RequestProcess throwing = (body, request, sender, receiver) -> {
            assertNull(sender);
            assertNull(receiver);
            throw failure;
        };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> NetworkAdapters.toPublic(throwing).process(payload, details(), null, null)));
        RequestProcess immediateWriteThenFailure = (body, request, sender, receiver) -> {
            receiver.set("immediate", DataValue.of(true));
            throw failure;
        };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> NetworkAdapters.toPublic(immediateWriteThenFailure)
                .process(payload, details(), null, publicStorage)));
        assertEquals(DataValue.of(true), nativeStorage.get("immediate").orElseThrow());
        assertEquals(2, calls.get());
    }

    @Test
    void failed_callback_retains_view_delegate_typed_payload_target_peer_and_all_failure_values() {
        var nativeStorage = new DataStorage();
        DataStore publicStorage = store(nativeStorage);
        var viewStorage = StorageAdapters.unwrap(publicStorage);
        RequestPayload payload = RequestPayload.of(Map.of("failed", DataKey.of(true)));
        AtomicInteger calls = new AtomicInteger();
        for (RequestFailure reason : RequestFailure.values()) {
            RequestFailed registered = (body, request, sender, receivedReason) -> {
                assertSame(NetworkAdapters.unwrap(payload), body);
                assertSame(viewStorage, sender);
                assertEquals(REQUEST, request.requestId());
                assertEquals(PEER, request.peer().type());
                assertEquals(42L, request.peer().hash());
                assertEquals(RequestFailureReason.valueOf(reason.name()), receivedReason);
                sender.set("failed", body.get("failed").orElseThrow());
                calls.incrementAndGet();
            };
            NetworkAdapters.toPublic(registered).fail(payload, details(), publicStorage, reason);
        }
        assertEquals(DataValue.of(true), nativeStorage.get("failed").orElseThrow());
        assertEquals(RequestFailure.values().length, calls.get());
        var failure = new IllegalStateException("registered failure callback exception");
        RequestFailed throwing = (body, request, sender, reason) -> {
            assertNull(sender);
            assertEquals(PEER, request.peer().type());
            throw failure;
        };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> NetworkAdapters.toPublic(throwing)
                .fail(payload, details(), null, RequestFailure.UNREACHABLE)));
    }

    private static DataStore store(DataStorage value) {
        return StorageAdapters.wrap(cn.howxu.mmcr.api.data.view.DataStorage.view(value));
    }
    private static RequestDetails details() {
        return NetworkAdapters.wrap(new RequestInfo(REQUEST, new MachineReference(PEER, 42L)));
    }
}
