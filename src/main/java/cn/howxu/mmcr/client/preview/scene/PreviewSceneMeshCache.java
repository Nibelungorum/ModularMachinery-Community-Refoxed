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
        private final Map<RenderType, List<MeshPart>> sortedParts;
        private final PreviewSceneGpuMesh gpuMesh;
        private SortedOrder sortedOrder;
        private boolean closed;

        Meshes(List<MeshPart> parts, Set<BlockPos> blockEntities) {
            this.parts = List.copyOf(parts);
            this.layers = transferLayers(this.parts);
            this.blockEntities = blockEntities;
            this.sortedParts = collectSortedParts(this.parts);
            this.gpuMesh = PreviewSceneGpuMesh.upload(layers);
        }

        Map<RenderType, List<MeshData>> layers() { return layers; }
        Set<BlockPos> blockEntities() { return blockEntities; }
        List<RenderType> renderTypes() { return layers.keySet().stream()
                .sorted(java.util.Comparator.comparing(RenderType::sortOnUpload))
                .toList(); }
        Map<RenderType, List<MeshPart>> sortedParts() { return sortedParts; }
        SortedOrder sortedOrder() { return sortedOrder; }

        private static Map<RenderType, List<MeshData>> transferLayers(List<MeshPart> parts) {
            Map<RenderType, List<MeshData>> flattened = new IdentityHashMap<>();
            for (MeshPart part : parts) {
                part.transferMeshes().forEach((layer, mesh) ->
                        flattened.computeIfAbsent(layer, ignored -> new ArrayList<>()).add(mesh));
            }
            flattened.replaceAll((layer, meshes) -> List.copyOf(meshes));
            return Map.copyOf(flattened);
        }

        private static Map<RenderType, List<MeshPart>> collectSortedParts(List<MeshPart> parts) {
            Map<RenderType, List<MeshPart>> sorted = new IdentityHashMap<>();
            for (MeshPart part : parts) {
                part.sortStates().keySet().forEach(renderType ->
                        sorted.computeIfAbsent(renderType, ignored -> new ArrayList<>()).add(part));
            }
            sorted.replaceAll((renderType, meshes) -> List.copyOf(meshes));
            return Map.copyOf(sorted);
        }

        void draw(RenderType layer, int framebufferId) { gpuMesh.draw(layer, framebufferId); }

        @Override
        public TranslucentCache replaceTranslucent(TranslucentCache result) {
            SortedOrder replacement = (SortedOrder) result;
            gpuMesh.replaceSorted(replacement);
            SortedOrder previous = sortedOrder;
            sortedOrder = replacement;
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
            if (sortedOrder != null) {
                try {
                    sortedOrder.close();
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
        private final Map<RenderType, ByteBufferBuilder> builders;
        private final Map<RenderType, MeshData> meshes;
        private final Map<RenderType, MeshData.SortState> sortStates;
        private boolean meshesTransferred;
        private boolean closed;

        MeshPart(Map<RenderType, ByteBufferBuilder> builders, Map<RenderType, MeshData> meshes,
                  Map<RenderType, MeshData.SortState> sortStates) {
            this.builders = Map.copyOf(builders);
            this.meshes = Map.copyOf(meshes);
            this.sortStates = Map.copyOf(sortStates);
        }

        ByteBufferBuilder builder(RenderType renderType) { return builders.get(renderType); }
        Map<RenderType, MeshData> meshes() { return meshes; }
        Map<RenderType, MeshData> transferMeshes() {
            meshesTransferred = true;
            return meshes;
        }
        Map<RenderType, MeshData.SortState> sortStates() { return sortStates; }

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
            for (ByteBufferBuilder builder : builders.values()) {
                try {
                    builder.close();
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

    static final class SortedOrder implements TranslucentCache {
            private final Map<RenderType, List<ByteBufferBuilder.Result>> indexBuffers;
            private final Map<RenderType, List<VertexFormat.IndexType>> indexTypes;

            SortedOrder(Map<RenderType, List<ByteBufferBuilder.Result>> indexBuffers,
                        Map<RenderType, List<VertexFormat.IndexType>> indexTypes) {
                for (RenderType renderType : indexBuffers.keySet()) {
                    if (indexBuffers.get(renderType).size() != indexTypes.getOrDefault(renderType, List.of()).size()) {
                        throw new IllegalArgumentException("sorted index metadata size mismatch for " + renderType);
                    }
                }
                Map<RenderType, List<ByteBufferBuilder.Result>> mutable = new IdentityHashMap<>();
                indexBuffers.forEach((renderType, buffers) -> mutable.put(renderType, new ArrayList<>(buffers)));
                this.indexBuffers = mutable;
                this.indexTypes = Map.copyOf(indexTypes);
            }

            Set<RenderType> renderTypes() { return indexBuffers.keySet(); }
            int size(RenderType renderType) { return indexBuffers.get(renderType).size(); }
            ByteBufferBuilder.Result take(RenderType renderType, int index) {
                List<ByteBufferBuilder.Result> buffers = indexBuffers.get(renderType);
                ByteBufferBuilder.Result result = buffers.get(index);
                if (result == null) throw new IllegalStateException("sorted index buffer already transferred");
                buffers.set(index, null);
                return result;
            }

            @Override
            public void close() {
                RuntimeException failure = null;
                for (List<ByteBufferBuilder.Result> buffers : indexBuffers.values()) {
                    for (ByteBufferBuilder.Result indexBuffer : buffers) {
                        if (indexBuffer == null) continue;
                        try {
                            indexBuffer.close();
                        } catch (RuntimeException exception) {
                            failure = MeshPart.appendFailure(failure, exception);
                        }
                    }
                }
                if (failure != null) throw failure;
            }
        }
}
