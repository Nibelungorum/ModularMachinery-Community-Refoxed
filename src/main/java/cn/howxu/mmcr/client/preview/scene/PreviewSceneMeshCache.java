/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

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
        if (previous != null) closeOnce(previous);
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
            throw new IllegalStateException("cannot close preview mesh result", exception);
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
        private final Set<net.minecraft.core.BlockPos> blockEntities;
        private final MeshData.SortState translucentSortState;
        private TranslucentOrder translucentOrder;

        Meshes(SectionBufferBuilderPack builders, Map<ChunkSectionLayer, List<MeshData>> layers,
               Set<net.minecraft.core.BlockPos> blockEntities, MeshData.SortState translucentSortState) {
            this.builders = builders;
            this.layers = layers;
            this.blockEntities = blockEntities;
            this.translucentSortState = translucentSortState;
        }

        SectionBufferBuilderPack builders() { return builders; }
        Map<ChunkSectionLayer, List<MeshData>> layers() { return layers; }
        Set<net.minecraft.core.BlockPos> blockEntities() { return blockEntities; }
        MeshData.SortState translucentSortState() { return translucentSortState; }
        TranslucentOrder translucentOrder() { return translucentOrder; }

        @Override
        public TranslucentCache replaceTranslucent(TranslucentCache result) {
            TranslucentOrder replacement = (TranslucentOrder) result;
            TranslucentOrder previous = translucentOrder;
            translucentOrder = replacement;
            return previous;
        }

        @Override
        public void close() {
            if (translucentOrder != null) translucentOrder.close();
            layers.values().forEach(meshes -> meshes.forEach(MeshData::close));
            builders.close();
        }
    }

    static final class TranslucentOrder implements TranslucentCache {
        private final ByteBufferBuilder.Result indexBuffer;

        TranslucentOrder(ByteBufferBuilder.Result indexBuffer) {
            this.indexBuffer = indexBuffer;
        }

        ByteBufferBuilder.Result indexBuffer() { return indexBuffer; }

        @Override
        public void close() {
            indexBuffer.close();
        }
    }
}
