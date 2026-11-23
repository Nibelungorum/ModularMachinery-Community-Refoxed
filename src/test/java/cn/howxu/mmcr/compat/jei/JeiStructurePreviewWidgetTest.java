package cn.howxu.mmcr.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.InputWithModifiers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies JEI structure-preview input forwarding.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiStructurePreviewWidgetTest {

    @ParameterizedTest
    @CsvSource({"false, 74", "true, 92"})
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

        assertThat(widget.handleInput(highlightX + 13, 97, input(true))).isTrue();
        assertThat(preview.highlightsEnabled).isTrue();
        assertThat(widget.handleInput(highlightX + 13, 97, input(false))).isTrue();
        assertThat(preview.highlightsEnabled).isFalse();
        assertThat(preview.highlightToggles).isEqualTo(2);
        assertThat(preview.clicks).isZero();

        // Below the viewport neither a control nor the preview should claim the click.
        assertThat(widget.handleInput(highlightX + 14, 100, input(false))).isFalse();
        assertThat(widget.handleInput(highlightX + 18, 100, input(false))).isFalse();
        assertThat(preview.highlightToggles).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void leftControlsAndJeiSideButtonsShareTheBottomOfTheViewport(int scale) {
        var viewport = StructurePreviewLayout.viewport(StructurePreviewLayout.heightForScale(scale));
        var sideButton = StructurePreviewLayout.sideButtonOrigin(viewport);
        var preview = new RecordingPreview();
        var widget = JeiStructurePreviewWidget.forTesting(preview, viewport.left(), viewport.top(),
                viewport.width(), viewport.height());
        assertThat(widget.getArea()).isEqualTo(viewport);
        assertThat(viewport.containsPoint(sideButton.x() + 12, sideButton.y() + 12)).isTrue();
        assertThat(viewport.containsPoint(sideButton.x(), sideButton.y() - 15)).isTrue(); // stacked bookmark

        int lastButtonPixelY = sideButton.y() + 12 - viewport.top();
        assertThat(widget.handleInput(74, lastButtonPixelY, input(false))).isTrue();
        assertThat(preview.highlightToggles).isOne();
        assertThat(widget.handleInput(74, lastButtonPixelY + 1, input(true))).isTrue();
        assertThat(preview.clicks).isOne(); // inside the viewport but below the controls
    }

    @Test
    void multi_stage_control_still_changes_stage_and_reset_keeps_its_mapping() {
        RecordingPreview preview = new RecordingPreview();
        preview.multipleStages = true;
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 168, 100);

        assertThat(widget.handleInput(75, 88, input(true))).isTrue();
        assertThat(preview.stageChanges).isZero();
        assertThat(widget.handleInput(75, 88, input(false))).isTrue();
        assertThat(preview.stageChanges).isEqualTo(1);
        assertThat(widget.handleInput(57, 88, input(false))).isTrue();
        assertThat(preview.resets).isEqualTo(1);
        assertThat(preview.highlightToggles).isZero();
        assertThat(preview.clicks).isZero();
    }

    @Test
    void starts_dragging_during_the_mouse_down_simulation_pass() {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 20, 20);
        InputConstants.Key leftMouse = InputConstants.Type.MOUSE.getOrCreate(0);

        assertThat(widget.handleInput(12, 2, input(true))).isTrue();
        assertThat(widget.handleMouseDragged(16, 6, leftMouse, 4, 4)).isTrue();
        assertThat(widget.handleInput(16, 6, input(false))).isTrue();

        assertThat(preview.clicks).isEqualTo(1);
        assertThat(preview.drags).isEqualTo(1);
        assertThat(preview.releases).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"1", "2"})
    void forwards_pan_buttons_but_does_not_activate_controls(int button) {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 168, 100);
        InputConstants.Key mouse = InputConstants.Type.MOUSE.getOrCreate(button);

        assertThat(widget.handleInput(20, 20, input(button, true))).isTrue();
        assertThat(preview.pressedButton).isEqualTo(button);
        assertThat(widget.handleMouseDragged(24, 25, mouse, 4, 5)).isTrue();
        assertThat(widget.handleInput(24, 25, input(button, false))).isTrue();
        assertThat(preview.clicks).isEqualTo(1);
        assertThat(preview.drags).isEqualTo(1);
        assertThat(preview.releases).isEqualTo(1);
        assertThat(preview.lastButton).isEqualTo(button);

        assertThat(widget.handleInput(57, 88, input(button, true))).isFalse();
        assertThat(widget.handleInput(57, 88, input(button, false))).isFalse();
        assertThat(preview.resets).isZero();
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "1, 0", "2, 0"})
    void another_button_cannot_interrupt_an_active_preview_drag(int button, int otherButton) {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 168, 100);
        InputConstants.Key mouse = InputConstants.Type.MOUSE.getOrCreate(button);

        assertThat(widget.handleInput(20, 20, input(button, true))).isTrue();
        assertThat(widget.handleInput(57, 88, input(otherButton, true))).isFalse();
        assertThat(widget.handleInput(57, 88, input(otherButton, false))).isFalse();
        assertThat(widget.handleInput(22, 22, input(otherButton, true))).isFalse();
        assertThat(widget.handleInput(22, 22, input(otherButton, false))).isFalse();
        assertThat(widget.handleMouseDragged(24, 25,
                InputConstants.Type.MOUSE.getOrCreate(otherButton), 4, 5)).isFalse();
        assertThat(widget.handleMouseDragged(24, 25, mouse, 4, 5)).isTrue();
        assertThat(widget.handleInput(24, 25, input(button, false))).isTrue();

        assertThat(preview.pressedButton).isEqualTo(-1);
        assertThat(preview.clicks).isEqualTo(1);
        assertThat(preview.drags).isEqualTo(1);
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
        // JEI omits the release outside its recipe area. A repeated press of the
        // same button also proves that the previous press has already ended.
        physicallyHeld[0] = oldButton == newButton;
        assertThat(widget.handleInput(20, 20, input(newButton, true))).isTrue();
        assertThat(preview.pressedButton).isEqualTo(newButton);
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
            public InputWithModifiers getInputWithModifiers() {
                return null;
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
            lastButton = button;
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (pressedButton != button) return false;
            pressedButton = -1;
            releases++;
            lastButton = button;
            return true;
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
