/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Per-frame state required to submit preview scene features.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PreviewSceneRenderContext(PoseStack poseStack, SubmitNodeStorage submitStorage,
                                        MultiBufferSource.BufferSource bufferSource, CameraRenderState cameraState,
                                        float partialTick) {
    public static final int LAYER_DEFAULT = 0;
    public static final int LAYER_OVERLAY = 1_000;
    public static final int LAYER_GIZMO = 10_000;
    public static final int LAYER_LAST = Integer.MAX_VALUE;
}
