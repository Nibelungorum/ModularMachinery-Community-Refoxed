/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.mixin;

import com.mojang.blaze3d.opengl.GlBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the GL PBO handle required by the depth readback bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(GlBuffer.class)
public interface GlBufferAccessor {
    @Accessor("handle")
    int mmcr$getHandle();
}
