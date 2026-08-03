package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nibelungorum.DefaultMachines;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerBlockEntityTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineRegistry.clearForTesting();
    }

    @Test
    void bind_default_machine_uses_owning_machine_id() {
        DefaultMachines.ensureRegistered();
        var be = controllerBlockEntityWithoutRunningMinecraftConstructor();

        be.bindDefaultMachine(MMCR.id("blast_furnace"));

        assertThat(be.getMachine()).isSameAs(MachineRegistry.getMachine(MMCR.id("blast_furnace")));
    }

    private static MachineControllerBlockEntity controllerBlockEntityWithoutRunningMinecraftConstructor() {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            return (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate MachineControllerBlockEntity for binding test", e);
        }
    }
}
