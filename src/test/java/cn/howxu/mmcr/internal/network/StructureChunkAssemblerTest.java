package cn.howxu.mmcr.internal.network;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the fix for {@code EncoderException: Failed to encode packet 'clientbound/minecraft:custom_payload'}:
 * one multiblock pattern is several megabytes, vanilla caps a single custom payload at 1 MiB, so structures
 * are deflated and sliced and the client reassembles them.
 */
class StructureChunkAssemblerTest {

    private static final int VANILLA_PAYLOAD_CAP = 1_048_576;

    private static byte[] payloadLikeStructure() {
        // Run-like repetitive content, which is what a structure encoding actually looks like.
        StringBuilder builder = new StringBuilder();
        Random random = new Random(7);
        for (int run = 0; run < 200_000; run++) {
            builder.append((char) ('A' + random.nextInt(6)));
            builder.append((char) run);
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void deflate_round_trips_exactly() {
        byte[] raw = payloadLikeStructure();
        byte[] compressed = StructureChunkAssembler.deflate(raw);

        assertThat(compressed.length).isLessThan(raw.length);
        assertThat(StructureChunkAssembler.inflate(compressed, raw.length * 2)).isEqualTo(raw);
    }

    @Test
    void slices_stay_under_the_payload_cap_and_reassemble() {
        byte[] raw = payloadLikeStructure();
        byte[] compressed = StructureChunkAssembler.deflate(raw);
        List<byte[]> slices = StructureChunkAssembler.split(compressed, PktStructureChunkPayload.MAX_CHUNK_BYTES);

        assertThat(slices).isNotEmpty();
        assertThat(slices).allSatisfy(slice ->
                assertThat(slice.length).isLessThanOrEqualTo(PktStructureChunkPayload.MAX_CHUNK_BYTES));
        assertThat(PktStructureChunkPayload.MAX_CHUNK_BYTES).isLessThan(VANILLA_PAYLOAD_CAP);

        int total = slices.stream().mapToInt(slice -> slice.length).sum();
        byte[] joined = new byte[total];
        int offset = 0;
        for (byte[] slice : slices) {
            System.arraycopy(slice, 0, joined, offset, slice.length);
            offset += slice.length;
        }
        assertThat(joined).isEqualTo(compressed);
        assertThat(StructureChunkAssembler.inflate(joined, raw.length * 2)).isEqualTo(raw);
    }

    @Test
    void transfer_completes_only_when_every_slice_arrived() {
        byte[] raw = payloadLikeStructure();
        List<byte[]> slices = StructureChunkAssembler.split(
                StructureChunkAssembler.deflate(raw), PktStructureChunkPayload.MAX_CHUNK_BYTES);
        var machineId = cn.howxu.mmcr.MMCR.id("chunk_test");
        var transfer = new StructureChunkAssembler.Transfer(3L);

        for (int index = 0; index < slices.size() - 1; index++) {
            transfer.accept(machineId, index, slices.size(), slices.get(index));
        }
        assertThat(transfer.isComplete(machineId)).isFalse();
        assertThat(transfer.assemble(machineId)).isNull();

        int last = slices.size() - 1;
        transfer.accept(machineId, last, slices.size(), slices.get(last));
        assertThat(transfer.isComplete(machineId)).isTrue();
        assertThat(transfer.assemble(machineId)).isEqualTo(raw);
    }

    @Test
    void inconsistent_chunk_counts_are_rejected() {
        var machineId = cn.howxu.mmcr.MMCR.id("chunk_conflict");
        var transfer = new StructureChunkAssembler.Transfer(1L);
        transfer.accept(machineId, 0, 2, new byte[]{1});

        assertThatThrownBy(() -> transfer.accept(machineId, 1, 3, new byte[]{2}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Inconsistent chunk count");
    }

    @Test
    void oversized_chunks_cannot_be_constructed() {
        assertThatThrownBy(() -> new PktStructureChunkPayload(1L, cn.howxu.mmcr.MMCR.id("too_big"),
                0, 1, new byte[PktStructureChunkPayload.MAX_CHUNK_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk size");
    }

    @Test
    void inflate_refuses_to_explode_past_its_limit() {
        byte[] raw = new byte[4096];
        byte[] compressed = StructureChunkAssembler.deflate(raw);

        assertThatThrownBy(() -> StructureChunkAssembler.inflate(compressed, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }
}
