package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.publicapi.machine.MachineIoView;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies built-in capability presentation uses the typed facet contract.
 *
 * @author howxu <dev@howxu.cn>
 */
class BuiltinCapabilityPresentationTest {
    @Test
    void energy_capability_exposes_display_through_presentation_facet() {
        LongValueStorage storage = new LongValueStorage(1_000L, 1_000L, null);
        storage.setAmount(250L);
        EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);

        List<CapabilityDisplay> displays = new MachineIoView(new CapabilitySnapshot(List.of(capability))).displays();

        assertThat(displays).containsExactly(new CapabilityDisplay("energy", "250", "FE", java.util.Optional.empty()));
    }
}
