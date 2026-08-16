package cn.howxu.mmcr.client.preview;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Host-neutral inputs supplied while extracting a structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
record PreviewRenderContext(GuiGraphicsExtractor graphics, PreviewViewport viewport, float partialTick,
                            int guiOriginX, int guiOriginY, int guiWidth, int guiHeight,
                            int framebufferWidth, int framebufferHeight, PreviewCamera camera) {
    PreviewViewport.FramebufferViewport framebufferViewport() {
        return viewport.framebufferViewport(guiWidth, guiHeight, framebufferWidth, framebufferHeight);
    }
}
