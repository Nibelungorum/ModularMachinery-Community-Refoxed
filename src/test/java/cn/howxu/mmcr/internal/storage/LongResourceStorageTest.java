package cn.howxu.mmcr.internal.storage;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LongResourceStorageTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void value_storage_clamps_to_capacity_and_transfer_limit() {
        LongValueStorage storage = new LongValueStorage(50L, 100L, () -> {});

        assertThat(storage.insert(100L, false)).isEqualTo(50L);
        assertThat(storage.amount()).isEqualTo(50L);

        LongValueStorage limited = new LongValueStorage(100L, 30L, () -> {});
        assertThat(limited.insert(100L, false)).isEqualTo(30L);
        assertThat(limited.amount()).isEqualTo(30L);
    }

    @Test
    void value_storage_clamps_amount_to_capacity() {
        LongValueStorage storage = new LongValueStorage(100L, 100L, () -> {});

        storage.setAmount(150L);

        assertThat(storage.amount()).isEqualTo(100L);
    }

    @Test
    void transaction_rollback_restores_fluid_amount_and_resource() {
        LongFluidStorage storage = new LongFluidStorage(100L, () -> {});
        FluidResource water = FluidResource.of(Fluids.WATER);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, water, 40L, transaction)).isEqualTo(40L);
            assertThat(storage.amount(0)).isEqualTo(40L);
        }

        assertThat(storage.amount(0)).isZero();
        assertThat(storage.resource(0)).isEqualTo(FluidResource.EMPTY);
    }

    @Test
    void transaction_commit_keeps_fluid_amount_and_resource() {
        LongFluidStorage storage = new LongFluidStorage(100L, () -> {});
        FluidResource water = FluidResource.of(Fluids.WATER);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, water, 40L, transaction)).isEqualTo(40L);
            transaction.commit();
        }

        assertThat(storage.amount(0)).isEqualTo(40L);
        assertThat(storage.resource(0)).isEqualTo(water);
    }

    @Test
    void extracting_the_last_fluid_clears_the_resource() {
        LongFluidStorage storage = new LongFluidStorage(100L, () -> {});
        FluidResource water = FluidResource.of(Fluids.WATER);
        storage.setContents(water, 40L);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.extract(0, water, 40L, transaction)).isEqualTo(40L);
            transaction.commit();
        }
        assertThat(storage.amount(0)).isZero();
        assertThat(storage.resource(0)).isEqualTo(FluidResource.EMPTY);
    }

    @Test
    void fluid_matching_uses_resource_identity_and_energy_has_no_resource_filter() {
        LongFluidStorage fluids = new LongFluidStorage(100L, () -> {});
        FluidResource water = FluidResource.of(Fluids.WATER);
        fluids.setContents(water, 10L);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(fluids.insert(0, FluidResource.of(Fluids.WATER), 1L, transaction)).isEqualTo(1L);
            transaction.commit();
        }
        assertThat(fluids.amount(0)).isEqualTo(11L);

        EnergyHandler energy = new LongEnergyStorage(100L, 20L, () -> {});
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(energy.insert(100, transaction)).isEqualTo(20);
            transaction.commit();
        }
        assertThat(energy.getAmountAsLong()).isEqualTo(20L);
    }

    @Test
    void merges_same_resource_and_keeps_different_resources_in_separate_slots() {
        LongResourceStorage<ItemResource> storage = new LongResourceStorage<>(
                ItemResource.class, 2, 100L, resource -> resource.isEmpty(), () -> {});
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        ItemResource gold = ItemResource.of(Items.GOLD_INGOT);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, iron, 60L, transaction)).isEqualTo(60L);
            assertThat(storage.insert(0, iron, 50L, transaction)).isEqualTo(40L);
            assertThat(storage.insert(1, gold, 25L, transaction)).isEqualTo(25L);
            transaction.commit();
        }

        assertThat(storage.amount(0)).isEqualTo(100L);
        assertThat(storage.resource(0)).isEqualTo(iron);
        assertThat(storage.amount(1)).isEqualTo(25L);
        assertThat(storage.resource(1)).isEqualTo(gold);
    }

    @Test
    void rejects_new_resource_when_all_type_slots_are_occupied() {
        LongResourceStorage<ItemResource> storage = new LongResourceStorage<>(
                ItemResource.class, 2, 100L, resource -> resource.isEmpty(), () -> {});
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        ItemResource gold = ItemResource.of(Items.GOLD_INGOT);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, iron, 10L, transaction)).isEqualTo(10L);
            assertThat(storage.insert(1, gold, 10L, transaction)).isEqualTo(10L);
            transaction.commit();
        }

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, gold, 1L, transaction)).isZero();
            assertThat(storage.insert(1, iron, 1L, transaction)).isZero();
            transaction.commit();
        }
    }

    @Test
    void supports_amounts_above_integer_range_and_rolls_back_transactions() {
        long largeAmount = (long) Integer.MAX_VALUE + 10L;
        LongResourceStorage<FluidResource> storage = new LongResourceStorage<>(
                FluidResource.class, 1, largeAmount + 100L, resource -> resource.isEmpty(), () -> {});
        FluidResource water = FluidResource.of(Fluids.WATER);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, water, largeAmount, transaction)).isEqualTo(largeAmount);
            transaction.commit();
        }

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.extract(0, water, 5L, transaction)).isEqualTo(5L);
            assertThat(storage.insert(0, water, 10L, transaction)).isEqualTo(10L);
        }

        assertThat(storage.amount(0)).isEqualTo(largeAmount);
        assertThat(storage.resource(0)).isEqualTo(water);
    }
}
