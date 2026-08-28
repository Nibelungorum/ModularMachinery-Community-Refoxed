package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies KubeJS machine behavior callbacks are retained as startup data.
 * @author howxu <dev@howxu.cn>
 */
class MachineBehaviorBuilderJSTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void create_object_retains_tick_callback() {
        AtomicInteger calls = new AtomicInteger();
        MachineRegistration registration = new MachineBuilderJS(MMCR.id("kubejs_tick_machine"))
                .tickBehavior(builder -> builder.serverTick(context -> calls.incrementAndGet()))
                .createObject();

        assertThat(registration.behavior().kind()).isEqualTo(MachineBehavior.Kind.TICK);
        ((TickBehavior) registration.behavior()).serverTick().accept(null);
        assertThat(calls).hasValue(1);
    }

    @Test
    void builder_rejects_mixing_recipe_and_tick_behaviors() {
        assertThatThrownBy(() -> new MachineBuilderJS(MMCR.id("kubejs_mixed_machine"))
                .recipeBehavior(builder -> builder.idleStart(context -> { }))
                .tickBehavior(builder -> builder.serverTick(context -> { })))
                .isInstanceOf(IllegalStateException.class);
    }
}
