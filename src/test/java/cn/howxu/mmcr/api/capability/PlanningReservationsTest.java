package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.internal.storage.BulkItemStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningReservationsTest {
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
}
