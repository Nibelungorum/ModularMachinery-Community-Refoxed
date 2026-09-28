package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests the public controller renderer registration event.
 * @author howxu <dev@howxu.cn>
 */
class MMCRMachineRendersEventTest {
    @Test
    void acceptsOneRendererPerKnownMachine() {
        ResourceLocation machine = ResourceLocation.fromNamespaceAndPath("test", "machine");
        ControllerRenderer renderer = (context, poseStack, buffers, light, overlay) -> { };
        MMCRMachineRendersEvent event = new MMCRMachineRendersEvent(List.of(machine));

        event.register(machine, renderer);

        assertSame(renderer, event.renderers().get(machine));
        assertThrows(UnsupportedOperationException.class, () -> event.renderers().clear());
    }

    @Test
    void rejectsNullAndDuplicateMachineIdsDuringConstruction() {
        ResourceLocation machine = ResourceLocation.fromNamespaceAndPath("test", "machine");

        assertThrows(ApiRegistrationException.class,
                () -> new MMCRMachineRendersEvent(Arrays.asList(machine, machine)));
        assertThrows(ApiRegistrationException.class,
                () -> new MMCRMachineRendersEvent(Arrays.asList(machine, null)));
    }

    @Test
    void rejectsUnknownAndDuplicateMachinesAndFrozenMutation() {
        ResourceLocation known = ResourceLocation.fromNamespaceAndPath("test", "known");
        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath("test", "unknown");
        ControllerRenderer renderer = (context, poseStack, buffers, light, overlay) -> { };
        MMCRMachineRendersEvent event = new MMCRMachineRendersEvent(List.of(known));
        event.register(known, renderer);

        assertThrows(ApiRegistrationException.class, () -> event.register(unknown, renderer));
        assertThrows(ApiRegistrationException.class, () -> event.register(known, renderer));
        event.freeze();
        assertThrows(IllegalStateException.class, () -> event.register(known, renderer));
    }
}
