package cn.howxu.mmcr.internal.network.ui;

import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Status;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the production dispatch gates and captured typed decoder/handler without fake world permissions.
 * @author howxu <dev@howxu.cn>
 */
class ControllerUiRequestDispatcherTest {
    private static final ResourceLocation MACHINE = ResourceLocation.parse("test:machine");
    private static final ResourceLocation MESSAGE = ResourceLocation.parse("test:message");
    private static final UUID SESSION = UUID.randomUUID();

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void unknown_message_wrong_machine_version_and_lane_do_not_call_author_decoder_or_context_or_handler() {
        AtomicInteger decoded = new AtomicInteger(), handled = new AtomicInteger(), contexts = new AtomicInteger();
        var protocols = protocols(decoded, handled);
        assertThat(dispatch(protocols, MACHINE, request(ResourceLocation.parse("test:unknown"), 1, new byte[]{7}), true, contexts).status())
                .isEqualTo(Status.UNSUPPORTED);
        assertThat(dispatch(protocols, ResourceLocation.parse("test:other"), request(MESSAGE, 1, new byte[]{7}), true, contexts).status())
                .isEqualTo(Status.UNSUPPORTED);
        assertThat(dispatch(protocols, MACHINE, request(MESSAGE, 2, new byte[]{7}), true, contexts).status())
                .isEqualTo(Status.VERSION_MISMATCH);
        assertThat(dispatch(protocols, MACHINE, request(MESSAGE, 1, new byte[]{7}), false, contexts).status())
                .isEqualTo(Status.INVALID_REQUEST);
        assertThat(decoded).hasValue(0);
        assertThat(handled).hasValue(0);
        assertThat(contexts).hasValue(0);
    }

    @Test
    void malformed_body_does_not_call_handler_and_success_uses_its_registered_response_codec() {
        AtomicInteger decoded = new AtomicInteger(), handled = new AtomicInteger(), contexts = new AtomicInteger();
        var protocols = protocols(decoded, handled);
        assertThat(dispatch(protocols, MACHINE, request(MESSAGE, 1, new byte[]{7, 0}), true, contexts).status())
                .isEqualTo(Status.INVALID_REQUEST);
        assertThat(decoded).hasValue(1);
        assertThat(handled).hasValue(0);
        assertThat(contexts).hasValue(0);
        var response = dispatch(protocols, MACHINE, request(MESSAGE, 1, new byte[]{7}), true, contexts);
        assertThat(response.status()).isEqualTo(Status.SUCCESS);
        assertThat(response.body()).containsExactly(8);
        assertThat(response.sessionId()).isEqualTo(SESSION);
        assertThat(response.requestId()).isEqualTo(1);
        assertThat(handled).hasValue(1);
        assertThat(contexts).hasValue(1);
    }

    @Test
    void rejected_handler_and_throwing_handler_or_response_encoder_return_empty_error_bodies() {
        var codec = codec(new AtomicInteger());
        var type = new UiProtocolRegistration.RequestType<>(MESSAGE, 1, codec, codec);
        var rejected = new UiProtocolRegistration(List.of(MACHINE));
        rejected.request(MACHINE, type, (context, value) -> UiProtocolRegistration.Result.reject(
                Component.translatable("test:rejected")));
        rejected.freeze();
        var response = dispatch(rejected, MACHINE, request(MESSAGE, 1, new byte[]{7}), true, new AtomicInteger());
        assertThat(response.status()).isEqualTo(Status.REJECTED);
        assertThat(response.reason()).isPresent();
        assertThat(response.body()).isEmpty();
        var failed = new UiProtocolRegistration(List.of(MACHINE));
        failed.request(MACHINE, type, (context, value) -> { throw new IllegalStateException("private handler detail"); });
        failed.freeze();
        response = dispatch(failed, MACHINE, request(MESSAGE, 1, new byte[]{7}), true, new AtomicInteger());
        assertThat(response.status()).isEqualTo(Status.HANDLER_FAILED);
        assertThat(response.body()).isEmpty();
        assertThat(response.reason().orElseThrow().getString()).doesNotContain("private handler detail");
        StreamCodec<RegistryFriendlyByteBuf, Integer> overflow = StreamCodec.of((b, value) -> {
            for (int i = 0; i <= ControllerUiPayloadCodec.RESPONSE_LIMIT; i++) b.writeByte(0);
        }, RegistryFriendlyByteBuf::readVarInt);
        var oversized = new UiProtocolRegistration(List.of(MACHINE));
        oversized.request(MACHINE, new UiProtocolRegistration.RequestType<>(MESSAGE, 1, codec, overflow),
                (context, value) -> UiProtocolRegistration.Result.success(value));
        oversized.freeze();
        assertThat(dispatch(oversized, MACHINE, request(MESSAGE, 1, new byte[]{7}), true, new AtomicInteger()).status())
                .isEqualTo(Status.HANDLER_FAILED);
    }

    @Test
    void monotonic_ids_include_invalid_and_busy_requests_and_tick_budget_never_replays_side_effects() {
        var window = new ControllerUiRequestDispatcher.RequestWindow();
        AtomicInteger decoded = new AtomicInteger(), handled = new AtomicInteger(), contexts = new AtomicInteger();
        var protocols = protocols(decoded, handled);
        for (long id = 1; id <= 16; id++) {
            assertThat(window.consume(id, 100)).isNull();
            var request = new PktControllerUiRequestPayload(3, SESSION, id, MESSAGE, 1, Optional.empty(), new byte[]{7});
            assertThat(dispatch(protocols, MACHINE, request, true, contexts).status()).isEqualTo(Status.SUCCESS);
        }
        assertThat(window.consume(17, 100)).isEqualTo(Status.BUSY);
        assertThat(window.consume(16, 100)).isEqualTo(Status.INVALID_REQUEST);
        assertThat(window.consume(17, 101)).isEqualTo(Status.INVALID_REQUEST);
        assertThat(window.consume(18, 101)).isNull();
        assertThat(window.consume(18, 102)).isEqualTo(Status.INVALID_REQUEST);
        assertThat(decoded).hasValue(16);
        assertThat(handled).hasValue(16);
    }

    private static UiProtocolRegistration protocols(AtomicInteger decoded, AtomicInteger handled) {
        var codec = codec(decoded);
        var protocols = new UiProtocolRegistration(List.of(MACHINE));
        protocols.request(MACHINE, new UiProtocolRegistration.RequestType<>(MESSAGE, 1, codec, codec), (context, value) -> {
            handled.incrementAndGet();
            return UiProtocolRegistration.Result.success(value + 1);
        });
        protocols.freeze();
        return protocols;
    }

    private static StreamCodec<RegistryFriendlyByteBuf, Integer> codec(AtomicInteger decoded) {
        return StreamCodec.of((b, value) -> b.writeVarInt(value), b -> {
            decoded.incrementAndGet();
            return b.readVarInt();
        });
    }

    private static PktControllerUiRequestPayload request(ResourceLocation id, int version, byte[] bytes) {
        return new PktControllerUiRequestPayload(3, SESSION, 1, id, version, Optional.of("base"), bytes);
    }

    private static PktControllerUiResponsePayload dispatch(UiProtocolRegistration protocols, ResourceLocation machineId,
            PktControllerUiRequestPayload request, boolean laneExists, AtomicInteger contexts) {
        return ControllerUiRequestDispatcher.dispatchRegistered(protocols, machineId, request, () -> laneExists,
                () -> { contexts.incrementAndGet(); return null; }, RegistryAccess.EMPTY);
    }
}
