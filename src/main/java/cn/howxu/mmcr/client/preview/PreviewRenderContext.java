package cn.howxu.mmcr.client.preview;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Host-neutral inputs supplied while extracting a structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
record PreviewRenderContext(GuiGraphicsExtractor graphics, PreviewViewport viewport, float partialTick,
                             int guiOriginX, int guiOriginY, PreviewCamera camera) {
    PreviewViewport absoluteViewport() {
        return new PreviewViewport(guiOriginX + viewport.x(), guiOriginY + viewport.y(), viewport.width(), viewport.height());
    }
}
