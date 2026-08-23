package cn.howxu.mmcr.client.preview.world;

import cn.howxu.mmcr.mixin.client.preview.RenderSetupAccessor;
import cn.howxu.mmcr.mixin.client.preview.RenderTypeAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

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
        if (gpuLayer == null || renderType == null) return;

        RenderSetup setup = ((RenderTypeAccessor) (Object) renderType).mmcr$getState();
        RenderSetupAccessor setupAccessor = (RenderSetupAccessor) (Object) setup;
        RenderTarget target = renderType.outputTarget().getRenderTarget();
        var color = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        var depth = target.useDepth
                ? (RenderSystem.outputDepthTextureOverride != null
                ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView()) : null;
        DynamicUniforms uniforms = RenderSystem.getDynamicUniforms();
        var transforms = uniforms.writeTransform(modelViewMatrix, new Vector4f(1, 1, 1, 1),
                new Vector3f(), setupAccessor.mmcr$getTextureTransform().getMatrix());

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "MMCR world preview " + layer, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(setupAccessor.mmcr$getPipeline());
            var scissor = RenderSystem.getScissorStateForRenderTypeDraws();
            if (scissor.enabled()) {
                pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            }
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, gpuLayer.vertices());
            for (var texture : setup.getTextures().entrySet()) {
                pass.bindTexture(texture.getKey(), texture.getValue().textureView(), texture.getValue().sampler());
            }
            pass.setIndexBuffer(gpuLayer.indices(), gpuLayer.indexType());
            pass.drawIndexed(0, 0, gpuLayer.indexCount(), 1);
        }
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
                    () -> "MMCR preview vertices", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            try {
                if (mesh.indexBuffer() == null) {
                    var sequential = RenderSystem.getSequentialBuffer(drawState.mode());
                    return new Layer(vertices, sequential.getBuffer(drawState.indexCount()), false,
                            sequential.type(), drawState.indexCount());
                }
                GpuBuffer indices = RenderSystem.getDevice().createBuffer(
                        () -> "MMCR preview indices", GpuBuffer.USAGE_INDEX, mesh.indexBuffer());
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
