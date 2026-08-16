/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.mixin;

import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Adds COPY_SRC to PiP depth attachments so preview picking can use public encoder readback.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureDepthCopyMixin {
    @ModifyConstant(method = "prepareTexturesAndProjection", constant = @Constant(intValue = 9))
    private int mmcr$enableDepthCopySource(int usage) {
        return usage | 2;
    }
}
