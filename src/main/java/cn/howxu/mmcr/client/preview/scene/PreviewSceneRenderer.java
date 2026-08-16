/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.client.preview.PreviewCamera;
import cn.howxu.mmcr.client.preview.PreviewLevel;
import cn.howxu.mmcr.client.preview.PreviewVisibility;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.client.preview.mixin.MeshDataAccessor;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.phys.BlockHitResult;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Render-thread facade for cached static preview geometry.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneRenderer implements AutoCloseable {
    private final PreviewLevel level;
    private final StructurePreviewSchema schema;
    private final SceneCompileState compileState = new SceneCompileState();
    private final PreviewSceneMeshCache meshes = new PreviewSceneMeshCache(null);
    private PreviewVisibility visibility = PreviewVisibility.ALL;
    private long requestedGeneration;
    private long lastRotationVersion = Long.MIN_VALUE;
    private boolean closed;
    private BlockHitResult hitResult;

    public PreviewSceneRenderer(PreviewLevel level, StructurePreviewSchema schema) {
        this.level = level;
        this.schema = schema;
        markDirty();
    }

    public void setVisibility(PreviewVisibility visibility) {
        assertRenderThread();
        this.visibility = visibility;
        level.updateVisibility(visibility);
        markDirty();
    }

    public void markDirty() {
        assertRenderThread();
        if (!closed) requestedGeneration = compileState.requestFullRebuild();
    }

    public void render(PreviewSceneRenderContext context, PreviewCamera camera) {
        assertRenderThread();
        if (closed) return;
        PreviewSceneCamera sceneCamera = PreviewSceneCamera.from(camera, 1, 1);
        if (sceneCamera.rotationVersion() != lastRotationVersion) {
            lastRotationVersion = sceneCamera.rotationVersion();
            compileState.onCameraRotation(lastRotationVersion);
        }
        if (compileState.pendingKind() == SceneCompileKind.FULL) {
            compileFull(sceneCamera);
        }
        PreviewSceneMeshCache.FullCache owner = meshes.current();
        if (owner instanceof PreviewSceneMeshCache.Meshes cache) {
            if (compileState.pendingKind() == SceneCompileKind.TRANSLUCENT_ONLY) {
                compileTranslucent(cache, sceneCamera);
            }
            draw(cache, ChunkSectionLayer.SOLID, RenderTypes.solidMovingBlock());
            draw(cache, ChunkSectionLayer.CUTOUT, RenderTypes.cutoutMovingBlock());
            drawTranslucent(cache, sceneCamera);
        }
    }

    public BlockHitResult hitResult() {
        return hitResult;
    }

    public void selectHit(BlockHitResult hitResult) {
        this.hitResult = hitResult;
    }

    @Override
    public void close() {
        assertRenderThread();
        if (closed) return;
        closed = true;
        compileState.close();
        meshes.close();
        level.close();
    }

    private void compileFull(PreviewSceneCamera camera) {
        long generation = requestedGeneration;
        AtomicBoolean cancelled = new AtomicBoolean(closed);
        PreviewSceneMeshCache.Meshes result = null;
        try {
            result = PreviewSceneMeshCompiler.compileFull(level, schema, visibility, camera, cancelled);
            if (compileState.accepts(generation, SceneCompileKind.FULL) && !cancelled.get()) {
                meshes.publish(result);
                compileState.markFullCachePublished();
                result = null;
            }
        } finally {
            if (result != null) meshes.reject(result);
        }
    }

    private void compileTranslucent(PreviewSceneMeshCache.Meshes cache, PreviewSceneCamera camera) {
        MeshData.SortState sortState = cache.translucentSortState();
        if (sortState == null) {
            compileState.markFullCachePublished();
            return;
        }
        PreviewSceneMeshCache.TranslucentOrder result = null;
        try {
            VertexSorting sorting = VertexSorting.byDistance(camera.eye().x, camera.eye().y, camera.eye().z);
            result = new PreviewSceneMeshCache.TranslucentOrder(sortState.buildSortedIndexBuffer(
                    cache.builders().buffer(ChunkSectionLayer.TRANSLUCENT), sorting));
            meshes.publishTranslucent(result);
            compileState.markFullCachePublished();
            result = null;
        } finally {
            if (result != null) meshes.reject(result);
        }
    }

    private void draw(PreviewSceneMeshCache.Meshes cache, ChunkSectionLayer layer, RenderType renderType) {
        List<MeshData> layerMeshes = cache.layers().get(layer);
        if (layerMeshes == null) return;
        for (MeshData mesh : layerMeshes) {
            drawCopy(renderType, mesh);
        }
    }

    private static void drawTranslucent(PreviewSceneMeshCache.Meshes cache, PreviewSceneCamera camera) {
        MeshData.SortState sortState = cache.translucentSortState();
        List<MeshData> translucent = cache.layers().get(ChunkSectionLayer.TRANSLUCENT);
        if (translucent == null) return;
        VertexSorting sorting = VertexSorting.byDistance(camera.eye().x, camera.eye().y, camera.eye().z);
        for (MeshData mesh : translucent) {
            ByteBufferBuilder.Result order = cache.translucentOrder() == null ? null : cache.translucentOrder().indexBuffer();
            drawCopy(RenderTypes.translucentMovingBlock(), mesh, order == null && sortState == null ? null
                    : order == null ? sortState.buildSortedIndexBuffer(cache.builders().buffer(ChunkSectionLayer.TRANSLUCENT), sorting)
                    : order);
        }
    }

    private static void drawCopy(RenderType renderType, MeshData cached) {
        drawCopy(renderType, cached, null);
    }

    private static void drawCopy(RenderType renderType, MeshData cached, ByteBufferBuilder.Result sortedIndices) {
        ByteBuffer vertices = cached.vertexBuffer();
        ByteBuffer indices = cached.indexBuffer();
        if (vertices == null || !vertices.hasRemaining()) return;
        try (ByteBufferBuilder vertexBuilder = new ByteBufferBuilder(vertices.remaining());
             ByteBufferBuilder indexBuilder = indices == null ? null : new ByteBufferBuilder(indices.remaining())) {
            MeshData drawMesh = new MeshData(copy(vertices, vertexBuilder), cached.drawState());
            if (sortedIndices != null) {
                ((MeshDataAccessor) (Object) drawMesh).mmcr$setIndexBuffer(sortedIndices);
            } else if (indices != null && indexBuilder != null) {
                ((MeshDataAccessor) (Object) drawMesh).mmcr$setIndexBuffer(copy(indices, indexBuilder));
            }
            try {
                renderType.draw(drawMesh);
            } finally {
                drawMesh.close();
            }
        }
    }

    private static ByteBufferBuilder.Result copy(ByteBuffer source, ByteBufferBuilder destination) {
        ByteBuffer duplicate = source.duplicate();
        int size = duplicate.remaining();
        long pointer = destination.reserve(size);
        org.lwjgl.system.MemoryUtil.memCopy(org.lwjgl.system.MemoryUtil.memAddress(duplicate), pointer, size);
        return destination.build();
    }

    private void assertRenderThread() {
        if (!net.minecraft.client.Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("preview scene rendering must occur on the render thread");
        }
    }
}
