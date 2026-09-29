/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-ResourceLocation: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Per-frame state required to draw preview scene features.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PreviewSceneRenderContext(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                        float partialTick, int framebufferId) { }
