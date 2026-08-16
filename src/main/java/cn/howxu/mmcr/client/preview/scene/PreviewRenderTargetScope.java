/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

/**
 * Restores the output texture overrides that preceded a preview render pass.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewRenderTargetScope implements AutoCloseable {
    private final OutputOverrides overrides;
    @Nullable
    private final Object color;
    @Nullable
    private final Object depth;

    private PreviewRenderTargetScope(OutputOverrides overrides, @Nullable Object color, @Nullable Object depth) {
        this.overrides = overrides;
        this.color = color;
        this.depth = depth;
    }

    public static PreviewRenderTargetScope redirect(@Nullable GpuTextureView color, @Nullable GpuTextureView depth) {
        RenderSystem.assertOnRenderThread();
        return redirect(RenderSystemOverrides.INSTANCE, color, depth);
    }

    static PreviewRenderTargetScope redirect(OutputOverrides overrides, @Nullable Object color, @Nullable Object depth) {
        PreviewRenderTargetScope scope = new PreviewRenderTargetScope(overrides, overrides.color(), overrides.depth());
        overrides.set(color, depth);
        return scope;
    }

    @Override
    public void close() {
        overrides.set(color, depth);
    }

    interface OutputOverrides {
        @Nullable Object color();

        @Nullable Object depth();

        void set(@Nullable Object color, @Nullable Object depth);
    }

    static final class TestOverrides implements OutputOverrides {
        @Nullable
        private Object color;
        @Nullable
        private Object depth;

        TestOverrides(@Nullable Object color, @Nullable Object depth) {
            this.color = color;
            this.depth = depth;
        }

        @Override
        public @Nullable Object color() {
            return color;
        }

        @Override
        public @Nullable Object depth() {
            return depth;
        }

        @Override
        public void set(@Nullable Object color, @Nullable Object depth) {
            this.color = color;
            this.depth = depth;
        }
    }

    private enum RenderSystemOverrides implements OutputOverrides {
        INSTANCE;

        @Override
        public @Nullable GpuTextureView color() {
            return RenderSystem.outputColorTextureOverride;
        }

        @Override
        public @Nullable GpuTextureView depth() {
            return RenderSystem.outputDepthTextureOverride;
        }

        @Override
        public void set(@Nullable Object color, @Nullable Object depth) {
            RenderSystem.outputColorTextureOverride = (GpuTextureView) color;
            RenderSystem.outputDepthTextureOverride = (GpuTextureView) depth;
        }
    }
}
