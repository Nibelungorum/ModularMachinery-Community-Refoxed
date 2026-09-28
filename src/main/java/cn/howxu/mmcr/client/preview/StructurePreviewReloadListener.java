package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.client.MultiblockPreviewClientHandler;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Requests preview cache replacement after client resources reload.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewReloadListener implements PreparableReloadListener {
    private static final List<WeakReference<StructurePreviewRenderer>> RENDERERS = new ArrayList<>();

    static synchronized void register(StructurePreviewRenderer renderer) {
        RENDERERS.add(new WeakReference<>(renderer));
    }

    static synchronized void unregister(StructurePreviewRenderer renderer) {
        RENDERERS.removeIf(reference -> reference.get() == null || reference.get() == renderer);
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager,
                                          ProfilerFiller preparationProfiler, ProfilerFiller reloadProfiler,
                                          Executor preparationExecutor, Executor reloadExecutor) {
        return CompletableFuture.completedFuture(null).thenCompose(preparationBarrier::wait).thenRunAsync(() -> {
            StructurePreviewCompilationCache.instance().clear();
            MultiblockPreviewClientHandler.invalidateWorldPreviewForReload();
            synchronized (StructurePreviewReloadListener.class) {
                RENDERERS.removeIf(reference -> {
                    StructurePreviewRenderer renderer = reference.get();
                    if (renderer == null) return true;
                    renderer.markDirty();
                    return false;
                });
            }
        }, reloadExecutor);
    }
}
