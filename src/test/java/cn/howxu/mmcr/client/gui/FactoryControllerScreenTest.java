package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FactoryControllerScreenTest {
    @Test
    void queue_layout_selects_thread_zero_and_maps_visible_rows() {
        assertThat(FactoryControllerScreen.defaultSelectedThread()).isZero();
        assertThat(FactoryControllerScreen.threadIndexAt(8, 8, 3, 10, 10)).isEqualTo(3);
        assertThat(FactoryControllerScreen.threadIndexAt(8, 8, 0, 100, 10)).isEqualTo(-1);
        assertThat(FactoryControllerScreen.threadIndexAt(8, 8, 0, 10, 39)).isZero();
        assertThat(FactoryControllerScreen.threadIndexAt(8, 8, 0, 10, 40)).isEqualTo(-1);
    }

    @Test
    void visible_row_maps_to_snapshot_thread_index() {
        List<FactoryRecipeScheduler.ThreadSnapshot> threads = List.of(thread(3), thread(42));

        assertThat(FactoryControllerScreen.threadIndexAt(8, 8, 0, 10, 41, threads)).isEqualTo(42);
    }

    @Test
    void visible_thread_count_matches_mmce_page_size() {
        assertThat(FactoryControllerScreen.visibleThreadCount(3)).isEqualTo(3);
        assertThat(FactoryControllerScreen.visibleThreadCount(8)).isEqualTo(6);
    }

    @Test
    void scroll_offset_is_capped_to_available_thread_pages() {
        assertThat(FactoryControllerScreen.clampScrollOffset(0, 8)).isZero();
        assertThat(FactoryControllerScreen.clampScrollOffset(2, 8)).isEqualTo(2);
        assertThat(FactoryControllerScreen.clampScrollOffset(7, 8)).isEqualTo(2);
    }

    @Test
    void scrollbar_handle_uses_mmce_offset_and_scroll_range() {
        assertThat(FactoryControllerScreen.shouldRenderScrollbar(6)).isFalse();
        assertThat(FactoryControllerScreen.shouldRenderScrollbar(7)).isTrue();
        assertThat(FactoryControllerScreen.scrollbarHandleY(0, 8)).isEqualTo(FactoryControllerScreen.SCROLLBAR_Y);
        assertThat(FactoryControllerScreen.scrollbarHandleY(1, 8)).isEqualTo(98);
        assertThat(FactoryControllerScreen.scrollbarHandleY(2, 8)).isEqualTo(189);
    }

    @Test
    void progress_is_zero_for_idle_and_full_when_complete() {
        assertThat(FactoryControllerScreen.progressWidth(0, 0)).isZero();
        assertThat(FactoryControllerScreen.progressWidth(100, 100)).isEqualTo(FactoryControllerScreen.THREAD_ROW_WIDTH);
    }

    @Test
    void thread_elements_use_the_full_atlas_without_extra_vertical_offset() {
        assertThat(FactoryControllerScreen.elementTextureWidth()).isEqualTo(256);
        assertThat(FactoryControllerScreen.elementTextureHeight()).isEqualTo(256);
        assertThat(FactoryControllerScreen.threadElementY(20)).isEqualTo(20);
    }

    @Test
    void selected_overlay_is_clipped_to_the_thread_element_bounds() {
        assertThat(FactoryControllerScreen.selectedOverlayX(20)).isEqualTo(20);
        assertThat(FactoryControllerScreen.selectedOverlayY(20)).isEqualTo(20);
        assertThat(FactoryControllerScreen.selectedOverlayWidth()).isEqualTo(FactoryControllerScreen.THREAD_ROW_WIDTH);
        assertThat(FactoryControllerScreen.selectedOverlayHeight()).isEqualTo(FactoryControllerScreen.THREAD_ROW_HEIGHT);
        assertThat(FactoryControllerScreen.selectedOverlayRight(20)).isEqualTo(105);
        assertThat(FactoryControllerScreen.selectedOverlayBottom(20)).isEqualTo(51);
    }

    @Test
    void progress_overlay_aligns_with_the_thread_element_without_extra_bottom_pixel() {
        assertThat(FactoryControllerScreen.progressOverlayX(20)).isEqualTo(20);
        assertThat(FactoryControllerScreen.progressOverlayY(20)).isEqualTo(20);
        assertThat(FactoryControllerScreen.progressOverlayHeight()).isEqualTo(FactoryControllerScreen.THREAD_ROW_HEIGHT);
        assertThat(FactoryControllerScreen.progressOverlayBottom(20)).isEqualTo(52);
    }

    @Test
    void detail_lines_match_factory_controller_spacing_and_show_progress_last_when_active() {
        assertThat(FactoryControllerScreen.detailTitleY(12)).isEqualTo(12);
        assertThat(FactoryControllerScreen.nextDetailY(12)).isEqualTo(26);
        assertThat(FactoryControllerScreen.shouldRenderProgress(false, 0)).isFalse();
        assertThat(FactoryControllerScreen.shouldRenderProgress(true, 0)).isFalse();
        assertThat(FactoryControllerScreen.shouldRenderProgress(true, 100)).isTrue();
    }

    private static FactoryRecipeScheduler.ThreadSnapshot thread(int index) {
        return new FactoryRecipeScheduler.ThreadSnapshot(index, false, false, false, "", 0, 0, 1);
    }
}
