/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.MMCR;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Owns the published mesh generation and its separately replaceable translucent ordering.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneMeshCache implements AutoCloseable {
    private FullCache current;
    private final Map<AutoCloseable, Boolean> closedResults = new IdentityHashMap<>();

    PreviewSceneMeshCache(FullCache current) {
        this.current = current;
    }

    FullCache current() {
        return current;
    }

    void publish(FullCache next) {
        FullCache previous = current;
        current = next;
        if (previous != null && previous != next) closeOnce(previous);
    }

    void reject(AutoCloseable result) {
        closeOnce(result);
    }

    void publishTranslucent(TranslucentCache result) {
        if (current == null) {
            reject(result);
            return;
        }
        TranslucentCache previous = current.replaceTranslucent(result);
        if (previous != null) closeOnce(previous);
    }

    @Override
    public void close() {
        if (current == null) return;
        FullCache previous = current;
        current = null;
        closeOnce(previous);
    }

    private void closeOnce(AutoCloseable owner) {
        if (closedResults.put(owner, Boolean.TRUE) != null) return;
        try {
            owner.close();
        } catch (Exception exception) {
            MMCR.LOG.error("Cannot close preview mesh result", exception);
        }
    }

    interface FullCache extends AutoCloseable {
        TranslucentCache replaceTranslucent(TranslucentCache result);
        @Override void close();
    }

    interface TranslucentCache extends AutoCloseable {
        @Override void close();
    }

    static final class Meshes implements FullCache {
        private final SectionBufferBuilderPack builders;
        private final Map<ChunkSectionLayer, List<MeshData>> layers;
        private final Set<BlockPos> blockEntities;
        private final MeshData.SortState translucentSortState;
        private final PreviewSceneGpuMesh gpuMesh;
        private TranslucentOrder translucentOrder;

        Meshes(SectionBufferBuilderPack builders, Map<ChunkSectionLayer, List<MeshData>> layers,
               Set<BlockPos> blockEntities, MeshData.SortState translucentSortState) {
            this.builders = builders;
            this.layers = layers;
            this.blockEntities = blockEntities;
            this.translucentSortState = translucentSortState;
            this.gpuMesh = PreviewSceneGpuMesh.upload(layers);
        }

        SectionBufferBuilderPack builders() { return builders; }
        Map<ChunkSectionLayer, List<MeshData>> layers() { return layers; }
        Set<BlockPos> blockEntities() { return blockEntities; }
        MeshData.SortState translucentSortState() { return translucentSortState; }
        TranslucentOrder translucentOrder() { return translucentOrder; }
        VertexFormat.IndexType translucentIndexType() {
            List<MeshData> translucent = layers.get(ChunkSectionLayer.TRANSLUCENT);
            return translucent == null || translucent.isEmpty()
                    ? null : translucent.getFirst().drawState().indexType();
        }

        void draw(ChunkSectionLayer layer) { gpuMesh.draw(layer); }

        @Override
        public TranslucentCache replaceTranslucent(TranslucentCache result) {
            TranslucentOrder replacement = (TranslucentOrder) result;
            gpuMesh.replaceTranslucent(replacement.indexBuffer(), replacement.indexType());
            TranslucentOrder previous = translucentOrder;
            translucentOrder = replacement;
            return previous;
        }

        @Override
        public void close() {
            RuntimeException failure = null;
            try {
                gpuMesh.close();
            } catch (RuntimeException exception) {
                failure = exception;
            }
            if (translucentOrder != null) {
                try {
                    translucentOrder.close();
                } catch (RuntimeException exception) {
                    failure = appendFailure(failure, exception);
                }
            }
            for (List<MeshData> meshes : layers.values()) {
                for (MeshData mesh : meshes) {
                    try {
                        mesh.close();
                    } catch (RuntimeException exception) {
                        failure = appendFailure(failure, exception);
                    }
                }
            }
            try {
                builders.close();
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            }
            if (failure != null) throw failure;
        }

        private static RuntimeException appendFailure(RuntimeException failure, RuntimeException next) {
            if (failure == null) return next;
            failure.addSuppressed(next);
            return failure;
        }
    }

    static final class TranslucentOrder implements TranslucentCache {
        private final ByteBufferBuilder.Result indexBuffer;
        private final VertexFormat.IndexType indexType;

        TranslucentOrder(ByteBufferBuilder.Result indexBuffer, VertexFormat.IndexType indexType) {
            this.indexBuffer = indexBuffer;
            this.indexType = indexType;
        }

        ByteBufferBuilder.Result indexBuffer() { return indexBuffer; }
        VertexFormat.IndexType indexType() { return indexType; }

        @Override
        public void close() {
            indexBuffer.close();
        }
    }
}
