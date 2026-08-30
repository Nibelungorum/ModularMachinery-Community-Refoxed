/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.client.preview.PreviewCamera;
import cn.howxu.mmcr.client.preview.StructurePreviewRenderer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/**
 * GUI extraction state for a cached structure-preview scene.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PreviewSceneRenderState(PreviewSceneRenderer scene, PreviewCamera camera,
                                       int x0, int y0, int x1, int y1, float partialTick,
                                       ScreenRectangle scissorArea,
                                       StructurePreviewRenderer owner) implements PictureInPictureRenderState {
    @Override
    public float scale() {
        return 1.0F;
    }

    @Override
    public ScreenRectangle bounds() {
        return PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
    }
}
