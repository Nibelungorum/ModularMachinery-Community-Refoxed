/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.client.preview.PreviewLevel;
import cn.howxu.mmcr.client.preview.PreviewVisibility;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.util.Util;

/**
 * Tesselates the immutable preview level into a private, render-thread-owned mesh generation.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneMeshCompiler {
    private static final int SINGLE_THREAD_PREVIEW_LIMIT = 80_000;

    private PreviewSceneMeshCompiler() { }

    static int workerCount(int stateCount) {
        return stateCount <= SINGLE_THREAD_PREVIEW_LIMIT ? 1 : 2;
    }

    static List<Partition> partitions(int size, int count) {
        List<Partition> result = new ArrayList<>(count);
        int baseSize = size / count;
        int remainder = size % count;
        int start = 0;
        for (int index = 0; index < count; index++) {
            int end = start + baseSize + (index < remainder ? 1 : 0);
            result.add(new Partition(start, end));
            start = end;
        }
        return result;
    }

    static PreviewSceneMeshCache.Meshes compileFull(PreviewLevel level, StructurePreviewSchema schema,
                                                    PreviewVisibility visibility, PreviewSceneCamera camera,
                                                    AtomicBoolean cancelled) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("preview mesh compilation must occur on the render thread");
        }
        Minecraft minecraft = Minecraft.getInstance();
        List<Map.Entry<BlockPos, BlockState>> entries = schema.states().entrySet().stream().toList();
        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
        FluidStateModelSet fluidSet = minecraft.getModelManager().getFluidStateModelSet();
        BlockColors blockColors = minecraft.getBlockColors();
        boolean ambientOcclusion = minecraft.options.ambientOcclusion().get();
        BlockAndTintGetter region = previewRegion(level, schema, visibility);
        int workerCount = workerCount(entries.size());
        List<WorkerResult> results = workerCount == 1
                ? List.of(compilePartition(entries, 0, entries.size(), visibility, camera, cancelled,
                        modelSet, fluidSet, blockColors, ambientOcclusion, region))
                : compileParallel(entries, workerCount, visibility, camera, cancelled,
                        modelSet, fluidSet, blockColors, ambientOcclusion, region);
        try {
            List<PreviewSceneMeshCache.MeshPart> parts = results.stream().map(WorkerResult::part).toList();
            Set<BlockPos> blockEntities = new HashSet<>();
            results.forEach(result -> blockEntities.addAll(result.blockEntities()));
            return new PreviewSceneMeshCache.Meshes(parts, blockEntities);
        } catch (RuntimeException exception) {
            results.forEach(result -> closeWorkerResult(result, exception));
            throw exception;
        }
    }

    private static List<WorkerResult> compileParallel(List<Map.Entry<BlockPos, BlockState>> entries,
                                                       int workerCount, PreviewVisibility visibility,
                                                       PreviewSceneCamera camera, AtomicBoolean cancelled,
                                                       BlockStateModelSet modelSet, FluidStateModelSet fluidSet,
                                                       BlockColors blockColors, boolean ambientOcclusion,
                                                       BlockAndTintGetter region) {
        List<CompletableFuture<WorkerResult>> futures = new ArrayList<>(workerCount);
        for (Partition partition : partitions(entries.size(), workerCount)) {
            futures.add(CompletableFuture.supplyAsync(() -> compilePartition(entries,
                    partition.startInclusive(), partition.endExclusive(), visibility, camera, cancelled,
                    modelSet, fluidSet, blockColors, ambientOcclusion, region), Util.backgroundExecutor()));
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            return futures.stream().map(CompletableFuture::join).toList();
        } catch (RuntimeException exception) {
            Throwable failure = exception instanceof CompletionException && exception.getCause() != null
                    ? exception.getCause() : exception;
            closeCompletedWorkers(futures, failure);
            rethrow(failure);
            throw new IllegalStateException("unreachable");
        }
    }

    private static WorkerResult compilePartition(List<Map.Entry<BlockPos, BlockState>> entries,
                                                  int startInclusive, int endExclusive,
                                                  PreviewVisibility visibility, PreviewSceneCamera camera,
                                                  AtomicBoolean cancelled, BlockStateModelSet modelSet,
                                                  FluidStateModelSet fluidSet, BlockColors blockColors,
                                                  boolean ambientOcclusion, BlockAndTintGetter region) {
        SectionBufferBuilderPack builders = new SectionBufferBuilderPack();
        Map<ChunkSectionLayer, BufferBuilder> started = new EnumMap<>(ChunkSectionLayer.class);
        Map<ChunkSectionLayer, MeshData> meshes = new EnumMap<>(ChunkSectionLayer.class);
        Set<BlockPos> blockEntities = new HashSet<>();
        BlockModelLighter.enableCaching();
        try {
            ModelBlockRenderer blockRenderer = new ModelBlockRenderer(ambientOcclusion, true, blockColors);
            FluidRenderer fluidRenderer = new FluidRenderer(fluidSet);
            BlockQuadOutput blockOutput = (x, y, z, quad, instance) -> builderFor(started, builders,
                    quad.materialInfo().layer()).putBlockBakedQuad(x, y, z, quad, instance);
            FluidRenderer.Output fluidOutput = layer -> new SectionOriginConsumer(builderFor(started, builders, layer));
            for (int index = startInclusive; index < endExclusive; index++) {
                if (cancelled.get()) throw new CancelledCompilation();
                Map.Entry<BlockPos, BlockState> entry = entries.get(index);
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue();
                if (!visibility.isVisible(pos, state) || state.isAir()) continue;
                if (state.hasBlockEntity()) blockEntities.add(pos);
                FluidState fluidState = state.getFluidState();
                if (!fluidState.isEmpty()) {
                    fluidRenderer.tesselate(region, pos, offset(fluidOutput, pos), state, fluidState);
                }
                if (state.getRenderShape() == RenderShape.MODEL) {
                    blockRenderer.tesselateBlock(blockOutput, pos.getX(), pos.getY(), pos.getZ(), region, pos, state,
                            modelSet.get(state), state.getSeed(pos));
                }
            }
            if (cancelled.get()) throw new CancelledCompilation();
            VertexSorting sorting = VertexSorting.byDistance(camera.eye().x, camera.eye().y, camera.eye().z);
            MeshData.SortState sortState = null;
            for (Map.Entry<ChunkSectionLayer, BufferBuilder> entry : started.entrySet()) {
                MeshData mesh = entry.getValue().build();
                if (mesh == null) continue;
                meshes.put(entry.getKey(), mesh);
                if (entry.getKey() == ChunkSectionLayer.TRANSLUCENT) {
                    sortState = mesh.sortQuads(builders.buffer(entry.getKey()), sorting);
                }
            }
            return new WorkerResult(new PreviewSceneMeshCache.MeshPart(builders, meshes, sortState), blockEntities);
        } catch (Throwable throwable) {
            closeWorkerResources(meshes, builders, throwable);
            rethrow(throwable);
            throw new IllegalStateException("unreachable");
        } finally {
            BlockModelLighter.clearCache();
        }
    }

    private static void closeCompletedWorkers(List<CompletableFuture<WorkerResult>> futures, Throwable failure) {
        for (CompletableFuture<WorkerResult> future : futures) {
            if (!future.isDone() || future.isCancelled() || future.isCompletedExceptionally()) continue;
            try {
                future.join().close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static void closeWorkerResult(WorkerResult result, RuntimeException failure) {
        try {
            result.close();
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void closeWorkerResources(Map<ChunkSectionLayer, MeshData> meshes,
                                             SectionBufferBuilderPack builders, Throwable failure) {
        meshes.values().forEach(mesh -> {
            try {
                mesh.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        });
        try {
            builders.close();
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new IllegalStateException("preview mesh compilation failed", failure);
    }

    private static BufferBuilder builderFor(Map<ChunkSectionLayer, BufferBuilder> started,
                                            SectionBufferBuilderPack builders, ChunkSectionLayer layer) {
        return started.computeIfAbsent(layer, key -> new BufferBuilder(builders.buffer(key), VertexFormat.Mode.QUADS,
                key.vertexFormat()));
    }

    static BlockAndTintGetter previewRegion(PreviewLevel level, StructurePreviewSchema schema,
                                            PreviewVisibility visibility) {
        Biome biome = level.getUncachedNoiseBiome(0, 0, 0).value();
        return new BlockAndTintGetter() {
            @Override public BlockState getBlockState(BlockPos position) {
                BlockState state = schema.stateAt(position);
                return state == null || !visibility.isVisible(position, state)
                        ? Blocks.AIR.defaultBlockState() : state;
            }
            @Override public FluidState getFluidState(BlockPos position) { return getBlockState(position).getFluidState(); }
            @Override public BlockEntity getBlockEntity(BlockPos position) { return null; }
            @Override public int getHeight() { return level.getHeight(); }
            @Override public int getMinY() { return level.getMinY(); }
            @Override public int getBrightness(LightLayer lightLayer, BlockPos position) { return 15; }
            @Override public CardinalLighting cardinalLighting() { return CardinalLighting.DEFAULT; }
            @Override public LevelLightEngine getLightEngine() { return LevelLightEngine.EMPTY; }
            @Override public int getBlockTint(BlockPos position, ColorResolver resolver) {
                return resolver.getColor(biome, position.getX(), position.getZ());
            }
        };
    }

    private static FluidRenderer.Output offset(FluidRenderer.Output output, BlockPos position) {
        float x = position.getX() & ~15;
        float y = position.getY() & ~15;
        float z = position.getZ() & ~15;
        return layer -> new SectionOriginConsumer(output.getBuilder(layer), x, y, z);
    }

    private static final class SectionOriginConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float x;
        private final float y;
        private final float z;

        private SectionOriginConsumer(VertexConsumer delegate) { this(delegate, 0, 0, 0); }
        private SectionOriginConsumer(VertexConsumer delegate, float x, float y, float z) {
            this.delegate = delegate; this.x = x; this.y = y; this.z = z;
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) { return delegate.addVertex(x + this.x, y + this.y, z + this.z); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return delegate.setColor(r, g, b, a); }
        @Override public VertexConsumer setColor(int color) { return delegate.setColor(color); }
        @Override public VertexConsumer setUv(float u, float v) { return delegate.setUv(u, v); }
        @Override public VertexConsumer setUv1(int u, int v) { return delegate.setUv1(u, v); }
        @Override public VertexConsumer setUv2(int u, int v) { return delegate.setUv2(u, v); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return delegate.setNormal(x, y, z); }
        @Override public VertexConsumer setLineWidth(float width) { return delegate.setLineWidth(width); }
    }

    private static final class CancelledCompilation extends RuntimeException { }

    record Partition(int startInclusive, int endExclusive) { }

    private static final class WorkerResult implements AutoCloseable {
        private final PreviewSceneMeshCache.MeshPart part;
        private final Set<BlockPos> blockEntities;

        private WorkerResult(PreviewSceneMeshCache.MeshPart part, Set<BlockPos> blockEntities) {
            this.part = part;
            this.blockEntities = Set.copyOf(blockEntities);
        }

        private PreviewSceneMeshCache.MeshPart part() { return part; }
        private Set<BlockPos> blockEntities() { return blockEntities; }

        @Override
        public void close() {
            part.close();
        }
    }
}
