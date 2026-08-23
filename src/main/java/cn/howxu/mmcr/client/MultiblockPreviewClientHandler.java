package cn.howxu.mmcr.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.client.preview.world.WorldPreviewMesh;
import cn.howxu.mmcr.client.preview.world.WorldPreviewMeshCache;
import cn.howxu.mmcr.client.preview.world.WorldPreviewMeshCompiler;
import cn.howxu.mmcr.client.preview.world.WorldPreviewMeshKey;
import cn.howxu.mmcr.client.preview.world.WorldPreviewGpuMesh;
import cn.howxu.mmcr.client.preview.world.WorldPreviewCompileInput;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private static final WorldPreviewMeshCache worldMeshCache = new WorldPreviewMeshCache();
    private static final ExecutorService WORLD_MESH_COMPILER = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("mmcr-world-preview-compiler-").factory());
    private static WorldPreviewMeshCache.Request worldMeshRequest;
    private static WorldPreviewMeshCache.Request compilingWorldMeshRequest;
    private static Future<?> compilingWorldMesh;
    private static AtomicBoolean compilingWorldMeshCancelled;
    private static WorldPreviewGpuMesh gpuMesh;
    private static WorldPreviewMeshKey gpuMeshKey;
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
        worldMeshRequest = worldMeshCache.requestToken(worldMeshKey());
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
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || controllerPos == null || !minecraft.level.dimension().equals(dimension)) return;
        if (!isActive(minecraft.level.getGameTime())) {
            clear();
            return;
        }
        if (!(event instanceof RenderLevelStageEvent.AfterOpaqueBlocks)
                && !(event instanceof RenderLevelStageEvent.AfterTranslucentBlocks)) return;
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

    static WorldPreviewMeshCache.Request worldMeshRequestForTesting() {
        return worldMeshRequest;
    }

    static void rebuildVisibleEntriesForTesting(Vec3 camera) {
        rebuildVisibleEntries(camera);
    }

    static void resolveModelForTesting(BlockState state) {
        resolveModelForState(state, ignored -> null);
    }

    static void resolveModelForTesting(BlockState state, Function<BlockState, BlockModel> resolver) {
        resolveModelForState(state, resolver);
    }

    static void resolveVisibleModelsForTesting(Function<BlockState, BlockModel> resolver) {
        for (MultiblockPreviewSnapshot.Entry entry : visibleEntries) {
            resolveModelForState(entry.state(), resolver);
        }
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

    public static void invalidateWorldPreviewForReload() {
        Minecraft.getInstance().execute(() -> {
            worldMeshRequest = null;
            worldMeshCache.clear();
        });
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
        if (camera != null && !cameraCell.equals(visibleEntriesCameraCell)) {
            worldMeshRequest = worldMeshCache.requestToken(
                    new WorldPreviewMeshKey(dimension, controllerPos, selectedLayer, cameraCell));
            cancelCompilation();
        }

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
        cancelCompilation();
        if (gpuMesh != null) {
            gpuMesh.close();
            gpuMesh = null;
            gpuMeshKey = null;
        }
        worldMeshRequest = null;
        dimension = null;
        controllerPos = null;
        entries = List.of();
        visibleEntries = List.of();
        layers = List.of();
        modelCache.clear();
        worldMeshCache.clear();
        visibleEntriesCameraCell = null;
        visibleEntriesRadius = -1.0;
        selectedLayer = Integer.MAX_VALUE;
        expiresAtTick = -1L;
    }

    private static WorldPreviewMeshKey worldMeshKey() {
        return new WorldPreviewMeshKey(dimension, controllerPos, selectedLayer, visibleEntriesCameraCell);
    }

    private static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        rebuildVisibleEntries(camera);
        if (visibleEntries.isEmpty()) return;
        WorldPreviewMeshKey key = worldMeshKey();
        WorldPreviewGpuMesh mesh = gpuMeshKey != null && gpuMeshKey.equals(key) ? gpuMesh : null;
        if (mesh == null) {
            if (gpuMesh != null) {
                gpuMesh.close();
                gpuMesh = null;
                gpuMeshKey = null;
            }
            AutoCloseable pending = worldMeshCache.takeCurrent(key);
            if (pending instanceof WorldPreviewMesh compiled) {
                gpuMesh = WorldPreviewGpuMesh.upload(compiled);
                gpuMeshKey = key;
                mesh = gpuMesh;
            } else {
                startCompilation(key, minecraft, camera);
                return;
            }
        }

        RenderSystem.getModelViewStack().pushMatrix();
        try {
            RenderSystem.getModelViewStack().set(event.getModelViewMatrix());
            if (event instanceof RenderLevelStageEvent.AfterOpaqueBlocks) {
                mesh.draw(ChunkSectionLayer.SOLID, event.getModelViewMatrix());
                mesh.draw(ChunkSectionLayer.CUTOUT, event.getModelViewMatrix());
            } else {
                mesh.resortTranslucent(camera);
                mesh.draw(ChunkSectionLayer.TRANSLUCENT, event.getModelViewMatrix());
            }
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
        }
    }

    private static void startCompilation(WorldPreviewMeshKey key, Minecraft minecraft, Vec3 camera) {
        if (compilingWorldMeshRequest != null && compilingWorldMeshRequest.key().equals(key)) return;
        cancelCompilation();
        if (worldMeshRequest == null) worldMeshRequest = worldMeshCache.requestToken(key);
        WorldPreviewMeshCache.Request request = worldMeshRequest;
        AtomicBoolean cancelled = new AtomicBoolean(false);
        List<MultiblockPreviewSnapshot.Entry> compileEntries = List.copyOf(visibleEntries);
        BlockPos compileController = controllerPos;
        int compileLayer = selectedLayer;
        Vec3 compileCamera = camera;
        WorldPreviewCompileInput input = WorldPreviewCompileInput.capture(minecraft.level, controllerPos,
                compileEntries, compileLayer, minecraft);
        compilingWorldMeshRequest = request;
        compilingWorldMeshCancelled = cancelled;
        compilingWorldMesh = WORLD_MESH_COMPILER.submit(() -> {
            WorldPreviewMesh result = null;
            try {
                result = WorldPreviewMeshCompiler.compileSnapshot(input, compileController, compileEntries,
                        compileLayer, compileCamera, cancelled);
            } catch (RuntimeException exception) {
                if (!cancelled.get()) MMCR.LOG.error("Cannot compile world preview mesh", exception);
            }
            WorldPreviewMesh compiled = result;
            minecraft.execute(() -> {
                if (request == compilingWorldMeshRequest) {
                    compilingWorldMesh = null;
                    compilingWorldMeshRequest = null;
                    compilingWorldMeshCancelled = null;
                }
                if (compiled != null) worldMeshCache.publish(request, compiled);
            });
        });
    }

    private static void cancelCompilation() {
        if (compilingWorldMeshCancelled != null) compilingWorldMeshCancelled.set(true);
        if (compilingWorldMesh != null) compilingWorldMesh.cancel(false);
        compilingWorldMesh = null;
        compilingWorldMeshRequest = null;
        compilingWorldMeshCancelled = null;
    }

    private static CachedModel resolveModelForState(BlockState state, Function<BlockState, BlockModel> resolver) {
        return modelCache.computeIfAbsent(state, currentState -> resolveModel(resolver.apply(currentState)));
    }

    private static CachedModel resolveModel(BlockModel model) {
        return new CachedModel(model);
    }

    static boolean updateRenderStateForTesting(BlockState state, BlockModel model) {
        BlockModelRenderState renderState = new BlockModelRenderState();
        updateRenderState(renderState, model, state);
        return !renderState.isEmpty();
    }

    private static void updateRenderState(BlockModelRenderState renderState, BlockModel model, BlockState state) {
        // BlockModel.update is the complete 26.1.2 resolver path, including special renderers.
        renderState.clear();
        model.update(renderState, state, BLOCK_DISPLAY_CONTEXT, 42L);
    }

    private record CachedModel(BlockModel model) {
    }
}
