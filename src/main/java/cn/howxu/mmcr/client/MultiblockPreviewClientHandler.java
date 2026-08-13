package cn.howxu.mmcr.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Comparator;
import java.util.List;

/**
 * Renders the active multiblock ghost preview for the local player.
 *
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID, value = Dist.CLIENT)
public final class MultiblockPreviewClientHandler {
    private static final int PREVIEW_COLOR = 0xAA66CCFF;
    private static final float LINE_WIDTH = 2.0F;

    private static ResourceKey<Level> dimension;
    private static BlockPos controllerPos;
    private static List<MultiblockPreviewSnapshot.Entry> entries = List.of();
    private static List<MultiblockPreviewSnapshot.Entry> visibleEntries = List.of();
    private static List<Integer> layers = List.of();
    private static int selectedLayer = Integer.MAX_VALUE;
    private static long expiresAtTick = -1L;

    private MultiblockPreviewClientHandler() {}

    public static void show(ResourceKey<Level> newDimension, BlockPos newControllerPos,
                            List<MultiblockPreviewSnapshot.Entry> newEntries, int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        showAtTick(newDimension, newControllerPos, newEntries, durationTicks, now);
    }

    static void showAtTick(ResourceKey<Level> newDimension, BlockPos newControllerPos,
                           List<MultiblockPreviewSnapshot.Entry> newEntries, int durationTicks, long now) {
        boolean sameActiveController = isActive(now)
                && newDimension.equals(dimension)
                && newControllerPos.equals(controllerPos);

        dimension = newDimension;
        controllerPos = newControllerPos.immutable();
        entries = List.copyOf(newEntries);
        layers = entries.stream().map(entry -> entry.relativePos().getY()).distinct().sorted().toList();
        selectedLayer = sameActiveController ? nextLayer() : Integer.MAX_VALUE;
        expiresAtTick = now + Math.max(1, durationTicks);
        rebuildVisibleEntries();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && !isActive(minecraft.level.getGameTime())) clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    @SubscribeEvent
    public static void onRenderAfterWeather(RenderLevelStageEvent.AfterWeather event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || controllerPos == null || !minecraft.level.dimension().equals(dimension)) return;
        if (!isActive(minecraft.level.getGameTime())) {
            clear();
            return;
        }
        render(event, minecraft);
    }

    static int selectedLayerForTesting() {
        return selectedLayer;
    }

    static int visibleEntryCountForTesting() {
        return visibleEntries.size();
    }

    static void clearForTesting() {
        clear();
    }

    private static boolean isActive(long now) {
        return controllerPos != null && expiresAtTick > now;
    }

    private static int nextLayer() {
        if (layers.isEmpty()) return Integer.MAX_VALUE;
        if (selectedLayer == Integer.MAX_VALUE) return layers.getFirst();
        int index = layers.indexOf(selectedLayer);
        if (index < 0 || index + 1 >= layers.size()) return Integer.MAX_VALUE;
        return layers.get(index + 1);
    }

    private static void rebuildVisibleEntries() {
        if (selectedLayer == Integer.MAX_VALUE) {
            visibleEntries = entries;
            return;
        }
        visibleEntries = entries.stream()
                .filter(entry -> entry.relativePos().getY() == selectedLayer)
                .sorted(Comparator.comparingInt((MultiblockPreviewSnapshot.Entry entry) -> entry.relativePos().getX())
                        .thenComparingInt(entry -> entry.relativePos().getZ()))
                .toList();
    }

    private static void clear() {
        dimension = null;
        controllerPos = null;
        entries = List.of();
        visibleEntries = List.of();
        layers = List.of();
        selectedLayer = Integer.MAX_VALUE;
        expiresAtTick = -1L;
    }

    private static void render(RenderLevelStageEvent.AfterWeather event, Minecraft minecraft) {
        if (visibleEntries.isEmpty()) return;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (MultiblockPreviewSnapshot.Entry entry : visibleEntries) {
            BlockPos worldPos = controllerPos.offset(entry.relativePos());
            ShapeRenderer.renderShape(
                    poseStack,
                    buffer.getBuffer(RenderTypes.lines()),
                    Shapes.create(new AABB(worldPos).inflate(-0.1D)),
                    0.0D,
                    0.0D,
                    0.0D,
                    PREVIEW_COLOR,
                    LINE_WIDTH);
        }
        poseStack.popPose();
        buffer.endBatch(RenderTypes.lines());
    }
}
