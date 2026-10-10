package cn.howxu.mmcr.client.controller.ui;

import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.RequestType;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Result;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Status;
import cn.howxu.mmcr.internal.network.ui.ControllerUiPayloadCodec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Controlled deadlines, bounded waiting and response ownership.
 * @author howxu <dev@howxu.cn> */
class PendingUiRequestsTest {
    private static final StreamCodec<RegistryFriendlyByteBuf, Integer> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value), RegistryFriendlyByteBuf::readVarInt);
    private static final RequestType<Integer, Integer> TYPE = new RequestType<>(Identifier.parse("test:request"), 1, CODEC, CODEC);

    @Test
    void timeout_at_deadline_ignores_late_response_and_uses_wrapping_monotonic_time() {
        var now = new AtomicLong(Long.MAX_VALUE - Duration.ofSeconds(5).toNanos());
        var pending = new PendingUiRequests(now::get);
        var entry = pending.add(TYPE);
        now.addAndGet(Duration.ofSeconds(10).toNanos() - 1);
        pending.expire();
        assertThat(entry.result().toCompletableFuture()).isNotDone();
        now.incrementAndGet();
        pending.respond(entry.requestId(), Status.SUCCESS, Optional.empty(), encode(42), RegistryAccess.EMPTY);
        assertThat(entry.result().toCompletableFuture().join().status()).isEqualTo(Status.TIMEOUT);
        assertThat(pending.add(TYPE).requestId()).isGreaterThan(entry.requestId());
    }

    @Test
    void busy_does_not_send_or_consume_id_and_expiry_recovers_capacity() {
        var now = new AtomicLong();
        var pending = new PendingUiRequests(now::get);
        var waiting = new ArrayList<PendingUiRequests.Entry<Integer>>();
        for (int i = 0; i < 32; i++) waiting.add(pending.add(TYPE));
        var overflow = pending.add(TYPE);
        assertThat(overflow.requestId()).isZero();
        assertThat(overflow.result().toCompletableFuture().join().status()).isEqualTo(Status.BUSY);
        now.set(Duration.ofSeconds(11).toNanos());
        var next = pending.add(TYPE);
        assertThat(next.requestId()).isEqualTo(waiting.getLast().requestId() + 1);
        assertThat(waiting).allSatisfy(entry -> assertThat(entry.result().toCompletableFuture().join().status()).isEqualTo(Status.TIMEOUT));
    }

    @Test
    void caller_cannot_complete_internal_future_and_responses_are_exactly_once() {
        var pending = new PendingUiRequests(() -> 0);
        var entry = pending.add(TYPE);
        entry.result().toCompletableFuture().complete(Result.success(99));
        pending.respond(entry.requestId(), Status.SUCCESS, Optional.empty(), encode(7), RegistryAccess.EMPTY);
        pending.respond(entry.requestId(), Status.SUCCESS, Optional.empty(), encode(8), RegistryAccess.EMPTY);
        assertThat(entry.result().toCompletableFuture().join().value()).contains(7);
        var invalid = pending.add(TYPE);
        pending.respond(invalid.requestId(), Status.SUCCESS, Optional.empty(), new byte[]{1, 2}, RegistryAccess.EMPTY);
        assertThat(invalid.result().toCompletableFuture().join().status()).isEqualTo(Status.INVALID_REQUEST);
    }

    @Test
    void close_completes_all_waiters_once_and_reentrant_timeout_callback_can_add() {
        var now = new AtomicLong();
        var pending = new PendingUiRequests(now::get);
        var first = pending.add(TYPE);
        var second = pending.add(TYPE);
        first.result().thenRun(() -> pending.add(TYPE));
        now.set(Duration.ofSeconds(10).toNanos());
        pending.expire();
        assertThat(second.result().toCompletableFuture().join().status()).isEqualTo(Status.TIMEOUT);
        var waiting = pending.add(TYPE);
        pending.close();
        pending.close();
        assertThat(waiting.result().toCompletableFuture().join().status()).isEqualTo(Status.CLOSED);
        assertThat(pending.add(TYPE).result().toCompletableFuture().join().status()).isEqualTo(Status.CLOSED);
    }

    private static byte[] encode(int value) {
        return ControllerUiPayloadCodec.encodeBounded(CODEC, value, RegistryAccess.EMPTY, ControllerUiPayloadCodec.RESPONSE_LIMIT);
    }
}
