/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-ResourceLocation: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.preview.PreviewCamera;
import cn.howxu.mmcr.client.preview.PreviewLevel;
import cn.howxu.mmcr.client.preview.PreviewVisibility;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    private CompletableFuture<PreviewSceneMeshCompiler.CompiledScene> fullCompilation;
    private AtomicBoolean fullCompilationCancelled;

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
        if (!closed) {
            cancelFullCompilation();
            requestedGeneration = compileState.requestFullRebuild();
        }
    }

    public void render(PreviewSceneRenderContext context, PreviewCamera camera, BlockHitResult hoverHit,
                       BlockHitResult selectedHit, boolean renderTranslucent, boolean renderBlockEntities) {
        assertRenderThread();
        if (closed) return;
        PreviewSceneCamera sceneCamera = PreviewSceneCamera.from(camera, 1, 1);
        if (lastEye == null || !lastEye.equals(sceneCamera.eye())) {
            lastEye = sceneCamera.eye();
            requestedGeneration = compileState.onCameraPanOrZoom();
        }
        if (compileState.pendingKind() == SceneCompileKind.FULL) {
            startFullCompilation(sceneCamera);
        }
        PreviewSceneMeshCache.FullCache owner = meshes.current();
        if (owner instanceof PreviewSceneMeshCache.Meshes cache) {
            if (compileState.pendingKind() == SceneCompileKind.TRANSLUCENT_ONLY) {
                compileSortedLayers(cache, sceneCamera);
            }
            for (PreviewSceneMeshCache.MeshLayer layer : cache.renderLayers()) {
                if (!layer.renderType().sortOnUpload()
                        || layer.interactiveOnly() == !renderTranslucent) {
                    draw(cache, layer, context.framebufferId());
                }
            }
            if (renderBlockEntities) submitBlockEntities(cache, context, sceneCamera);
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
        Matrix4f inverse = camera.inverseViewProjection();
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
        cancelFullCompilation();
        compileState.close();
        meshes.close();
        level.close();
    }

    private void startFullCompilation(PreviewSceneCamera camera) {
        if (fullCompilation != null) return;
        long generation = requestedGeneration;
        AtomicBoolean cancelled = new AtomicBoolean(closed);
        PreviewSceneMeshCompiler.CompilationInput input =
                PreviewSceneMeshCompiler.capture(level, schema, visibility);
        CompletableFuture<PreviewSceneMeshCompiler.CompiledScene> compilation =
                PreviewSceneMeshCompiler.compileAsync(input, camera, cancelled);
        fullCompilation = compilation;
        fullCompilationCancelled = cancelled;
        Minecraft minecraft = Minecraft.getInstance();
        compilation.whenComplete((compiled, failure) -> minecraft.execute(() -> finishFullCompilation(
                compilation, generation, camera, cancelled, compiled, failure)));
    }

    private void finishFullCompilation(CompletableFuture<PreviewSceneMeshCompiler.CompiledScene> compilation,
                                       long generation, PreviewSceneCamera camera, AtomicBoolean cancelled,
                                       PreviewSceneMeshCompiler.CompiledScene compiled, Throwable failure) {
        if (fullCompilation == compilation) {
            fullCompilation = null;
            fullCompilationCancelled = null;
        }
        if (failure != null) {
            if (!cancelled.get() && !closed) {
                MMCR.LOG.error("Cannot compile preview scene mesh", failure);
            }
            return;
        }
        if (compiled == null) return;
        PreviewSceneMeshCache.Meshes result = null;
        boolean handedOff = false;
        try {
            if (!compileState.accepts(generation, SceneCompileKind.FULL) || cancelled.get() || closed) return;
            result = PreviewSceneMeshCompiler.assemble(compiled);
            meshes.publish(result);
            result = null;
            handedOff = true;
            compileState.markFullCachePublished();
            if (lastEye != null && !lastEye.equals(camera.eye())) {
                requestedGeneration = compileState.onCameraPanOrZoom();
            }
        } finally {
            if (result != null) meshes.reject(result);
            if (!handedOff) compiled.close();
        }
    }

    private void cancelFullCompilation() {
        if (fullCompilationCancelled != null) fullCompilationCancelled.set(true);
    }

    private void compileSortedLayers(PreviewSceneMeshCache.Meshes cache, PreviewSceneCamera camera) {
        Map<PreviewSceneMeshCache.MeshLayer, List<PreviewSceneMeshCache.MeshPart>> sortedParts =
                cache.sortedParts();
        if (sortedParts.isEmpty()) {
            return;
        }
        long generation = requestedGeneration;
        PreviewSceneMeshCache.SortedOrder result = null;
        Map<PreviewSceneMeshCache.MeshLayer, List<ByteBufferBuilder.Result>> indexBuffers =
                new java.util.LinkedHashMap<>();
        try {
            VertexSorting sorting = VertexSorting.byDistance(camera.eye().x, camera.eye().y, camera.eye().z);
            Map<PreviewSceneMeshCache.MeshLayer, List<VertexFormat.IndexType>> indexTypes =
                    new java.util.LinkedHashMap<>();
            for (Map.Entry<PreviewSceneMeshCache.MeshLayer,
                    List<PreviewSceneMeshCache.MeshPart>> entry : sortedParts.entrySet()) {
                List<ByteBufferBuilder.Result> layerBuffers = new ArrayList<>(entry.getValue().size());
                List<VertexFormat.IndexType> layerTypes = new ArrayList<>(entry.getValue().size());
                indexBuffers.put(entry.getKey(), layerBuffers);
                indexTypes.put(entry.getKey(), layerTypes);
                for (PreviewSceneMeshCache.MeshPart part : entry.getValue()) {
                    MeshData.SortState sortState = part.sortStates().get(entry.getKey());
                    ByteBufferBuilder.Result indexBuffer = sortState.buildSortedIndexBuffer(
                            part.builder(entry.getKey()), sorting);
                    if (indexBuffer == null) {
                        indexBuffers.values().forEach(buffers -> buffers.forEach(ByteBufferBuilder.Result::close));
                        return;
                    }
                    layerBuffers.add(indexBuffer);
                    layerTypes.add(sortState.indexType());
                }
            }
            result = new PreviewSceneMeshCache.SortedOrder(indexBuffers, indexTypes);
            if (!compileState.accepts(generation, SceneCompileKind.TRANSLUCENT_ONLY)
                    || meshes.current() != cache || closed) return;
            meshes.publishTranslucent(result);
            compileState.markTranslucentCachePublished(generation);
            result = null;
        } catch (RuntimeException exception) {
            if (result == null) closeIndexBuffers(indexBuffers, exception);
            throw exception;
        } finally {
            if (result != null) meshes.reject(result);
        }
    }

    private static void closeIndexBuffers(
            Map<PreviewSceneMeshCache.MeshLayer, List<ByteBufferBuilder.Result>> indexBuffers,
            RuntimeException failure) {
        for (List<ByteBufferBuilder.Result> buffers : indexBuffers.values()) {
            for (ByteBufferBuilder.Result buffer : buffers) {
                try {
                    buffer.close();
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    private static void draw(PreviewSceneMeshCache.Meshes cache,
            PreviewSceneMeshCache.MeshLayer layer, int framebufferId) {
        cache.draw(layer, framebufferId);
    }

    private void submitBlockEntities(PreviewSceneMeshCache.Meshes cache, PreviewSceneRenderContext context,
                                     PreviewSceneCamera sceneCamera) {
        if (context == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        BlockEntityRenderDispatcher blockEntities = minecraft.getBlockEntityRenderDispatcher();
        for (BlockPos position : cache.blockEntities()) {
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity == null) continue;
            try {
                renderBlockEntity(blockEntities, blockEntity, position, context);
            } catch (RuntimeException exception) {
                MMCR.LOG.error("Cannot render preview block entity {} at {} with state {}",
                        schema.machineId(), position, blockEntity.getBlockState(), exception);
            }
        }
        context.bufferSource().endBatch();
    }

    private static <T extends BlockEntity> void renderBlockEntity(BlockEntityRenderDispatcher dispatcher,
            T blockEntity, BlockPos position, PreviewSceneRenderContext context) {
        BlockEntityRenderer<T> renderer = dispatcher.getRenderer(blockEntity);
        if (renderer == null) return;
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        try {
            poseStack.translate(position.getX(), position.getY(), position.getZ());
            renderer.render(blockEntity, context.partialTick(), poseStack, context.bufferSource(),
                    15728880, OverlayTexture.NO_OVERLAY);
        } finally {
            poseStack.popPose();
        }
    }

    private void drawOutlines(PreviewSceneRenderContext context, BlockHitResult hoverHit, BlockHitResult selectedHit) {
        if (context == null) return;
        if (hoverHit != null) drawHighlight(context, hoverHit, 0xFFFFFF00);
        if (selectedHit != null && !selectedHit.equals(hoverHit)) drawHighlight(context, selectedHit, 0xFF00FFFF);
    }

    private static void drawHighlight(PreviewSceneRenderContext context, BlockHitResult hit, int color) {
        AABB box = new AABB(hit.getBlockPos()).inflate(0.002D);
        PoseStack.Pose pose = new PoseStack().last();
        RenderType fillType = RenderType.debugQuads();
        VertexConsumer fill = context.bufferSource().getBuffer(fillType);
        drawFilledBox(fill, pose, box, (color & 0x00FFFFFF) | 0x44000000);
        context.bufferSource().endBatch(fillType);

        RenderType lineType = RenderType.lines();
        VertexConsumer lines = context.bufferSource().getBuffer(lineType);
        drawOutline(lines, pose, box, color);
        context.bufferSource().endBatch(lineType);
    }

    private static void drawFilledBox(VertexConsumer vertices, PoseStack.Pose pose, AABB box, int color) {
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        quad(vertices, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, color);
        quad(vertices, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, color);
        quad(vertices, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, color);
        quad(vertices, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, color);
        quad(vertices, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, color);
        quad(vertices, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, color);
    }

    private static void quad(VertexConsumer vertices, PoseStack.Pose pose,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz, int color) {
        vertices.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(color);
        vertices.addVertex(pose, (float) bx, (float) by, (float) bz).setColor(color);
        vertices.addVertex(pose, (float) cx, (float) cy, (float) cz).setColor(color);
        vertices.addVertex(pose, (float) dx, (float) dy, (float) dz).setColor(color);
    }

    private static void drawOutline(VertexConsumer vertices, PoseStack.Pose pose, AABB box, int color) {
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        line(vertices, pose, x0, y0, z0, x1, y0, z0, color); line(vertices, pose, x1, y0, z0, x1, y0, z1, color);
        line(vertices, pose, x1, y0, z1, x0, y0, z1, color); line(vertices, pose, x0, y0, z1, x0, y0, z0, color);
        line(vertices, pose, x0, y1, z0, x1, y1, z0, color); line(vertices, pose, x1, y1, z0, x1, y1, z1, color);
        line(vertices, pose, x1, y1, z1, x0, y1, z1, color); line(vertices, pose, x0, y1, z1, x0, y1, z0, color);
        line(vertices, pose, x0, y0, z0, x0, y1, z0, color); line(vertices, pose, x1, y0, z0, x1, y1, z0, color);
        line(vertices, pose, x1, y0, z1, x1, y1, z1, color); line(vertices, pose, x0, y0, z1, x0, y1, z1, color);
    }

    private static void line(VertexConsumer vertices, PoseStack.Pose pose, double x0, double y0, double z0,
                              double x1, double y1, double z1, int color) {
        float nx = (float) (x1 - x0), ny = (float) (y1 - y0), nz = (float) (z1 - z0);
        vertices.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(color).setNormal(pose, nx, ny, nz);
        vertices.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, -nx, -ny, -nz);
    }

    private void assertRenderThread() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null ? !minecraft.isSameThread() : Thread.currentThread() != renderThread) {
            throw new IllegalStateException("preview scene rendering must occur on the render thread");
        }
    }
}
