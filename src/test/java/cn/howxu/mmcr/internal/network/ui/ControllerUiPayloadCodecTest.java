package cn.howxu.mmcr.internal.network.ui;

import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Status;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.HeaderData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.LaneData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.LaneProgress;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.RecipeData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.OutputData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.TextLineData;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Behavior tests for malformed framing, exact decoding, ownership and capacity failures.
 * @author howxu <dev@howxu.cn>
 */
class ControllerUiPayloadCodecTest {
    private static final UUID SESSION = UUID.randomUUID();
    private static final StreamCodec<RegistryFriendlyByteBuf, Integer> INT = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value), RegistryFriendlyByteBuf::readVarInt);
    private static final OutputType<MutableOutput> OUTPUT = new OutputType.Definition<>(id("ui_wire_output"),
            Codec.LONG.xmap(MutableOutput::new, value -> value.amount).fieldOf("amount"),
            (value, chance) -> value, (value, modifiers) -> value, value -> value,
            RecipeSyncCodec.of(16, (buffer, value) -> buffer.writeLong(value.amount),
                    buffer -> new MutableOutput(buffer.readLong()), value -> {}));

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        OutputRegistry.register(OUTPUT);
    }

    @Test
    void exact_body_round_trip_releases_encoder_and_decoder_buffers_and_keeps_input_owned() {
        AtomicReference<RegistryFriendlyByteBuf> encoded = new AtomicReference<>();
        AtomicReference<RegistryFriendlyByteBuf> decoded = new AtomicReference<>();
        StreamCodec<RegistryFriendlyByteBuf, Integer> codec = StreamCodec.of((buffer, value) -> {
            encoded.set(buffer);
            assertThat(buffer.getConnectionType()).isEqualTo(ConnectionType.NEOFORGE);
            buffer.writeVarInt(value);
        }, buffer -> {
            decoded.set(buffer);
            int value = buffer.readVarInt();
            buffer.setByte(0, 0); // Decoder cannot mutate the caller's retained bytes.
            return value;
        });
        byte[] bytes = ControllerUiPayloadCodec.encodeBounded(codec, 200, RegistryAccess.EMPTY, 16);
        byte[] baseline = bytes.clone();
        assertThat(ControllerUiPayloadCodec.decodeExact(codec, bytes, RegistryAccess.EMPTY, 16)).isEqualTo(200);
        assertThat(bytes).containsExactly(baseline);
        assertThat(encoded.get().refCnt()).isZero();
        assertThat(decoded.get().refCnt()).isZero();
    }

    @Test
    void tail_underread_overread_and_throwing_decoders_release_their_independent_buffers() {
        AtomicReference<RegistryFriendlyByteBuf> seen = new AtomicReference<>();
        StreamCodec<RegistryFriendlyByteBuf, Integer> underread = StreamCodec.of(INT::encode, b -> {
            seen.set(b);
            return b.readVarInt();
        });
        assertThatThrownBy(() -> ControllerUiPayloadCodec.decodeExact(underread, new byte[]{1, 2}, RegistryAccess.EMPTY, 4))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unread");
        assertThat(seen.get().refCnt()).isZero();
        StreamCodec<RegistryFriendlyByteBuf, Integer> overread = StreamCodec.of(INT::encode, b -> {
            seen.set(b);
            return b.readInt();
        });
        assertThatThrownBy(() -> ControllerUiPayloadCodec.decodeExact(overread, new byte[]{1}, RegistryAccess.EMPTY, 4))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(seen.get().refCnt()).isZero();
        StreamCodec<RegistryFriendlyByteBuf, Integer> throwing = StreamCodec.of(INT::encode, b -> {
            seen.set(b);
            throw new IllegalStateException("test decoder");
        });
        assertThatThrownBy(() -> ControllerUiPayloadCodec.decodeExact(throwing, new byte[]{1}, RegistryAccess.EMPTY, 4))
                .isInstanceOf(IllegalStateException.class);
        assertThat(seen.get().refCnt()).isZero();
    }

    @Test
    void encoder_capacity_is_enforced_during_encoding_and_released_on_overflow_or_exception() {
        AtomicReference<RegistryFriendlyByteBuf> seen = new AtomicReference<>();
        AtomicInteger written = new AtomicInteger();
        StreamCodec<RegistryFriendlyByteBuf, Integer> overflow = StreamCodec.of((b, value) -> {
            seen.set(b);
            for (int i = 0; i < 100; i++) {
                b.writeByte(i);
                written.incrementAndGet();
            }
        }, RegistryFriendlyByteBuf::readVarInt);
        assertThatThrownBy(() -> ControllerUiPayloadCodec.encodeBounded(overflow, 1, RegistryAccess.EMPTY, 8))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(written).hasValue(8);
        assertThat(seen.get().refCnt()).isZero();
        StreamCodec<RegistryFriendlyByteBuf, Integer> throwing = StreamCodec.of((b, value) -> {
            seen.set(b);
            throw new IllegalStateException("test encoder");
        }, RegistryFriendlyByteBuf::readVarInt);
        assertThatThrownBy(() -> ControllerUiPayloadCodec.encodeBounded(throwing, 1, RegistryAccess.EMPTY, 8))
                .isInstanceOf(IllegalStateException.class);
        assertThat(seen.get().refCnt()).isZero();
    }

    @Test
    void oversized_body_never_invokes_decoder_and_bad_declared_lengths_never_allocate_body() {
        AtomicInteger calls = new AtomicInteger();
        StreamCodec<RegistryFriendlyByteBuf, Integer> codec = StreamCodec.of(INT::encode, b -> {
            calls.incrementAndGet();
            return b.readVarInt();
        });
        assertThatThrownBy(() -> ControllerUiPayloadCodec.decodeExact(codec, new byte[17], RegistryAccess.EMPTY, 16))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
        for (int length : new int[]{-1, Integer.MAX_VALUE, 17, 2}) {
            RegistryFriendlyByteBuf b = buffer();
            try {
                b.writeVarInt(length);
                b.writeByte(1);
                assertThatThrownBy(() -> ControllerUiPayloadCodec.readBody(b, 16))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThat(b.readableBytes()).isEqualTo(1);
            } finally { b.release(); }
        }
    }

    @Test
    void whole_packet_is_capacity_bounded_and_rejects_excess_bytes_before_running_its_decoder() {
        AtomicInteger decodes = new AtomicInteger();
        var codec = ControllerUiPayloadCodec.<Integer>packetCodec((buffer, value) -> {
            for (int i = 0; i < value; i++) buffer.writeByte(0);
        }, buffer -> { decodes.incrementAndGet(); return buffer.readableBytes(); }, 16);
        RegistryFriendlyByteBuf b = buffer();
        try {
            assertThatThrownBy(() -> codec.encode(b, 17)).isInstanceOf(IndexOutOfBoundsException.class);
            assertThat(b.writerIndex()).isZero(); // No partial header/body escapes an unsuccessful encoder.
            b.writeZero(17);
            assertThatThrownBy(() -> codec.decode(b)).isInstanceOf(IllegalArgumentException.class);
            assertThat(decodes).hasValue(0);
            assertThat(b.readerIndex()).isZero();
        } finally { b.release(); }
    }

    @Test
    void all_opaque_envelopes_copy_on_construction_and_read_and_reject_invalid_headers() {
        byte[] source = {1, 2};
        var request = new PktControllerUiRequestPayload(3, SESSION, 1, id("request"), 1, Optional.of("base"), source);
        var response = new PktControllerUiResponsePayload(3, SESSION, 1, Status.SUCCESS, Optional.empty(), source);
        var state = new PktControllerUiCustomStatePayload(3, SESSION, id("state"), 1, 0, source);
        source[0] = 9;
        request.body()[1] = 9;
        response.body()[1] = 9;
        state.body()[1] = 9;
        assertThat(roundTrip(PktControllerUiRequestPayload.STREAM_CODEC, request).body()).containsExactly(1, 2);
        assertThat(roundTrip(PktControllerUiResponsePayload.STREAM_CODEC, response).body()).containsExactly(1, 2);
        assertThat(roundTrip(PktControllerUiCustomStatePayload.STREAM_CODEC, state).body()).containsExactly(1, 2);
        assertThatThrownBy(() -> new PktControllerUiRequestPayload(-1, SESSION, 1, id("request"), 1, Optional.empty(), source))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktControllerUiRequestPayload(3, SESSION, 0, id("request"), 1, Optional.empty(), source))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktControllerUiRequestPayload(3, SESSION, 1, id("request"), 0, Optional.empty(), source))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktControllerUiResponsePayload(3, SESSION, 1, Status.REJECTED, Optional.empty(), source))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktControllerUiCustomStatePayload(3, SESSION, id("state"), 1, -1, source))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failed_response_reason_owns_nested_translatable_arguments() {
        var argument = Component.translatable("test:argument");
        var reason = Component.translatable("test:reason", argument);
        var packet = new PktControllerUiResponsePayload(3, SESSION, 1, Status.REJECTED, Optional.of(reason), new byte[0]);
        argument.append(Component.literal("changed"));
        packet.reason().orElseThrow().copy().append(Component.literal("other"));
        var copy = roundTrip(PktControllerUiResponsePayload.STREAM_CODEC, packet);
        assertThat(copy.reason().orElseThrow()).isEqualTo(Component.translatable("test:reason", Component.translatable("test:argument")));
    }

    @Test
    void full_snapshot_round_trip_preserves_header_storage_availability_recipe_and_both_text_scopes() {
        var failure = ExecutionStatus.blocked(id("blocked"), id("machine"),
                FailureOccurrence.at(BuiltinFailureReasons.MISSING_ENERGY, id("machine"), FailurePhase.RUNTIME,
                        id("recipe"), null, Map.of("required", "20")));
        var header = new HeaderData(id("machine"), ControllerUiSnapshot.Kind.FACTORY, ControllerUiSnapshot.Role.HOST,
                Component.translatable("test:machine"), true, true, false, 2, id("host"), 1, 3,
                List.of(id("level")), 2, 16, 1, 1, List.of(id("pool")), id("pool"), failure, true,
                Map.of("counter", DataValue.fromInternal(cn.howxu.mmcr.api.data.DataValue.of(12))),
                List.of(new TextLineData(id("global"), ControllerUiSnapshot.TextLine.Scope.CONTROLLER, Component.translatable("test:global"))));
        var resource = new MutableOutput(2);
        var output = new OutputData(resource, 8);
        resource.amount = 99;
        var lane = new LaneData("base", 0, true, false, true, id("recipe"), 2, 10, 4, failure,
                List.of(new TextLineData(id("lane"), ControllerUiSnapshot.TextLine.Scope.OPERATION, Component.translatable("test:lane"))),
                new RecipeData(List.of(output), 20, 0, 2.5, 10, 4));
        var snapshot = new ControllerUiSnapshotData(SESSION, 1, true, Level.OVERWORLD, new BlockPos(1, 2, 3), header, List.of(lane));
        var packet = new PktControllerUiSnapshotPayload(3, SESSION, 1, snapshot);
        var decoded = roundTrip(PktControllerUiSnapshotPayload.STREAM_CODEC, packet);
        assertThat(decoded.snapshotData()).isEqualTo(snapshot);
        var decodedOutput = decoded.snapshotData().laneData().getFirst().recipe().outputData().getFirst();
        assertThat(decodedOutput.amount()).isEqualTo(8);
        assertThat(decodedOutput.resource().amount()).isEqualTo(2);
        ((MutableOutput) decodedOutput.resource()).amount = 500;
        assertThat(decodedOutput.resource().amount()).isEqualTo(2);
        var tooManyOutputs = new LaneData("base", 0, true, false, true, id("recipe"), 2, 10, 4, null, List.of(),
                new RecipeData(Collections.nCopies(ControllerUiPayloadCodec.MAX_OUTPUTS + 1, output), 0, 0, 0, 10, 4));
        var oversized = new PktControllerUiSnapshotPayload(3, SESSION, 1,
                new ControllerUiSnapshotData(SESSION, 1, true, Level.OVERWORLD, BlockPos.ZERO, header, List.of(tooManyOutputs)));
        assertThatThrownBy(() -> roundTrip(PktControllerUiSnapshotPayload.STREAM_CODEC, oversized))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("list count");
        assertThatThrownBy(() -> new PktControllerUiSnapshotPayload(3, SESSION, 2, snapshot))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktControllerUiSnapshotPayload(3, UUID.randomUUID(), 1, snapshot))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void progress_rejects_duplicate_lanes_bad_baselines_oversized_lists_and_tail_bytes() {
        var progress = new LaneProgress("base", id("recipe"), 3, 10, 4);
        var packet = new PktControllerUiProgressPayload(3, SESSION, 2, 1, List.of(progress));
        assertThat(roundTrip(PktControllerUiProgressPayload.STREAM_CODEC, packet)).isEqualTo(packet);
        assertThatThrownBy(() -> new PktControllerUiProgressPayload(3, SESSION, 1, 1, List.of(progress)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktControllerUiProgressPayload(3, SESSION, 2, 1, List.of(progress, progress)))
                .isInstanceOf(IllegalArgumentException.class);
        RegistryFriendlyByteBuf b = buffer();
        try {
            b.writeVarInt(3); b.writeUUID(SESSION); b.writeLong(2); b.writeLong(1);
            b.writeVarInt(Integer.MAX_VALUE);
            assertThatThrownBy(() -> PktControllerUiProgressPayload.STREAM_CODEC.decode(b))
                    .isInstanceOf(IllegalArgumentException.class);
            b.clear();
            PktControllerUiProgressPayload.STREAM_CODEC.encode(b, packet);
            b.writeByte(0);
            assertThatThrownBy(() -> PktControllerUiProgressPayload.STREAM_CODEC.decode(b))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unread");
        } finally { b.release(); }
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        return ControllerUiPayloadCodec.decodeExact(codec,
                ControllerUiPayloadCodec.encodeBounded(codec, value, RegistryAccess.EMPTY, ControllerUiPayloadCodec.SNAPSHOT_LIMIT),
                RegistryAccess.EMPTY, ControllerUiPayloadCodec.SNAPSHOT_LIMIT);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static Identifier id(String path) { return Identifier.parse("test:" + path); }

    /** Mutable addon value with deliberately aliasing copy; wire/cache must still own its bytes.
     * @author howxu <dev@howxu.cn>
     */
    private static final class MutableOutput implements MachineOutput {
        private long amount;
        private MutableOutput(long amount) { this.amount = amount; }
        public OutputType<MutableOutput> outputType() { return OUTPUT; }
        public float chance() { return 1; }
        public long amount() { return amount; }
    }
}
