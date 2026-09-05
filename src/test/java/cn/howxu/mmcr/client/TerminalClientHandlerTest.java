package cn.howxu.mmcr.client;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the terminal screen opening input predicate.
 * @author howxu <dev@howxu.cn>
 */
class TerminalClientHandlerTest {
    @Test
    void opens_for_an_unshifted_main_hand_terminal_air_use_without_a_screen() {
        assertThat(TerminalClientHandler.shouldOpenScreen(
                true, InteractionHand.MAIN_HAND, false, false, true, true)).isTrue();
    }

    @Test
    void does_not_open_while_shift_is_down() {
        assertThat(TerminalClientHandler.shouldOpenScreen(
                true, InteractionHand.MAIN_HAND, true, false, true, true)).isFalse();
    }

    @Test
    void does_not_open_when_a_screen_is_already_open() {
        assertThat(TerminalClientHandler.shouldOpenScreen(
                true, InteractionHand.MAIN_HAND, false, true, true, true)).isFalse();
    }

    @Test
    void does_not_open_when_the_crosshair_is_not_in_air() {
        assertThat(TerminalClientHandler.shouldOpenScreen(
                true, InteractionHand.MAIN_HAND, false, false, false, true)).isFalse();
    }

    @Test
    void does_not_open_without_a_main_hand_terminal() {
        assertThat(TerminalClientHandler.shouldOpenScreen(
                true, InteractionHand.MAIN_HAND, false, false, true, false)).isFalse();
    }
}
