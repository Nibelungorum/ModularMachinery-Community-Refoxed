package cn.howxu.mmcr.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.client.preview.PreviewHighlights;
import cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactory;
import cn.howxu.mmcr.config.ClientConfig;
import cn.howxu.mmcr.internal.item.TerminalData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;

/**
 * Projects the selected terminal stage's preview colors onto world positions, independently of ghost blocks.
 *
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID, value = Dist.CLIENT)
public final class TerminalHighlightClientHandler {
    private static @Nullable GlobalPos controller;
    private static Map<BlockPos, Integer> highlights = Map.of();

    private TerminalHighlightClientHandler() {}

    public static void applyState(TerminalData data, @Nullable Identifier machineId, Direction facing) {
        clear();
        if (!data.highlightEnabled() || data.controller() == null || machineId == null) return;
        Machine machine = MachineRegistry.getMachine(machineId);
        if (machine == null) return;
        MachineStructureStage stage = machine.structureStages().stream()
                .filter(candidate -> candidate.number() == data.stage()).findFirst().orElse(null);
        if (stage == null) return;
        controller = data.controller();
        Map<BlockPos, Integer> projected = new LinkedHashMap<>();
        new StructurePreviewSchemaFactory().create(stage, machineId, facing).highlights().forEach((position, mask) -> {
            if (data.previewLayer() == Integer.MAX_VALUE || data.previewLayer() == position.getY()) {
                projected.put(data.controller().pos().offset(position), PreviewHighlights.color(mask));
            }
        });
        highlights = Map.copyOf(projected);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    @SubscribeEvent
    public static void onRenderLevelAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (highlights.isEmpty() || minecraft.level == null || controller == null
                || !minecraft.level.dimension().equals(controller.dimension())) return;
        double radius;
        try {
            radius = ClientConfig.PREVIEW_RENDER_RADIUS.get();
        } catch (IllegalStateException ignored) {
            radius = ClientConfig.DEFAULT_PREVIEW_RENDER_RADIUS;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        VertexConsumer vertices = buffers.getBuffer(RenderTypes.debugFilledBox());
        forEachVisibleHighlight(camera, radius, (position, color) ->
                drawFilledBox(vertices, pose.last(), new AABB(position).inflate(0.002D)
                        .move(-camera.x, -camera.y, -camera.z), color));
        buffers.endBatch(RenderTypes.debugFilledBox());
    }

    static void forEachVisibleHighlight(Vec3 camera, double radius, BiConsumer<BlockPos, Integer> consumer) {
        double radiusSquared = radius * radius;
        highlights.forEach((position, color) -> {
            double dx = position.getX() + 0.5D - camera.x;
            double dy = position.getY() + 0.5D - camera.y;
            double dz = position.getZ() + 0.5D - camera.z;
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) consumer.accept(position, color);
        });
    }

    static void clear() {
        controller = null;
        highlights = Map.of();
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
}
