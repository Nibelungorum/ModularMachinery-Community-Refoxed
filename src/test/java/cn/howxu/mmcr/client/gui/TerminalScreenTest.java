package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.internal.item.TerminalAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies pure terminal screen state transitions.
 * @author howxu <dev@howxu.cn>
 */
class TerminalScreenTest {
    @Test
    void level_controls_are_silent_when_machine_has_no_level_slots() {
        TerminalScreen.LevelView view = TerminalScreen.levelView(List.of(), Map.of());

        assertThat(view.typeButtonActive()).isFalse();
        assertThat(view.levelButtonActive()).isFalse();
        assertThat(view.slotStack().isEmpty()).isTrue();
    }

    @Test
    void layer_plus_wraps_from_highest_to_lowest_and_r_wraps_to_all() {
        List<Integer> layers = List.of(-2, 0, 4);

        assertThat(TerminalScreen.nextLayer(4, layers)).isEqualTo(-2);
        assertThat(TerminalScreen.resetLayer()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void action_buttons_close_screen_but_configuration_buttons_do_not() {
        assertThat(TerminalScreen.closesAfter(TerminalAction.BUILD)).isTrue();
        assertThat(TerminalScreen.closesAfter(TerminalAction.SET_STAGE)).isFalse();
    }
}
