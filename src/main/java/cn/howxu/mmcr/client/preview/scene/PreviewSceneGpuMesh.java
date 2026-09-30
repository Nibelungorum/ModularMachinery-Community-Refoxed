package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
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
                        buffer.bind();
                        buffer.upload(mesh);
                        buffers.add(buffer);
                    } catch (RuntimeException exception) {
                        try {
                            buffer.close();
                        } catch (RuntimeException cleanupFailure) {
                            exception.addSuppressed(cleanupFailure);
                        }
                        throw exception;
                    }
                }
            });
            uploaded.replaceAll((renderType, buffers) -> List.copyOf(buffers));
            return new PreviewSceneGpuMesh(uploaded);
        } catch (RuntimeException exception) {
            closeBuffers(uploaded.values(), exception);
            for (List<MeshData> meshes : source.values()) {
                for (MeshData mesh : meshes) {
                    if (consumed.containsKey(mesh)) continue;
                    try {
                        mesh.close();
                    } catch (RuntimeException cleanupFailure) {
                        exception.addSuppressed(cleanupFailure);
                    }
                }
            }
            throw exception;
        } finally {
            VertexBuffer.unbind();
        }
    }

    void draw(RenderType renderType, int framebufferId) {
        RenderSystem.assertOnRenderThread();
        List<VertexBuffer> buffers = layers.get(renderType);
        if (buffers == null) return;
        renderType.setupRenderState();
        try {
            GlStateManager._glBindFramebuffer(36160, framebufferId);
            for (VertexBuffer buffer : buffers) {
                buffer.bind();
                buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(),
                        RenderSystem.getShader());
            }
        } finally {
            VertexBuffer.unbind();
            renderType.clearRenderState();
        }
    }

    void replaceSorted(PreviewSceneMeshCache.SortedOrder order) {
        RenderSystem.assertOnRenderThread();
        for (RenderType renderType : order.renderTypes()) {
            List<VertexBuffer> buffers = layers.get(renderType);
            if (buffers == null || buffers.size() != order.size(renderType)) {
                throw new IllegalArgumentException("sorted layer count mismatch for " + renderType);
            }
            try {
                for (int index = 0; index < buffers.size(); index++) {
                    VertexBuffer buffer = buffers.get(index);
                    buffer.bind();
                    buffer.uploadIndexBuffer(order.take(renderType, index));
                }
            } finally {
                VertexBuffer.unbind();
            }
        }
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        RuntimeException failure = closeBuffers(layers.values(), null);
        if (failure != null) throw failure;
    }

    private static RuntimeException closeBuffers(Iterable<List<VertexBuffer>> groups, RuntimeException failure) {
        RuntimeException result = failure;
        for (List<VertexBuffer> buffers : groups) {
            for (VertexBuffer buffer : buffers) {
                try {
                    buffer.close();
                } catch (RuntimeException cleanupFailure) {
                    if (result == null) result = cleanupFailure;
                    else result.addSuppressed(cleanupFailure);
                }
            }
        }
        return result;
    }
}
