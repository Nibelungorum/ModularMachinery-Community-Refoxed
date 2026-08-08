package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PktMachineAppearancePayloadTest {

    @Test
    void payloadRetainsCompleteAppearanceSnapshot() {
        Identifier firstId = Identifier.parse("mmcr:first");
        Identifier secondId = Identifier.parse("mmcr:second");
        Map<Identifier, MachineAppearanceSpec> specs = Map.of(
                firstId, MachineAppearanceSpec.fromBasicBlock(Identifier.parse("kubejs:steel_casing")),
                secondId, new MachineAppearanceSpec(
                        Identifier.parse("mmcr:basic_casing"),
                        Identifier.parse("mmcr:block/controller_base"),
                        Identifier.parse("mmcr:block/port_base")));

        PktMachineAppearancePayload payload = new PktMachineAppearancePayload(specs);

        assertThat(payload.specs()).isEqualTo(specs);
    }

    @Test
    void rejects_more_than_maximum_specs() {
        Map<Identifier, MachineAppearanceSpec> specs = new HashMap<>();
        for (int i = 0; i < 4097; i++) {
            specs.put(Identifier.parse("mmcr:machine_" + i), MachineAppearanceSpec.defaults());
        }

        assertThatThrownBy(() -> new PktMachineAppearancePayload(specs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Too many machine appearance specs");
    }
}
