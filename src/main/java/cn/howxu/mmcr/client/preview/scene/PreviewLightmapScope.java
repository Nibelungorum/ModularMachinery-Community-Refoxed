package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

/**
 * Render-thread scoped override for the lightmap used by block render pipelines.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewLightmapScope {
    private static final ThreadLocal<GpuTextureView> CURRENT = new ThreadLocal<>();

    private PreviewLightmapScope() { }

    static void with(@Nullable GpuTextureView view, Runnable render) {
        GpuTextureView previous = CURRENT.get();
        if (view == null) CURRENT.remove();
        else CURRENT.set(view);
        try {
            render.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static @Nullable GpuTextureView current() {
        return CURRENT.get();
    }
}
