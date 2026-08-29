package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.controller.ControllerRuntimeContext;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope.CONTROLLER;
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
    void append_after_translatable_exposes_relative_ordering_to_kubejs() {
        ControllerScreenTextState state = new ControllerScreenTextState();
        state.append(CONTROLLER, Identifier.parse("example:target"), Component.literal("target"));
        ControllerScreenTextEventJS event = new ControllerScreenTextEventJS(
                new ControllerRuntimeContext(MACHINE_ID, BlockPos.ZERO, state));

        event.appendAfterTranslatable("controller", "example:after", "example:target",
                "example.controller.after", Component.literal("arg"));

        assertThat(state.snapshot().lines())
                .extracting(line -> line.lineId().toString())
                .containsExactly("example:target", "example:after");
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
        assertThatThrownBy(() -> event.appendAfterTranslatable("controller", "example:line", "line", "example:key"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("afterLineId");
        assertThatThrownBy(() -> new MMCRStartupEventJS().registerControllerScreenText(null, ignored -> { }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("machineId");
    }

    @Test
    void server_event_does_not_expose_controller_screen_text_registration() {
        assertThat(MMCRServerEventJS.class.getMethods())
                .extracting(Method::getName)
                .doesNotContain("registerControllerScreenText");
    }

    private static void clearRegistryForTesting() throws Exception {
        Method method = ControllerScreenTextRegistry.class.getDeclaredMethod("clearForTesting");
        method.setAccessible(true);
        method.invoke(null);
    }

    private static void clearCurrentServerForTesting() throws Exception {
        Field field = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        field.setAccessible(true);
        field.set(null, null);
    }

}
