package cn.howxu.mmcr.client.preview;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Host-neutral inputs supplied while extracting a structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
record PreviewRenderContext(GuiGraphics graphics, PreviewViewport viewport, float partialTick,
                             int guiOriginX, int guiOriginY, int mouseX, int mouseY, PreviewCamera camera) {
    PreviewViewport absoluteViewport() {
        return new PreviewViewport(guiOriginX + viewport.x(), guiOriginY + viewport.y(), viewport.width(), viewport.height());
    }
}
