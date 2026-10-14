package cn.howxu.mmcr.internal.storage;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(ints = {1, 64})
    void extended_item_handler_merges_independently_created_items_with_identical_data(int stackLimit) {
        AtomicInteger changes = new AtomicInteger();
        LongItemStorage storage = new LongItemStorage(1, Long.MAX_VALUE, changes::incrementAndGet);
        ItemStack first = dataItem(stackLimit, 1073741824L);
        ItemStack second = dataItem(stackLimit, 1073741824L);

        assertThat(storage.insertItem(0, first, false).isEmpty()).isTrue();
        assertThat(storage.insertItem(0, second, true).isEmpty()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(1L);
        assertThat(changes).hasValue(1);
        assertThat(storage.insertItem(0, second, false).isEmpty()).isTrue();

        assertThat(storage.amount(0)).isEqualTo(2L);
        assertThat(ItemStack.isSameItemSameComponents(storage.resource(0), second)).isTrue();
        assertThat(changes).hasValue(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 64})
    void extended_item_handler_rejects_different_data_without_changing_stored_items(int stackLimit) {
        LongItemStorage storage = new LongItemStorage(1, Long.MAX_VALUE, null);
        ItemStack stored = dataItem(stackLimit, 1073741824L);
        ItemStack different = dataItem(stackLimit, 1073741823L);
        storage.insertItem(0, stored, false);

        assertThat(storage.insertItem(0, different, true).getCount()).isEqualTo(1);
        assertThat(storage.insertItem(0, different, false).getCount()).isEqualTo(1);
        assertThat(storage.amount(0)).isEqualTo(1L);
        assertThat(ItemStack.isSameItemSameComponents(storage.resource(0), stored)).isTrue();
    }

    @Test
    void ordinary_item_handler_keeps_unstackable_items_separate() {
        LongItemStorage storage = new LongItemStorage(1, 64L, null);
        ItemStack item = dataItem(1, 1073741824L);

        assertThat(storage.insertItem(0, item, false).isEmpty()).isTrue();
        assertThat(storage.insertItem(0, item.copy(), true).getCount()).isEqualTo(1);
        assertThat(storage.insertItem(0, item.copy(), false).getCount()).isEqualTo(1);
        assertThat(storage.amount(0)).isEqualTo(1L);
    }

    private static ItemStack dataItem(int stackLimit, long energy) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        CompoundTag data = new CompoundTag();
        data.putLong("energy", energy);
        stack.set(DataComponents.MAX_STACK_SIZE, stackLimit);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
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
