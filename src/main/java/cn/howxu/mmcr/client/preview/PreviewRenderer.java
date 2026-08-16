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

    void close();
}
