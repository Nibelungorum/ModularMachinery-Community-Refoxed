package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicMachineTest {

    @Test
    void dynamic_machine_preserves_factory_thread_specs() {
        FactoryThreadSpec thread = new FactoryThreadSpec("smelting", List.of(MMCR.id("iron_recipe")));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("factory_threads_machine"),
                "Factory Threads",
                new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(MMCR.id("factory_threads_machine")),
                MachineAppearanceSpec.defaults(),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                Map.of(),
                16,
                true,
                true,
                4,
                List.of(thread));

        assertThat(machine.factoryThreads()).containsExactly(thread);
    }

    @Test
    void compatibilityConstructorsDefaultParallelAndFactoryCapabilities() {
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("parallel_defaults"),
                "Parallel Defaults",
                new BlockArray(Map.of()));

        assertThat(machine.maxParallelism()).isEqualTo(1);
        assertThat(machine.parallelizable()).isFalse();
        assertThat(machine.hasFactory()).isFalse();
        assertThat(machine.factoryThreadLimit()).isEqualTo(1);
    }

    @Test
    void explicitParallelAndFactoryCapabilitiesAreClamped() {
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("parallel_machine"),
                "Parallel Machine",
                new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(MMCR.id("parallel_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                0,
                true,
                true,
                0);

        assertThat(machine.maxParallelism()).isEqualTo(1);
        assertThat(machine.parallelizable()).isTrue();
        assertThat(machine.hasFactory()).isTrue();
        assertThat(machine.factoryThreadLimit()).isEqualTo(1);
    }
}
