package cn.howxu.mmcr.test;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import cn.howxu.mmcr.api.capability.status.FailureTrace;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.port.PortDefinitionRegistry;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.sync.FailureStatusCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Shared bootstrap postconditions after cold menu initialization and cross-suite cleanup.
 * @author howxu <dev@howxu.cn>
 */
class TestBootstrapTest {
    @Test
    void cold_menu_registration_preserves_absent_loaded_and_unavailable_overrides() throws Throwable {
        Set<URL> classpath = new LinkedHashSet<>();
        String dependencies = System.getProperty("mmcr.apiExternalClasspath");
        assertThat(dependencies).as("Gradle's explicit main dependency classpath").isNotBlank();
        for (String entry : dependencies.split(File.pathSeparator)) {
            classpath.add(Path.of(entry).toUri().toURL());
        }
        classpath.add(MMCR.class.getProtectionDomain().getCodeSource().getLocation());
        classpath.add(TestBootstrapMenuFixture.class.getProtectionDomain().getCodeSource().getLocation());
        classpath.add(Assertions.class.getProtectionDomain().getCodeSource().getLocation());
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        // Each fixture gets fresh ModUIs static finals, independently of the worker's suite order.
        for (int override = 0; override < 3; override++) {
            try (URLClassLoader loader = new URLClassLoader(classpath.toArray(URL[]::new),
                    ClassLoader.getPlatformClassLoader())) {
                Thread.currentThread().setContextClassLoader(loader);
                try {
                    Class.forName("cn.howxu.mmcr.test.TestBootstrapMenuFixture", true, loader)
                            .getMethod("verify", int.class).invoke(null, override);
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        }
    }

    @Test
    void menu_bootstrap_keeps_port_bindings_owned_after_public_api_registry_reset() throws Exception {
        TestBootstrap.bootstrap();
        try {
            PublicApiBootstrap.clearForTesting();
            PublicApiBootstrap.begin();
            assertThat(PortDefinitionRegistry.values()).isNotEmpty().allSatisfy(definition ->
                    definition.bindings().forEach(binding ->
                            assertThat(CapabilityRegistry.get(binding.type())).as(definition.id().toString()).isNotNull()));
        } finally {
            TestBootstrap.restoreMachineDefinitions();
            TestBootstrap.bootstrap();
        }
    }

    @Test
    void one_bootstrap_after_machine_cleanup_restores_known_status_and_unknown_fallback() throws Exception {
        TestBootstrap.bootstrap();
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();

        TestBootstrap.bootstrap();

        assertThat(MachineDefinitions.getRegistration(MMCR.id("test_cube"))).isNotNull();
        assertThat(MachineRegistry.getCompiled(MMCR.id("test_cube"))).isNotNull();
        ExecutionStatus known = status(BuiltinFailureReasons.MISSING_ENERGY);
        assertThat(roundTrip(known)).isEqualTo(known);
        assertThat(roundTrip(known).details()).doesNotContainKey("raw_reason_id");
        assertThat(FailureReasonRegistry.isFrozen()).isTrue();
        assertThatThrownBy(() -> FailureReasonRegistry.register(
                new FailureReason(MMCR.id("bootstrap_frozen"), "test.bootstrap.frozen")))
                .isInstanceOf(IllegalStateException.class);

        FailureReason removed = new FailureReason(MMCR.id("removed_bootstrap_reason"), "test.bootstrap.removed");
        ExecutionStatus unknown = roundTrip(status(removed));
        assertThat(unknown.reason()).isSameAs(BuiltinFailureReasons.UNKNOWN);
        assertThat(unknown.details()).containsEntry("raw_reason_id", removed.id().toString())
                .containsEntry("required", "20");
        assertThat(unknown.failure().trace()).isEqualTo(known.failure().trace());
    }

    @Test
    void warm_bootstrap_repairs_reasons_without_freezing_an_open_registry() throws Exception {
        TestBootstrap.bootstrap();
        FailureReasonRegistry.clearForTesting();
        try {
            TestBootstrap.bootstrap();
            assertThat(roundTrip(status(BuiltinFailureReasons.MISSING_ENERGY)))
                    .isEqualTo(status(BuiltinFailureReasons.MISSING_ENERGY));
            assertThat(FailureReasonRegistry.isFrozen()).isFalse();
            FailureReason addon = new FailureReason(MMCR.id("bootstrap_addon"), "test.bootstrap.addon");
            FailureReasonRegistry.register(addon);
            TestBootstrap.bootstrap();
            assertThat(roundTrip(status(addon)).reason()).isSameAs(addon);
        } finally {
            TestBootstrap.restoreMachineDefinitions();
            TestBootstrap.bootstrap();
        }
    }

    private static ExecutionStatus status(FailureReason reason) {
        return new ExecutionStatus(MMCR.id("bootstrap_status"), StatusSeverity.BLOCKED, MMCR.id("crafting"),
                new FailureOccurrence(reason, FailureTrace.single(new FailureTrace.Frame(
                        MMCR.id("controller"), FailurePhase.RUNTIME, MMCR.id("bootstrap_recipe"), 2)),
                        Map.of("required", "20")));
    }

    private static ExecutionStatus roundTrip(ExecutionStatus status) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
        try {
            FailureStatusCodec.write(buffer, status);
            return FailureStatusCodec.read(buffer);
        } finally {
            buffer.release();
        }
    }
}
