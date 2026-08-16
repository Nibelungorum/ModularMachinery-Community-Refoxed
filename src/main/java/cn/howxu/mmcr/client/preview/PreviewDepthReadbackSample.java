package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.client.preview.scene.PreviewSceneCamera;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Immutable request data retained until the PiP depth PBO can be mapped by its owner.
 *
 * @author howxu <dev@howxu.cn>
 */
record PreviewDepthReadbackSample(GpuTexture depthTexture, int textureWidth, int textureHeight,
                                  GpuBuffer buffer, long generation, PreviewFrameViewport frame,
                                  PreviewFrameViewport.Pixel texel, int mouseX, int mouseY, PreviewSceneCamera camera) {
    static PreviewDepthReadbackSample of(GpuTexture depthTexture, int textureWidth, int textureHeight,
                                         GpuBuffer buffer, long generation, PreviewFrameViewport frame,
                                         int mouseX, int mouseY, PreviewSceneCamera camera) {
        return new PreviewDepthReadbackSample(depthTexture, textureWidth, textureHeight, buffer, generation, frame,
                frame.depthTexturePixel(mouseX, mouseY, textureWidth, textureHeight), mouseX, mouseY, camera);
    }

    float ndcX() {
        return 2.0F * (texel.x() + 0.5F) / textureWidth - 1.0F;
    }

    float ndcY() {
        return 1.0F - 2.0F * (texel.y() + 0.5F) / textureHeight;
    }
}
