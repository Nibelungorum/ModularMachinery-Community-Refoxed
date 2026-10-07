package cn.howxu.mmcr.client.preview.world;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns persistent GPU buffers for a compiled world preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class WorldPreviewGpuMesh implements AutoCloseable {
    private final Map<RenderType, VertexBuffer> layers;
    private final WorldPreviewMesh source;
    private final Map<RenderType, Vec3> sortedCameras = new HashMap<>();
    private boolean closed;

    private WorldPreviewGpuMesh(WorldPreviewMesh source, Map<RenderType, VertexBuffer> layers) {
        this.source = source;
        this.layers = Map.copyOf(layers);
    }

    public static WorldPreviewGpuMesh upload(WorldPreviewMesh mesh) {
        RenderSystem.assertOnRenderThread();
        Map<RenderType, VertexBuffer> uploaded = new LinkedHashMap<>();
        try {
            for (Map.Entry<RenderType, MeshData> entry : mesh.meshes().entrySet()) {
                VertexBuffer buffer = new VertexBuffer(entry.getKey().sortOnUpload()
                        ? VertexBuffer.Usage.DYNAMIC : VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(entry.getValue());
                uploaded.put(entry.getKey(), buffer);
            }
            VertexBuffer.unbind();
            return new WorldPreviewGpuMesh(mesh, uploaded);
        } catch (RuntimeException exception) {
            VertexBuffer.unbind();
            uploaded.values().forEach(VertexBuffer::close);
            mesh.close();
            throw exception;
        }
    }

    public void draw(RenderType layer, Matrix4f modelView, Vec3 camera) {
        RenderSystem.assertOnRenderThread();
        VertexBuffer buffer = layers.get(layer);
        if (buffer == null) return;
        layer.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        try {
            buffer.bind();
            if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(cameraRelativeOffset(camera));
            buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), shader);
        } finally {
            if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
            VertexBuffer.unbind();
            layer.clearRenderState();
        }
    }

    static Vector3f cameraRelativeOffset(Vec3 camera) {
        // Block shaders compute fog from Position + ChunkOffset, before ModelViewMat.
        return new Vector3f((float) -camera.x, (float) -camera.y, (float) -camera.z);
    }

    public void resort(RenderType layer, Vec3 camera) {
        RenderSystem.assertOnRenderThread();
        VertexBuffer buffer = layers.get(layer);
        if (buffer == null || !WorldPreviewMeshCompiler.needsResort(sortedCameras.get(layer), camera)) return;
        var sorted = source.sortedIndex(layer, camera);
        if (sorted == null) return;
        buffer.bind();
        buffer.uploadIndexBuffer(sorted);
        VertexBuffer.unbind();
        sortedCameras.put(layer, camera);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        layers.values().forEach(VertexBuffer::close);
        source.close();
    }
}
