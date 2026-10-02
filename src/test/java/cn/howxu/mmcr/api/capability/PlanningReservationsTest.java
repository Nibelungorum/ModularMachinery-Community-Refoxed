package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
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
}
