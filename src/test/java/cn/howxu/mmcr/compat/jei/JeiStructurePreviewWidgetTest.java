package cn.howxu.mmcr.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies JEI structure-preview input forwarding.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiStructurePreviewWidgetTest {

    @ParameterizedTest
    @CsvSource({"false, 69", "true, 87"})
    void last_control_consumes_simulation_and_toggles_only_on_real_click(boolean multipleStages, int highlightX) {
        RecordingPreview preview = new RecordingPreview();
        preview.multipleStages = multipleStages;
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 168, 100);

        assertThat(widget.handleInput(highlightX, 88, input(true))).isTrue();
        assertThat(preview.highlightsEnabled).isFalse();
        assertThat(preview.highlightToggles).isZero();
        assertThat(widget.handleMouseDragged(highlightX + 1, 89,
                InputConstants.Type.MOUSE.getOrCreate(0), 1, 1)).isFalse();
        assertThat(widget.handleInput(highlightX, 88, input(false))).isTrue();
        assertThat(preview.highlightsEnabled).isTrue();
        assertThat(preview.highlightToggles).isEqualTo(1);
        assertThat(preview.clicks).isZero();
        assertThat(preview.drags).isZero();
        assertThat(preview.releases).isZero();
        assertThat(preview.stageChanges).isZero();

        assertThat(widget.handleInput(highlightX + 13, 100, input(true))).isTrue();
        assertThat(preview.highlightsEnabled).isTrue();
        assertThat(widget.handleInput(highlightX + 13, 100, input(false))).isTrue();
        assertThat(preview.highlightsEnabled).isFalse();
        assertThat(preview.highlightToggles).isEqualTo(2);
        assertThat(preview.clicks).isZero();

        // The gap after H must not become another control or begin a preview drag.
        assertThat(widget.handleInput(highlightX + 14, 100, input(false))).isFalse();
        assertThat(widget.handleInput(highlightX + 18, 100, input(false))).isFalse();
        assertThat(preview.highlightToggles).isEqualTo(2);
    }

    @Test
    void multi_stage_control_still_changes_stage_and_reset_keeps_its_mapping() {
        RecordingPreview preview = new RecordingPreview();
        preview.multipleStages = true;
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 168, 100);

        assertThat(widget.handleInput(70, 88, input(true))).isTrue();
        assertThat(preview.stageChanges).isZero();
        assertThat(widget.handleInput(70, 88, input(false))).isTrue();
        assertThat(preview.stageChanges).isEqualTo(1);
        assertThat(widget.handleInput(52, 88, input(true))).isTrue();
        assertThat(widget.handleInput(52, 88, input(false))).isTrue();
        assertThat(preview.resets).isEqualTo(1);
        assertThat(preview.highlightToggles).isZero();
        assertThat(preview.clicks).isZero();
    }

    @Test
    void simulation_only_reports_whether_the_preview_can_handle_the_click() {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 20, 20);

        assertThat(widget.handleInput(12, 2, input(true))).isTrue();

        assertThat(preview.pressed).isFalse();
        assertThat(preview.clicks).isZero();
        assertThat(preview.drags).isZero();
        assertThat(preview.releases).isZero();
    }

    @Test
    void actual_click_executes_a_balanced_preview_gesture() {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 20, 20);

        assertThat(widget.handleInput(12, 2, input(true))).isTrue();
        assertThat(widget.handleInput(12, 2, input(false))).isTrue();

        assertThat(preview.pressed).isFalse();
        assertThat(preview.clicks).isEqualTo(1);
        assertThat(preview.releases).isEqualTo(1);
    }

    @Test
    void actual_drag_releases_when_the_pointer_leaves_the_preview() {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 20, 20);
        InputConstants.Key leftMouse = InputConstants.Type.MOUSE.getOrCreate(0);

        assertThat(widget.handleInput(12, 2, input(true))).isTrue();
        assertThat(widget.handleMouseDragged(16, 6, leftMouse, 4, 4)).isTrue();
        assertThat(preview.pressed).isTrue();
        assertThat(widget.handleInput(30, 30, input(false))).isFalse();

        assertThat(preview.pressed).isFalse();
        assertThat(preview.clicks).isEqualTo(1);
        assertThat(preview.drags).isEqualTo(1);
        assertThat(preview.releases).isEqualTo(1);
    }

    @Test
    void mismatched_actual_input_cancels_an_active_drag() {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 20, 20);
        InputConstants.Key leftMouse = InputConstants.Type.MOUSE.getOrCreate(0);

        assertThat(widget.handleInput(12, 2, input(true))).isTrue();
        assertThat(widget.handleMouseDragged(16, 6, leftMouse, 4, 4)).isTrue();
        assertThat(widget.handleInput(16, 6, input(3, true))).isFalse();
        assertThat(preview.pressed).isTrue();
        assertThat(widget.handleInput(16, 6, input(3, false))).isFalse();

        assertThat(preview.pressed).isFalse();
        assertThat(preview.releases).isEqualTo(1);
    }

    @Test
    void closing_cancels_an_active_drag() {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 20, 20);
        InputConstants.Key leftMouse = InputConstants.Type.MOUSE.getOrCreate(0);

        assertThat(widget.handleInput(12, 2, input(true))).isTrue();
        assertThat(widget.handleMouseDragged(16, 6, leftMouse, 4, 4)).isTrue();
        widget.close();

        assertThat(preview.pressed).isFalse();
        assertThat(preview.releases).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "2"})
    void drag_from_a_control_cannot_become_a_preview_gesture(int button) {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 168, 100);

        assertThat(widget.handleInput(70, 88, input(button, true))).isEqualTo(button == 0);
        assertThat(widget.handleMouseDragged(70, 84,
                InputConstants.Type.MOUSE.getOrCreate(button), 0, -4)).isFalse();
        assertThat(widget.handleInput(70, 84, input(button, false))).isFalse();
        assertThat(preview.clicks).isZero();
        assertThat(preview.drags).isZero();
        assertThat(preview.highlightToggles).isZero();
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "2"})
    void release_outside_before_the_first_drag_clears_the_pending_preview_press(int button) {
        RecordingPreview preview = new RecordingPreview();
        boolean[] physicallyHeld = {true};
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview,
                0, 0, 168, 100, key -> physicallyHeld[0]);
        InputConstants.Key mouse = InputConstants.Type.MOUSE.getOrCreate(button);
        assertThat(widget.handleInput(20, 20, input(button, true))).isTrue();
        assertThat(widget.handleMouseDragged(200, 200, mouse, 180, 180)).isFalse();

        physicallyHeld[0] = false;
        widget.drawWidget(null, 0, 0);
        physicallyHeld[0] = true;
        assertThat(widget.handleMouseDragged(24, 25, mouse, -176, -175)).isFalse();
        assertThat(widget.handleMouseDragged(28, 30, mouse, 4, 5)).isFalse();
        assertThat(preview.clicks).isZero();
        assertThat(preview.drags).isZero();
        assertThat(preview.releases).isZero();
    }

    @ParameterizedTest
    @CsvSource({"1", "2"})
    void forwards_pan_buttons_but_does_not_activate_controls(int button) {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 168, 100);
        InputConstants.Key mouse = InputConstants.Type.MOUSE.getOrCreate(button);

        assertThat(widget.handleInput(20, 20, input(button, true))).isTrue();
        assertThat(preview.pressedButton).isEqualTo(-1);
        assertThat(widget.handleMouseDragged(24, 25, mouse, 4, 5)).isTrue();
        assertThat(preview.pressedButton).isEqualTo(button);
        assertThat(widget.handleInput(24, 25, input(button, false))).isTrue();
        assertThat(preview.clicks).isEqualTo(1);
        assertThat(preview.drags).isEqualTo(1);
        assertThat(preview.releases).isEqualTo(1);
        assertThat(preview.lastButton).isEqualTo(button);

        assertThat(widget.handleInput(52, 88, input(button, true))).isFalse();
        assertThat(widget.handleInput(52, 88, input(button, false))).isFalse();
        assertThat(preview.resets).isZero();
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "1, 0", "2, 0"})
    void another_button_cannot_interrupt_an_active_preview_drag(int button, int otherButton) {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 168, 100);
        InputConstants.Key mouse = InputConstants.Type.MOUSE.getOrCreate(button);

        assertThat(widget.handleInput(20, 20, input(button, true))).isTrue();
        assertThat(widget.handleMouseDragged(24, 25, mouse, 4, 5)).isTrue();
        assertThat(widget.handleInput(52, 88, input(otherButton, true))).isFalse();
        assertThat(widget.handleInput(52, 88, input(otherButton, false))).isFalse();
        assertThat(widget.handleInput(22, 22, input(otherButton, true))).isFalse();
        assertThat(widget.handleInput(22, 22, input(otherButton, false))).isFalse();
        assertThat(widget.handleMouseDragged(24, 25,
                InputConstants.Type.MOUSE.getOrCreate(otherButton), 4, 5)).isFalse();
        assertThat(widget.handleMouseDragged(24, 25, mouse, 4, 5)).isTrue();
        assertThat(widget.handleInput(24, 25, input(button, false))).isTrue();

        assertThat(preview.pressedButton).isEqualTo(-1);
        assertThat(preview.clicks).isEqualTo(1);
        assertThat(preview.drags).isEqualTo(2);
        assertThat(preview.releases).isEqualTo(1);
        assertThat(preview.resets).isZero();
    }

    private static IJeiUserInput input(boolean simulate) {
        return input(0, simulate);
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "1, 1", "2, 0", "0, 1"})
    void next_press_recovers_when_jei_did_not_deliver_the_previous_release(int oldButton, int newButton) {
        RecordingPreview preview = new RecordingPreview();
        boolean[] physicallyHeld = {true};
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview,
                0, 0, 168, 100, button -> physicallyHeld[0]);

        assertThat(widget.handleInput(20, 20, input(oldButton, true))).isTrue();
        assertThat(widget.handleMouseDragged(24, 25,
                InputConstants.Type.MOUSE.getOrCreate(oldButton), 4, 5)).isTrue();
        // JEI omits the release outside its recipe area. A repeated press of the
        // same button also proves that the previous press has already ended.
        physicallyHeld[0] = oldButton == newButton;
        assertThat(widget.handleInput(20, 20, input(newButton, true))).isTrue();
        assertThat(preview.pressedButton).isEqualTo(-1);
        assertThat(widget.handleMouseDragged(24, 25,
                InputConstants.Type.MOUSE.getOrCreate(newButton), 4, 5)).isTrue();
        assertThat(widget.handleInput(24, 25, input(newButton, false))).isTrue();

        assertThat(preview.pressedButton).isEqualTo(-1);
        assertThat(preview.clicks).isEqualTo(2);
        assertThat(preview.releases).isEqualTo(2);
    }

    private static IJeiUserInput input(int button, boolean simulate) {
        return new IJeiUserInput() {
            @Override
            public InputConstants.Key getKey() {
                return InputConstants.Type.MOUSE.getOrCreate(button);
            }

            @Override
            public int getModifiers() {
                return 0;
            }

            @Override
            public boolean isSimulate() {
                return simulate;
            }

            @Override
            public boolean is(KeyMapping keyMapping) {
                return false;
            }

            @Override
            public boolean is(IJeiKeyMapping keyMapping) {
                return false;
            }
        };
    }

    private static final class RecordingPreview implements JeiStructurePreviewWidget.Preview {
        private int clicks;
        private int drags;
        private int releases;
        private boolean pressed;
        private boolean multipleStages;
        private boolean highlightsEnabled;
        private int highlightToggles;
        private int stageChanges;
        private int resets;
        private int lastButton = -1;
        private int pressedButton = -1;

        @Override
        public boolean hasMultipleStages() {
            return multipleStages;
        }

        @Override
        public boolean positionHighlightsEnabled() {
            return highlightsEnabled;
        }

        @Override
        public void togglePositionHighlights() {
            highlightsEnabled = !highlightsEnabled;
            highlightToggles++;
        }

        @Override
        public void selectNextStage() {
            stageChanges++;
        }

        @Override
        public void reset() {
            resets++;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (pressedButton >= 0) return false;
            pressedButton = button;
            clicks++;
            pressed = true;
            lastButton = button;
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (pressedButton != button) return false;
            pressedButton = -1;
            pressed = false;
            releases++;
            lastButton = button;
            return mouseX >= 0 && mouseX < 20 && mouseY >= 0 && mouseY < 20;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (pressedButton != button) return false;
            drags++;
            lastButton = button;
            return true;
        }
    }
}
