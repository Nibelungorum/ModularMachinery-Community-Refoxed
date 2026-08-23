package cn.howxu.mmcr.client.preview.world;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies render-thread mesh upload ownership without requiring a live GPU.
 *
 * @author howxu <dev@howxu.cn>
 */
class WorldPreviewMeshUploaderTest {
    @Test
    void uploadCreatesOneResourcePerStartedLayer() {
        RecordingBackend backend = new RecordingBackend();
        WorldPreviewMesh mesh = mesh(ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT);

        UploadedWorldPreviewMesh uploaded = WorldPreviewMeshUploader.upload(mesh, backend);

        assertThat(backend.uploads).containsExactly(ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT);
        uploaded.close();
    }

    @Test
    void failedUploadClosesAlreadyCreatedResources() {
        RecordingBackend backend = new RecordingBackend();
        backend.failOn = ChunkSectionLayer.CUTOUT;
        WorldPreviewMesh mesh = mesh(ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT);

        assertThatThrownBy(() -> WorldPreviewMeshUploader.upload(mesh, backend))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected upload failure");
        assertThat(backend.closed).containsExactly(ChunkSectionLayer.SOLID);
    }

    @Test
    void closeIsIdempotent() {
        RecordingBackend backend = new RecordingBackend();
        UploadedWorldPreviewMesh uploaded = WorldPreviewMeshUploader.upload(
                mesh(ChunkSectionLayer.SOLID), backend);

        uploaded.close();
        uploaded.close();

        assertThat(backend.closed).containsExactly(ChunkSectionLayer.SOLID);
    }

    private static WorldPreviewMesh mesh(ChunkSectionLayer... layers) {
        Map<ChunkSectionLayer, MeshData> meshes = new EnumMap<>(ChunkSectionLayer.class);
        for (ChunkSectionLayer layer : layers) meshes.put(layer, nonEmptyMeshData());
        return new WorldPreviewMesh(() -> { }, meshes, null, Set.of());
    }

    private static MeshData nonEmptyMeshData() {
        ByteBufferBuilder buffer = new ByteBufferBuilder(64);
        buffer.reserve(1);
        return new MeshData(buffer.build(), new MeshData.DrawState(DefaultVertexFormat.BLOCK, 1, 0,
                VertexFormat.Mode.QUADS, VertexFormat.IndexType.SHORT));
    }

    private static final class RecordingBackend implements WorldPreviewMeshUploader.Backend {
        private final java.util.List<ChunkSectionLayer> uploads = new java.util.ArrayList<>();
        private final java.util.List<ChunkSectionLayer> closed = new java.util.ArrayList<>();
        private ChunkSectionLayer failOn;

        @Override
        public AutoCloseable upload(ChunkSectionLayer layer, MeshData mesh) {
            uploads.add(layer);
            if (layer == failOn) throw new IllegalStateException("injected upload failure");
            return () -> closed.add(layer);
        }
    }
}
