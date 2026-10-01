package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.OptionalGameTestSource;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import java.util.Set;
import java.util.LinkedHashSet;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the GameTest registration facade independently of its reflection helper.
 * @author howxu <dev@howxu.cn>
 */
class GameTestRegistrationTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void resetFixture() {
        OptionalGameTestSource.reset();
    }

    @Test
    void forwards_all_canonical_startup_events_to_present_source() {
        GameTestRegistration.registerStartupSources("cn.howxu.mmcr.OptionalGameTestSource",
                new MachineDefinitionRegistration(),
                new StructureRegistration(Set.of()), new MachineRecipeRegistration());

        assertThat(OptionalGameTestSource.invoked()).isTrue();
        assertThat(OptionalGameTestSource.structuresInvoked()).isTrue();
        assertThat(OptionalGameTestSource.recipesInvoked()).isTrue();
    }

    @Test
    void ignores_absent_startup_source() {
        assertThatCode(() -> GameTestRegistration.registerStartupSources(
                "cn.howxu.mmcr.MissingGameTestRegistry", new MachineDefinitionRegistration(),
                new StructureRegistration(Set.of()), new MachineRecipeRegistration()))
                .doesNotThrowAnyException();
    }

    @Test
    void forwards_register_tests_to_present_source() {
        Set<Method> tests = new LinkedHashSet<>();
        RegisterGameTestsEvent event = new RegisterGameTestsEvent(tests);

        GameTestRegistration.registerTests("cn.howxu.mmcr.OptionalGameTestSource", event);

        assertThat(OptionalGameTestSource.testsInvoked()).isTrue();
        assertThat(tests).extracting(Method::getName).contains("optionalSourceTest");
    }

    @Test
    void ignores_absent_register_tests_source() {
        assertThatCode(() -> GameTestRegistration.registerTests(
                "cn.howxu.mmcr.MissingGameTestRegistry", new RegisterGameTestsEvent(new LinkedHashSet<>())))
                .doesNotThrowAnyException();
    }

    @Test
    void invokes_present_development_source() {
        OptionalSourceRegistration.invokeDevelopmentSource(
                "cn.howxu.mmcr.OptionalGameTestSource", "registerMachineDefinitions",
                new Class<?>[]{MachineDefinitionRegistration.class}, new MachineDefinitionRegistration());

        assertThat(OptionalGameTestSource.invoked()).isTrue();
    }
}
