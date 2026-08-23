package cn.howxu.mmcr.client.preview.world;

import cn.howxu.mmcr.mixin.client.preview.MeshDataAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix4fc;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;

/**
 * Owns persistent GPU buffers for a compiled world preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class WorldPreviewGpuMesh implements AutoCloseable {
    private static final Map<ChunkSectionLayer, RenderType> RENDER_TYPES = Map.of(
            ChunkSectionLayer.SOLID, RenderTypes.solidMovingBlock(),
            ChunkSectionLayer.CUTOUT, RenderTypes.cutoutMovingBlock(),
            ChunkSectionLayer.TRANSLUCENT, RenderTypes.translucentMovingBlock());

    private final Map<ChunkSectionLayer, Layer> layers;
    private final WorldPreviewMesh source;
    private boolean closed;

    private WorldPreviewGpuMesh(WorldPreviewMesh source, Map<ChunkSectionLayer, Layer> layers) {
        this.source = source;
        this.layers = Map.copyOf(layers);
    }

    public static WorldPreviewGpuMesh upload(WorldPreviewMesh mesh) {
        RenderSystem.assertOnRenderThread();
        Map<ChunkSectionLayer, Layer> uploaded = new EnumMap<>(ChunkSectionLayer.class);
        try {
            for (Map.Entry<ChunkSectionLayer, MeshData> entry : mesh.meshes().entrySet()) {
                uploaded.put(entry.getKey(), Layer.upload(entry.getValue()));
            }
            return new WorldPreviewGpuMesh(mesh, uploaded);
        } catch (RuntimeException exception) {
            uploaded.values().forEach(layer -> layer.closeSuppressing(exception));
            mesh.close();
            throw exception;
        }
    }

    public void draw(ChunkSectionLayer layer, Matrix4fc modelViewMatrix) {
        RenderSystem.assertOnRenderThread();
        Layer gpuLayer = layers.get(layer);
        RenderType renderType = RENDER_TYPES.get(layer);
        if (gpuLayer == null || renderType == null) {
            return;
        }
        MeshData sourceMesh = source.meshes().get(layer);
        if (sourceMesh != null) {
            drawCopy(renderType, sourceMesh);
        }
    }

    private static void drawCopy(RenderType renderType, MeshData cached) {
        ByteBuffer vertices = cached.vertexBuffer();
        ByteBuffer indices = cached.indexBuffer();
        if (vertices == null || !vertices.hasRemaining()) return;
        try (ByteBufferBuilder vertexBuilder = new ByteBufferBuilder(vertices.remaining());
             ByteBufferBuilder indexBuilder = indices == null ? null : new ByteBufferBuilder(indices.remaining())) {
            MeshData drawMesh = new MeshData(copy(vertices, vertexBuilder), cached.drawState());
            if (indices != null && indexBuilder != null) {
                ((MeshDataAccessor) (Object) drawMesh).mmcr$setIndexBuffer(copy(indices, indexBuilder));
            }
            try {
                renderType.draw(drawMesh);
            } finally {
                drawMesh.close();
            }
        }
    }

    private static ByteBufferBuilder.Result copy(ByteBuffer source, ByteBufferBuilder destination) {
        ByteBuffer duplicate = source.duplicate();
        long pointer = destination.reserve(duplicate.remaining());
        MemoryUtil.memCopy(MemoryUtil.memAddress(duplicate), pointer, duplicate.remaining());
        return destination.build();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        layers.values().forEach(Layer::close);
        source.close();
    }

    public void resortTranslucent(Vec3 camera) {
        RenderSystem.assertOnRenderThread();
        Layer layer = layers.get(ChunkSectionLayer.TRANSLUCENT);
        if (layer == null || !WorldPreviewMeshCompiler.needsTranslucentResort(layer.camera(), camera)) return;
        layer.replaceIndices(source.sortedTranslucentIndex(camera), camera);
    }

    private static final class Layer implements AutoCloseable {
        private final GpuBuffer vertices;
        private GpuBuffer indices;
        private final boolean ownsIndices;
        private final VertexFormat.IndexType indexType;
        private final int indexCount;
        private Vec3 camera;

        private Layer(GpuBuffer vertices, GpuBuffer indices, boolean ownsIndices,
                VertexFormat.IndexType indexType, int indexCount) {
            this.vertices = vertices;
            this.indices = indices;
            this.ownsIndices = ownsIndices;
            this.indexType = indexType;
            this.indexCount = indexCount;
        }

        private GpuBuffer vertices() { return vertices; }
        private GpuBuffer indices() { return indices; }
        private VertexFormat.IndexType indexType() { return indexType; }
        private int indexCount() { return indexCount; }

        private static Layer upload(MeshData mesh) {
            MeshData.DrawState drawState = mesh.drawState();
            GpuBuffer vertices = RenderSystem.getDevice().createBuffer(
                    () -> "MMCR preview vertices", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer().duplicate());
            try {
                if (mesh.indexBuffer() == null) {
                    var sequential = RenderSystem.getSequentialBuffer(drawState.mode());
                    return new Layer(vertices, sequential.getBuffer(drawState.indexCount()), false,
                            sequential.type(), drawState.indexCount());
                }
                GpuBuffer indices = RenderSystem.getDevice().createBuffer(
                        () -> "MMCR preview indices", GpuBuffer.USAGE_INDEX, mesh.indexBuffer().duplicate());
                return new Layer(vertices, indices, true, drawState.indexType(), drawState.indexCount());
            } catch (RuntimeException exception) {
                vertices.close();
                throw exception;
            }
        }

        @Override
        public void close() {
            if (ownsIndices) indices.close();
            vertices.close();
        }

        private Vec3 camera() { return camera; }

        private void replaceIndices(com.mojang.blaze3d.vertex.ByteBufferBuilder.Result sorted, Vec3 camera) {
            if (sorted == null) return;
            GpuBuffer replacement = RenderSystem.getDevice().createBuffer(
                    () -> "MMCR preview translucent indices", GpuBuffer.USAGE_INDEX, sorted.byteBuffer());
            if (ownsIndices) indices.close();
            indices = replacement;
            this.camera = camera;
        }

        private void closeSuppressing(RuntimeException failure) {
            try {
                close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }
}
