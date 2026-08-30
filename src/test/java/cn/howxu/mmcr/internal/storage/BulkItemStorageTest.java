package cn.howxu.mmcr.internal.storage;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BulkItemStorageTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.GOLD_INGOT);
    }

    @Test
    void matching_resources_merge() {
        BulkItemStorage storage = new BulkItemStorage(100L, () -> {});
        ItemResource iron = resource(Items.IRON_INGOT);

        assertThat(storage.insert(iron, 40L, false)).isEqualTo(40L);
        assertThat(storage.insert(iron, 20L, false)).isEqualTo(20L);
        assertThat(storage.amount(0)).isEqualTo(60L);
        assertThat(storage.resource(0)).isEqualTo(iron);
    }

    @Test
    void different_resources_are_rejected_when_capacity_is_occupied() {
        BulkItemStorage storage = new BulkItemStorage(100L, () -> {});
        ItemResource iron = resource(Items.IRON_INGOT);

        storage.insert(iron, 1L, false);

        assertThat(storage.insert(resource(Items.GOLD_INGOT), 1L, false)).isZero();
    }

    @Test
    void caps_requests_at_resource_maximum() {
        long requested = 100L;
        BulkItemStorage storage = new BulkItemStorage(requested + 1L, () -> {});
        ItemResource iron = resource(Items.IRON_INGOT);

        assertThat(storage.insert(iron, requested, false)).isEqualTo(64L);
        assertThat(storage.extract(iron, requested, false)).isEqualTo(64L);
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void simulation_leaves_amount_unchanged() {
        BulkItemStorage storage = new BulkItemStorage(100L, () -> {});
        ItemResource iron = resource(Items.IRON_INGOT);
        storage.insert(iron, 30L, false);

        assertThat(storage.insert(iron, 10L, true)).isEqualTo(10L);
        assertThat(storage.extract(iron, 10L, true)).isEqualTo(10L);
        assertThat(storage.amount(0)).isEqualTo(30L);
    }

    @Test
    void committed_transaction_updates_amount() {
        BulkItemStorage storage = new BulkItemStorage(100L, () -> {});
        ItemResource iron = resource(Items.IRON_INGOT);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, iron, 25L, transaction)).isEqualTo(25L);
            assertThat(storage.amount(0)).isEqualTo(25L);
            transaction.commit();
        }

        assertThat(storage.amount(0)).isEqualTo(25L);
    }

    @Test
    void uncommitted_transaction_restores_resource_and_amount() {
        BulkItemStorage storage = new BulkItemStorage(100L, () -> {});
        ItemResource iron = resource(Items.IRON_INGOT);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, iron, 25L, transaction)).isEqualTo(25L);
            assertThat(storage.resource(0)).isEqualTo(iron);
            assertThat(storage.amount(0)).isEqualTo(25L);
        }

        assertThat(storage.resource(0)).isEqualTo(ItemResource.EMPTY);
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void capacity_and_transaction_insert_use_resource_stack_limit() {
        ItemStack stack = Items.IRON_INGOT.getDefaultInstance();
        stack.set(DataComponents.MAX_STACK_SIZE, 16);
        ItemResource resource = ItemResource.of(stack);
        BulkItemStorage storage = new BulkItemStorage(100L, () -> {});

        assertThat(storage.capacity(0, resource)).isEqualTo(16L);
        assertThat(storage.insert(resource, 32L, true)).isEqualTo(16L);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, resource, 32L, transaction)).isEqualTo(16L);
            transaction.commit();
        }

        assertThat(storage.amount(0)).isEqualTo(16L);
    }

    private static ItemResource resource(Item item) {
        ItemStack stack = item.getDefaultInstance();
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return ItemResource.of(stack);
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(
                DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }
}
