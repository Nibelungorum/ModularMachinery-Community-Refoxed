package cn.howxu.mmcr;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

/** Verifies optional GameTest source handling when the source exists or is absent.
 * @author howxu <dev@howxu.cn>
 */
class OptionalGameTestSourceTest {
    @Test
    void invokes_present_optional_source() {
        MMCR.invokeOptionalSourceForTesting("cn.howxu.mmcr.OptionalGameTestSource", "accept",
                new Class<?>[]{MMCRMachineDefinationsEvent.class}, new MMCRMachineDefinationsEvent());

        assertThat(OptionalGameTestSource.invoked()).isTrue();
    }

    @Test
    void ignores_missing_optional_source() {
        assertThatCode(() -> MMCR.invokeOptionalSourceForTesting("cn.howxu.mmcr.MissingGameTestSource", "accept",
                new Class<?>[]{MMCRMachineDefinationsEvent.class}, new MMCRMachineDefinationsEvent()))
                .doesNotThrowAnyException();
    }
}
