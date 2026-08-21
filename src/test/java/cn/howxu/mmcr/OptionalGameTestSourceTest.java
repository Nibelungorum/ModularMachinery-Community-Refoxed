package cn.howxu.mmcr;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.internal.registration.GameTestRegistration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

/** Verifies optional GameTest source handling when the source exists or is absent.
 * @author howxu <dev@howxu.cn>
 */
class OptionalGameTestSourceTest {
    @Test
    void invokes_present_optional_source() {
        GameTestRegistration.invokeOptionalSourceForTesting("cn.howxu.mmcr.OptionalGameTestSource", "accept",
                new Class<?>[]{MMCRMachineDefinationsEvent.class}, new MMCRMachineDefinationsEvent());
        GameTestRegistration.invokeOptionalSourceForTesting("cn.howxu.mmcr.OptionalGameTestSource", "acceptStructures",
                new Class<?>[]{MMCRMachineStructuresEvent.class}, new MMCRMachineStructuresEvent(java.util.Set.of()));
        GameTestRegistration.invokeOptionalSourceForTesting("cn.howxu.mmcr.OptionalGameTestSource", "acceptRecipes",
                new Class<?>[]{MMCRMachineRecipesEvent.class}, new MMCRMachineRecipesEvent());

        assertThat(OptionalGameTestSource.invoked()).isTrue();
        assertThat(OptionalGameTestSource.structuresInvoked()).isTrue();
        assertThat(OptionalGameTestSource.recipesInvoked()).isTrue();
    }

    @Test
    void ignores_missing_optional_source() {
        assertThatCode(() -> GameTestRegistration.invokeOptionalSourceForTesting("cn.howxu.mmcr.MissingGameTestSource", "accept",
                new Class<?>[]{MMCRMachineDefinationsEvent.class}, new MMCRMachineDefinationsEvent()))
                .doesNotThrowAnyException();
    }
}
