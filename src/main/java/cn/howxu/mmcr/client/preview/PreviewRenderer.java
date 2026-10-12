package cn.howxu.mmcr.client.preview;

/**
 * Rendering boundary used by the host-neutral structure-preview widget.
 *
 * @author howxu <dev@howxu.cn>
 */
interface PreviewRenderer {
    StructurePreviewSchema schema();

    void setVisibility(PreviewVisibility visibility);

    void resetCamera();

    void render(PreviewRenderContext context);

    default void prepareSynchronously(PreviewCamera camera) {
        throw new UnsupportedOperationException("Synchronous preparation is not supported by this preview renderer");
    }

    default void setInteractive(boolean interactive) { }

    Object hitResult();

    void selectHit(Object hitResult);

    void close();
}
