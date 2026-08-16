/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.mixin.client.preview;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the owned PiP depth attachment only for asynchronous pixel copies.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(PictureInPictureRenderer.class)
public interface PictureInPictureRendererAccessor {
    @Accessor("depthTexture")
    GpuTexture mmcr$getDepthTexture();
}
