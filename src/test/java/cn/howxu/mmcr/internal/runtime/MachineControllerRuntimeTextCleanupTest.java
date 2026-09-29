package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies runtime text cleanup when a controller structure is invalidated.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerRuntimeTextCleanupTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void resetJadeAvailability() {
        JadeTextSupport.resetForTesting();
    }

    @Test
    void clearAllTextClearsCustomJadeText() throws Exception {
        JadeTextSupport.enable();
        MachineControllerRuntime runtime = runtimeOf(RuntimeTestFixtures.controller(MMCR.id("test_cube")));
        runtime.behaviorContext().jadeText().append(ResourceLocation.parse("example:jade"), Component.literal("jade"));

        runtime.clearAllText();

        assertThat(runtime.jadeTextSnapshot().lines()).isEmpty();
    }

    @Test
    void structureInvalidationClearsCustomText() throws Exception {
        JadeTextSupport.enable();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        controller.setFormed(true);
        runtime.behaviorContext().jadeText().append(ResourceLocation.parse("example:jade"), Component.literal("jade"));
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                ResourceLocation.parse("example:controller"), Component.literal("controller"));

        controller.setFormed(false);

        assertThat(runtime.jadeTextSnapshot().lines()).isEmpty();
        assertThat(runtime.screenText().snapshot().lines()).isEmpty();
    }

    private static MachineControllerRuntime runtimeOf(MachineControllerBlockEntity controller) throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        field.setAccessible(true);
        return (MachineControllerRuntime) field.get(controller);
    }
}
