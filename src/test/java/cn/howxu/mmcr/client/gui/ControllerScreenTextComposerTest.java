package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests shared controller screen text composition and wrapping.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerScreenTextComposerTest {

    @Test
    void merge_keeps_standard_lines_before_external_lines_and_uses_default_color() {
        ControllerTextLine standard = new ControllerTextLine(Component.literal("standard"), 0xFF123456);
        ControllerScreenTextSnapshot.Line external = new ControllerScreenTextSnapshot.Line(
                ControllerScreenTextScope.CONTROLLER, ResourceLocation.parse("test:external"), Component.literal("external"));

        List<ControllerTextLine> lines = ControllerScreenTextComposer.merge(List.of(standard), List.of(external));

        assertThat(lines).containsExactly(
                standard,
                new ControllerTextLine(Component.literal("external"), ControllerScreenTextComposer.DEFAULT_EXTERNAL_COLOR));
    }

    @Test
    void wrap_splits_long_components_into_immutable_visual_lines() throws Exception {
        ControllerTextLine line = new ControllerTextLine(Component.literal("abcdefghij"), 0xFF123456);

        List<ControllerScreenTextComposer.VisualLine> wrapped = ControllerScreenTextComposer.wrap(testFont(), List.of(line), 5);

        assertThat(wrapped).hasSize(2).allSatisfy(visualLine -> {
            assertThat(visualLine.color()).isEqualTo(line.color());
            assertThat(visualLine.text()).isInstanceOf(FormattedCharSequence.class);
        });
        assertThat(wrapped).isUnmodifiable();
    }

    @Test
    void wrap_keeps_icon_metadata_only_on_the_first_wrapped_segment() throws Exception {
        ControllerTextLine line = new ControllerTextLine(Component.literal("abcdefghij"), 0xFF123456,
                new ControllerTextLine.ItemIcon(ItemStack.EMPTY), List.of(Component.literal("tip")));

        List<ControllerScreenTextComposer.VisualLine> wrapped = ControllerScreenTextComposer.wrap(
                testFont(), List.of(line), 17);

        assertThat(wrapped).hasSize(2);
        assertThat(wrapped.get(0).source()).isSameAs(line);
        assertThat(wrapped.get(0).firstSegment()).isTrue();
        assertThat(wrapped.get(0).textXOffset()).isEqualTo(line.textXOffset());
        assertThat(wrapped.get(1).source()).isSameAs(line);
        assertThat(wrapped.get(1).firstSegment()).isFalse();
        assertThat(wrapped.get(1).textXOffset()).isZero();
    }

    @Test
    void wrap_preserves_output_indent_after_the_icon_segment() throws Exception {
        ControllerTextLine line = new ControllerTextLine(Component.literal("abcdefghij"), 0xFF123456,
                new ControllerTextLine.ItemIcon(ItemStack.EMPTY), List.of(), 4);

        List<ControllerScreenTextComposer.VisualLine> wrapped = ControllerScreenTextComposer.wrap(
                testFont(), List.of(line), 23);

        assertThat(wrapped).hasSize(2);
        assertThat(wrapped.get(0).textXOffset()).isEqualTo(15);
        assertThat(wrapped.get(1).textXOffset()).isEqualTo(4);
    }

    static Font testFont() throws Exception {
        Unsafe unsafe = unsafe();
        Font font = (Font) unsafe.allocateInstance(Font.class);
        Field splitter = Font.class.getDeclaredField("splitter");
        unsafe.putObject(font, unsafe.objectFieldOffset(splitter),
                new StringSplitter((codePoint, style) -> 1.0F));
        return font;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
