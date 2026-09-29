/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-ResourceLocation: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.MMCR;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.RenderType;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
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
        private final List<MeshPart> parts;
        private final Map<RenderType, List<MeshData>> layers;
        private final Set<BlockPos> blockEntities;
        private final List<MeshPart> translucentParts;
        private final PreviewSceneGpuMesh gpuMesh;
        private TranslucentOrder translucentOrder;
        private boolean closed;

        Meshes(List<MeshPart> parts, Set<BlockPos> blockEntities) {
            this.parts = List.copyOf(parts);
            this.layers = transferLayers(this.parts);
            this.blockEntities = blockEntities;
            this.translucentParts = this.parts.stream()
                    .filter(part -> part.translucentSortState() != null)
                    .toList();
            this.gpuMesh = PreviewSceneGpuMesh.upload(layers);
        }

        Map<RenderType, List<MeshData>> layers() { return layers; }
        Set<BlockPos> blockEntities() { return blockEntities; }
        List<MeshPart> translucentParts() { return translucentParts; }
        TranslucentOrder translucentOrder() { return translucentOrder; }

        private static Map<RenderType, List<MeshData>> transferLayers(List<MeshPart> parts) {
            Map<RenderType, List<MeshData>> flattened = new IdentityHashMap<>();
            for (MeshPart part : parts) {
                part.transferMeshes().forEach((layer, mesh) ->
                        flattened.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(mesh));
            }
            flattened.replaceAll((layer, meshes) -> List.copyOf(meshes));
            return Map.copyOf(flattened);
        }

        void draw(RenderType layer) { gpuMesh.draw(layer); }

        @Override
        public TranslucentCache replaceTranslucent(TranslucentCache result) {
            TranslucentOrder replacement = (TranslucentOrder) result;
            gpuMesh.replaceTranslucent(replacement);
            TranslucentOrder previous = translucentOrder;
            translucentOrder = replacement;
            return previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
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
            for (MeshPart part : parts) {
                try {
                    part.close();
                } catch (RuntimeException exception) {
                    failure = appendFailure(failure, exception);
                }
            }
            if (failure != null) throw failure;
        }

        private static RuntimeException appendFailure(RuntimeException failure, RuntimeException next) {
            if (failure == null) return next;
            failure.addSuppressed(next);
            return failure;
        }
    }

    static final class MeshPart implements AutoCloseable {
        private final SectionBufferBuilderPack builders;
        private final Map<RenderType, MeshData> meshes;
        private final MeshData.SortState translucentSortState;
        private boolean meshesTransferred;
        private boolean closed;

        MeshPart(SectionBufferBuilderPack builders, Map<RenderType, MeshData> meshes,
                 MeshData.SortState translucentSortState) {
            this.builders = builders;
            this.meshes = Map.copyOf(meshes);
            this.translucentSortState = translucentSortState;
        }

        SectionBufferBuilderPack builders() { return builders; }
        Map<RenderType, MeshData> meshes() { return meshes; }
        Map<RenderType, MeshData> transferMeshes() {
            meshesTransferred = true;
            return meshes;
        }
        MeshData.SortState translucentSortState() { return translucentSortState; }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            RuntimeException failure = null;
            for (MeshData mesh : meshesTransferred ? List.<MeshData>of() : meshes.values()) {
                try {
                    mesh.close();
                } catch (RuntimeException exception) {
                    failure = appendFailure(failure, exception);
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
            private final List<ByteBufferBuilder.Result> indexBuffers;
            private final List<VertexFormat.IndexType> indexTypes;

            TranslucentOrder(List<ByteBufferBuilder.Result> indexBuffers,
                              List<VertexFormat.IndexType> indexTypes) {
                if (indexBuffers.size() != indexTypes.size()) {
                    throw new IllegalArgumentException("translucent index metadata size mismatch");
                }
                this.indexBuffers = new ArrayList<>(indexBuffers);
                this.indexTypes = List.copyOf(indexTypes);
            }

            List<ByteBufferBuilder.Result> indexBuffers() { return List.copyOf(indexBuffers); }
            List<VertexFormat.IndexType> indexTypes() { return indexTypes; }
            int size() { return indexBuffers.size(); }
            ByteBufferBuilder.Result take(int index) {
                ByteBufferBuilder.Result result = indexBuffers.get(index);
                if (result == null) throw new IllegalStateException("translucent index buffer already transferred");
                indexBuffers.set(index, null);
                return result;
            }

            @Override
            public void close() {
                RuntimeException failure = null;
                for (ByteBufferBuilder.Result indexBuffer : indexBuffers) {
                    if (indexBuffer == null) continue;
                    try {
                        indexBuffer.close();
                    } catch (RuntimeException exception) {
                        failure = MeshPart.appendFailure(failure, exception);
                    }
                }
                if (failure != null) throw failure;
            }
        }
}
