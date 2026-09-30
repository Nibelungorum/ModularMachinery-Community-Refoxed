package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Buffers dynamic preview geometry while forcing every render type into the preview framebuffer.
 *
 * @author howxu <dev@howxu.cn>
 */
final class PreviewSceneBufferSource implements MultiBufferSource, AutoCloseable {
    private final int framebufferId;
    private final Map<RenderType, ByteBufferBuilder> backingBuffers = new LinkedHashMap<>();
    private final Map<RenderType, BufferBuilder> startedBuilders = new LinkedHashMap<>();
    private boolean closed;

    PreviewSceneBufferSource(int framebufferId) {
        this.framebufferId = framebufferId;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        BufferBuilder existing = startedBuilders.get(renderType);
        if (existing != null && !renderType.canConsolidateConsecutiveGeometry()) {
            endBatch(renderType);
            existing = null;
        }
        if (existing != null) return existing;
        ByteBufferBuilder backing = backingBuffers.computeIfAbsent(renderType,
                ignored -> new ByteBufferBuilder(renderType.bufferSize()));
        BufferBuilder builder = new BufferBuilder(backing, renderType.mode(), renderType.format());
        startedBuilders.put(renderType, builder);
        return builder;
    }

    void endBatch() {
        for (RenderType renderType : new ArrayList<>(startedBuilders.keySet())) {
            endBatch(renderType);
        }
    }

    void endBatch(RenderType renderType) {
        BufferBuilder builder = startedBuilders.remove(renderType);
        if (builder == null) return;
        MeshData mesh = builder.build();
        if (mesh == null) return;
        boolean uploadStarted = false;
        try {
            if (renderType.sortOnUpload()) {
                mesh.sortQuads(backingBuffers.get(renderType), RenderSystem.getVertexSorting());
            }
            renderType.setupRenderState();
            try {
                GlStateManager._glBindFramebuffer(36160, framebufferId);
                uploadStarted = true;
                BufferUploader.drawWithShader(mesh);
            } finally {
                renderType.clearRenderState();
            }
        } finally {
            if (!uploadStarted) mesh.close();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        try {
            endBatch();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        for (ByteBufferBuilder buffer : List.copyOf(backingBuffers.values())) {
            try {
                buffer.close();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        backingBuffers.clear();
        if (failure != null) throw failure;
    }
}
