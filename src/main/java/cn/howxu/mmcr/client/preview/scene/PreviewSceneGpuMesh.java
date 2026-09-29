package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns persistent 1.21.1 vertex buffers for a compiled GUI preview scene.
 *
 * @author howxu <dev@howxu.cn>
 */
final class PreviewSceneGpuMesh implements AutoCloseable {
    private static final RenderType PREVIEW_TRANSLUCENT = RenderType.create(
            "mmcr_preview_translucent", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 786432,
            true, true, RenderType.CompositeState.builder()
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .createCompositeState(true));
    private final Map<RenderType, List<VertexBuffer>> layers;
    private boolean closed;

    private PreviewSceneGpuMesh(Map<RenderType, List<VertexBuffer>> layers) {
        this.layers = Map.copyOf(layers);
    }

    static PreviewSceneGpuMesh upload(Map<RenderType, List<MeshData>> source) {
        RenderSystem.assertOnRenderThread();
        Map<RenderType, List<VertexBuffer>> uploaded = new IdentityHashMap<>();
        Map<MeshData, Boolean> consumed = new IdentityHashMap<>();
        try {
            source.forEach((renderType, meshes) -> {
                List<VertexBuffer> buffers = new ArrayList<>(meshes.size());
                uploaded.put(renderType, buffers);
                for (MeshData mesh : meshes) {
                    VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    consumed.put(mesh, Boolean.TRUE);
                    try {
                        buffer.upload(mesh);
                        buffers.add(buffer);
                    } catch (RuntimeException exception) {
                        buffer.close();
                        throw exception;
                    }
                }
            });
            uploaded.replaceAll((renderType, buffers) -> List.copyOf(buffers));
            return new PreviewSceneGpuMesh(uploaded);
        } catch (RuntimeException exception) {
            uploaded.values().forEach(buffers -> buffers.forEach(VertexBuffer::close));
            source.values().forEach(meshes -> meshes.stream()
                    .filter(mesh -> !consumed.containsKey(mesh)).forEach(MeshData::close));
            throw exception;
        }
    }

    void draw(RenderType renderType) {
        RenderSystem.assertOnRenderThread();
        List<VertexBuffer> buffers = layers.get(renderType);
        if (buffers == null) return;
        RenderType drawType = renderType == RenderType.translucent() ? PREVIEW_TRANSLUCENT : renderType;
        drawType.setupRenderState();
        try {
            for (VertexBuffer buffer : buffers) {
                buffer.bind();
                buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(),
                        RenderSystem.getShader());
            }
        } finally {
            VertexBuffer.unbind();
            drawType.clearRenderState();
        }
    }

    void replaceTranslucent(PreviewSceneMeshCache.TranslucentOrder order) {
        RenderSystem.assertOnRenderThread();
        List<VertexBuffer> buffers = layers.get(RenderType.translucent());
        if (buffers == null || buffers.isEmpty()) return;
        if (buffers.size() != order.size()) {
            throw new IllegalArgumentException("translucent layer count mismatch");
        }
        for (int index = 0; index < buffers.size(); index++) {
            buffers.get(index).uploadIndexBuffer(order.take(index));
        }
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        layers.values().forEach(buffers -> buffers.forEach(VertexBuffer::close));
    }
}
