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

    private static IJeiUserInput input(boolean simulate) {
        return new IJeiUserInput() {
            @Override
            public InputConstants.Key getKey() {
                return InputConstants.Type.MOUSE.getOrCreate(0);
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

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            clicks++;
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            releases++;
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            drags++;
            return true;
        }
    }
}
