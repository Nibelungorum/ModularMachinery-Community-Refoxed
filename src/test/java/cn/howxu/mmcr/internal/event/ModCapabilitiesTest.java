package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.registry.PortKinds;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModCapabilitiesTest {

    @Test
    void native_ports_are_all_registered_for_pipe_capabilities() {
        Set<String> registered = ModCapabilities.nativeCapabilityPortIds();

        assertThat(registered).containsAll(PortKinds.all().stream()
                .filter(kind -> kind.itemBusSize().isPresent()
                        || kind.fluidHatchSize().isPresent()
                        || kind.energyHatchSize().isPresent())
                .map(kind -> kind.id())
                .toList());
    }

    @Test
    void factory_controller_is_registered_for_pipe_item_capability() {
        assertThat(ModCapabilities.nativeCapabilityBlockEntityIds()).contains("factory_controller");
        assertThat(ModCapabilities.nativeCapabilityBlockEntityIds()).doesNotContain("factory_scheduler");
    }

}
