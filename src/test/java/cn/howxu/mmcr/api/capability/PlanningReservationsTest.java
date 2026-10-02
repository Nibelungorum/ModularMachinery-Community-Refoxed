package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.internal.storage.BulkItemStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningReservationsTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void different_resource_views_cannot_claim_the_same_empty_physical_slot() {
        Object inventory = new Object();
        PlanningReservations reservations = new PlanningReservations();
        assertThat(reservations.reserveNativeInsert(inventory, 0, "item", null, 0L, 64L, 1L)).isTrue();
        assertThat(reservations.reserveNativeInsert(inventory, 0, "chemical", null, 0L, 8_000L, 1L)).isFalse();
        assertThat(reservations.nativeKey(inventory, 0, null)).isEqualTo("item");
    }

    @Test
    void copied_reservations_do_not_consume_the_original_network_key() {
        Object network = new Object();
        PlanningReservations original = new PlanningReservations();
        assertThat(original.reserveNativeExtract(network, "oxygen", "oxygen", "oxygen", 10L, 3L)).isTrue();
        PlanningReservations copy = original.copy();
        assertThat(copy.reserveNativeExtract(network, "oxygen", "oxygen", "oxygen", 10L, 7L)).isTrue();
        assertThat(copy.reserveNativeExtract(network, "oxygen", "oxygen", "oxygen", 10L, 1L)).isFalse();
        assertThat(original.nativeAmount(network, "oxygen", 10L)).isEqualTo(7L);
        assertThat(copy.nativeKey(network, "oxygen", "oxygen")).isNull();
    }

    @Test
    void item_reservations_are_cumulative_for_the_same_native_handler_slot() {
        BulkItemStorage storage = new BulkItemStorage(10L, () -> {});
        PlanningReservations reservations = new PlanningReservations();
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 1);

        assertThat(reservations.reserveItemInsert(storage, 0, iron, 4L, 10L)).isTrue();
        assertThat(reservations.itemAmount(storage, 0)).isEqualTo(4L);
        assertThat(reservations.reserveItemInsert(storage, 0, iron, 7L, 10L)).isFalse();
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void fluid_reservations_preserve_resource_identity_and_do_not_mutate_handler() {
        LongFluidStorage storage = new LongFluidStorage(100L, () -> {});
        FluidStack water = new FluidStack(Fluids.WATER, 20);
        storage.fill(water, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.reserveFluidExtract(storage, 0, water, 10L)).isTrue();
        assertThat(reservations.fluidAmount(storage, 0)).isEqualTo(10L);
        assertThat(reservations.reserveFluidExtract(storage, 0, new FluidStack(Fluids.LAVA, 1), 1L)).isFalse();
        assertThat(storage.amount(0)).isEqualTo(20L);
    }

    @Test
    void reservations_read_complete_long_item_and_fluid_amounts() {
        long amount = (long) Integer.MAX_VALUE + 42L;
        LongItemStorage items = new LongItemStorage(1, Long.MAX_VALUE, () -> {});
        items.setContents(0, new ItemStack(Items.IRON_INGOT), amount);
        LongFluidStorage fluids = new LongFluidStorage(Long.MAX_VALUE, () -> {});
        fluids.setContents(0, new FluidStack(Fluids.WATER, 1), amount);
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.itemAmount(items, 0)).isEqualTo(amount);
        assertThat(reservations.fluidAmount(fluids, 0)).isEqualTo(amount);
        assertThat(reservations.reserveItemExtract(items, 0, new ItemStack(Items.IRON_INGOT), amount)).isTrue();
        assertThat(reservations.reserveFluidExtract(fluids, 0, new FluidStack(Fluids.WATER, 1), amount)).isTrue();
        assertThat(reservations.itemAmount(items, 0)).isZero();
        assertThat(reservations.fluidAmount(fluids, 0)).isZero();
    }

    @Test
    void empty_reads_copies_and_rejected_writes_do_not_allocate_maps() {
        LongItemStorage items = new LongItemStorage(1, 10L, () -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        Object identity = new Object();
        PlanningReservations reservations = new PlanningReservations();

        assertThat(reservations.item(items, 0).isEmpty()).isTrue();
        assertThat(reservations.itemAmount(items, 0)).isZero();
        assertThat(reservations.nativeKey(identity, 0, null)).isNull();
        assertThat(reservations.nativeAmount(identity, 0, 0L)).isZero();
        assertThat(reservations.reserveItemExtract(items, 0, iron, 1L)).isFalse();
        assertThat(reservations.reserveItemInsert(items, 0, iron, 0L, 10L)).isFalse();
        assertThat(reservations.reserveItemInsert(items, 0, iron, 11L, 10L)).isFalse();
        assertThat(reservations.reserveNativeExtract(identity, 0, "iron", null, 0L, 1L)).isFalse();
        assertThat(reservations.reserveNativeInsert(identity, 0, "iron", null, 0L, 10L, 11L)).isFalse();
        PlanningReservations copy = reservations.copy();
        assertThat(reservations).extracting("resources", "outputReservations", "values", "nativeSlots")
                .containsExactly(null, null, null, null);
        assertThat(copy).extracting("resources", "outputReservations", "values", "nativeSlots")
                .containsExactly(null, null, null, null);
        assertThat(copy.reserveItemInsert(items, 0, iron, 4L, 10L)).isTrue();
        assertThat(copy.itemAmount(items, 0)).isEqualTo(4L);
        assertThat(reservations.itemAmount(items, 0)).isZero();
    }

    @Test
    void native_copies_share_physical_slots_but_isolate_reservations_and_identities() {
        Object identity = new String("network");
        Object equalIdentity = new String("network");
        PlanningReservations reservations = new PlanningReservations();
        assertThat(reservations.reserveNativeInsert(identity, 0, "iron", null, 0L, 10L, 4L)).isTrue();
        PlanningReservations copy = reservations.copy();
        assertThat(copy.reserveNativeExtract(identity, 0, "iron", null, 0L, 2L)).isTrue();
        assertThat(copy.reserveNativeInsert(identity, 0, "iron", null, 0L, 10L, 3L)).isTrue();
        assertThat(copy.reserveNativeInsert(identity, 0, "copper", null, 0L, 10L, 1L)).isFalse();
        assertThat(copy.reserveNativeInsert(identity, 1, "copper", null, 0L, 10L, 6L)).isTrue();
        assertThat(copy.reserveNativeInsert(equalIdentity, 0, "copper", null, 0L, 10L, 1L)).isTrue();
        assertThat(copy.nativeAmount(identity, 0, 0L)).isEqualTo(5L);
        assertThat(reservations.nativeAmount(identity, 0, 0L)).isEqualTo(4L);
        assertThat(reservations.nativeKey(identity, 1, null)).isNull();
        assertThat(reservations.nativeAmount(equalIdentity, 0, 0L)).isZero();
        assertThat(reservations).extracting("resources", "outputReservations", "values").containsExactly(null, null, null);
    }

    @Test
    void populated_copies_isolate_all_reservation_kinds() {
        Object identity = new Object();
        LongItemStorage items = new LongItemStorage(1, 10L, () -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        items.setContents(0, iron, 6L);
        LongValueStorage value = new LongValueStorage(10L, 10L, null);
        value.setAmount(6L);
        PlanningReservations reservations = new PlanningReservations();
        assertThat(reservations.reserveItemExtract(items, 0, iron, 2L)).isTrue();
        assertThat(reservations.reserveNativeExtract(identity, 0, "iron", "iron", 6L, 2L)).isTrue();
        assertThat(reservations.reserveOutput(identity, "iron", 2L)).isTrue();
        assertThat(reservations.reserveValue(value, 2L, false)).isTrue();
        PlanningReservations copy = reservations.copy();
        assertThat(copy.reserveItemExtract(items, 0, iron, 4L)).isTrue();
        assertThat(copy.reserveNativeExtract(identity, 0, "iron", "iron", 6L, 4L)).isTrue();
        assertThat(copy.reserveOutput(identity, "iron", 4L)).isTrue();
        assertThat(copy.reserveValue(value, 4L, false)).isTrue();
        assertThat(copy.itemAmount(items, 0)).isZero();
        assertThat(copy.nativeAmount(identity, 0, 6L)).isZero();
        assertThat(copy.outputAvailable(identity, "iron", 10L)).isEqualTo(4L);
        assertThat(copy.valueAvailable(value, false)).isZero();
        assertThat(reservations.itemAmount(items, 0)).isEqualTo(4L);
        assertThat(reservations.nativeAmount(identity, 0, 6L)).isEqualTo(4L);
        assertThat(reservations.outputAvailable(identity, "iron", 10L)).isEqualTo(8L);
        assertThat(reservations.valueAvailable(value, false)).isEqualTo(4L);
        assertThat(items.amount(0)).isEqualTo(6L);
        assertThat(value.amount()).isEqualTo(6L);
    }
}
