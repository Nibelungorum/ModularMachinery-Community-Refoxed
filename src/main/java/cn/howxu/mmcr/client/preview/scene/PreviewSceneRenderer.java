/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.preview.PreviewCamera;
import cn.howxu.mmcr.client.preview.PreviewLevel;
import cn.howxu.mmcr.client.preview.PreviewVisibility;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.mixin.client.preview.MeshDataAccessor;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Render-thread facade for cached static preview geometry.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneRenderer {
    private final PreviewLevel level;
    private final StructurePreviewSchema schema;
    private final SceneCompileState compileState = new SceneCompileState();
    private final PreviewSceneMeshCache meshes = new PreviewSceneMeshCache(null);
    private final Thread renderThread = Thread.currentThread();
    private PreviewVisibility visibility = PreviewVisibility.ALL;
    private long requestedGeneration;
    private Vector3f lastEye;
    private boolean closed;

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

    public void render(PreviewSceneRenderContext context, PreviewCamera camera, BlockHitResult hoverHit, BlockHitResult selectedHit) {
        assertRenderThread();
        if (closed) return;
        PreviewSceneCamera sceneCamera = PreviewSceneCamera.from(camera, 1, 1);
        if (lastEye == null || !lastEye.equals(sceneCamera.eye())) {
            lastEye = sceneCamera.eye();
            requestedGeneration = compileState.onCameraPanOrZoom();
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
            submitBlockEntities(cache, context, sceneCamera);
            drawOutlines(context, hoverHit, selectedHit);
        }
    }

    public BlockHitResult clip(Vec3 from, Vec3 to) {
        HitResult result = level.clip(new ClipContext(from, to,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY,
                CollisionContext.empty()));
        if (!(result instanceof BlockHitResult block)) return null;
        BlockState state = level.getBlockState(block.getBlockPos());
        return state != null && !state.isAir() && visibility.isVisible(block.getBlockPos(), state) ? block : null;
    }

    public @Nullable BlockHitResult rayTrace(PreviewSceneCamera camera,
                                             double mouseX, double mouseY,
                                             int width, int height) {
        if (width <= 0 || height <= 0 || mouseX < 0.0D || mouseX >= width
                || mouseY < 0.0D || mouseY >= height) {
            return null;
        }
        float ndcX = (float) (2.0D * (mouseX + 0.5D) / width - 1.0D);
        float ndcY = (float) (1.0D - 2.0D * (mouseY + 0.5D) / height);
        Matrix4f inverse = camera.projection().mul(camera.view(), new Matrix4f()).invert();
        Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, -1.0F, 1.0F));
        Vector4f far = inverse.transform(new Vector4f(ndcX, ndcY, 1.0F, 1.0F));
        Vec3 from = new Vec3(near.x / near.w, near.y / near.w, near.z / near.w);
        Vec3 to = new Vec3(far.x / far.w, far.y / far.w, far.z / far.w);
        return clip(from, to);
    }

    public void dispose() {
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
            return;
        }
        long generation = requestedGeneration;
        PreviewSceneMeshCache.TranslucentOrder result = null;
        try {
            VertexSorting sorting = VertexSorting.byDistance(camera.eye().x, camera.eye().y, camera.eye().z);
            ByteBufferBuilder.Result indexBuffer = sortState.buildSortedIndexBuffer(
                    cache.builders().buffer(ChunkSectionLayer.TRANSLUCENT), sorting);
            if (indexBuffer == null) return;
            result = new PreviewSceneMeshCache.TranslucentOrder(indexBuffer);
            if (!compileState.accepts(generation, SceneCompileKind.TRANSLUCENT_ONLY)
                    || meshes.current() != cache || closed) return;
            meshes.publishTranslucent(result);
            compileState.markTranslucentCachePublished(generation);
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
        List<MeshData> translucent = cache.layers().get(ChunkSectionLayer.TRANSLUCENT);
        if (translucent == null) return;
        for (MeshData mesh : translucent) {
            ByteBufferBuilder.Result order = translucentDrawIndex(
                    cache.translucentOrder() == null ? null : cache.translucentOrder().indexBuffer(),
                    ((MeshDataAccessor) (Object) mesh).mmcr$getIndexBuffer());
            drawCopy(RenderTypes.translucentMovingBlock(), mesh, order);
        }
    }

    private void submitBlockEntities(PreviewSceneMeshCache.Meshes cache, PreviewSceneRenderContext context,
                                     PreviewSceneCamera sceneCamera) {
        if (context == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        BlockEntityRenderDispatcher blockEntities = minecraft.getBlockEntityRenderDispatcher();
        FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
        CameraRenderState cameraState = context.cameraState();
        blockEntities.prepare(new Vec3(sceneCamera.eye()));
        try {
            for (BlockPos position : cache.blockEntities()) {
                BlockEntity blockEntity = level.getBlockEntity(position);
                if (blockEntity == null) continue;
                try {
                    BlockEntityRenderState renderState = blockEntities.tryExtractRenderState(
                            blockEntity, context.partialTick(), null, null);
                    if (renderState != null) {
                        blockEntities.submit(renderState, context.poseStack(), context.submitStorage(), cameraState);
                    }
                } catch (RuntimeException exception) {
                    MMCR.LOG.error("Cannot render preview block entity {} at {} with state {}",
                            schema.machineId(), position, blockEntity.getBlockState(), exception);
                }
            }
            features.renderSolidFeatures();
            features.renderTranslucentFeatures();
        } finally {
            features.clearSubmitNodes();
            context.bufferSource().endBatch();
        }
    }

    private void drawOutlines(PreviewSceneRenderContext context, BlockHitResult hoverHit, BlockHitResult selectedHit) {
        if (context == null) return;
        if (hoverHit != null) drawOutline(context, hoverHit, 0xFFFFFF00);
        if (selectedHit != null && !selectedHit.equals(hoverHit)) drawOutline(context, selectedHit, 0xFF00FFFF);
    }

    private static void drawOutline(PreviewSceneRenderContext context, BlockHitResult hit, int color) {
        AABB box = new AABB(hit.getBlockPos()).inflate(0.002D);
        VertexConsumer vertices = context.bufferSource().getBuffer(RenderTypes.lines());
        PoseStack poseStack = new PoseStack();
        PoseStack.Pose pose = poseStack.last();
        float width = Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        line(vertices, pose, x0, y0, z0, x1, y0, z0, color, width); line(vertices, pose, x1, y0, z0, x1, y0, z1, color, width);
        line(vertices, pose, x1, y0, z1, x0, y0, z1, color, width); line(vertices, pose, x0, y0, z1, x0, y0, z0, color, width);
        line(vertices, pose, x0, y1, z0, x1, y1, z0, color, width); line(vertices, pose, x1, y1, z0, x1, y1, z1, color, width);
        line(vertices, pose, x1, y1, z1, x0, y1, z1, color, width); line(vertices, pose, x0, y1, z1, x0, y1, z0, color, width);
        line(vertices, pose, x0, y0, z0, x0, y1, z0, color, width); line(vertices, pose, x1, y0, z0, x1, y1, z0, color, width);
        line(vertices, pose, x1, y0, z1, x1, y1, z1, color, width); line(vertices, pose, x0, y0, z1, x0, y1, z1, color, width);
        context.bufferSource().endLastBatch();
    }

    private static void line(VertexConsumer vertices, PoseStack.Pose pose, double x0, double y0, double z0,
                             double x1, double y1, double z1, int color, float width) {
        float nx = (float) (x1 - x0), ny = (float) (y1 - y0), nz = (float) (z1 - z0);
        vertices.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
        vertices.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, -nx, -ny, -nz).setLineWidth(width);
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
            if (sortedIndices != null && indexBuilder != null) {
                ((MeshDataAccessor) (Object) drawMesh).mmcr$setIndexBuffer(copyIndexForDraw(sortedIndices, indexBuilder));
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
        MemoryUtil.memCopy(MemoryUtil.memAddress(duplicate), pointer, size);
        return destination.build();
    }

    static ByteBufferBuilder.Result copyIndexForDraw(ByteBufferBuilder.Result source, ByteBufferBuilder destination) {
        return copy(source.byteBuffer(), destination);
    }

    static ByteBufferBuilder.Result translucentDrawIndex(ByteBufferBuilder.Result publishedOrder,
                                                         ByteBufferBuilder.Result cachedMeshIndex) {
        return publishedOrder != null ? publishedOrder : cachedMeshIndex;
    }

    private void assertRenderThread() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null ? !minecraft.isSameThread() : Thread.currentThread() != renderThread) {
            throw new IllegalStateException("preview scene rendering must occur on the render thread");
        }
    }
}
