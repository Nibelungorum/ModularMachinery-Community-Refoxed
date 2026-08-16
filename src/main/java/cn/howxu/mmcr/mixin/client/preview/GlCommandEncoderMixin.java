/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.mixin.client.preview;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Attaches PiP depth textures to the encoder's read framebuffer for a one-pixel PBO copy.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderMixin implements DepthTextureReadbackBridge {
    @Final
    @Shadow
    private int readFbo;

    @Override
    public void mmcr$copyDepthTextureToBuffer(GpuTexture depthTexture, GpuBuffer destination, long offset,
                                              Runnable callback, int x, int y) {
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousPixelPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int textureId = ((GlTexture) depthTexture).glId();
        int bufferHandle = ((GlBufferAccessor) (Object) (GlBuffer) destination).mmcr$getHandle();
        DepthAttachmentRestore.Attachment previousDepthAttachment = null;
        try {
            GlStateManager.clearGlErrors();
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            previousDepthAttachment = mmcr$getDepthAttachment();
            GlStateManager._glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, textureId, 0);
            GL11.glReadBuffer(GL11.GL_NONE);
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, bufferHandle);
            GlStateManager._readPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, offset);
            int error = GlStateManager._getError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("Couldn't read PiP depth texture " + depthTexture.getLabel()
                        + ": GL error " + error);
            }
            RenderSystem.queueFencedTask(callback);
        } finally {
            if (previousDepthAttachment != null) mmcr$restoreDepthAttachment(previousDepthAttachment);
            GL11.glReadBuffer(previousReadBuffer);
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPixelPackBuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousFramebuffer);
        }
    }

    private DepthAttachmentRestore.Attachment mmcr$getDepthAttachment() {
        int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int objectId = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        int level = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
        return switch (type) {
            case GL11.GL_TEXTURE -> DepthAttachmentRestore.Attachment.texture(objectId, level);
            case GL30.GL_RENDERBUFFER -> DepthAttachmentRestore.Attachment.renderbuffer(objectId);
            default -> DepthAttachmentRestore.Attachment.none();
        };
    }

    private void mmcr$restoreDepthAttachment(DepthAttachmentRestore.Attachment attachment) {
        switch (attachment.kind()) {
            case TEXTURE -> GlStateManager._glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, attachment.objectId(), attachment.level());
            case RENDERBUFFER -> GL30.glFramebufferRenderbuffer(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, attachment.objectId());
            case NONE -> GlStateManager._glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, 0, 0);
        }
    }
}
