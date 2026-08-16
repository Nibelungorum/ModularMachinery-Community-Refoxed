/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the published mesh generation and releases each generation exactly once.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneMeshCache implements AutoCloseable {
    private CacheOwner current;

    PreviewSceneMeshCache(CacheOwner current) {
        this.current = current;
    }

    CacheOwner current() {
        return current;
    }

    void publish(CacheOwner next) {
        if (!next.full()) {
            reject(next);
            return;
        }
        CacheOwner previous = current;
        current = next;
        if (previous != null) previous.close();
    }

    void reject(CacheOwner result) {
        result.close();
    }

    void publishTranslucent(CacheOwner result) {
        if (current == null || result.full()) {
            reject(result);
            return;
        }
        current.replaceTranslucent(result);
    }

    @Override
    public void close() {
        if (current != null) {
            current.close();
            current = null;
        }
    }

    enum Layer {
        SOLID, CUTOUT, TRANSLUCENT
    }

    interface CacheOwner extends AutoCloseable {
        boolean full();

        Object replaceTranslucent(CacheOwner replacement);

        @Override
        void close();
    }

    static final class Meshes implements CacheOwner {
        private final SectionBufferBuilderPack builders;
        private final Map<ChunkSectionLayer, List<MeshData>> layers;
        private final Set<net.minecraft.core.BlockPos> blockEntities;
        private final MeshData.SortState translucentSortState;
        private boolean closed;

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

        @Override public boolean full() { return true; }

        @Override public Object replaceTranslucent(CacheOwner replacement) {
            throw new UnsupportedOperationException("translucent results only replace draw indices");
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            layers.values().forEach(meshes -> meshes.forEach(MeshData::close));
            builders.close();
        }
    }
}
