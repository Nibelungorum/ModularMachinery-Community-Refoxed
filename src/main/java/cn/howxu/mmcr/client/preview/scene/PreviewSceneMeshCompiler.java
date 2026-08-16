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
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Proxy;

/**
 * Tesselates the immutable preview level into a private, render-thread-owned mesh generation.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneMeshCompiler {
    private PreviewSceneMeshCompiler() { }

    static PreviewSceneMeshCache.Meshes compileFull(PreviewLevel level, StructurePreviewSchema schema,
                                                    PreviewVisibility visibility, PreviewSceneCamera camera,
                                                    AtomicBoolean cancelled) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("preview mesh compilation must occur on the render thread");
        }
        SectionBufferBuilderPack builders = new SectionBufferBuilderPack();
        Map<ChunkSectionLayer, BufferBuilder> started = new EnumMap<>(ChunkSectionLayer.class);
        Map<ChunkSectionLayer, List<MeshData>> meshes = new EnumMap<>(ChunkSectionLayer.class);
        Set<BlockPos> blockEntities = new HashSet<>();
        try {
            Minecraft minecraft = Minecraft.getInstance();
            var modelSet = minecraft.getModelManager().getBlockStateModelSet();
            var fluidSet = minecraft.getModelManager().getFluidStateModelSet();
            ModelBlockRenderer blockRenderer = new ModelBlockRenderer(minecraft.options.ambientOcclusion().get(), true,
                    minecraft.getBlockColors());
            FluidRenderer fluidRenderer = new FluidRenderer(fluidSet);
            BlockAndTintGetter region = previewRegion(level);
            BlockQuadOutput blockOutput = (x, y, z, quad, instance) -> builderFor(started, builders,
                    quad.materialInfo().layer()).putBlockBakedQuad(x, y, z, quad, instance);
            FluidRenderer.Output fluidOutput = layer -> new SectionOriginConsumer(builderFor(started, builders, layer));
            for (Map.Entry<BlockPos, BlockState> entry : schema.states().entrySet()) {
                if (cancelled.get()) break;
                BlockPos pos = entry.getKey();
                BlockState state = level.getBlockState(pos);
                if (!visibility.isVisible(pos, entry.getValue()) || state.isAir()) continue;
                if (state.hasBlockEntity()) blockEntities.add(pos);
                if (!state.getFluidState().isEmpty()) {
                    fluidRenderer.tesselate(region, pos, offset(fluidOutput, pos), state, state.getFluidState());
                }
                if (state.getRenderShape() == RenderShape.MODEL) {
                    blockRenderer.tesselateBlock(blockOutput, pos.getX(), pos.getY(), pos.getZ(), region, pos, state,
                            modelSet.get(state), state.getSeed(pos));
                }
            }
            if (cancelled.get()) throw new CancelledCompilation();
            MeshData.SortState sortState = null;
            VertexSorting sorting = VertexSorting.byDistance(camera.eye().x, camera.eye().y, camera.eye().z);
            for (Map.Entry<ChunkSectionLayer, BufferBuilder> entry : started.entrySet()) {
                MeshData mesh = entry.getValue().build();
                if (mesh == null) continue;
                if (entry.getKey() == ChunkSectionLayer.TRANSLUCENT) {
                    sortState = mesh.sortQuads(builders.buffer(entry.getKey()), sorting);
                }
                meshes.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(mesh);
            }
            return new PreviewSceneMeshCache.Meshes(builders, meshes, Set.copyOf(blockEntities), sortState);
        } catch (RuntimeException exception) {
            meshes.values().forEach(list -> list.forEach(MeshData::close));
            builders.close();
            throw exception;
        }
    }

    private static BufferBuilder builderFor(Map<ChunkSectionLayer, BufferBuilder> started,
                                            SectionBufferBuilderPack builders, ChunkSectionLayer layer) {
        return started.computeIfAbsent(layer, key -> new BufferBuilder(builders.buffer(key), VertexFormat.Mode.QUADS,
                key.vertexFormat()));
    }

    private static BlockAndTintGetter previewRegion(PreviewLevel level) {
        return (BlockAndTintGetter) Proxy.newProxyInstance(BlockAndTintGetter.class.getClassLoader(),
                new Class<?>[] {BlockAndTintGetter.class}, (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(level, arguments);
                    }
                    return method.invoke(level, arguments);
                });
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
}
