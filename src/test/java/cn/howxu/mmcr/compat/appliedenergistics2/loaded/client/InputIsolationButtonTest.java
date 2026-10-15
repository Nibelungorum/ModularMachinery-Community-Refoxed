package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies native AE toggles wait for server confirmation before changing state.
 *
 * @author howxu <dev@howxu.cn>
 */
class InputIsolationButtonTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void repeated_presses_keep_requesting_the_same_change_until_server_confirmation() {
        var state = InputIsolationMenuState.forPort(null);
        List<Boolean> requested = new ArrayList<>();
        var button = new InputIsolationButton(requested::add);
        button.updateState(state);
        assertThat(button.visible).isFalse();
        assertThat(button.active).isFalse();

        state.dataSlot().set(1);
        button.updateState(state);
        assertThat(button.visible).isTrue();
        assertThat(button.active).isTrue();
        var disabledTooltip = button.getTooltipMessage();
        button.onPress();
        button.onPress();
        assertThat(requested).containsExactly(true, true);
        assertThat(state.enabled()).isFalse();
        assertThat(button.getTooltipMessage()).isEqualTo(disabledTooltip);

        state.dataSlot().set(3);
        button.updateState(state);
        assertThat(button.getTooltipMessage()).isNotEqualTo(disabledTooltip);
        button.onPress();
        button.onPress();
        assertThat(requested).containsExactly(true, true, false, false);
        assertThat(state.enabled()).isTrue();

        state.dataSlot().set(1);
        button.updateState(state);
        assertThat(button.getTooltipMessage()).isEqualTo(disabledTooltip);
        state.dataSlot().set(0);
        button.updateState(state);
        assertThat(button.visible).isFalse();
        assertThat(button.active).isFalse();
    }
}
