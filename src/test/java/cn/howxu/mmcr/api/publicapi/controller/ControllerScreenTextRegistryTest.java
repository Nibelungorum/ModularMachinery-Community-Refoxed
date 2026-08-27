package cn.howxu.mmcr.api.publicapi.controller;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.SystemReport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.debugchart.SampleLogger;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controller screen text registry behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerScreenTextRegistryTest {
    private static final Identifier MACHINE_ID = MMCR.id("test_cube");
    private static final Identifier OTHER_MACHINE_ID = MMCR.id("other_machine");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void openPublicStartupRegistration() throws Exception {
        clearCurrentServerForTesting();
        PublicApiBootstrap.clearForTesting();
        PublicApiBootstrap.begin();
    }

    @AfterEach
    void cleanup() throws Exception {
        ControllerScreenTextRegistry.clearForTesting();
        clearCurrentServerForTesting();
    }

    @Test
    void appliesOnlyMatchingMachineHandlersInRegistrationOrder() {
        ControllerScreenTextState state = new ControllerScreenTextState();
        ControllerRuntimeContext context = context(MACHINE_ID, state);
        List<String> calls = new ArrayList<>();

        ControllerScreenTextRegistry.register(MACHINE_ID, received -> {
            calls.add("first");
            assertThat(received).isSameAs(context);
            received.screenText().append(ControllerScreenTextScope.CONTROLLER,
                    Identifier.parse("example:first"), Component.literal("first"));
        });
        ControllerScreenTextRegistry.register(OTHER_MACHINE_ID, received -> calls.add("other"));
        ControllerScreenTextRegistry.register(MACHINE_ID, received -> calls.add("second"));

        ControllerScreenTextRegistry.apply(context);

        assertThat(calls).containsExactly("first", "second");
        assertThat(state.snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text()).isEqualTo(Component.literal("first")));
    }

    @Test
    void handlerFailureDoesNotPreventLaterHandlers() {
        ControllerScreenTextState state = new ControllerScreenTextState();
        List<String> calls = new ArrayList<>();
        ControllerRuntimeContext context = context(MACHINE_ID, state);
        ControllerScreenTextRegistry.register(MACHINE_ID, received -> {
            calls.add("failed");
            throw new IllegalStateException("expected test failure");
        });
        ControllerScreenTextRegistry.register(MACHINE_ID, received -> {
            calls.add("continued");
            received.screenText().append(ControllerScreenTextScope.OPERATION,
                    Identifier.parse("example:continued"), Component.literal("continued"));
        });

        ControllerScreenTextRegistry.apply(context);

        assertThat(calls).containsExactly("failed", "continued");
        assertThat(state.snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text()).isEqualTo(Component.literal("continued")));
    }

    @Test
    void unregisterRemovesOnlyItsRegistration() {
        ControllerScreenTextState state = new ControllerScreenTextState();
        List<String> calls = new ArrayList<>();
        ControllerRuntimeContext context = context(MACHINE_ID, state);
        ControllerScreenTextRegistry.Registration removed = ControllerScreenTextRegistry.register(
                MACHINE_ID, received -> calls.add("removed"));
        ControllerScreenTextRegistry.register(MACHINE_ID, received -> calls.add("kept"));

        removed.unregister();
        ControllerScreenTextRegistry.apply(context);

        assertThat(calls).containsExactly("kept");
    }

    @Test
    void registry_does_not_expose_server_script_registration_or_reload_lifecycle() {
        assertThat(ControllerScreenTextRegistry.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("registerServerScript", "beginServerScriptReload", "endServerScriptReload",
                        "abortServerScriptReload", "beginServerScriptReloadFromReloadHook",
                        "endServerScriptReloadFromReloadHook", "abortServerScriptReloadFromReloadHook");
    }

    @Test
    void runtimeContextUsesConfiguredMachinePositionAndSameTextState() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MACHINE_ID);
        MachineControllerRuntime runtime = runtimeOf(controller);

        ControllerRuntimeContext context = runtime.runtimeContext();

        assertThat(context.machineId()).isEqualTo(MACHINE_ID);
        assertThat(context.controllerPos()).isEqualTo(controller.getBlockPos());
        assertThat(context.screenText()).isSameAs(runtime.screenText());
        context.screenText().append(ControllerScreenTextScope.CONTROLLER,
                Identifier.parse("example:runtime"), Component.literal("runtime"));
        assertThat(runtime.screenText().snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text()).isEqualTo(Component.literal("runtime")));
    }

    @Test
    void runtimeCleanupSeparatesOperationTextFromControllerText() throws Exception {
        MachineControllerRuntime runtime = runtimeOf(RuntimeTestFixtures.controller(MACHINE_ID));
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                Identifier.parse("example:controller"), Component.literal("controller"));
        runtime.screenText().append(ControllerScreenTextScope.OPERATION,
                Identifier.parse("example:operation"), Component.literal("operation"));

        runtime.clearOperationText();

        assertThat(runtime.screenText().snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.lineId()).isEqualTo(Identifier.parse("example:controller")));
        runtime.clearAllText();
        assertThat(runtime.screenText().snapshot().lines()).isEmpty();
    }

    @Test
    void startupRegistrationWorksBeforeServerExists() {
        assertThat(ServerLifecycleHooks.getCurrentServer()).isNull();
        List<String> calls = new ArrayList<>();
        ControllerScreenTextRegistry.register(MACHINE_ID, received -> calls.add("startup"));

        ControllerScreenTextRegistry.apply(context(MACHINE_ID, new ControllerScreenTextState()));

        assertThat(calls).containsExactly("startup");
    }

    @Test
    void serverMutationsMustRunOnTheCurrentServerThread() throws Exception {
        installCurrentServerForTesting(Thread.currentThread());
        ControllerScreenTextRegistry.Registration startup = ControllerScreenTextRegistry.register(
                MACHINE_ID, received -> { });
        List<Throwable> failures = new ArrayList<>();

        Thread worker = new Thread(() -> {
            failures.add(attempt(() -> ControllerScreenTextRegistry.register(MACHINE_ID, received -> { })));
            failures.add(attempt(startup::unregister));
        });
        worker.start();
        worker.join();

        assertThat(failures).hasSize(2);
        assertThat(failures).allSatisfy(failure ->
                assertThat(failure).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("server thread"));
    }

    @Test
    void testCleanupHookIsNotPublic() throws Exception {
        int modifiers = ControllerScreenTextRegistry.class.getDeclaredMethod("clearForTesting").getModifiers();

        assertThat(Modifier.isPublic(modifiers)).isFalse();
    }

    private static ControllerRuntimeContext context(Identifier machineId, ControllerScreenTextState state) {
        return new ControllerRuntimeContext(machineId, new BlockPos(3, 4, 5), state);
    }

    private static MachineControllerRuntime runtimeOf(MachineControllerBlockEntity controller) throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        field.setAccessible(true);
        return (MachineControllerRuntime) field.get(controller);
    }

    private static Throwable attempt(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static void installCurrentServerForTesting(Thread serverThread) throws Exception {
        TestServer server = allocate(TestServer.class);
        server.serverThread = serverThread;
        Field field = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        field.setAccessible(true);
        field.set(null, server);
    }

    private static void clearCurrentServerForTesting() throws Exception {
        Field field = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        field.setAccessible(true);
        field.set(null, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    /**
     * Minimal server-loop identity used to exercise NeoForge's server-thread check.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class TestServer extends MinecraftServer {
        private Thread serverThread;

        private TestServer() {
            super(null, null, null, null, Optional.empty(), Proxy.NO_PROXY, null, null, null, false);
        }

        @Override
        public Thread getRunningThread() {
            return serverThread;
        }

        @Override
        protected boolean initServer() throws IOException {
            return false;
        }

        @Override
        public LevelBasedPermissionSet operatorUserPermissions() {
            return LevelBasedPermissionSet.ALL;
        }

        @Override
        public LevelBasedPermissionSet getFunctionCompilationPermissions() {
            return LevelBasedPermissionSet.OWNER;
        }

        @Override
        public boolean shouldRconBroadcast() {
            return false;
        }

        @Override
        public boolean isDedicatedServer() {
            return false;
        }

        @Override
        public int getRateLimitPacketsPerSecond() {
            return 0;
        }

        @Override
        public boolean useNativeTransport() {
            return false;
        }

        @Override
        public boolean isPublished() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        @Override
        public boolean isSingleplayerOwner(NameAndId nameAndId) {
            return false;
        }

        @Override
        protected SampleLogger getTickTimeLogger() {
            return null;
        }

        @Override
        public boolean isTickTimeLoggingEnabled() {
            return false;
        }

        @Override
        public int getMaxPlayers() {
            return 1;
        }

        @Override
        public SystemReport fillServerSystemReport(SystemReport report) {
            return report;
        }
    }
}
