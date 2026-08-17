package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PktMultiblockPreviewPayloadTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void payload_preserves_preview_snapshot_fields() {
        var payload = new PktMultiblockPreviewPayload(
                Level.OVERWORLD,
                new BlockPos(1, 2, 3),
                List.of(new MultiblockPreviewSnapshot.Entry(new BlockPos(4, 5, 6), Blocks.IRON_BLOCK.defaultBlockState())),
                200);

        var buffer = new RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
        PktMultiblockPreviewPayload.STREAM_CODEC.encode(buffer, payload);
        var decoded = PktMultiblockPreviewPayload.STREAM_CODEC.decode(buffer);

        assertEquals(ResourceKey.create(Registries.DIMENSION, Level.OVERWORLD.identifier()), decoded.dimension());
        assertEquals(new BlockPos(1, 2, 3), decoded.controllerPos());
        assertEquals(new BlockPos(4, 5, 6), decoded.entries().getFirst().relativePos());
        assertEquals(Blocks.IRON_BLOCK.defaultBlockState(), decoded.entries().getFirst().state());
        assertEquals(200, decoded.durationTicks());
    }

    @Test
    void clear_payload_has_empty_entries_and_zero_duration() {
        var payload = PktMultiblockPreviewPayload.clear(Level.OVERWORLD, new BlockPos(1, 2, 3));

        var buffer = new RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
        PktMultiblockPreviewPayload.STREAM_CODEC.encode(buffer, payload);
        var decoded = PktMultiblockPreviewPayload.STREAM_CODEC.decode(buffer);

        assertEquals(ResourceKey.create(Registries.DIMENSION, Level.OVERWORLD.identifier()), decoded.dimension());
        assertEquals(new BlockPos(1, 2, 3), decoded.controllerPos());
        assertEquals(List.of(), decoded.entries());
        assertEquals(0, decoded.durationTicks());
    }
}
