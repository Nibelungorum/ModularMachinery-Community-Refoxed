/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.mixin.client.preview;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes vanilla depth-texture buffer copies attach their source to the depth attachment.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderMixin {
    @Final
    @Shadow
    private int readFbo;

    @Inject(
            method = "copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;IIIII)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mmcr$supportDepthCopy(GpuTexture source, GpuBuffer destination, long offset,
                                       Runnable callback, int mipLevel, int x, int y, int width, int height,
                                       CallbackInfo ci) {
        if (!source.getFormat().hasDepthAspect()) return;

        int sourceGlId = ((GlTexture) source).glId();
        int destinationHandle = ((GlBufferAccessor) (Object) destination).mmcr$getHandle();
        GlStateManager.clearGlErrors();
        GlStateManager._glBindFramebuffer(36008, this.readFbo);
        GlStateManager._glFramebufferTexture2D(36008, 36096, 3553, sourceGlId, mipLevel);
        GlStateManager._glFramebufferTexture2D(36008, 36064, 3553, 0, mipLevel);
        GlStateManager._glBindBuffer(35051, destinationHandle);
        GlStateManager._pixelStore(3330, width);
        GlStateManager._readPixels(x, y, width, height,
                GlConst.toGlExternalId(source.getFormat()), GlConst.toGlType(source.getFormat()), offset);
        RenderSystem.queueFencedTask(callback);
        GlStateManager._glFramebufferTexture2D(36008, 36096, 3553, 0, mipLevel);
        GlStateManager._glBindFramebuffer(36008, 0);
        GlStateManager._glBindBuffer(35051, 0);
        int error = GlStateManager._getError();
        if (error != 0) {
            throw new IllegalStateException("Couldn't perform depth copyToBuffer for texture "
                    + source.getLabel() + ": GL error " + error);
        }

        ci.cancel();
    }
}
