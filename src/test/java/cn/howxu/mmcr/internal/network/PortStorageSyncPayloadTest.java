package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.capability.sync.CapabilitySyncEntry;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.registries.BaseMappedRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the typed, server-authoritative port storage payload boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class PortStorageSyncPayloadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        Method setSync = BaseMappedRegistry.class.getDeclaredMethod("setSync", boolean.class);
        setSync.setAccessible(true);
        setSync.invoke(BuiltInRegistries.ITEM, true);
        setSync.invoke(BuiltInRegistries.FLUID, true);
    }

    @Test
    void payload_codec_round_trips_position_kind_order_resource_identity_and_long_values() {
        PktPortStorageSyncPayload payload = new PktPortStorageSyncPayload(
                new BlockPos(1, 2, 3), PortKinds.COMBINED_INPUT.id(),
                List.of(new CapabilitySyncEntry(cn.howxu.mmcr.MMCR.id("item"), 0, new byte[] {1, 2, 3}),
                        new CapabilitySyncEntry(cn.howxu.mmcr.MMCR.id("fluid"), 1, new byte[] {4, 5})));
        RegistryFriendlyByteBuf buffer = buffer();

        PktPortStorageSyncPayload.STREAM_CODEC.encode(buffer, payload);
        PktPortStorageSyncPayload decoded = PktPortStorageSyncPayload.STREAM_CODEC.decode(buffer);

        assertThat(decoded.pos()).isEqualTo(payload.pos());
        assertThat(decoded.kind()).isEqualTo(payload.kind());
        assertThat(decoded.entries()).hasSize(2);
        assertThat(decoded.entries().getFirst().typeId()).isEqualTo(payload.entries().getFirst().typeId());
        assertThat(decoded.entries().getFirst().payload()).containsExactly(1, 2, 3);
        assertThat(decoded.entries()).extracting(CapabilitySyncEntry::capabilityIndex).containsExactly(0, 1);
    }

    @Test
    void payload_rejects_malformed_kind_negative_values_and_invalid_entry_counts() {
        assertThatThrownBy(() -> new PktPortStorageSyncPayload(BlockPos.ZERO, "not a kind", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapabilitySyncEntry(cn.howxu.mmcr.MMCR.id("item"), -1, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);

        List<CapabilitySyncEntry> tooMany = new ArrayList<>();
        for (int slot = 0; slot <= PktPortStorageSyncPayload.MAX_ENTRIES; slot++) {
            tooMany.add(new CapabilitySyncEntry(cn.howxu.mmcr.MMCR.id("item"), slot, new byte[0]));
        }
        assertThatThrownBy(() -> new PktPortStorageSyncPayload(BlockPos.ZERO,
                PortKinds.EXTENDED_ITEM_INPUT.id(), tooMany))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payload_rejects_total_entry_bytes_above_the_packet_budget() {
        List<CapabilitySyncEntry> entries = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            entries.add(new CapabilitySyncEntry(cn.howxu.mmcr.MMCR.id("item"), index,
                    new byte[CapabilitySyncEntry.MAX_PAYLOAD_BYTES]));
        }

        assertThatThrownBy(() -> new PktPortStorageSyncPayload(BlockPos.ZERO,
                PortKinds.EXTENDED_ITEM_INPUT.id(), entries)).isInstanceOf(IllegalArgumentException.class);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),
                new RegistryAccess.ImmutableRegistryAccess(
                        List.of(BuiltInRegistries.ITEM, BuiltInRegistries.FLUID)));
    }
}
