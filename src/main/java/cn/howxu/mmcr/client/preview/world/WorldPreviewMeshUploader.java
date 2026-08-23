package cn.howxu.mmcr.client.preview.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Uploads compiled preview layers and transfers their ownership to an uploaded mesh.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class WorldPreviewMeshUploader {
    private static final List<ChunkSectionLayer> LAYER_ORDER = List.of(
            ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT, ChunkSectionLayer.TRANSLUCENT);

    private WorldPreviewMeshUploader() { }

    public static UploadedWorldPreviewMesh upload(WorldPreviewMesh mesh) {
        RenderSystem.assertOnRenderThread();
        return upload(mesh, new GpuBackend());
    }

    static UploadedWorldPreviewMesh upload(WorldPreviewMesh mesh, Backend backend) {
        Map<ChunkSectionLayer, AutoCloseable> resources = new EnumMap<>(ChunkSectionLayer.class);
        try {
            for (ChunkSectionLayer layer : LAYER_ORDER) {
                MeshData layerMesh = mesh.meshes().get(layer);
                if (layerMesh != null) resources.put(layer, backend.upload(layer, layerMesh));
            }
            return new UploadedWorldPreviewMesh(mesh, resources);
        } catch (RuntimeException exception) {
            closeResources(resources.values(), exception);
            try {
                mesh.close();
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    interface Backend {
        AutoCloseable upload(ChunkSectionLayer layer, MeshData mesh);
    }

    private static final class GpuBackend implements Backend {
        @Override
        public AutoCloseable upload(ChunkSectionLayer layer, MeshData mesh) {
            GpuBuffer vertices = RenderSystem.getDevice().createBuffer(
                    () -> "MMCR world preview " + layer + " vertices", GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer());
            GpuBuffer indices = null;
            try {
                if (mesh.indexBuffer() != null) {
                    indices = RenderSystem.getDevice().createBuffer(
                            () -> "MMCR world preview " + layer + " indices", GpuBuffer.USAGE_INDEX,
                            mesh.indexBuffer());
                }
                return new GpuResources(vertices, indices);
            } catch (RuntimeException exception) {
                vertices.close();
                if (indices != null) indices.close();
                throw exception;
            }
        }
    }

    private static final class GpuResources implements AutoCloseable {
        private final GpuBuffer vertices;
        private final GpuBuffer indices;

        private GpuResources(GpuBuffer vertices, GpuBuffer indices) {
            this.vertices = vertices;
            this.indices = indices;
        }

        @Override
        public void close() {
            if (indices != null) indices.close();
            vertices.close();
        }
    }

    private static void closeResources(Iterable<? extends AutoCloseable> resources, RuntimeException failure) {
        List<AutoCloseable> remaining = new ArrayList<>();
        resources.forEach(remaining::add);
        for (AutoCloseable resource : remaining) {
            try {
                resource.close();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }
}
