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
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeBoolean(true);
        buffer.writeBoolean(false);
        buffer.writeVarInt(0);
        buffer.writeVarInt(count);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeUtf("Factory");
        buffer.writeVarInt(0);
        buffer.writeUtf("");
        buffer.writeVarInt(size);
        return buffer;
    }

    private static void writeThread(RegistryFriendlyByteBuf buffer, int index) {
        buffer.writeVarInt(index);
        buffer.writeBoolean(index == 0);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeUtf("");
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeUtf("");
        buffer.writeBoolean(false);
        buffer.writeUtf("");
    }
}
