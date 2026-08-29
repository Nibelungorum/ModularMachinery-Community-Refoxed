package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.internal.capability.SmartInterfaceCapability;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies named smart-interface values exposed through the machine IO view.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineIoViewTest {
    @Test
    void smart_interface_values_are_read_once_from_shared_input_and_output_capabilities() {
        FloatValueStorage storage = new FloatValueStorage();
        storage.set("mode", 2.5F);
        storage.set("enabled", 1F);
        MachineCapability input = new SmartInterfaceCapability(storage, IOType.INPUT);
        MachineCapability output = new SmartInterfaceCapability(storage, IOType.OUTPUT);
        MachineIoView view = new MachineIoView(new CapabilitySnapshot(List.of(input, output)));

        assertThat(view.smartInterfaceValue("mode")).contains(2.5F);
        assertThat(view.smartInterfaceValue("missing")).isEmpty();
        assertThat(view.smartInterfaceValue(null)).isEmpty();
        assertThat(view.smartInterfaceValues())
                .containsExactlyInAnyOrderEntriesOf(Map.of("mode", 2.5F, "enabled", 1F));
        assertThatThrownBy(() -> view.smartInterfaceValues().put("new", 3F))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
