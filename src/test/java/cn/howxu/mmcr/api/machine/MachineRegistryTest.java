package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineRegistryTest {

    @AfterEach
    void cleanup() {
        MachineRegistry.clearForTesting();
    }

    @Test
    void register_then_get() {
        var machine = new DynamicMachine(
                Identifier.fromNamespaceAndPath("mmcr", "test"), "Test", new BlockArray(Map.of()));

        MachineRegistry.register(machine);

        assertThat(MachineRegistry.getMachine(machine.registryName())).isEqualTo(machine);
    }

    @Test
    void get_unknown_returns_null() {
        assertThat(MachineRegistry.getMachine(
                Identifier.fromNamespaceAndPath("mmcr", "missing"))).isNull();
    }

    @Test
    void duplicate_register_throws() {
        var m1 = new DynamicMachine(Identifier.fromNamespaceAndPath("mmcr", "dup"), "X", new BlockArray(Map.of()));
        var m2 = new DynamicMachine(Identifier.fromNamespaceAndPath("mmcr", "dup"), "Y", new BlockArray(Map.of()));

        MachineRegistry.register(m1);

        assertThatThrownBy(() -> MachineRegistry.register(m2)).isInstanceOf(IllegalStateException.class);
    }
}
