package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the final factory snapshot payload boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class PktFactoryControllerStatePayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void supported_thread_counts_round_trip_without_truncation() {
        for (int count : List.of(1, 65, 128)) {
            FactorySnapshot snapshot = snapshot(count);
            RegistryFriendlyByteBuf buffer = buffer();

            PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer,
                    new PktFactoryControllerStatePayload(BlockPos.ZERO, snapshot));

            assertThat(PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer).snapshot())
                    .isEqualTo(snapshot);
            buffer.release();
        }
    }

    @Test
    void decoder_rejects_oversized_thread_list_before_allocation() {
        RegistryFriendlyByteBuf buffer = header(0, 1, 0, 1, 0, PktFactoryControllerStatePayload.MAX_THREAD_SNAPSHOTS + 1);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    @Test
    void encoder_rejects_oversized_thread_snapshot() {
        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(BlockPos.ZERO,
                        snapshot(PktFactoryControllerStatePayload.MAX_THREAD_SNAPSHOTS + 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encoder_rejects_oversized_machine_level_snapshot() {
        FactorySnapshot snapshot = new FactorySnapshot(false, false, List.of(), 0, 1, 0, 1,
                false, List.of(FactoryRuntime.ThreadSnapshot.idleBase()), "", 0, null,
                IntStream.range(0, PktFactoryControllerStatePayload.MAX_LEVEL_SNAPSHOTS + 1)
                        .mapToObj(Integer::toString).toList());

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(BlockPos.ZERO, snapshot)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encoder_rejects_incomplete_presentation_lanes() {
        FactorySnapshot snapshot = new FactorySnapshot(false, false, List.of(), 0, 2, 0, 1,
                false, List.of(FactoryRuntime.ThreadSnapshot.idleBase()), "", 0, null, List.of());

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(BlockPos.ZERO, snapshot)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoder_rejects_active_lane_count_above_lane_limit() {
        RegistryFriendlyByteBuf buffer = header(0, 1, 2, 1, 0, 1);
        writeThread(buffer, 0);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    @Test
    void decoder_rejects_invalid_thread_progress_and_parallelism() {
        for (int[] values : List.of(new int[]{-1, 0, 1}, new int[]{2, 1, 1}, new int[]{0, 1, 0})) {
            RegistryFriendlyByteBuf buffer = header(0, 1, 0, 1, 0, 1);
            writeThread(buffer, 0, values[0], values[1], values[2]);

            assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                    .isInstanceOf(IllegalArgumentException.class);
            buffer.release();
        }
    }

    @Test
    void decoder_rejects_duplicate_thread_indexes() {
        RegistryFriendlyByteBuf buffer = header(0, 2, 0, 1, 0, 2);
        writeThread(buffer, 0);
        writeThread(buffer, 0);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
        buffer.release();
    }

    @Test
    void decoder_rejects_oversized_strings() {
        RegistryFriendlyByteBuf buffer = header(0, 1, 0, 1, 0, 0);
        buffer.clear();
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeBoolean(true);
        buffer.writeBoolean(false);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeBoolean(false);
        buffer.writeUtf("x".repeat(PktFactoryControllerStatePayload.MAX_STRING_LENGTH + 1));

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(RuntimeException.class);
        buffer.release();
    }

    private static FactorySnapshot snapshot(int count) {
        return new FactorySnapshot(false, false, List.of(), 0, count, 0, 1, false,
                IntStream.range(0, count).mapToObj(index -> new FactoryRuntime.ThreadSnapshot(index,
                        index == 0, false, false, "", 0, 0, 1, "", false, "")).toList(),
                "", 0, null, List.of());
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static RegistryFriendlyByteBuf header(int activeParallelism, int laneLimit, int activeLaneCount,
                                                  int maxParallelism, int parallelSlots, int threadCount) {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeBoolean(true);
        buffer.writeBoolean(false);
        buffer.writeVarInt(activeParallelism);
        buffer.writeVarInt(laneLimit);
        buffer.writeVarInt(activeLaneCount);
        buffer.writeVarInt(maxParallelism);
        buffer.writeBoolean(false);
        buffer.writeUtf("");
        buffer.writeVarInt(parallelSlots);
        buffer.writeVarInt(0);
        buffer.writeBoolean(false);
        buffer.writeVarInt(0);
        buffer.writeVarInt(threadCount);
        return buffer;
    }

    private static void writeThread(RegistryFriendlyByteBuf buffer, int index) {
        writeThread(buffer, index, 0, 0, 1);
    }

    private static void writeThread(RegistryFriendlyByteBuf buffer, int index, int tick, int totalTick,
                                    int parallelism) {
        buffer.writeVarInt(index);
        buffer.writeBoolean(index == 0);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeUtf("");
        buffer.writeVarInt(tick);
        buffer.writeVarInt(totalTick);
        buffer.writeVarInt(parallelism);
        buffer.writeUtf("");
        buffer.writeBoolean(false);
        buffer.writeUtf("");
    }
}
