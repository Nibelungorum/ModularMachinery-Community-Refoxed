package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;

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
    void selector_geometry_matches_the_approved_texture() throws IOException {
        var background = ImageIO.read(getClass().getResource("/assets/mmcr/textures/gui/gui_recipe_pool.png"));
        assertThat(RecipePoolScreen.IMAGE_WIDTH).isEqualTo(background.getWidth());
        assertThat(RecipePoolScreen.IMAGE_HEIGHT).isEqualTo(background.getHeight());
        assertThat(RecipePoolScreen.VISIBLE_ROWS).isEqualTo(4);
        assertThat(RecipePoolScreen.RETURN_X).isEqualTo(92);
        assertThat(RecipePoolScreen.RETURN_Y).isEqualTo(123);

        int x = RecipePoolScreen.SCROLLBAR_X;
        int bottom = RecipePoolScreen.scrollbarHandleY(4, 8) + RecipePoolScreen.SCROLLBAR_HANDLE_HEIGHT;
        assertThat(bottom).isEqualTo(RecipePoolScreen.RETURN_Y);
        int trackColor = background.getRGB(x, RecipePoolScreen.scrollbarHandleY(0, 8));
        assertThat(background.getRGB(x, bottom - 1)).isEqualTo(trackColor);
        assertThat(background.getRGB(x, bottom)).isNotEqualTo(trackColor);
    }

    @Test
    void row_hit_testing_and_scroll_offsets_are_bounded() {
        int top = 20;
        assertThat(RecipePoolScreen.rowIndexAt(10, top, 2, 15, top + 3, 8)).isEqualTo(-1);
        assertThat(RecipePoolScreen.rowIndexAt(10, top, 2, 15, top + 4, 8)).isEqualTo(2);
        assertThat(RecipePoolScreen.rowIndexAt(10, top, 2, 100, top + 4, 8)).isEqualTo(-1);
        assertThat(RecipePoolScreen.rowIndexAt(10, top, 2, 15, top + 36, 8)).isEqualTo(-1);
        assertThat(RecipePoolScreen.rowIndexAt(10, top, 2, 15, top + 134, 8)).isEqualTo(5);
        assertThat(RecipePoolScreen.rowIndexAt(10, top, 2, 15, top + 135, 8)).isEqualTo(-1);
        assertThat(RecipePoolScreen.clampScrollOffset(-1, 8)).isZero();
        assertThat(RecipePoolScreen.clampScrollOffset(99, 8)).isEqualTo(4);
        assertThat(RecipePoolScreen.scrollbarHandleY(0, 8)).isEqualTo(4);
        for (int offset = 0; offset <= 4; offset++) {
            int mouseY = RecipePoolScreen.scrollbarHandleY(offset, 8) + 15;
            assertThat(RecipePoolScreen.scrollOffsetFromScrollbarY(mouseY, 8, 15)).isEqualTo(offset);
        }
    }

    @Test
    void scroll_wheel_covers_the_last_row_and_track_but_not_the_return_button() {
        int listX = RecipePoolScreen.ROW_X;
        int trackX = RecipePoolScreen.SCROLLBAR_X;
        assertThat(RecipePoolScreen.mouseOverListOrScrollbar(listX, 134, 8)).isTrue();
        assertThat(RecipePoolScreen.mouseOverListOrScrollbar(listX, 135, 8)).isFalse();
        assertThat(RecipePoolScreen.mouseOverListOrScrollbar(trackX, 3, 8)).isFalse();
        assertThat(RecipePoolScreen.mouseOverListOrScrollbar(trackX, 4, 8)).isTrue();
        assertThat(RecipePoolScreen.mouseOverListOrScrollbar(trackX, 121, 8)).isTrue();
        assertThat(RecipePoolScreen.mouseOverListOrScrollbar(trackX, 122, 8)).isTrue();
        assertThat(RecipePoolScreen.mouseOverListOrScrollbar(trackX, RecipePoolScreen.RETURN_Y, 8)).isFalse();
    }

    @Test
    void unknown_pool_display_name_falls_back_to_the_complete_id() {
        var id = MMCR.id("missing/display_name");
        assertThat(RecipePoolDisplayName.component(id).getString()).isEqualTo(id.toString());
    }
}
