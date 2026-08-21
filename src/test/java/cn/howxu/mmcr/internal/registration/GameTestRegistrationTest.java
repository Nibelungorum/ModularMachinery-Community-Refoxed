package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.OptionalGameTestSource;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the GameTest registration facade independently of its reflection helper.
 * @author howxu <dev@howxu.cn>
 */
class GameTestRegistrationTest {
    @BeforeEach
    void resetFixture() {
        OptionalGameTestSource.reset();
    }

    @Test
    void forwards_all_canonical_startup_events_to_present_source() {
        GameTestRegistration.registerStartupSources("cn.howxu.mmcr.OptionalGameTestSource",
                new MMCRMachineDefinationsEvent(),
                new MMCRMachineStructuresEvent(java.util.Set.of()), new MMCRMachineRecipesEvent());

        assertThat(OptionalGameTestSource.invoked()).isTrue();
        assertThat(OptionalGameTestSource.structuresInvoked()).isTrue();
        assertThat(OptionalGameTestSource.recipesInvoked()).isTrue();
    }

    @Test
    void ignores_absent_startup_source() {
        assertThatCode(() -> GameTestRegistration.registerStartupSources(
                "cn.howxu.mmcr.MissingGameTestRegistry", new MMCRMachineDefinationsEvent(),
                new MMCRMachineStructuresEvent(java.util.Set.of()), new MMCRMachineRecipesEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    void forwards_register_tests_to_present_source() {
        GameTestRegistration.registerTests("cn.howxu.mmcr.OptionalGameTestSource", null);

        assertThat(OptionalGameTestSource.testsInvoked()).isTrue();
    }
}
