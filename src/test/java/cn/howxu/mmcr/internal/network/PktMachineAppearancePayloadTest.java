package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PktMachineAppearancePayloadTest {

    @Test
    void payloadRetainsCompleteAppearanceSnapshot() {
        ResourceLocation firstId = ResourceLocation.parse("mmcr:first");
        ResourceLocation secondId = ResourceLocation.parse("mmcr:second");
        Map<ResourceLocation, MachineAppearanceSpec> specs = Map.of(
                firstId, MachineAppearanceSpec.fromBasicBlock(ResourceLocation.parse("kubejs:steel_casing")),
                secondId, new MachineAppearanceSpec(
                        ResourceLocation.parse("mmcr:basic_casing"),
                        ResourceLocation.parse("mmcr:block/controller_base"),
                        ResourceLocation.parse("mmcr:block/port_base")));

        PktMachineAppearancePayload payload = new PktMachineAppearancePayload(specs);

        assertThat(payload.specs()).isEqualTo(specs);
    }

    @Test
    void rejects_more_than_maximum_specs() {
        Map<ResourceLocation, MachineAppearanceSpec> specs = new HashMap<>();
        for (int i = 0; i < 4097; i++) {
            specs.put(ResourceLocation.parse("mmcr:machine_" + i), MachineAppearanceSpec.defaults());
        }

        assertThatThrownBy(() -> new PktMachineAppearancePayload(specs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Too many machine appearance specs");
    }

    @Test
    void rejects_null_snapshot() {
        assertThatThrownBy(() -> new PktMachineAppearancePayload(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("specs null");
    }

    @Test
    void stream_codec_preserves_controller_state_overlays() {
        ResourceLocation machineId = ResourceLocation.parse("mmcr:stateful");
        PktMachineAppearancePayload payload = new PktMachineAppearancePayload(Map.of(machineId,
                new MachineAppearanceSpec(ResourceLocation.parse("mmcr:basic_casing"), null, null,
                        ResourceLocation.parse("mmcr:block/custom_idle"), ResourceLocation.parse("mmcr:block/custom_active"))));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);

        PktMachineAppearancePayload.STREAM_CODEC.encode(buffer, payload);

        assertThat(PktMachineAppearancePayload.STREAM_CODEC.decode(buffer)).isEqualTo(payload);
        buffer.release();
    }
}
