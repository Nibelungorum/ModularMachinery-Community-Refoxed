package cn.howxu.mmcr.client.recipe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Whole-row reachability and consistent scroll hit testing.
 * @author howxu <dev@howxu.cn>
 */
class RecipeDetailsLayoutTest {
    @Test
    void preservesWholeRowsAndKeepsAnOversizedRowReachable() {
        assertThat(RecipeDetailsLayout.pages(List.of(10, 18, 40, 10), 28)).containsExactly(
                new RecipeDetailsLayout.Page(0, 2, 28),
                new RecipeDetailsLayout.Page(2, 3, 40),
                new RecipeDetailsLayout.Page(3, 4, 10));
    }

    @Test
    void clippingAndInputUseTheSameTransform() {
        var viewport = new RecipeDetailsLayout.Viewport(12, 30, 120, 40, 18);
        assertThat(viewport.contains(20, 69)).isTrue();
        assertThat(viewport.contains(20, 70)).isFalse();
        assertThat(viewport.contains(11, 35)).isFalse();
        assertThat(viewport.contains(132, 35)).isFalse();
        assertThat(viewport.contains(12, 30)).isTrue();
        assertThat(viewport.contains(20, 29)).isFalse();
        assertThat(viewport.contentY(35)).isEqualTo(23);
    }

    @Test
    void emptyDetailsDoNotCreateAnEmptyPage() {
        assertThat(RecipeDetailsLayout.pages(List.of(), 28)).isEmpty();
    }

    @Test
    void exactCapacityDoesNotCreateATrailingPage() {
        assertThat(RecipeDetailsLayout.pages(List.of(10, 18), 28))
                .containsExactly(new RecipeDetailsLayout.Page(0, 2, 28));
    }

    @Test
    void leadingAndConsecutiveOversizedRowsOwnNonemptyPages() {
        assertThat(RecipeDetailsLayout.pages(List.of(40, 50, 10), 28)).containsExactly(
                new RecipeDetailsLayout.Page(0, 1, 40),
                new RecipeDetailsLayout.Page(1, 2, 50),
                new RecipeDetailsLayout.Page(2, 3, 10));
    }

    @Test
    void everyRowIsReachedExactlyOnceAcrossMultiplePages() {
        var heights = List.of(18, 18, 10, 10, 18, 18, 10);
        var pages = RecipeDetailsLayout.pages(heights, 28);
        int nextRow = 0;
        for (var page : pages) {
            assertThat(page.firstRow()).isEqualTo(nextRow);
            assertThat(page.endRow()).isGreaterThan(page.firstRow());
            assertThat(page.contentHeight()).isEqualTo(heights.subList(page.firstRow(), page.endRow())
                    .stream().mapToInt(Integer::intValue).sum());
            nextRow = page.endRow();
        }
        assertThat(nextRow).isEqualTo(heights.size());
    }

    @Test
    void scrollingCanReachTheBottomOfAnOversizedRow() {
        var page = RecipeDetailsLayout.pages(List.of(60), 28).getFirst();
        var viewport = new RecipeDetailsLayout.Viewport(12, 30, 120, 28, page.maxScrollY(28));
        assertThat(viewport.contentY(57)).isEqualTo(59);
        assertThat(page.maxScrollY(80)).isZero();
    }

    @Test
    void rejectsNonpositiveCapacityAndNegativeRowHeights() {
        assertThatIllegalArgumentException().isThrownBy(() -> RecipeDetailsLayout.pages(List.of(), 0));
        assertThatIllegalArgumentException().isThrownBy(() -> RecipeDetailsLayout.pages(List.of(10), -1));
        assertThatIllegalArgumentException().isThrownBy(() -> RecipeDetailsLayout.pages(List.of(-1), 28));
    }
}
