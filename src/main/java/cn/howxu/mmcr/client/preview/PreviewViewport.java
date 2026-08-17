package cn.howxu.mmcr.client.preview;

/**
 * Screen rectangle occupied by a structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PreviewViewport(int x, int y, int width, int height) {
    public boolean contains(double pointerX, double pointerY) {
        return pointerX >= x && pointerX < x + width && pointerY >= y && pointerY < y + height;
    }

    public FramebufferViewport framebufferViewport(int guiWidth, int guiHeight, int framebufferWidth, int framebufferHeight) {
        int framebufferX = x * framebufferWidth / guiWidth;
        int framebufferY = (guiHeight - y - height) * framebufferHeight / guiHeight;
        int framebufferViewportWidth = width * framebufferWidth / guiWidth;
        int framebufferViewportHeight = height * framebufferHeight / guiHeight;
        return new FramebufferViewport(framebufferX, framebufferY, framebufferViewportWidth, framebufferViewportHeight);
    }

    /**
     * Viewport coordinates in the bottom-left framebuffer coordinate system.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record FramebufferViewport(int x, int y, int width, int height) {
    }
}
