package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.OptionalGameTestSource;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import com.mojang.serialization.Lifecycle;

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
        WritableRegistry<TestEnvironmentDefinition<?>> environments = new MappedRegistry<>(
                ResourceKey.createRegistryKey(Identifier.parse("mmcr:test_environments")), Lifecycle.stable());
        WritableRegistry<GameTestInstance> tests = new MappedRegistry<>(
                ResourceKey.createRegistryKey(Identifier.parse("mmcr:test_instances")), Lifecycle.stable());
        RegisterGameTestsEvent event = new RegisterGameTestsEvent(environments, tests);

        GameTestRegistration.registerTests("cn.howxu.mmcr.OptionalGameTestSource", event);

        assertThat(OptionalGameTestSource.testsInvoked()).isTrue();
        assertThat(tests.getValue(Identifier.parse("mmcr:optional_source_test"))).isNotNull();
    }

    @Test
    void ignores_absent_register_tests_source() {
        assertThatCode(() -> GameTestRegistration.registerTests(
                "cn.howxu.mmcr.MissingGameTestRegistry", new RegisterGameTestsEvent(
                        new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.parse("mmcr:test_environments")), Lifecycle.stable()),
                        new MappedRegistry<>(ResourceKey.createRegistryKey(Identifier.parse("mmcr:test_instances")), Lifecycle.stable()))))
                .doesNotThrowAnyException();
    }
}
