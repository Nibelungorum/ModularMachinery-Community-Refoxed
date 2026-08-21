package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PktFactoryControllerStatePayloadTest {
    private static final int MAX_THREAD_SNAPSHOTS = 1024;
    private static final int MAX_STRING_LENGTH = 256;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void supported_thread_counts_round_trip_without_truncation() {
        for (int count : List.of(1, 65, 128)) {
            FactoryControllerSnapshot snapshot = snapshot(count);
            RegistryFriendlyByteBuf buffer = buffer();

            PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer,
                    new PktFactoryControllerStatePayload(snapshot));

            assertThat(PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer).snapshot())
                    .isEqualTo(snapshot);
        }
    }

    @Test
    void decoder_rejects_oversized_thread_list_before_allocation() {
        RegistryFriendlyByteBuf buffer = header(1, MAX_THREAD_SNAPSHOTS + 1);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encoder_rejects_oversized_thread_snapshot() {
        FactoryControllerSnapshot snapshot = snapshot(MAX_THREAD_SNAPSHOTS + 1);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(snapshot)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encoder_rejects_inconsistent_thread_count() {
        FactoryControllerSnapshot snapshot = new FactoryControllerSnapshot(BlockPos.ZERO, true, false,
                0, 2, 0, 1, List.of(FactoryRecipeScheduler.ThreadSnapshot.idleBase()));

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(snapshot)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoder_rejects_inconsistent_thread_count() {
        RegistryFriendlyByteBuf buffer = header(2, 1);
        writeThread(buffer, 0);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoder_rejects_negative_active_thread_count() {
        RegistryFriendlyByteBuf buffer = header(-1, 1, 1);
        writeThread(buffer, 0);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoder_rejects_malformed_numeric_snapshot_fields() {
        for (int[] values : List.of(
                new int[]{-1, 1, 0, 1},
                new int[]{0, 0, 0, 1},
                new int[]{0, 1, -1, 1},
                new int[]{0, 1, 0, 0},
                new int[]{0, 1, 0, 1, -1})) {
            RegistryFriendlyByteBuf buffer = values.length == 5
                    ? header(0, 1, 1, 0, 1, values[4])
                    : header(values[0], values[1], 1, values[2], values[3], 0);
            writeThread(buffer, 0, values.length == 5 ? 0 : -1, 0, 1);

            assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void decoder_rejects_invalid_thread_progress_and_parallelism() {
        for (int[] threadValues : List.of(
                new int[]{-1, 0, 1},
                new int[]{2, 1, 1},
                new int[]{0, 1, 0})) {
            RegistryFriendlyByteBuf buffer = header(0, 1, 1);
            writeThread(buffer, 0, threadValues[0], threadValues[1], threadValues[2]);

            assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void encoder_rejects_invalid_snapshot_relationships() {
        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(new FactoryControllerSnapshot(BlockPos.ZERO, true, false,
                        0, 1, -1, 1, "Factory", 0, "", List.of(
                        FactoryRecipeScheduler.ThreadSnapshot.idleBase())))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(new FactoryControllerSnapshot(BlockPos.ZERO, true, false,
                        0, 1, 0, 1, "Factory", -1, "", List.of(
                        FactoryRecipeScheduler.ThreadSnapshot.idleBase())))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer(),
                new PktFactoryControllerStatePayload(new FactoryControllerSnapshot(BlockPos.ZERO, true, false,
                        0, 1, 0, 1, "Factory", 0, "", List.of(
                        new FactoryRecipeScheduler.ThreadSnapshot(0, true, false, true,
                                "mmcr:recipe", 2, 1, 1))))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoder_rejects_active_thread_count_above_thread_count() {
        RegistryFriendlyByteBuf buffer = header(2, 1, 1);
        writeThread(buffer, 0);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoder_rejects_oversized_strings() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeBoolean(true);
        buffer.writeBoolean(false);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeVarInt(MAX_STRING_LENGTH * 4 + 1);
        buffer.writeZero(MAX_STRING_LENGTH * 4 + 1);
        buffer.writeVarInt(0);
        buffer.writeUtf("");
        buffer.writeVarInt(1);
        buffer.writeBoolean(true);

        assertThatThrownBy(() -> PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer))
                .isInstanceOf(RuntimeException.class);
    }

    private static FactoryControllerSnapshot snapshot(int count) {
        return new FactoryControllerSnapshot(BlockPos.ZERO, true, false, 0, count, 0, 1,
                "Factory", 0, "", java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new FactoryRecipeScheduler.ThreadSnapshot(index, index == 0, false,
                        false, "", 0, 0, 1)).toList());
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static RegistryFriendlyByteBuf header(int count, int size) {
        return header(0, count, size);
    }

    private static RegistryFriendlyByteBuf header(int active, int count, int size) {
        return header(active, count, size, 0, 1, 0);
    }

    private static RegistryFriendlyByteBuf header(int active, int count, int size,
                                                  int currentParallelism, int maxParallelism, int parallelSlots) {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeBoolean(true);
        buffer.writeBoolean(false);
        buffer.writeVarInt(active);
        buffer.writeVarInt(count);
        buffer.writeVarInt(currentParallelism);
        buffer.writeVarInt(maxParallelism);
        buffer.writeUtf("Factory");
        buffer.writeVarInt(parallelSlots);
        buffer.writeUtf("");
        buffer.writeVarInt(size);
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
