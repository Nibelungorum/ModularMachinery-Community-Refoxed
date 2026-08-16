package cn.howxu.mmcr.client.preview;

/**
 * Maps logical preview coordinates to the PiP target created for one frame.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PreviewFrameViewport(int framebufferX, int framebufferY, int framebufferWidth, int framebufferHeight,
                                   int logicalX, int logicalY, int logicalWidth, int logicalHeight,
                                   int depthTextureWidth, int depthTextureHeight) {
    public boolean containsLogical(int mouseX, int mouseY) {
        return mouseX >= logicalX && mouseX < logicalX + logicalWidth
                && mouseY >= logicalY && mouseY < logicalY + logicalHeight;
    }

    public int depthX(int mouseX) {
        return (mouseX - logicalX) * depthTextureWidth / logicalWidth;
    }

    public int depthY(int mouseY) {
        return (mouseY - logicalY) * depthTextureHeight / logicalHeight;
    }

    public PreviewFrameViewport withDepthTextureSize(int width, int height) {
        return new PreviewFrameViewport(framebufferX, framebufferY, framebufferWidth, framebufferHeight,
                logicalX, logicalY, logicalWidth, logicalHeight, width, height);
    }
}
