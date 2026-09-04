package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderer;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests the public controller renderer registration event.
 * @author howxu <dev@howxu.cn>
 */
class MMCRMachineRendersEventTest {
    @Test
    void acceptsOneRendererPerKnownMachine() {
        Identifier machine = Identifier.fromNamespaceAndPath("test", "machine");
        ControllerRenderer renderer = (context, poseStack, collector, camera) -> { };
        MMCRMachineRendersEvent event = new MMCRMachineRendersEvent(List.of(machine));

        event.register(machine, renderer);

        assertSame(renderer, event.renderers().get(machine));
        assertThrows(UnsupportedOperationException.class, () -> event.renderers().clear());
    }

    @Test
    void rejectsUnknownAndDuplicateMachinesAndFrozenMutation() {
        Identifier known = Identifier.fromNamespaceAndPath("test", "known");
        Identifier unknown = Identifier.fromNamespaceAndPath("test", "unknown");
        ControllerRenderer renderer = (context, poseStack, collector, camera) -> { };
        MMCRMachineRendersEvent event = new MMCRMachineRendersEvent(List.of(known));
        event.register(known, renderer);

        assertThrows(ApiRegistrationException.class, () -> event.register(unknown, renderer));
        assertThrows(ApiRegistrationException.class, () -> event.register(known, renderer));
        event.freeze();
        assertThrows(IllegalStateException.class, () -> event.register(known, renderer));
    }
}
