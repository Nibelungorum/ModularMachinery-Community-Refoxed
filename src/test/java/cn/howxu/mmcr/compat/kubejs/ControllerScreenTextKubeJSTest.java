package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.controller.ControllerRuntimeContext;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.SystemReport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.debugchart.SampleLogger;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KubeJS controller screen text registration and runtime bridge tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerScreenTextKubeJSTest {
    private static final Identifier MACHINE_ID = MMCR.id("kubejs_controller_text_machine");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void openPublicRegistration() throws Exception {
        clearRegistryForTesting();
        clearCurrentServerForTesting();
        PublicApiBootstrap.clearForTesting();
        PublicApiBootstrap.begin();
    }

    @AfterEach
    void clearRegistrations() throws Exception {
        clearRegistryForTesting();
        clearCurrentServerForTesting();
        PublicApiBootstrap.clearForTesting();
    }

    @Test
    void startup_registration_reaches_common_registry_and_snapshot() {
        BlockPos controllerPos = new BlockPos(3, 4, 5);
        AtomicReference<Thread> handlerThread = new AtomicReference<>();
        new MMCRStartupEventJS().registerControllerScreenText(MACHINE_ID.toString(), event -> {
            handlerThread.set(Thread.currentThread());
            assertThat(event.machineId()).isEqualTo(MACHINE_ID);
            assertThat(event.controllerPos()).isEqualTo(controllerPos);
            event.append("controller", "example:startup", Component.literal("startup"));
        });

        ControllerScreenTextState state = new ControllerScreenTextState();
        ControllerScreenTextRegistry.apply(new ControllerRuntimeContext(MACHINE_ID, controllerPos, state));

        assertThat(handlerThread).hasValue(Thread.currentThread());
        assertThat(state.snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text()).isEqualTo(Component.literal("startup")));
    }

    @Test
    void append_translatable_preserves_all_kubejs_arguments() {
        ControllerScreenTextState state = new ControllerScreenTextState();
        ControllerScreenTextEventJS event = new ControllerScreenTextEventJS(
                new ControllerRuntimeContext(MACHINE_ID, BlockPos.ZERO, state));
        Object[] arguments = {Component.literal("75%"), 4};

        event.appendTranslatable("operation", "example:progress", "example.controller.progress", arguments);

        assertThat(state.snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text()).isEqualTo(
                        Component.translatable("example.controller.progress", arguments)));
    }

    @Test
    void invalid_kubejs_screen_text_values_have_facing_errors() {
        ControllerScreenTextState state = new ControllerScreenTextState();
        ControllerScreenTextEventJS event = new ControllerScreenTextEventJS(
                new ControllerRuntimeContext(MACHINE_ID, BlockPos.ZERO, state));

        assertThatThrownBy(() -> event.append(null, "example:line", Component.literal("text")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scope");
        assertThatThrownBy(() -> event.append("", "example:line", Component.literal("text")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scope");
        assertThatThrownBy(() -> event.append("controller", null, Component.literal("text")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lineId");
        assertThatThrownBy(() -> event.append("controller", "line", Component.literal("text")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("namespace");
        assertThatThrownBy(() -> event.appendTranslatable("controller", "example:line", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("key");
        assertThatThrownBy(() -> event.appendTranslatable("controller", "example:line", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("key");
        assertThatThrownBy(() -> new MMCRStartupEventJS().registerControllerScreenText(null, ignored -> { }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("machineId");
    }

    @Test
    void server_registration_replaces_only_server_handlers_and_outputs_snapshot() throws Exception {
        TestServer server = installCurrentServerForTesting(Thread.currentThread());
        List<String> calls = new ArrayList<>();
        new MMCRStartupEventJS().registerControllerScreenText(MACHINE_ID.toString(), event -> calls.add("startup"));

        Object firstReload = new Object();
        beginServerScriptReloadForTesting(firstReload, 0);
        new MMCRServerEventJS().registerControllerScreenText(MACHINE_ID.toString(), event -> {
            calls.add("old server");
            event.append("operation", "example:server", Component.literal("old"));
        });
        Plugin.completeServerReloadForTesting(firstReload, 0, () -> { });

        Object secondReload = new Object();
        beginServerScriptReloadForTesting(secondReload, 0);
        new MMCRServerEventJS().registerControllerScreenText(MACHINE_ID.toString(), event -> {
            calls.add("new server");
            event.append("operation", "example:server", Component.literal("new"));
        });
        Plugin.completeServerReloadForTesting(secondReload, 0, () -> { });

        ControllerScreenTextState state = new ControllerScreenTextState();
        ControllerScreenTextRegistry.apply(new ControllerRuntimeContext(MACHINE_ID, BlockPos.ZERO, state));

        assertThat(server.getRunningThread()).isSameAs(Thread.currentThread());
        assertThat(calls).containsExactly("startup", "new server");
        assertThat(state.snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text()).isEqualTo(Component.literal("new")));
    }

    @Test
    void server_registration_requires_reload_window_but_accepts_registration_inside_it() throws Exception {
        installCurrentServerForTesting(Thread.currentThread());
        MMCRServerEventJS event = new MMCRServerEventJS();

        assertThatThrownBy(() -> event.registerControllerScreenText(MACHINE_ID.toString(), ignored -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reload");

        Object reload = new Object();
        beginServerScriptReloadForTesting(reload, 0);
        event.registerControllerScreenText(MACHINE_ID.toString(), text ->
                text.append("controller", "example:window", Component.literal("accepted")));
        Plugin.completeServerReloadForTesting(reload, 0, () -> { });

        ControllerScreenTextState state = new ControllerScreenTextState();
        ControllerScreenTextRegistry.apply(new ControllerRuntimeContext(MACHINE_ID, BlockPos.ZERO, state));

        assertThat(state.snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text()).isEqualTo(Component.literal("accepted")));
    }

    private static void clearRegistryForTesting() throws Exception {
        Method method = ControllerScreenTextRegistry.class.getDeclaredMethod("clearForTesting");
        method.setAccessible(true);
        method.invoke(null);
    }

    private static void beginServerScriptReloadForTesting(Object manager, int errorCount) throws Exception {
        Method method = Plugin.class.getDeclaredMethod("beginServerScriptReload", Object.class, int.class);
        method.setAccessible(true);
        method.invoke(null, manager, errorCount);
    }

    private static TestServer installCurrentServerForTesting(Thread serverThread) throws Exception {
        TestServer server = allocate(TestServer.class);
        server.serverThread = serverThread;
        Field field = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        field.setAccessible(true);
        field.set(null, server);
        return server;
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
     * Minimal server identity used to exercise server-thread registration checks.
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

        @Override public boolean shouldRconBroadcast() { return false; }
        @Override public boolean isDedicatedServer() { return false; }
        @Override public int getRateLimitPacketsPerSecond() { return 0; }
        @Override public boolean useNativeTransport() { return false; }
        @Override public boolean isPublished() { return false; }
        @Override public boolean shouldInformAdmins() { return false; }
        @Override public boolean isSingleplayerOwner(NameAndId nameAndId) { return false; }
        @Override protected SampleLogger getTickTimeLogger() { return null; }
        @Override public boolean isTickTimeLoggingEnabled() { return false; }
        @Override public int getMaxPlayers() { return 1; }
        @Override public SystemReport fillServerSystemReport(SystemReport report) { return report; }
    }
}
