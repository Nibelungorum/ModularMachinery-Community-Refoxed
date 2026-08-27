package cn.howxu.mmcr.api.publicapi.controller;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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

    @AfterEach
    void cleanup() {
        ControllerScreenTextRegistry.clearForTesting();
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
    void serverScriptReloadReplacesOnlyServerScriptRegistrations() {
        ControllerScreenTextState state = new ControllerScreenTextState();
        List<String> calls = new ArrayList<>();
        ControllerRuntimeContext context = context(MACHINE_ID, state);
        ControllerScreenTextRegistry.register(MACHINE_ID, received -> calls.add("startup"));
        ControllerScreenTextRegistry.beginServerScriptReload();
        ControllerScreenTextRegistry.registerServerScript(MACHINE_ID, received -> calls.add("old script"));
        ControllerScreenTextRegistry.endServerScriptReload();

        ControllerScreenTextRegistry.beginServerScriptReload();
        ControllerScreenTextRegistry.registerServerScript(MACHINE_ID, received -> calls.add("new script"));
        ControllerScreenTextRegistry.endServerScriptReload();
        ControllerScreenTextRegistry.apply(context);

        assertThat(calls).containsExactly("startup", "new script");
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

    private static ControllerRuntimeContext context(Identifier machineId, ControllerScreenTextState state) {
        return new ControllerRuntimeContext(machineId, new BlockPos(3, 4, 5), state);
    }

    private static MachineControllerRuntime runtimeOf(MachineControllerBlockEntity controller) throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        field.setAccessible(true);
        return (MachineControllerRuntime) field.get(controller);
    }
}
