package cn.howxu.mmcr.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.config.ClientConfig;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.client.preview.world.WorldPreviewMesh;
import cn.howxu.mmcr.client.preview.world.WorldPreviewMeshCache;
import cn.howxu.mmcr.client.preview.world.WorldPreviewMeshCompiler;
import cn.howxu.mmcr.client.preview.world.WorldPreviewMeshKey;
import cn.howxu.mmcr.client.preview.world.WorldPreviewGpuMesh;
import cn.howxu.mmcr.client.preview.world.WorldPreviewCompileInput;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    private static final WorldPreviewMeshCache worldMeshCache = new WorldPreviewMeshCache();
    private static final ExecutorService WORLD_MESH_COMPILER = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("mmcr-world-preview-compiler-").factory());
    private static WorldPreviewMeshCache.Request worldMeshRequest;
    private static WorldPreviewMeshCache.Request compilingWorldMeshRequest;
    private static Future<?> compilingWorldMesh;
    private static AtomicBoolean compilingWorldMeshCancelled;
    private static WorldPreviewCompileInput worldMeshCompileInput;
    private static WorldPreviewGpuMesh gpuMesh;
    private static WorldPreviewMeshKey gpuMeshKey;
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
        if (previewDimension.equals(dimension) && previewControllerPos.equals(controllerPos)) clear();
    }

    public static void setSelectedLayer(int newLayer) {
        if (controllerPos == null || (newLayer != Integer.MAX_VALUE && !layers.contains(newLayer))) return;
        if (selectedLayer == newLayer) return;
        selectedLayer = newLayer;
        worldMeshCompileInput = null;
        cancelCompilation();
        if (gpuMesh != null) {
            gpuMesh.close();
            gpuMesh = null;
            gpuMeshKey = null;
        }
        rebuildVisibleEntries();
    }

    static void showAtTick(ResourceKey<Level> newDimension, BlockPos newControllerPos,
                           List<MultiblockPreviewSnapshot.Entry> newEntries, int durationTicks, long now) {
        boolean sameActiveController = isActive(now)
                && newDimension.equals(dimension)
                && newControllerPos.equals(controllerPos);

        dimension = newDimension;
        controllerPos = newControllerPos.immutable();
        entries = List.copyOf(newEntries);
        worldMeshCompileInput = null;
        visibleEntriesCameraCell = null;
        layers = entries.stream().map(entry -> entry.relativePos().getY()).distinct().sorted().toList();
        selectedLayer = sameActiveController ? nextLayer() : Integer.MAX_VALUE;
        worldMeshRequest = worldMeshCache.requestToken(worldMeshKey());
        expiresAtTick = durationTicks < 0 ? Long.MAX_VALUE : now + Math.max(1, durationTicks);
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
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS
                && stage != RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS
                && stage != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS
                && stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                && stage != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) return;

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

    static WorldPreviewMeshCache.Request worldMeshRequestForTesting() {
        return worldMeshRequest;
    }

    static void rebuildVisibleEntriesForTesting(Vec3 camera) {
        rebuildVisibleEntries(camera);
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
            worldMeshCompileInput = null;
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
            radius = ClientConfig.PREVIEW_RENDER_RADIUS.get();
        } catch (IllegalStateException ignored) {
            radius = ClientConfig.DEFAULT_PREVIEW_RENDER_RADIUS;
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
        worldMeshCache.clear();
        worldMeshCompileInput = null;
        visibleEntriesCameraCell = null;
        visibleEntriesRadius = -1.0;
        selectedLayer = Integer.MAX_VALUE;
        expiresAtTick = -1L;
    }

    private static WorldPreviewMeshKey worldMeshKey() {
        return new WorldPreviewMeshKey(dimension, controllerPos, selectedLayer, visibleEntriesCameraCell);
    }

    private static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        Vec3 camera = event.getCamera().getPosition();
        rebuildVisibleEntries(camera);
        if (visibleEntries.isEmpty()) return;
        WorldPreviewMeshKey key = worldMeshKey();
        WorldPreviewGpuMesh mesh = gpuMesh;
        if (gpuMeshKey == null || !gpuMeshKey.equals(key)) {
            AutoCloseable pending = worldMeshCache.takeCurrent(key);
            if (pending instanceof WorldPreviewMesh compiled) {
                WorldPreviewGpuMesh previous = gpuMesh;
                gpuMesh = WorldPreviewGpuMesh.upload(compiled);
                gpuMeshKey = key;
                mesh = gpuMesh;
                if (previous != null) previous.close();
            } else {
                startCompilation(key, minecraft, camera);
                if (mesh == null) return;
            }
        }

        RenderSystem.getModelViewStack().pushMatrix();
        try {
            RenderSystem.getModelViewStack().set(event.getModelViewMatrix());
            RenderSystem.getModelViewStack().translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
            RenderSystem.applyModelViewMatrix();
            RenderLevelStageEvent.Stage stage = event.getStage();
            if (stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
                mesh.draw(RenderType.solid());
            } else if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS) {
                mesh.draw(RenderType.cutoutMipped());
            } else if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
                mesh.draw(RenderType.cutout());
            } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                mesh.resortTranslucent(camera);
                mesh.draw(RenderType.translucent());
            } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
                mesh.draw(RenderType.tripwire());
            }
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
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
        if (worldMeshCompileInput == null) {
            worldMeshCompileInput = WorldPreviewCompileInput.capture(minecraft.level, compileController,
                    entries, compileLayer, minecraft);
        }
        WorldPreviewCompileInput input = worldMeshCompileInput;
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
                if (compiled != null) {
                    worldMeshCache.publish(request, compiled);
                }
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

}
