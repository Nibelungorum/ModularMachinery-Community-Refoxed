package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PktControllerSpecsPayloadTest {

    @Test
    void payloadRetainsCompleteSpecSnapshot() {
        ResourceLocation firstId = ResourceLocation.parse("mmcr:first");
        ResourceLocation secondId = ResourceLocation.parse("mmcr:second");
        Map<ResourceLocation, MachineControllerSpec> specs = Map.of(
                firstId, testSpec(firstId),
                secondId, testSpec(secondId));

        PktControllerSpecsPayload payload = new PktControllerSpecsPayload(specs);

        assertThat(payload.specs()).isEqualTo(specs);
    }

    private static MachineControllerSpec testSpec(ResourceLocation machineId) {
        return new MachineControllerSpec(
                ResourceLocation.fromNamespaceAndPath(machineId.getNamespace(), machineId.getPath() + "_controller"),
                ResourceLocation.parse("mmcr:block/front"),
                ResourceLocation.parse("mmcr:block/side"),
                ResourceLocation.parse("mmcr:block/top"),
                ResourceLocation.parse("mmcr:block/bottom"),
                true,
                false,
                true);
    }
}
