package cn.howxu.mmcr.client;

import cn.howxu.mmcr.MMCR;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;

/**
 * Renders the latest multiblock mismatch marker for the local player.
 *
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID, value = Dist.CLIENT)
public final class MultiblockMismatchHighlightClientHandler {
    private static final long DURATION_MILLIS = 8_000L;
    private static final long PHASE_MILLIS = 300L;
    private static final int RED = 0xFFFF0000;
    private static final float LINE_WIDTH = 4.0F;

    private static Highlight active;

    private MultiblockMismatchHighlightClientHandler() {}

    public static void show(ResourceKey<Level> dimension, BlockPos pos) {
        active = new Highlight(dimension, pos.immutable(), System.currentTimeMillis() + DURATION_MILLIS);
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Highlight highlight = active;
        Minecraft minecraft = Minecraft.getInstance();
        if (highlight == null || minecraft.level == null) return;

        long now = System.currentTimeMillis();
        if (now >= highlight.expiresAtMillis) {
            active = null;
            return;
        }
        if (!minecraft.level.dimension().equals(highlight.dimension)) return;
        if (((highlight.expiresAtMillis - now) / PHASE_MILLIS) % 2L == 0L) return;

        AABB box = new AABB(highlight.pos).inflate(0.005D);
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lines(), (pose, buffer) ->
                renderBoxEdges(pose, buffer, camera, box));
    }

    private static void renderBoxEdges(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, AABB box) {
        line(pose, buffer, camera, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ);
        line(pose, buffer, camera, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ);
        line(pose, buffer, camera, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ);
        line(pose, buffer, camera, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ);

        line(pose, buffer, camera, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ);
        line(pose, buffer, camera, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ);
        line(pose, buffer, camera, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ);
        line(pose, buffer, camera, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ);

        line(pose, buffer, camera, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ);
        line(pose, buffer, camera, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
        line(pose, buffer, camera, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ);
        line(pose, buffer, camera, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera,
            double x1, double y1, double z1, double x2, double y2, double z2) {
        float sx = (float) (x1 - camera.x());
        float sy = (float) (y1 - camera.y());
        float sz = (float) (z1 - camera.z());
        float ex = (float) (x2 - camera.x());
        float ey = (float) (y2 - camera.y());
        float ez = (float) (z2 - camera.z());
        Vector3f normal = new Vector3f(ex - sx, ey - sy, ez - sz).normalize();
        buffer.addVertex(pose, sx, sy, sz).setColor(RED).setNormal(pose, normal).setLineWidth(LINE_WIDTH);
        buffer.addVertex(pose, ex, ey, ez).setColor(RED).setNormal(pose, normal).setLineWidth(LINE_WIDTH);
    }

    private record Highlight(ResourceKey<Level> dimension, BlockPos pos, long expiresAtMillis) {}
}
