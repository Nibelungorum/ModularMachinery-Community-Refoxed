package cn.howxu.mmcr.client.preview;

/**
 * Position roles and their additive, constant-opacity preview colors.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewHighlights {
    public static final int INTERFACE = 1;
    public static final int LEVEL = 2;
    public static final int MODIFIER = 4;

    private PreviewHighlights() {
    }

    public static int color(int mask) {
        if (mask == 0) return 0;
        return 0x44000000
                | ((mask & INTERFACE) != 0 ? 0xFF0000 : 0)
                | ((mask & LEVEL) != 0 ? 0x00FF00 : 0)
                | ((mask & MODIFIER) != 0 ? 0x0000FF : 0);
    }
}
