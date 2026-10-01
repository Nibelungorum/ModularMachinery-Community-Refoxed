package cn.howxu.mmcr;

import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.internal.registration.GameTestRegistration;
import java.util.Set;
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
                new Class<?>[]{MachineDefinitionRegistration.class}, new MachineDefinitionRegistration());
        GameTestRegistration.invokeOptionalSourceForTesting("cn.howxu.mmcr.OptionalGameTestSource", "acceptStructures",
                new Class<?>[]{StructureRegistration.class}, new StructureRegistration(Set.of()));
        GameTestRegistration.invokeOptionalSourceForTesting("cn.howxu.mmcr.OptionalGameTestSource", "acceptRecipes",
                new Class<?>[]{MachineRecipeRegistration.class}, new MachineRecipeRegistration());

        assertThat(OptionalGameTestSource.invoked()).isTrue();
        assertThat(OptionalGameTestSource.structuresInvoked()).isTrue();
        assertThat(OptionalGameTestSource.recipesInvoked()).isTrue();
    }

    @Test
    void ignores_missing_optional_source() {
        assertThatCode(() -> GameTestRegistration.invokeOptionalSourceForTesting("cn.howxu.mmcr.MissingGameTestSource", "accept",
                new Class<?>[]{MachineDefinitionRegistration.class}, new MachineDefinitionRegistration()))
                .doesNotThrowAnyException();
    }
}
