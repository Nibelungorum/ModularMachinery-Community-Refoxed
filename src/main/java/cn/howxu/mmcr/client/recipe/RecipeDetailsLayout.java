package cn.howxu.mmcr.client.recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Whole-row detail pages and the shared clipping/input coordinate transform.
 * Heights are supplied after the viewer has measured wrapped text and icons.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeDetailsLayout {
    private RecipeDetailsLayout() {}

    public static List<Page> pages(List<Integer> rowHeights, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        List<Page> pages = new ArrayList<>();
        int first = 0;
        int height = 0;
        for (int row = 0; row < rowHeights.size(); row++) {
            int rowHeight = rowHeights.get(row);
            if (rowHeight < 0) throw new IllegalArgumentException("row height must be non-negative");
            if (row > first && (long) height + rowHeight > capacity) {
                pages.add(new Page(first, row, height));
                first = row;
                height = 0;
            }
            height += rowHeight;
        }
        if (first < rowHeights.size()) pages.add(new Page(first, rowHeights.size(), height));
        return List.copyOf(pages);
    }

    /** Row range with an exclusive end and the full, unclipped content height.
     * @author howxu <dev@howxu.cn>
     */
    public record Page(int firstRow, int endRow, int contentHeight) {
        public int maxScrollY(int viewportHeight) {
            return Math.max(0, contentHeight - viewportHeight);
        }
    }

    /** Screen bounds and scroll offset; use the same transform for drawing and hit testing.
     * @author howxu <dev@howxu.cn>
     */
    public record Viewport(int x, int y, int width, int height, int scrollY) {
        public boolean contains(double screenX, double screenY) {
            return screenX >= x && screenX < (double) x + width
                    && screenY >= y && screenY < (double) y + height;
        }

        public double contentY(double screenY) {
            return screenY - y + scrollY;
        }
    }
}
