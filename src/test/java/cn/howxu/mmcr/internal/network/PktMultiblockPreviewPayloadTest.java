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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import io.netty.buffer.Unpooled;
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
                Unpooled.buffer(),
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
    void payload_preserves_non_default_block_state_properties() {
        var state = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        var payload = new PktMultiblockPreviewPayload(
                Level.OVERWORLD,
                BlockPos.ZERO,
                List.of(new MultiblockPreviewSnapshot.Entry(BlockPos.ZERO, state)),
                200);

        var buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
        PktMultiblockPreviewPayload.STREAM_CODEC.encode(buffer, payload);
        var decoded = PktMultiblockPreviewPayload.STREAM_CODEC.decode(buffer);

        assertEquals(state, decoded.entries().getFirst().state());
    }

    @Test
    void clear_payload_has_empty_entries_and_zero_duration() {
        var payload = PktMultiblockPreviewPayload.clear(Level.OVERWORLD, new BlockPos(1, 2, 3));

        var buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
        PktMultiblockPreviewPayload.STREAM_CODEC.encode(buffer, payload);
        var decoded = PktMultiblockPreviewPayload.STREAM_CODEC.decode(buffer);

        assertEquals(ResourceKey.create(Registries.DIMENSION, Level.OVERWORLD.identifier()), decoded.dimension());
        assertEquals(new BlockPos(1, 2, 3), decoded.controllerPos());
        assertEquals(List.of(), decoded.entries());
        assertEquals(0, decoded.durationTicks());
    }

    @Test
    void payload_allows_up_to_131072_preview_entries() {
        List<MultiblockPreviewSnapshot.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 131073; i++) {
            entries.add(new MultiblockPreviewSnapshot.Entry(new BlockPos(i, 0, 0), Blocks.STONE.defaultBlockState()));
        }

        var payload = new PktMultiblockPreviewPayload(Level.OVERWORLD, BlockPos.ZERO, entries, 200);

        assertEquals(131072, payload.entries().size());
        assertEquals(new BlockPos(131071, 0, 0), payload.entries().getLast().relativePos());
    }
}
