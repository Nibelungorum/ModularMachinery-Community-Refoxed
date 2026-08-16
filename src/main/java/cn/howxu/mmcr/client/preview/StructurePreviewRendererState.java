package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.client.preview.scene.SceneCompileKind;

/**
 * Tracks adapter requests without discarding a published scene cache.
 *
 * @author howxu <dev@howxu.cn>
 */
final class StructurePreviewRendererState {
    private long generation;
    private boolean closed;
    private boolean fullCachePublished;
    private SceneCompileKind pendingKind;
    private long depthReadbackGeneration;
    private int depthMouseX = Integer.MIN_VALUE;
    private int depthMouseY = Integer.MIN_VALUE;
    private long depthReadbackAt = Long.MIN_VALUE;
    private int textureWidth;
    private int textureHeight;
    private int viewportX;
    private int viewportY;
    private int viewportWidth;
    private int viewportHeight;
    private boolean releaseRequested;

    void setVisibility(PreviewVisibility visibility) {
        requestFullRebuild();
    }

    void markDirty() {
        requestFullRebuild();
    }

    void markFullCachePublished() {
        fullCachePublished = true;
        pendingKind = null;
    }

    void onCameraPanOrZoom() {
    }

    void onCameraRotation(long rotationVersion) {
        if (fullCachePublished && !closed && pendingKind != SceneCompileKind.FULL) {
            generation++;
            pendingKind = SceneCompileKind.TRANSLUCENT_ONLY;
        }
    }

    void close() {
        if (!closed) {
            generation++;
            depthReadbackGeneration++;
        }
        closed = true;
        pendingKind = null;
    }

    long generation() {
        return generation;
    }

    SceneCompileKind pendingKind() {
        return pendingKind;
    }

    boolean accepts(long resultGeneration, SceneCompileKind kind) {
        return !closed && generation == resultGeneration
                && (kind != SceneCompileKind.TRANSLUCENT_ONLY || fullCachePublished);
    }

    long beginDepthReadback() {
        return ++depthReadbackGeneration;
    }

    boolean acceptsDepthReadback(long token) {
        return !closed && token == depthReadbackGeneration;
    }

    boolean shouldReadDepth(int mouseX, int mouseY, long nowMillis) {
        return mouseX != depthMouseX || mouseY != depthMouseY || nowMillis - depthReadbackAt >= 50L;
    }

    void markDepthReadbackRequested(int mouseX, int mouseY, long nowMillis) {
        depthMouseX = mouseX;
        depthMouseY = mouseY;
        depthReadbackAt = nowMillis;
    }

    void setFrame(int textureWidth, int textureHeight, int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.viewportX = viewportX;
        this.viewportY = viewportY;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    boolean containsMouse(int mouseX, int mouseY) {
        return mouseX >= viewportX && mouseX < viewportX + viewportWidth
                && mouseY >= viewportY && mouseY < viewportY + viewportHeight;
    }

    int textureMouseX(int mouseX) {
        return (mouseX - viewportX) * textureWidth / viewportWidth;
    }

    int textureMouseY(int mouseY) {
        return (mouseY - viewportY) * textureHeight / viewportHeight;
    }

    int textureWidth() {
        return textureWidth;
    }

    int textureHeight() {
        return textureHeight;
    }

    int outlineCount(boolean hasHover, boolean hasSelection, boolean matching) {
        if (!hasHover) return hasSelection ? 1 : 0;
        return hasSelection && !matching ? 2 : 1;
    }

    boolean requestCloseRelease() {
        if (releaseRequested) return false;
        releaseRequested = true;
        close();
        return true;
    }

    private void requestFullRebuild() {
        if (!closed) {
            generation++;
            pendingKind = SceneCompileKind.FULL;
        }
    }
}
