package cn.howxu.mmcr.client.gui;

import java.lang.reflect.Field;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests scrollable text screen behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
class ScrollableTextScreenTest {

    @Test
    void visible_line_count_uses_scaled_font_height_and_spacing() {
        assertThat(AbstractScrollableTextScreen.visibleLineCount(100, 0.85F, 10, 9)).isEqualTo(10);
    }

    @Test
    void factory_detail_viewport_includes_its_final_visible_line() {
        assertThat(AbstractScrollableTextScreen.visibleLineCount(92, 0.85F, 10, 9)).isEqualTo(9);
    }

    @Test
    void visible_line_count_always_allows_one_line() {
        assertThat(AbstractScrollableTextScreen.visibleLineCount(1, 0.85F, 10, 9)).isEqualTo(1);
    }

    @Test
    void max_scroll_offset_is_zero_when_content_fits() {
        assertThat(AbstractScrollableTextScreen.maxScrollOffset(5, 6)).isZero();
    }

    @Test
    void content_that_fits_does_not_consume_wheel_scrolling() {
        assertThat(AbstractScrollableTextScreen.hasScrollableOverflow(5, 6)).isFalse();
        assertThat(AbstractScrollableTextScreen.hasScrollableOverflow(7, 6)).isTrue();
    }

    @Test
    void scroll_offset_is_clamped_to_content_range() {
        assertThat(AbstractScrollableTextScreen.clampScrollOffset(-1, 12, 5)).isZero();
        assertThat(AbstractScrollableTextScreen.clampScrollOffset(99, 12, 5)).isEqualTo(7);
    }

    @Test
    void wheel_moves_one_line_and_uses_minecraft_scroll_direction() {
        assertThat(AbstractScrollableTextScreen.scrollOffsetAfter(0, 12, 5, -1)).isEqualTo(1);
        assertThat(AbstractScrollableTextScreen.scrollOffsetAfter(7, 12, 5, 1)).isEqualTo(6);
    }

    @Test
    void viewport_hit_test_excludes_edges_after_the_viewport() {
        AbstractScrollableTextScreen.TextViewport viewport =
                new AbstractScrollableTextScreen.TextViewport(12, 24, 152, 103, 0.85F, 10);

        assertThat(AbstractScrollableTextScreen.containsViewport(viewport, 0, 0, 12, 24)).isTrue();
        assertThat(AbstractScrollableTextScreen.containsViewport(viewport, 0, 0, 164, 126)).isFalse();
    }

    @Test
    void tooltip_hit_test_excludes_blank_space_after_rendered_text() {
        assertThat(AbstractScrollableTextScreen.containsTextLine(
                10, 20, 11, 40, 0.85F, 10, 52, 29)).isTrue();
        assertThat(AbstractScrollableTextScreen.containsTextLine(
                10, 20, 11, 40, 0.85F, 10, 53, 29)).isFalse();
    }

    @Test
    void tooltip_row_maps_to_the_current_visual_line_after_scrolling() throws Exception {
        TestScreen screen = TestScreen.create();
        screen.setLines(List.of(line("one"), line("two"), line("three")));
        screen.scroll(-1);

        assertThat(AbstractScrollableTextScreen.textLineIndexAt(
                screen.scrollableTextViewport(), 0, screen.firstLine(), 0)).isEqualTo(1);
    }

    @Test
    void shrinking_external_visual_lines_clamps_the_existing_scroll_offset() throws Exception {
        TestScreen screen = TestScreen.create();
        screen.setLines(List.of(line("one"), line("two"), line("three")));

        assertThat(screen.scroll(-1)).isTrue();
        assertThat(screen.scroll(-1)).isTrue();
        assertThat(screen.firstLine()).isEqualTo(2);

        screen.setLines(List.of(line("one")));

        assertThat(screen.firstLine()).isZero();
    }

    @Test
    void render_frame_reuses_layout_for_counts_visible_rows_and_tooltip_then_refreshes_next_frame() throws Exception {
        TestScreen screen = TestScreen.create();
        screen.setLines(List.of(line("one"), line("two"), line("three")));
        screen.render(null, 0, 0, 0);
        assertThat(screen.lineReads).isEqualTo(1);
        assertThat(screen.frameLineCount).isEqualTo(3);
        screen.setLines(List.of(line("next")));
        screen.render(null, 0, 0, 0);
        assertThat(screen.lineReads).isEqualTo(2);
        assertThat(screen.frameLineCount).isEqualTo(1);
        screen.firstLine();
        assertThat(screen.lineReads).isGreaterThan(2);
    }

    @Test
    void failed_render_clears_frame_layout_before_outside_input_queries() throws Exception {
        TestScreen screen = TestScreen.create();
        screen.setLines(List.of(line("before"), line("second")));
        screen.failRender = true;
        assertThatThrownBy(() -> screen.render(null, 0, 0, 0)).isInstanceOf(IllegalStateException.class);
        screen.setLines(List.of(line("after")));
        assertThat(screen.scrollableTextLineCount()).isEqualTo(1);
        assertThat(screen.lineReads).isEqualTo(2);
    }

    private static ControllerTextLine line(String text) {
        return new ControllerTextLine(Component.literal(text), 0xFFFFFFFF);
    }

    private static final class TestScreen extends AbstractScrollableTextScreen<AbstractContainerMenu> {
        private List<ControllerTextLine> lines;
        private int lineReads;
        private int frameLineCount;
        private boolean failRender;

        private TestScreen() {
            super(null, null, Component.empty(), 176, 213);
        }

        private static TestScreen create() throws Exception {
            TestScreen screen = (TestScreen) unsafe().allocateInstance(TestScreen.class);
            Field font = Screen.class.getDeclaredField("font");
            unsafe().putObject(screen, unsafe() .objectFieldOffset(font),
                    ControllerScreenTextComposerTest.testFont());
            screen.lines = new ArrayList<>();
            return screen;
        }

        private void setLines(List<ControllerTextLine> replacement) {
            lines.clear();
            lines.addAll(replacement);
        }

        private boolean scroll(double deltaY) {
            return mouseScrolled(0, 0, 0, deltaY);
        }

        private int firstLine() {
            return firstVisibleTextLine();
        }

        @Override
        protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        }

        @Override
        protected TextViewport scrollableTextViewport() {
            return new TextViewport(0, 0, 100, 1, 1.0F, 1);
        }

        @Override
        protected List<ControllerTextLine> scrollableTextLines() {
            lineReads++;
            return lines;
        }

        @Override
        protected void renderFrame(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            frameLineCount = scrollableTextLineCount();
            firstVisibleTextLine();
            lastVisibleTextLineExclusive();
            visibleTextRow(0);
            wrappedTextLines();
            renderScrollableTooltip(graphics, mouseX, mouseY, 0);
            if (failRender) throw new IllegalStateException("render");
        }


        private static sun.misc.Unsafe unsafe() throws Exception {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (sun.misc.Unsafe) field.get(null);
        }
    }
}
