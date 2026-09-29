package cn.howxu.mmcr.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.item.MultiblockDetectorItem;
import cn.howxu.mmcr.internal.item.MultiblockDetectorSelection;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * Client-only outline renderer for the multiblock detector selected export region.
 *
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID, value = Dist.CLIENT)
public final class MultiblockDetectorSelectionRenderer {

    private static final int GREEN = 0xFF00FF00;
    private MultiblockDetectorSelectionRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        ItemStack detector = heldDetector(minecraft.player);
        if (detector.isEmpty()) return;

        MultiblockDetectorSelection selection = MultiblockDetectorItem.selection(detector);
        if (selection.firstPos() == null || selection.secondPos() == null) return;

        BlockPos first = selection.firstPos();
        BlockPos second = selection.secondPos();
        double minX = Math.min(first.getX(), second.getX());
        double minY = Math.min(first.getY(), second.getY());
        double minZ = Math.min(first.getZ(), second.getZ());
        double maxX = Math.max(first.getX(), second.getX()) + 1.0D;
        double maxY = Math.max(first.getY(), second.getY()) + 1.0D;
        double maxZ = Math.max(first.getZ(), second.getZ()) + 1.0D;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        renderBoxEdges(poseStack.last(), buffer.getBuffer(RenderType.lines()), camera,
                minX, minY, minZ, maxX, maxY, maxZ);
        buffer.endBatch(RenderType.lines());
        if (shouldRenderMask(detector, selection)) {
            renderBoxFaces(poseStack, buffer.getBuffer(RenderType.debugFilledBox()), camera,
                    minX, minY, minZ, maxX, maxY, maxZ);
            buffer.endBatch(RenderType.debugFilledBox());
        }
    }

    static boolean shouldRenderMask(ItemStack detector, MultiblockDetectorSelection selection) {
        return selection.isComplete() && detector.getOrDefault(ModDataComponents.MULTIBLOCK_DETECTOR_MASK.get(), false);
    }

    private static ItemStack heldDetector(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.MULTIBLOCK_DETECTOR.get())) return main;
        ItemStack off = player.getOffhandItem();
        return off.is(ModItems.MULTIBLOCK_DETECTOR.get()) ? off : ItemStack.EMPTY;
    }

    private static void renderBoxEdges(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        line(pose, buffer, camera, minX, minY, minZ, maxX, minY, minZ);
        line(pose, buffer, camera, minX, minY, maxZ, maxX, minY, maxZ);
        line(pose, buffer, camera, minX, maxY, minZ, maxX, maxY, minZ);
        line(pose, buffer, camera, minX, maxY, maxZ, maxX, maxY, maxZ);

        line(pose, buffer, camera, minX, minY, minZ, minX, maxY, minZ);
        line(pose, buffer, camera, maxX, minY, minZ, maxX, maxY, minZ);
        line(pose, buffer, camera, minX, minY, maxZ, minX, maxY, maxZ);
        line(pose, buffer, camera, maxX, minY, maxZ, maxX, maxY, maxZ);

        line(pose, buffer, camera, minX, minY, minZ, minX, minY, maxZ);
        line(pose, buffer, camera, maxX, minY, minZ, maxX, minY, maxZ);
        line(pose, buffer, camera, minX, maxY, minZ, minX, maxY, maxZ);
        line(pose, buffer, camera, maxX, maxY, minZ, maxX, maxY, maxZ);
    }

    private static void renderBoxFaces(PoseStack poseStack, VertexConsumer buffer, Vec3 camera,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        LevelRenderer.addChainedFilledBoxVertices(poseStack, buffer,
                minX - camera.x(), minY - camera.y(), minZ - camera.z(),
                maxX - camera.x(), maxY - camera.y(), maxZ - camera.z(),
                0.0F, 1.0F, 0.0F, 0.4F);
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
        buffer.addVertex(pose, sx, sy, sz).setColor(GREEN).setNormal(pose, normal.x(), normal.y(), normal.z());
        buffer.addVertex(pose, ex, ey, ez).setColor(GREEN).setNormal(pose, normal.x(), normal.y(), normal.z());
    }
}
