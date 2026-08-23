package cn.howxu.mmcr.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Renders the active multiblock ghost preview for the local player.
 *
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID, value = Dist.CLIENT)
public final class MultiblockPreviewClientHandler {
    private static ResourceKey<Level> dimension;
    private static BlockPos controllerPos;
    private static List<MultiblockPreviewSnapshot.Entry> entries = List.of();
    private static List<MultiblockPreviewSnapshot.Entry> visibleEntries = List.of();
    private static List<Integer> layers = List.of();
    private static final Map<BlockState, CachedModel> modelCache = new HashMap<>();
    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();
    private static BlockPos visibleEntriesCameraCell;
    private static double visibleEntriesRadius = -1.0;
    private static int selectedLayer = Integer.MAX_VALUE;
    private static long expiresAtTick = -1L;

    private MultiblockPreviewClientHandler() {}

    public static void show(ResourceKey<Level> newDimension, BlockPos newControllerPos,
                            List<MultiblockPreviewSnapshot.Entry> newEntries, int durationTicks) {
        if (newEntries.isEmpty()) {
            clearPreview(newDimension, newControllerPos);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        showAtTick(newDimension, newControllerPos, newEntries, durationTicks, now);
    }

    public static void clearPreview(ResourceKey<Level> previewDimension, BlockPos previewControllerPos) {
        if (controllerPos != null && previewDimension.equals(dimension) && previewControllerPos.equals(controllerPos)) clear();
    }

    static void showAtTick(ResourceKey<Level> newDimension, BlockPos newControllerPos,
                           List<MultiblockPreviewSnapshot.Entry> newEntries, int durationTicks, long now) {
        boolean sameActiveController = isActive(now)
                && newDimension.equals(dimension)
                && newControllerPos.equals(controllerPos);

        dimension = newDimension;
        controllerPos = newControllerPos.immutable();
        entries = List.copyOf(newEntries);
        modelCache.clear();
        visibleEntriesCameraCell = null;
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
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
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

    static void rebuildVisibleEntriesForTesting(Vec3 camera) {
        rebuildVisibleEntries(camera);
    }

    static void resolveModelForTesting(BlockState state) {
        resolveModelForState(state, ignored -> null);
    }

    static void resolveModelForTesting(BlockState state, Function<BlockState, BlockStateModel> resolver) {
        resolveModelForState(state, resolver);
    }

    static int resolvedModelCacheSizeForTesting() {
        return modelCache.size();
    }

    static boolean rendersPreviewOutlineForTesting() {
        return false;
    }

    static void expireForTesting(long now) {
        if (!isActive(now)) clear();
    }

    static void unloadClientLevelForTesting() {
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
        visibleEntriesCameraCell = null;
        rebuildVisibleEntries(null);
    }

    private static void rebuildVisibleEntries(Vec3 camera) {
        double radius;
        try {
            radius = Config.PREVIEW_RENDER_RADIUS.get();
        } catch (IllegalStateException ignored) {
            radius = Config.DEFAULT_PREVIEW_RENDER_RADIUS;
        }
        BlockPos cameraCell = camera == null ? null : BlockPos.containing(camera);
        if (camera != null && cameraCell.equals(visibleEntriesCameraCell) && radius == visibleEntriesRadius) return;

        var candidates = selectedLayer == Integer.MAX_VALUE ? entries : entries.stream()
                .filter(entry -> entry.relativePos().getY() == selectedLayer)
                .sorted(Comparator.comparingInt((MultiblockPreviewSnapshot.Entry entry) -> entry.relativePos().getX())
                        .thenComparingInt(entry -> entry.relativePos().getZ()))
                .toList();
        if (camera == null) {
            visibleEntries = candidates;
            visibleEntriesRadius = radius;
            return;
        }

        double radiusSquared = radius * radius;
        visibleEntries = candidates.stream().filter(entry -> {
            BlockPos worldPos = controllerPos.offset(entry.relativePos());
            double dx = worldPos.getX() + 0.5 - camera.x;
            double dy = worldPos.getY() + 0.5 - camera.y;
            double dz = worldPos.getZ() + 0.5 - camera.z;
            return dx * dx + dy * dy + dz * dz <= radiusSquared;
        }).toList();
        visibleEntriesCameraCell = cameraCell;
        visibleEntriesRadius = radius;
    }

    private static void clear() {
        dimension = null;
        controllerPos = null;
        entries = List.of();
        visibleEntries = List.of();
        layers = List.of();
        modelCache.clear();
        visibleEntriesCameraCell = null;
        visibleEntriesRadius = -1.0;
        selectedLayer = Integer.MAX_VALUE;
        expiresAtTick = -1L;
    }

    private static void render(SubmitCustomGeometryEvent event, Minecraft minecraft) {
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        rebuildVisibleEntries(camera);
        if (visibleEntries.isEmpty()) return;
        var collector = event.getSubmitNodeCollector();

        for (MultiblockPreviewSnapshot.Entry entry : visibleEntries) {
            BlockPos worldPos = controllerPos.offset(entry.relativePos());
            poseStack.pushPose();
            poseStack.translate(worldPos.getX() - camera.x, worldPos.getY() - camera.y, worldPos.getZ() - camera.z);

            resolveModelForState(entry.state(), state ->
                    minecraft.getModelManager().getBlockStateModelSet().get(state));
            BlockModelRenderState renderState = new BlockModelRenderState();
            minecraft.getBlockModelResolver().update(renderState, entry.state(), BLOCK_DISPLAY_CONTEXT);
            renderState.submitMultiLayer(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);

            poseStack.popPose();
        }
    }

    private static CachedModel resolveModelForState(BlockState state, Function<BlockState, BlockStateModel> resolver) {
        return modelCache.computeIfAbsent(state, currentState -> resolveModel(resolver.apply(currentState)));
    }

    private static CachedModel resolveModel(BlockStateModel model) {
        if (model == null) return new CachedModel(false);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        boolean translucent = parts.stream().anyMatch(part ->
                (part.materialFlags() & BakedQuad.FLAG_TRANSLUCENT) != 0);
        return new CachedModel(translucent);
    }

    private record CachedModel(boolean translucent) {
    }
}
