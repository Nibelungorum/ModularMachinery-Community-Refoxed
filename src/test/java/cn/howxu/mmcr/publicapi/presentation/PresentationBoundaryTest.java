package cn.howxu.mmcr.publicapi.presentation;

import cn.howxu.mmcr.api.controller.ControllerRuntimeContext;
import cn.howxu.mmcr.api.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.api.facade.presentation.PresentationAdapters;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.internal.runtime.JadeTextState;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Public text edits must use the live core ordering and registration.
 * @author howxu <dev@howxu.cn>
 */
class PresentationBoundaryTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    private static final ResourceLocation FIRST = ResourceLocation.parse("test:first");
    private static final ResourceLocation SECOND = ResourceLocation.parse("test:second");
    private static final ResourceLocation LAST = ResourceLocation.parse("test:last");

    @Test
    void controller_registration_forwards_relative_order_replace_scope_and_unregister() {
        PublicApiBootstrap.clearForTesting();
        PublicApiBootstrap.begin();
        var state = new ControllerScreenTextState();
        ResourceLocation machine = ResourceLocation.parse("test:public_text_boundary");
        var registration = ControllerTexts.register(machine, context -> {
            assertEquals(machine, context.machineId());
            assertEquals(BlockPos.ZERO, context.controllerPos());
            var text = context.screenText();
            text.append(TextScope.CONTROLLER, FIRST, Component.translatable("test.first"));
            text.append(TextScope.CONTROLLER, LAST, Component.translatable("test.last"));
            text.appendAfter(TextScope.CONTROLLER, SECOND, FIRST, Component.translatable("test.second"));
            text.replace(FIRST, Component.translatable("test.replaced"));
        });
        try {
            ControllerScreenTextRegistry.apply(new ControllerRuntimeContext(machine, BlockPos.ZERO, state));
            assertEquals(List.of(FIRST, SECOND, LAST), state.snapshot().lines().stream().map(v -> v.lineId()).toList());
            assertEquals(Component.translatable("test.first"), state.snapshot().lines().getFirst().text());
            state.flushReplacements();
            assertEquals(Component.translatable("test.replaced"), state.snapshot().lines().getFirst().text());
            ControllerText text = PresentationAdapters.wrap(state);
            text.remove(TextScope.CONTROLLER, LAST);
            text.appendAfter(TextScope.OPERATION, LAST, FIRST, Component.translatable("test.cross_scope"));
            assertEquals(List.of(FIRST, SECOND), state.snapshot().lines().stream().map(v -> v.lineId()).toList());
            text.append(TextScope.OPERATION, LAST, Component.translatable("test.operation"));
            text.clear(TextScope.OPERATION);
            assertEquals(List.of(FIRST, SECOND), state.snapshot().lines().stream().map(v -> v.lineId()).toList());
            registration.unregister();
            registration.unregister();
            text.clear(TextScope.CONTROLLER);
            ControllerScreenTextRegistry.apply(new ControllerRuntimeContext(machine, BlockPos.ZERO, state));
            assertTrue(state.snapshot().lines().isEmpty());
        } finally {
            registration.unregister();
            PublicApiBootstrap.clearForTesting();
        }
    }

    @Test
    void jade_handle_forwards_all_operations_to_the_same_state() {
        var state = new JadeTextState();
        JadeText text = PresentationAdapters.wrap(state);
        text.append(FIRST, Component.translatable("test.first"));
        text.append(LAST, Component.translatable("test.last"));
        text.appendAfter(SECOND, FIRST, Component.translatable("test.second"));
        text.replace(SECOND, Component.translatable("test.replaced"));
        assertEquals(List.of(FIRST, SECOND, LAST), state.snapshot().lines().stream().map(v -> v.lineId()).toList());
        assertEquals(Component.translatable("test.replaced"), state.snapshot().lines().get(1).text());
        text.remove(FIRST);
        assertEquals(List.of(SECOND, LAST), state.snapshot().lines().stream().map(v -> v.lineId()).toList());
        text.clear();
        assertTrue(state.snapshot().lines().isEmpty());
    }
}
