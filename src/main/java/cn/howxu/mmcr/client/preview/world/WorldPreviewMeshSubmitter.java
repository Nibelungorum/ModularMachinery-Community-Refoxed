package cn.howxu.mmcr.client.preview.world;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Submits a compiled world preview once per render layer.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class WorldPreviewMeshSubmitter {
    private static final Map<ChunkSectionLayer, RenderType> RENDER_TYPES = Map.of(
            ChunkSectionLayer.SOLID, RenderTypes.solidMovingBlock(),
            ChunkSectionLayer.CUTOUT, RenderTypes.cutoutMovingBlock(),
            ChunkSectionLayer.TRANSLUCENT, RenderTypes.translucentMovingBlock());

    private WorldPreviewMeshSubmitter() { }

    public static int submit(WorldPreviewMesh mesh, PoseStack poseStack, SubmitNodeCollector collector,
            Vec3 camera) {
        poseStack.pushPose();
        poseStack.translate(-camera.x(), -camera.y(), -camera.z());
        int submissions = 0;
        for (Map.Entry<ChunkSectionLayer, RenderType> entry : RENDER_TYPES.entrySet()) {
            if (!mesh.meshes().containsKey(entry.getKey())) continue;
            ChunkSectionLayer layer = entry.getKey();
            collector.submitCustomGeometry(poseStack, entry.getValue(),
                    (pose, consumer) -> mesh.replay(layer, pose, consumer));
            submissions++;
        }
        poseStack.popPose();
        return submissions;
    }
}
