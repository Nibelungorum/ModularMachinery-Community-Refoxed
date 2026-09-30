package cn.howxu.mmcr.internal.storage;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LongResourceStorageTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void value_storage_simulation_preserves_amount_and_execution_reports_change() {
        AtomicInteger changes = new AtomicInteger();
        LongValueStorage storage = new LongValueStorage(100L, 40L, changes::incrementAndGet);

        assertThat(storage.insert(80L, true)).isEqualTo(40L);
        assertThat(storage.amount()).isZero();
        assertThat(storage.insert(80L, false)).isEqualTo(40L);

        assertThat(storage.amount()).isEqualTo(40L);
        assertThat(changes).hasValue(1);
    }

    @Test
    void item_handler_simulates_then_executes_with_normalized_identity() {
        BulkItemStorage storage = new BulkItemStorage(100L, () -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 30);

        assertThat(storage.insertItem(0, iron, true).isEmpty()).isTrue();
        assertThat(storage.amount(0)).isZero();
        assertThat(storage.insertItem(0, iron, false).isEmpty()).isTrue();

        assertThat(ItemStack.isSameItemSameComponents(storage.resource(0), iron)).isTrue();
        assertThat(storage.resource(0).getCount()).isEqualTo(1);
        ItemStack projected = storage.getStackInSlot(0);
        assertThat(ItemStack.isSameItemSameComponents(projected, iron)).isTrue();
        assertThat(projected.getCount()).isEqualTo(iron.getCount());
        ItemStack rejected = storage.insertItem(0, new ItemStack(Items.GOLD_INGOT, 1), true);
        assertThat(ItemStack.isSameItemSameComponents(rejected, new ItemStack(Items.GOLD_INGOT))).isTrue();
        assertThat(rejected.getCount()).isEqualTo(1);
    }

    @Test
    void item_handler_preserves_long_backing_amount_while_projecting_native_stack_size() {
        long amount = (long) Integer.MAX_VALUE + 10L;
        BulkItemStorage storage = new BulkItemStorage(amount + 1L, () -> {});

        assertThat(storage.forceInsert(new ItemStack(Items.IRON_INGOT, 1), amount, true)).isEqualTo(amount);
        assertThat(storage.amount(0)).isZero();
        assertThat(storage.forceInsert(new ItemStack(Items.IRON_INGOT, 1), amount, false)).isEqualTo(amount);

        assertThat(storage.amount(0)).isEqualTo(amount);
        assertThat(storage.getStackInSlot(0).getCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(storage.forceExtract(0, amount, false)).isEqualTo(amount);
        assertThat(storage.resource(0).isEmpty()).isTrue();
    }

    @Test
    void fluid_handler_simulates_then_executes_and_clears_identity_after_drain() {
        AtomicInteger changes = new AtomicInteger();
        LongFluidStorage storage = new LongFluidStorage(100L, changes::incrementAndGet);
        FluidStack water = new FluidStack(Fluids.WATER, 40);

        assertThat(storage.fill(water, IFluidHandler.FluidAction.SIMULATE)).isEqualTo(40);
        assertThat(storage.amount(0)).isZero();
        assertThat(storage.fill(water, IFluidHandler.FluidAction.EXECUTE)).isEqualTo(40);
        assertThat(FluidStack.isSameFluidSameComponents(storage.resource(0), water)).isTrue();
        assertThat(storage.resource(0).getAmount()).isEqualTo(1);
        FluidStack simulated = storage.drain(water, IFluidHandler.FluidAction.SIMULATE);
        assertThat(FluidStack.isSameFluidSameComponents(simulated, water)).isTrue();
        assertThat(simulated.getAmount()).isEqualTo(water.getAmount());
        assertThat(storage.amount(0)).isEqualTo(40L);
        FluidStack drained = storage.drain(water, IFluidHandler.FluidAction.EXECUTE);
        assertThat(FluidStack.isSameFluidSameComponents(drained, water)).isTrue();
        assertThat(drained.getAmount()).isEqualTo(water.getAmount());

        assertThat(storage.amount(0)).isZero();
        assertThat(storage.resource(0).isEmpty()).isTrue();
        assertThat(changes).hasValue(2);
    }

    @Test
    void fluid_handler_retains_long_backing_amount() {
        long amount = (long) Integer.MAX_VALUE + 10L;
        LongFluidStorage storage = new LongFluidStorage(amount + 1L, () -> {});
        FluidStack water = new FluidStack(Fluids.WATER, 1);

        assertThat(storage.forceInsert(0, water, amount, false)).isEqualTo(amount);
        assertThat(storage.getAmountAsLong()).isEqualTo(amount);
        assertThat(storage.getFluidInTank(0).getAmount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void energy_storage_simulates_then_executes_with_long_accounting() {
        AtomicInteger changes = new AtomicInteger();
        LongEnergyStorage storage = new LongEnergyStorage(Long.MAX_VALUE, Long.MAX_VALUE, changes::incrementAndGet);
        long amount = (long) Integer.MAX_VALUE + 10L;

        assertThat(storage.insertLong(amount, true)).isEqualTo(amount);
        assertThat(storage.getAmountAsLong()).isZero();
        assertThat(storage.insertLong(amount, false)).isEqualTo(amount);
        assertThat(storage.getAmountAsLong()).isEqualTo(amount);
        assertThat(storage.extractLong(amount, true)).isEqualTo(amount);
        assertThat(storage.getAmountAsLong()).isEqualTo(amount);
        assertThat(storage.extractLong(amount, false)).isEqualTo(amount);

        assertThat(storage.getAmountAsLong()).isZero();
        assertThat(changes).hasValue(2);
    }
}
