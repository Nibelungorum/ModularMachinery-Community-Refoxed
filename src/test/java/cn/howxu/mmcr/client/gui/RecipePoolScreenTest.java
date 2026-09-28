package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recipe-pool selector geometry tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class RecipePoolScreenTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void selector_geometry_matches_the_approved_texture() {
        assertThat(RecipePoolScreen.IMAGE_WIDTH).isEqualTo(108);
        assertThat(RecipePoolScreen.IMAGE_HEIGHT).isEqualTo(141);
        assertThat(RecipePoolScreen.VISIBLE_ROWS).isEqualTo(4);
        assertThat(RecipePoolScreen.RETURN_X).isEqualTo(92);
        assertThat(RecipePoolScreen.RETURN_Y).isEqualTo(125);
    }

    @Test
    void row_hit_testing_and_scroll_offsets_are_bounded() {
        assertThat(RecipePoolScreen.rowIndexAt(10, 20, 2, 15, 27, 8)).isEqualTo(2);
        assertThat(RecipePoolScreen.rowIndexAt(10, 20, 2, 100, 27, 8)).isEqualTo(-1);
        assertThat(RecipePoolScreen.clampScrollOffset(-1, 8)).isZero();
        assertThat(RecipePoolScreen.clampScrollOffset(99, 8)).isEqualTo(4);
        assertThat(RecipePoolScreen.scrollbarHandleY(0, 8)).isEqualTo(RecipePoolScreen.SCROLLBAR_Y);
        assertThat(RecipePoolScreen.scrollOffsetFromScrollbarY(RecipePoolScreen.SCROLLBAR_BOTTOM, 8, 0))
                .isEqualTo(4);
    }

    @Test
    void unknown_pool_display_name_falls_back_to_the_complete_id() {
        var id = MMCR.id("missing/display_name");
        assertThat(RecipePoolDisplayName.component(id).getString()).isEqualTo(id.toString());
    }
}
