package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author HowXu {@literal <dev@howxu.cn>}
 */
class StructureChunkAssemblerTest {
    private static final Identifier ID = MMCR.id("structure_transfer");

    @Test
    void productionSizeSlicesReassembleInIndexOrderWithoutPublishingPartialBytes() {
        byte[] original = new byte[2 * PktStructureChunkPayload.MAX_CHUNK_BYTES + 100];
        new Random(7).nextBytes(original);
        var input = Unpooled.wrappedBuffer(original);
        var output = Unpooled.buffer();
        try {
            var slices = StructureChunkAssembler.split(input, PktStructureChunkPayload.MAX_CHUNK_BYTES);
            assertThat(slices).hasSizeGreaterThan(1);
            var first = new PktStructureChunkPayload(1, ID, 0, slices.size(), slices.getFirst());
            var assembler = new StructureChunkAssembler(first);
            for (int i = slices.size() - 1; i > 0; i--) {
                assembler.accept(new PktStructureChunkPayload(1, ID, i, slices.size(), slices.get(i)));
            }
            assertThat(assembler.inflateInto(output)).isFalse();
            assertThat(output.isReadable()).isFalse();
            assembler.accept(first);
            assertThat(assembler.inflateInto(output)).isTrue();
            assertThat(output.equals(input)).isTrue();
        } finally {
            input.release();
            output.release();
        }
    }

    @Test
    void conflictingSliceCountsDoNotCompleteATransfer() {
        var first = new PktStructureChunkPayload(1, ID, 0, 2, new byte[]{1});
        var assembler = new StructureChunkAssembler(first);
        assembler.accept(first);
        assertThatThrownBy(() -> assembler.accept(new PktStructureChunkPayload(1, ID, 1, 3, new byte[]{2})))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
