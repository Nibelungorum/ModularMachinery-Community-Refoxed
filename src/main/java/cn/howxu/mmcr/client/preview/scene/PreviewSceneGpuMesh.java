/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Owns persistent GPU buffers for a compiled JEI preview scene.
 *
 * @author howxu <dev@howxu.cn>
 */
final class PreviewSceneGpuMesh implements AutoCloseable {
    private static final Map<ChunkSectionLayer, RenderType> RENDER_TYPES = Map.of(
            ChunkSectionLayer.SOLID, RenderTypes.solidMovingBlock(),
            ChunkSectionLayer.CUTOUT, RenderTypes.cutoutMovingBlock(),
            ChunkSectionLayer.TRANSLUCENT, RenderTypes.translucentMovingBlock());
    private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    private final Map<ChunkSectionLayer, List<Layer>> layers;
    private boolean closed;

    private PreviewSceneGpuMesh(Map<ChunkSectionLayer, List<Layer>> layers) {
        this.layers = Map.copyOf(layers);
    }

    static PreviewSceneGpuMesh upload(Map<ChunkSectionLayer, List<MeshData>> source) {
        RenderSystem.assertOnRenderThread();
        Map<ChunkSectionLayer, List<Layer>> uploaded = new EnumMap<>(ChunkSectionLayer.class);
        try {
            for (Map.Entry<ChunkSectionLayer, List<MeshData>> entry : source.entrySet()) {
                List<Layer> layerList = new ArrayList<>(entry.getValue().size());
                try {
                    for (MeshData mesh : entry.getValue()) {
                        layerList.add(Layer.upload(mesh));
                    }
                } catch (RuntimeException exception) {
                    layerList.forEach(layer -> layer.closeSuppressing(exception));
                    throw exception;
                }
                uploaded.put(entry.getKey(), List.copyOf(layerList));
            }
            return new PreviewSceneGpuMesh(uploaded);
        } catch (RuntimeException exception) {
            uploaded.values().forEach(list -> list.forEach(layer -> layer.closeSuppressing(exception)));
            throw exception;
        }
    }

    void draw(ChunkSectionLayer layer) {
        RenderSystem.assertOnRenderThread();
        RenderType renderType = RENDER_TYPES.get(layer);
        List<Layer> layerList = layers.get(layer);
        if (renderType == null || layerList == null) return;
        for (Layer mesh : layerList) {
            drawLayer(renderType, mesh);
        }
    }

    void replaceTranslucent(ByteBufferBuilder.Result sorted, VertexFormat.IndexType indexType) {
        RenderSystem.assertOnRenderThread();
        List<Layer> layerList = layers.get(ChunkSectionLayer.TRANSLUCENT);
        if (layerList == null || layerList.isEmpty()) return;
        layerList.getFirst().replaceIndex(sorted, indexType);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        for (List<Layer> layerList : layers.values()) {
            for (Layer layer : layerList) {
                try {
                    layer.close();
                } catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) throw failure;
    }

    private static void drawLayer(RenderType renderType, Layer layer) {
        Minecraft minecraft = Minecraft.getInstance();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrix(), WHITE, ZERO, IDENTITY);
        RenderTarget target = renderType.outputTarget().getRenderTarget();
        GpuTextureView color = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        GpuTextureView depth = target.useDepth
                ? (RenderSystem.outputDepthTextureOverride != null
                ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
                : null;
        GpuSampler blockSampler = RenderSystem.getSamplerCache().getSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.NEAREST, true);
        GpuSampler lightmapSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "MMCR preview cached draw", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(renderType.pipeline());
            ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
            if (scissor.enabled()) {
                pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            }
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", minecraft.getTextureManager()
                    .getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView(), blockSampler);
            pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), lightmapSampler);
            pass.setVertexBuffer(0, layer.vertices);
            pass.setIndexBuffer(layer.indices, layer.indexType);
            pass.drawIndexed(0, 0, layer.indexCount, 1);
        }
    }

    private static final class Layer implements AutoCloseable {
        private final GpuBuffer vertices;
        private GpuBuffer indices;
        private boolean ownsIndices;
        private VertexFormat.IndexType indexType;
        private final int indexCount;

        private Layer(GpuBuffer vertices, GpuBuffer indices, boolean ownsIndices,
                      VertexFormat.IndexType indexType, int indexCount) {
            this.vertices = vertices;
            this.indices = indices;
            this.ownsIndices = ownsIndices;
            this.indexType = indexType;
            this.indexCount = indexCount;
        }

        private static Layer upload(MeshData mesh) {
            MeshData.DrawState drawState = mesh.drawState();
            ByteBuffer vertexBytes = mesh.vertexBuffer();
            if (vertexBytes == null || !vertexBytes.hasRemaining()) {
                throw new IllegalArgumentException("preview mesh has no vertex data");
            }
            GpuBuffer vertices = RenderSystem.getDevice().createBuffer(
                    () -> "MMCR preview vertices", GpuBuffer.USAGE_VERTEX, vertexBytes.duplicate());
            try {
                ByteBuffer indexBytes = mesh.indexBuffer();
                if (indexBytes == null) {
                    var sequential = RenderSystem.getSequentialBuffer(drawState.mode());
                    return new Layer(vertices, sequential.getBuffer(drawState.indexCount()), false,
                            sequential.type(), drawState.indexCount());
                }
                GpuBuffer indices = RenderSystem.getDevice().createBuffer(
                        () -> "MMCR preview indices", GpuBuffer.USAGE_INDEX, indexBytes.duplicate());
                return new Layer(vertices, indices, true, drawState.indexType(), drawState.indexCount());
            } catch (RuntimeException exception) {
                vertices.close();
                throw exception;
            }
        }

        private void replaceIndex(ByteBufferBuilder.Result sorted, VertexFormat.IndexType replacementType) {
            if (sorted == null) return;
            GpuBuffer replacement = RenderSystem.getDevice().createBuffer(
                    () -> "MMCR preview translucent indices", GpuBuffer.USAGE_INDEX,
                    sorted.byteBuffer().duplicate());
            GpuBuffer previous = indices;
            boolean previousOwned = ownsIndices;
            indices = replacement;
            ownsIndices = true;
            indexType = replacementType;
            if (previousOwned) previous.close();
        }

        @Override
        public void close() {
            RuntimeException failure = null;
            if (ownsIndices) {
                try {
                    indices.close();
                } catch (RuntimeException exception) {
                    failure = exception;
                }
            }
            try {
                vertices.close();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
            if (failure != null) throw failure;
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
