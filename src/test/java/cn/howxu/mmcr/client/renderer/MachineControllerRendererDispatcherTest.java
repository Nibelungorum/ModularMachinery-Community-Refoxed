package cn.howxu.mmcr.client.renderer;

import cn.howxu.mmcr.api.publicapi.render.ControllerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Tests failure containment for machine controller renderer dispatch.
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerRendererDispatcherTest {
    @Test
    void rendererFailureIsLoggedAndDoesNotEscapeSubmit() {
        Identifier machine = Identifier.fromNamespaceAndPath("test", "machine");
        ControllerRenderer renderer = (context, poseStack, collector, camera) -> {
            throw new IllegalStateException("test failure");
        };
        MachineControllerRendererDispatcher dispatcher =
                new MachineControllerRendererDispatcher(machine, renderer);

        assertDoesNotThrow(() -> dispatcher.invokeForTesting(
                null, new PoseStack(), null, null));
    }
}
