package cn.howxu.mmcr.client.preview.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Client GL bridge for the one-pixel PiP depth readback.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface DepthTextureReadbackBridge {
    void mmcr$copyDepthTextureToBuffer(GpuTexture depthTexture, GpuBuffer destination, long offset,
                                       Runnable callback, int x, int y);
}
