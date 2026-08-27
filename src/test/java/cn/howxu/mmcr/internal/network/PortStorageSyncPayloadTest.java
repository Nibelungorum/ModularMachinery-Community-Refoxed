package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import cn.howxu.mmcr.internal.port.ExtendedFluidHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedItemBusSize;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.registries.BaseMappedRegistry;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
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
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        FluidResource water = FluidResource.of(Fluids.WATER);
        PktPortStorageSyncPayload payload = new PktPortStorageSyncPayload(
                new BlockPos(1, 2, 3), PortKinds.COMBINED_INPUT.id(),
                List.of(new ItemStorageEntry(0, iron, Long.MAX_VALUE - 1L, Long.MAX_VALUE),
                        new ItemStorageEntry(1, ItemResource.EMPTY, 0L, Long.MAX_VALUE)),
                List.of(new FluidStorageEntry(0, water, 9_000_000_000L, Long.MAX_VALUE)));
        RegistryFriendlyByteBuf buffer = buffer();

        PktPortStorageSyncPayload.STREAM_CODEC.encode(buffer, payload);
        PktPortStorageSyncPayload decoded = PktPortStorageSyncPayload.STREAM_CODEC.decode(buffer);

        assertThat(decoded.pos()).isEqualTo(payload.pos());
        assertThat(decoded.kind()).isEqualTo(payload.kind());
        assertThat(decoded.itemEntries()).containsExactlyElementsOf(payload.itemEntries());
        assertThat(decoded.fluidEntries()).containsExactlyElementsOf(payload.fluidEntries());
        assertThat(decoded.itemEntries().getFirst().resource()).isEqualTo(iron);
        assertThat(decoded.fluidEntries().getFirst().resource()).isEqualTo(water);
        assertThat(decoded.fluidEntries().getFirst().amount()).isEqualTo(9_000_000_000L);
    }

    @Test
    void payload_rejects_malformed_kind_negative_values_and_invalid_entry_counts() {
        assertThatThrownBy(() -> new PktPortStorageSyncPayload(BlockPos.ZERO, "not a kind", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemStorageEntry(0, ItemResource.EMPTY, -1L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FluidStorageEntry(0, FluidResource.EMPTY, 1L, 0L))
                .isInstanceOf(IllegalArgumentException.class);

        List<ItemStorageEntry> tooMany = new ArrayList<>();
        for (int slot = 0; slot <= PktPortStorageSyncPayload.MAX_ENTRIES; slot++) {
            tooMany.add(new ItemStorageEntry(slot, ItemResource.EMPTY, 0L, 1L));
        }
        assertThatThrownBy(() -> new PktPortStorageSyncPayload(BlockPos.ZERO,
                PortKinds.EXTENDED_ITEM_INPUT.id(), tooMany, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payload_rejects_out_of_order_entries() {
        assertThatThrownBy(() -> new PktPortStorageSyncPayload(BlockPos.ZERO,
                PortKinds.EXTENDED_ITEM_INPUT.id(),
                List.of(new ItemStorageEntry(1, ItemResource.EMPTY, 0L, 1L),
                        new ItemStorageEntry(0, ItemResource.EMPTY, 0L, 1L)), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payload_rejects_item_slots_outside_the_kind_storage() {
        assertThatThrownBy(() -> new PktPortStorageSyncPayload(BlockPos.ZERO,
                PortKinds.EXTENDED_ITEM_INPUT.id(),
                List.of(new ItemStorageEntry(ExtendedItemBusSize.BASIC.slots(), ItemResource.EMPTY, 0L, 1L)), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktPortStorageSyncPayload(BlockPos.ZERO,
                PortKinds.EXTENDED_FLUID_INPUT.id(), List.of(),
                List.of(new FluidStorageEntry(ExtendedFluidHatchSize.BASIC.slots(), FluidResource.EMPTY, 0L, 1L))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payload_rejects_resource_lists_for_kinds_without_that_capability() {
        ItemStorageEntry item = new ItemStorageEntry(0, ItemResource.EMPTY, 0L, 1L);
        FluidStorageEntry fluid = new FluidStorageEntry(0, FluidResource.EMPTY, 0L, 1L);

        assertThatThrownBy(() -> new PktPortStorageSyncPayload(BlockPos.ZERO,
                PortKinds.EXTENDED_ITEM_INPUT.id(), List.of(), List.of(fluid)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktPortStorageSyncPayload(BlockPos.ZERO,
                PortKinds.EXTENDED_FLUID_INPUT.id(), List.of(item), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PktPortStorageSyncPayload(BlockPos.ZERO,
                PortKinds.ENERGY_INPUT.id(), List.of(item), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),
                new RegistryAccess.ImmutableRegistryAccess(
                        List.of(BuiltInRegistries.ITEM, BuiltInRegistries.FLUID)));
    }
}
