package cn.howxu.mmcr.client.preview;

import java.util.Objects;

/**
 * Immutable coordinate conversion for one PiP frame.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PreviewFrameViewport(PreviewViewport absoluteGuiBounds,
                                   PreviewViewport.FramebufferViewport framebufferViewport,
                                   int pipAllocationWidth, int pipAllocationHeight, int guiScale) {
    public PreviewFrameViewport {
        Objects.requireNonNull(absoluteGuiBounds, "absoluteGuiBounds");
        Objects.requireNonNull(framebufferViewport, "framebufferViewport");
        if (absoluteGuiBounds.width() <= 0 || absoluteGuiBounds.height() <= 0
                || pipAllocationWidth <= 0 || pipAllocationHeight <= 0 || guiScale <= 0) {
            throw new IllegalArgumentException("preview dimensions and guiScale must be positive");
        }
        if (pipAllocationWidth != absoluteGuiBounds.width() * guiScale
                || pipAllocationHeight != absoluteGuiBounds.height() * guiScale) {
            throw new IllegalArgumentException("PiP allocation must be logical bounds scaled by guiScale");
        }
    }

    public boolean containsAbsoluteGui(int mouseX, int mouseY) {
        return absoluteGuiBounds.contains(mouseX, mouseY);
    }

    public Pixel pipLocalLogical(int absoluteMouseX, int absoluteMouseY) {
        return new Pixel(absoluteMouseX - absoluteGuiBounds.x(), absoluteMouseY - absoluteGuiBounds.y());
    }

    public Pixel framebufferPixel(int absoluteMouseX, int absoluteMouseY) {
        Pixel local = pipLocalLogical(absoluteMouseX, absoluteMouseY);
        return new Pixel(framebufferViewport.x() + local.x() * framebufferViewport.width() / absoluteGuiBounds.width(),
                framebufferViewport.y() + (absoluteGuiBounds.height() - 1 - local.y()) * framebufferViewport.height() / absoluteGuiBounds.height());
    }

    public Pixel depthTexturePixel(int absoluteMouseX, int absoluteMouseY, int depthTextureWidth, int depthTextureHeight) {
        Pixel local = pipLocalLogical(absoluteMouseX, absoluteMouseY);
        return new Pixel(local.x() * depthTextureWidth / absoluteGuiBounds.width(),
                (absoluteGuiBounds.height() - 1 - local.y()) * depthTextureHeight / absoluteGuiBounds.height());
    }

    public Pixel depthTexturePixel(int absoluteMouseX, int absoluteMouseY) {
        return depthTexturePixel(absoluteMouseX, absoluteMouseY, pipAllocationWidth, pipAllocationHeight);
    }

    public record Pixel(int x, int y) {
    }
}
