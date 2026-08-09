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
    }

    @Test
    void visible_row_maps_to_snapshot_thread_index() {
        List<FactoryRecipeScheduler.ThreadSnapshot> threads = List.of(thread(3), thread(42));

        assertThat(FactoryControllerScreen.threadIndexAt(8, 8, 0, 10, 40, threads)).isEqualTo(42);
    }

    @Test
    void progress_is_zero_for_idle_and_full_when_complete() {
        assertThat(FactoryControllerScreen.progressWidth(0, 0)).isZero();
        assertThat(FactoryControllerScreen.progressWidth(100, 100)).isEqualTo(FactoryControllerScreen.THREAD_ROW_WIDTH);
    }

    @Test
    void thread_elements_use_the_full_atlas_and_a_small_vertical_offset() {
        assertThat(FactoryControllerScreen.elementTextureWidth()).isEqualTo(256);
        assertThat(FactoryControllerScreen.elementTextureHeight()).isEqualTo(256);
        assertThat(FactoryControllerScreen.threadElementY(20)).isEqualTo(22);
    }

    private static FactoryRecipeScheduler.ThreadSnapshot thread(int index) {
        return new FactoryRecipeScheduler.ThreadSnapshot(index, false, false, false, "", 0, 0, 1);
    }
}
