package cn.howxu.mmcr.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies JEI structure-preview input forwarding.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiStructurePreviewWidgetTest {

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

        assertThat(widget.handleMouseDragged(16, 6, leftMouse, 4, 4)).isTrue();
        assertThat(widget.handleInput(16, 6, input(1, true))).isFalse();
        assertThat(preview.pressed).isTrue();
        assertThat(widget.handleInput(16, 6, input(1, false))).isFalse();

        assertThat(preview.pressed).isFalse();
        assertThat(preview.releases).isEqualTo(1);
    }

    @Test
    void closing_cancels_an_active_drag() {
        RecordingPreview preview = new RecordingPreview();
        JeiStructurePreviewWidget widget = JeiStructurePreviewWidget.forTesting(preview, 0, 0, 20, 20);
        InputConstants.Key leftMouse = InputConstants.Type.MOUSE.getOrCreate(0);

        assertThat(widget.handleMouseDragged(16, 6, leftMouse, 4, 4)).isTrue();
        widget.close();

        assertThat(preview.pressed).isFalse();
        assertThat(preview.releases).isEqualTo(1);
    }

    private static IJeiUserInput input(boolean simulate) {
        return input(0, simulate);
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

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            clicks++;
            pressed = true;
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (!pressed) return false;
            releases++;
            pressed = false;
            return mouseX >= 0 && mouseX < 20 && mouseY >= 0 && mouseY < 20;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (!pressed) return false;
            drags++;
            return true;
        }
    }
}
