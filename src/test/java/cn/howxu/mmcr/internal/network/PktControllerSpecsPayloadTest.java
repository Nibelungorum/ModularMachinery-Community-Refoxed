package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PktControllerSpecsPayloadTest {

    @Test
    void payloadRetainsCompleteSpecSnapshot() {
        Identifier firstId = Identifier.parse("mmcr:first");
        Identifier secondId = Identifier.parse("mmcr:second");
        Map<Identifier, MachineControllerSpec> specs = Map.of(
                firstId, testSpec(firstId),
                secondId, testSpec(secondId));

        PktControllerSpecsPayload payload = new PktControllerSpecsPayload(specs);

        assertThat(payload.specs()).isEqualTo(specs);
    }

    private static MachineControllerSpec testSpec(Identifier machineId) {
        return new MachineControllerSpec(
                Identifier.fromNamespaceAndPath(machineId.getNamespace(), machineId.getPath() + "_controller"),
                Identifier.parse("mmcr:block/front"),
                Identifier.parse("mmcr:block/side"),
                Identifier.parse("mmcr:block/top"),
                Identifier.parse("mmcr:block/bottom"),
                true,
                false,
                true);
    }
}
