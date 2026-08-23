package cn.howxu.mmcr.client.preview.world;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Owns render-thread upload resources and submits the cached mesh once per layer.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class UploadedWorldPreviewMesh implements AutoCloseable {
    private static final Map<ChunkSectionLayer, RenderType> RENDER_TYPES = Map.of(
            ChunkSectionLayer.SOLID, RenderTypes.solidMovingBlock(),
            ChunkSectionLayer.CUTOUT, RenderTypes.cutoutMovingBlock(),
            ChunkSectionLayer.TRANSLUCENT, RenderTypes.translucentMovingBlock());

    private final WorldPreviewMesh mesh;
    private final Map<ChunkSectionLayer, AutoCloseable> resources;
    private boolean closed;

    UploadedWorldPreviewMesh(WorldPreviewMesh mesh, Map<ChunkSectionLayer, AutoCloseable> resources) {
        this.mesh = mesh;
        this.resources = Map.copyOf(resources);
    }

    public int submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, BlockPos origin) {
        poseStack.pushPose();
        poseStack.translate(-camera.x(), -camera.y(), -camera.z());
        int submissions = 0;
        for (Map.Entry<ChunkSectionLayer, RenderType> entry : RENDER_TYPES.entrySet()) {
            ChunkSectionLayer layer = entry.getKey();
            if (!resources.containsKey(layer)) continue;
            collector.submitCustomGeometry(poseStack, entry.getValue(),
                    (pose, consumer) -> mesh.replay(layer, pose, consumer));
            submissions++;
        }
        poseStack.popPose();
        return submissions;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        for (AutoCloseable resource : resources.values()) {
            try {
                resource.close();
            } catch (Exception exception) {
                if (failure == null) failure = new IllegalStateException("cannot close uploaded world preview mesh", exception);
                else failure.addSuppressed(exception);
            }
        }
        try {
            mesh.close();
        } catch (RuntimeException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }
}
