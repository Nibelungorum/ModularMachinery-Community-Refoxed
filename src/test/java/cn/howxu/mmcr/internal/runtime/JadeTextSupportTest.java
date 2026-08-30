package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.publicapi.controller.JadeText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies optional Jade text runtime support.
 *
 * @author howxu <dev@howxu.cn>
 */
class JadeTextSupportTest {

    @BeforeEach
    void resetJadeAvailability() {
        JadeTextSupport.resetForTesting();
    }

    @Test
    void createsNoopBeforeJadeIntegrationIsEnabled() {
        assertThat(JadeTextSupport.create()).isSameAs(JadeText.noop());
    }

    @Test
    void createsIndependentStateAfterJadeIntegrationIsEnabled() {
        JadeTextSupport.enable();

        JadeText first = JadeTextSupport.create();
        JadeText second = JadeTextSupport.create();
        assertThat(first).isInstanceOf(JadeTextState.class).isNotSameAs(second);
    }
}
