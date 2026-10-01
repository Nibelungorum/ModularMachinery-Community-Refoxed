/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-ResourceLocation: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.config.ClientConfig;
import cn.howxu.mmcr.client.preview.PreviewLevel;
import cn.howxu.mmcr.client.preview.PreviewVisibility;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.Util;

/**
 * Tesselates immutable preview data into CPU-side mesh parts for render-thread assembly.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneMeshCompiler {
    private PreviewSceneMeshCompiler() { }

    static int workerCount(int stateCount) {
        return stateCount <= ClientConfig.sceneParallelCompileThreshold() ? 1 : 2;
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

    static CompilationInput capture(PreviewLevel level, StructurePreviewSchema schema,
                                    PreviewVisibility visibility) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("preview mesh compilation must occur on the render thread");
        }
        Minecraft minecraft = Minecraft.getInstance();
        List<Map.Entry<BlockPos, BlockState>> entries = schema.states().entrySet().stream().toList();
        return new CompilationInput(entries, visibility,
                minecraft.getBlockRenderer(), previewRegion(level, schema, visibility));
    }

    static CompletableFuture<CompiledScene> compileAsync(CompilationInput input, PreviewSceneCamera camera,
                                                          AtomicBoolean cancelled) {
        return compileAsync(input, camera, cancelled, Util.backgroundExecutor());
    }

    static CompletableFuture<CompiledScene> compileAsync(CompilationInput input, PreviewSceneCamera camera,
                                                          AtomicBoolean cancelled, Executor executor) {
        int workerCount = workerCount(input.entries().size());
        List<CompletableFuture<WorkerResult>> futures = new ArrayList<>(workerCount == 1 ? 1 : workerCount + 1);
        LayerSelection partitionSelection = workerCount == 1 ? LayerSelection.ALL : LayerSelection.UNSORTED;
        for (Partition partition : partitions(input.entries().size(), workerCount)) {
            futures.add(CompletableFuture.supplyAsync(() -> compilePartition(input,
                    partition.startInclusive(), partition.endExclusive(), camera, cancelled,
                    partitionSelection), executor));
        }
        if (workerCount > 1) {
            futures.add(CompletableFuture.supplyAsync(() -> compilePartition(input,
                    0, input.entries().size(), camera, cancelled, LayerSelection.SORTED), executor));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) closeCompletedWorkers(futures, unwrap(failure));
                })
                .thenApply(ignored -> collectWorkers(futures));
    }

    static PreviewSceneMeshCache.Meshes assemble(CompiledScene compiled) {
        return new PreviewSceneMeshCache.Meshes(compiled.parts(), compiled.blockEntities());
    }

    private static CompiledScene collectWorkers(List<CompletableFuture<WorkerResult>> futures) {
        try {
            List<WorkerResult> results = futures.stream().map(CompletableFuture::join).toList();
            List<PreviewSceneMeshCache.MeshPart> parts = results.stream().map(WorkerResult::part).toList();
            Set<BlockPos> blockEntities = new HashSet<>();
            results.forEach(result -> blockEntities.addAll(result.blockEntities()));
            return new CompiledScene(parts, blockEntities);
        } catch (RuntimeException exception) {
            closeCompletedWorkers(futures, exception);
            throw exception;
        }
    }

    private static WorkerResult compilePartition(CompilationInput input, int startInclusive, int endExclusive,
            PreviewSceneCamera camera, AtomicBoolean cancelled, LayerSelection selection) {
        Map<PreviewSceneMeshCache.MeshLayer, ByteBufferBuilder> builders = new java.util.LinkedHashMap<>();
        Map<PreviewSceneMeshCache.MeshLayer, BufferBuilder> started = new java.util.LinkedHashMap<>();
        Map<PreviewSceneMeshCache.MeshLayer, MeshData> meshes = new java.util.LinkedHashMap<>();
        Set<BlockPos> blockEntities = new HashSet<>();
        ModelBlockRenderer.enableCaching();
        try {
            BlockRenderDispatcher blockRenderer = input.blockRenderer();
            RandomSource random = RandomSource.create();
            PoseStack poseStack = new PoseStack();
            for (int index = startInclusive; index < endExclusive; index++) {
                if (cancelled.get()) throw new CancelledCompilation();
                Map.Entry<BlockPos, BlockState> entry = input.entries().get(index);
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue();
                if (!input.visibility().isVisible(pos, state) || state.isAir()) continue;
                if (selection != LayerSelection.SORTED && state.hasBlockEntity()) blockEntities.add(pos);
                var model = state.getRenderShape() == RenderShape.MODEL
                        ? blockRenderer.getBlockModel(state) : null;
                ModelData modelData = model == null ? ModelData.EMPTY
                        : model.getModelData(input.region(), pos, state, ModelData.EMPTY);
                List<RenderType> modelLayers;
                if (model == null) {
                    modelLayers = List.of();
                } else {
                    random.setSeed(state.getSeed(pos));
                    modelLayers = model.getRenderTypes(state, random, modelData).asList();
                }
                FluidState fluidState = state.getFluidState();
                RenderType fluidLayer = fluidState.isEmpty() ? null : ItemBlockRenderTypes.getRenderLayer(fluidState);
                boolean translucentOnly = fluidLayer != null || !modelLayers.isEmpty();
                if (fluidLayer != null && !fluidLayer.sortOnUpload()) translucentOnly = false;
                if (modelLayers.stream().anyMatch(layer -> !layer.sortOnUpload())) translucentOnly = false;
                if (!fluidState.isEmpty() && selection.accepts(fluidLayer)) {
                    blockRenderer.renderLiquid(pos, input.region(), new SectionOriginConsumer(
                            builderFor(started, builders,
                                    new PreviewSceneMeshCache.MeshLayer(fluidLayer, false)),
                            pos.getX() & ~15, pos.getY() & ~15,
                            pos.getZ() & ~15), state, fluidState);
                    if (fluidLayer.sortOnUpload() && !translucentOnly) {
                        blockRenderer.renderLiquid(pos, input.region(), new SectionOriginConsumer(
                                builderFor(started, builders,
                                        new PreviewSceneMeshCache.MeshLayer(fluidLayer, true)),
                                pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15), state, fluidState);
                    }
                }
                if (model != null) {
                    for (RenderType renderType : modelLayers) {
                        if (!selection.accepts(renderType)) continue;
                        renderModelLayer(blockRenderer, input, state, pos, poseStack, random, modelData,
                                renderType, builderFor(started, builders,
                                        new PreviewSceneMeshCache.MeshLayer(renderType, false)));
                        if (renderType.sortOnUpload() && !translucentOnly) {
                            renderModelLayer(blockRenderer, input, state, pos, poseStack, random, modelData,
                                    renderType, builderFor(started, builders,
                                            new PreviewSceneMeshCache.MeshLayer(renderType, true)));
                        }
                    }
                }
            }
            if (cancelled.get()) throw new CancelledCompilation();
            VertexSorting sorting = VertexSorting.byDistance(camera.eye().x, camera.eye().y, camera.eye().z);
            Map<PreviewSceneMeshCache.MeshLayer, MeshData.SortState> sortStates = new java.util.LinkedHashMap<>();
            for (Map.Entry<PreviewSceneMeshCache.MeshLayer, BufferBuilder> entry : started.entrySet()) {
                MeshData mesh = entry.getValue().build();
                if (mesh == null) continue;
                meshes.put(entry.getKey(), mesh);
                if (entry.getKey().renderType().sortOnUpload()) {
                    MeshData.SortState sortState = mesh.sortQuads(builders.get(entry.getKey()), sorting);
                    if (sortState != null) sortStates.put(entry.getKey(), sortState);
                }
            }
            return new WorkerResult(new PreviewSceneMeshCache.MeshPart(builders, meshes, sortStates), blockEntities);
        } catch (Throwable throwable) {
            closeWorkerResources(meshes, builders, throwable);
            rethrow(throwable);
            throw new IllegalStateException("unreachable");
        } finally {
            ModelBlockRenderer.clearCache();
        }
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
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

    private static void closeWorkerResources(Map<PreviewSceneMeshCache.MeshLayer, MeshData> meshes,
            Map<PreviewSceneMeshCache.MeshLayer, ByteBufferBuilder> builders, Throwable failure) {
        meshes.values().forEach(mesh -> {
            try {
                mesh.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        });
        for (ByteBufferBuilder builder : builders.values()) {
            try {
                builder.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new IllegalStateException("preview mesh compilation failed", failure);
    }

    private static BufferBuilder builderFor(Map<PreviewSceneMeshCache.MeshLayer, BufferBuilder> started,
            Map<PreviewSceneMeshCache.MeshLayer, ByteBufferBuilder> builders,
            PreviewSceneMeshCache.MeshLayer layer) {
        return started.computeIfAbsent(layer, key -> new BufferBuilder(
                builders.computeIfAbsent(key, ignored -> new ByteBufferBuilder(key.renderType().bufferSize())),
                key.renderType().mode(), key.renderType().format()));
    }

    private static void renderModelLayer(BlockRenderDispatcher blockRenderer, CompilationInput input,
            BlockState state, BlockPos pos, PoseStack poseStack, RandomSource random, ModelData modelData,
            RenderType renderType, VertexConsumer vertices) {
        poseStack.pushPose();
        try {
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            random.setSeed(state.getSeed(pos));
            blockRenderer.renderBatched(state, pos, input.region(), poseStack,
                    vertices, true, random, modelData, renderType);
        } finally {
            poseStack.popPose();
        }
    }

    private record SectionOriginConsumer(VertexConsumer delegate, float x, float y, float z)
            implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            return delegate.addVertex(x + this.x, y + this.y, z + this.z);
        }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return delegate.setColor(red, green, blue, alpha);
        }
        @Override public VertexConsumer setUv(float u, float v) { return delegate.setUv(u, v); }
        @Override public VertexConsumer setUv1(int u, int v) { return delegate.setUv1(u, v); }
        @Override public VertexConsumer setUv2(int u, int v) { return delegate.setUv2(u, v); }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            return delegate.setNormal(x, y, z);
        }
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
            @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
            @Override public int getBrightness(LightLayer lightLayer, BlockPos position) { return 15; }
            @Override public LevelLightEngine getLightEngine() { return level.getLightEngine(); }
            @Override public float getShade(net.minecraft.core.Direction direction, boolean shade) {
                return level.getShade(direction, shade);
            }
            @Override public int getBlockTint(BlockPos position, ColorResolver resolver) {
                return resolver.getColor(biome, position.getX(), position.getZ());
            }
        };
    }

    private static final class CancelledCompilation extends RuntimeException { }

    private enum LayerSelection {
        ALL,
        UNSORTED,
        SORTED;

        boolean accepts(RenderType renderType) {
            return this == ALL || renderType.sortOnUpload() == (this == SORTED);
        }
    }

    record Partition(int startInclusive, int endExclusive) { }

    record CompilationInput(List<Map.Entry<BlockPos, BlockState>> entries, PreviewVisibility visibility,
                            BlockRenderDispatcher blockRenderer, BlockAndTintGetter region) { }

    /** CPU-side mesh results that have not yet been handed to the render-thread GPU owner. */
    static final class CompiledScene implements AutoCloseable {
        private final List<PreviewSceneMeshCache.MeshPart> parts;
        private final Set<BlockPos> blockEntities;
        private boolean closed;

        private CompiledScene(List<PreviewSceneMeshCache.MeshPart> parts, Set<BlockPos> blockEntities) {
            this.parts = List.copyOf(parts);
            this.blockEntities = Set.copyOf(blockEntities);
        }

        List<PreviewSceneMeshCache.MeshPart> parts() { return parts; }
        Set<BlockPos> blockEntities() { return blockEntities; }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            RuntimeException failure = null;
            for (PreviewSceneMeshCache.MeshPart part : parts) {
                try {
                    part.close();
                } catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
            if (failure != null) throw failure;
        }
    }

    private record WorkerResult(PreviewSceneMeshCache.MeshPart part,
                                Set<BlockPos> blockEntities) implements AutoCloseable {
            private WorkerResult(PreviewSceneMeshCache.MeshPart part, Set<BlockPos> blockEntities) {
                this.part = part;
                this.blockEntities = Set.copyOf(blockEntities);
            }

            @Override
            public void close() {
                part.close();
            }
        }
}
