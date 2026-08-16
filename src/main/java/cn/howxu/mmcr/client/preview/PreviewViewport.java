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
}
