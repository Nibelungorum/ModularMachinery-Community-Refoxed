package cn.howxu.mmcr.client;

import net.neoforged.bus.api.SubscribeEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that the client preview handler uses concrete event subscriptions.
 * @author howxu <dev@howxu.cn>
 */
class MultiblockPreviewClientHandlerTest {
    @Test
    void subscribes_only_to_concrete_event_types() {
        for (Method method : MultiblockPreviewClientHandler.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(SubscribeEvent.class)) continue;

            assertThat(method.getParameterCount()).isEqualTo(1);
            assertThat(Modifier.isAbstract(method.getParameterTypes()[0].getModifiers())).isFalse();
        }
    }
}
