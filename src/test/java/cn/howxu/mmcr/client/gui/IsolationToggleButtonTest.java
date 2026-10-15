package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises selected rendering and synchronized tooltip state without a graphics device.
 * @author howxu <dev@howxu.cn>
 */
class IsolationToggleButtonTest {
    private Minecraft previous;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @BeforeEach
    void client() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previous = (Minecraft) instance.get(null);
        instance.set(null, ((Unsafe) field.get(null)).allocateInstance(Minecraft.class));
    }

    @AfterEach
    void restoreClient() throws Exception {
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previous);
    }

    @Test
    void selected_background_precedes_label_and_focus_keeps_the_green_fill() throws Exception {
        var state = InputIsolationMenuState.forPort(null);
        var button = new RecordingButton(state);
        var graphics = graphics();
        button.events = graphics.events;
        state.dataSlot().set(3);
        button.renderWidget(graphics, 0, 0, 0);
        assertThat(graphics.events).containsExactly(0xFF66BB6A, 0xFF2E7D32, "label");
        graphics.events.clear();
        button.setFocused(true);
        button.renderWidget(graphics, 0, 0, 0);
        assertThat(graphics.events).containsExactly(0xFFF2F3F3, 0xFF2E7D32, "label");
        graphics.events.clear();
        state.dataSlot().set(1);
        button.renderWidget(graphics, 0, 0, 0);
        assertThat(graphics.events).containsExactly(0xFFF2F3F3, 0xFF9A9FB4, "label");
    }

    @Test
    void visibility_and_tooltip_follow_support_and_confirmed_local_or_full_state() throws Exception {
        var state = InputIsolationMenuState.forPort(null);
        var button = new RecordingButton(state);
        button.updateState(state, false);
        assertThat(button.visible).isFalse();
        assertThat(button.active).isFalse();
        state.dataSlot().set(1);
        button.updateState(state, false);
        assertThat(button.visible).isTrue();
        assertThat(button.active).isTrue();
        assertThat(tooltipMessage(button)).isEqualTo(Component.translatable("mmcr.input_isolation.port.tooltip",
                Component.translatable("mmcr.input_isolation.port.disabled",
                        Component.translatable("mmcr.input_isolation.disabled").withStyle(ChatFormatting.RED))));
        state.dataSlot().set(3);
        button.updateState(state, false);
        assertThat(tooltipMessage(button)).isEqualTo(Component.translatable("mmcr.input_isolation.port.tooltip",
                Component.translatable("mmcr.input_isolation.port.enabled",
                        Component.translatable("mmcr.input_isolation.enabled").withStyle(ChatFormatting.GREEN))));
        button.updateState(state, true);
        assertThat(tooltipMessage(button)).isEqualTo(Component.translatable("mmcr.input_isolation.full.tooltip",
                Component.translatable("mmcr.input_isolation.full.enabled",
                        Component.translatable("mmcr.input_isolation.enabled").withStyle(ChatFormatting.GREEN))));
        state.dataSlot().set(1);
        button.updateState(state, true);
        assertThat(tooltipMessage(button)).isEqualTo(Component.translatable("mmcr.input_isolation.full.tooltip",
                Component.translatable("mmcr.input_isolation.full.disabled",
                        Component.translatable("mmcr.input_isolation.disabled").withStyle(ChatFormatting.RED))));
    }

    private static Component tooltipMessage(RecordingButton button) throws Exception {
        Field message = Tooltip.class.getDeclaredField("message");
        message.setAccessible(true);
        return (Component) message.get(button.lastTooltip);
    }

    private static RecordingGraphics graphics() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var graphics = (RecordingGraphics) ((Unsafe) field.get(null)).allocateInstance(RecordingGraphics.class);
        graphics.events = new ArrayList<>();
        return graphics;
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class RecordingButton extends IsolationToggleButton {
        private List<Object> events;
        private Tooltip lastTooltip;
        private RecordingButton(InputIsolationMenuState state) {
            super(5, 7, Component.empty(), state::enabled, ignored -> {});
        }
        @Override public void renderString(GuiGraphics graphics, Font font, int color) { events.add("label"); }
        @Override public void setTooltip(Tooltip tooltip) {
            super.setTooltip(tooltip);
            lastTooltip = tooltip;
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class RecordingGraphics extends GuiGraphics {
        private List<Object> events;
        private RecordingGraphics() { super(null, (MultiBufferSource.BufferSource) null); }
        @Override public void fill(int x0, int y0, int x1, int y1, int color) { events.add(color); }
    }
}
