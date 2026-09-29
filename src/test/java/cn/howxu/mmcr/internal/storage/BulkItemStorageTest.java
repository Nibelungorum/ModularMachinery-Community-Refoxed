package cn.howxu.mmcr.internal.storage;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BulkItemStorageTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void matching_items_merge_through_native_handler_execution() {
        BulkItemStorage storage = new BulkItemStorage(100L, () -> {});

        assertThat(storage.insertItem(0, new ItemStack(Items.IRON_INGOT, 40), false)).isEmpty();
        assertThat(storage.insertItem(0, new ItemStack(Items.IRON_INGOT, 20), false)).isEmpty();

        assertThat(storage.amount(0)).isEqualTo(60L);
        assertThat(ItemStack.isSameItemSameComponents(storage.resource(0), new ItemStack(Items.IRON_INGOT))).isTrue();
        assertThat(storage.resource(0).getCount()).isEqualTo(1);
    }

    @Test
    void different_items_are_rejected_when_slot_is_occupied() {
        BulkItemStorage storage = new BulkItemStorage(100L, () -> {});
        storage.insertItem(0, new ItemStack(Items.IRON_INGOT, 1), false);

        ItemStack rejected = storage.insertItem(0, new ItemStack(Items.GOLD_INGOT, 1), true);
        assertThat(ItemStack.isSameItemSameComponents(rejected, new ItemStack(Items.GOLD_INGOT))).isTrue();
        assertThat(rejected.getCount()).isEqualTo(1);
        assertThat(storage.amount(0)).isEqualTo(1L);
    }

    @Test
    void simulation_does_not_mutate_and_execution_notifies_once() {
        java.util.concurrent.atomic.AtomicInteger changes = new java.util.concurrent.atomic.AtomicInteger();
        BulkItemStorage storage = new BulkItemStorage(100L, changes::incrementAndGet);
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 20);

        assertThat(storage.insertItem(0, iron, true)).isEmpty();
        assertThat(storage.amount(0)).isZero();
        assertThat(storage.insertItem(0, iron, false)).isEmpty();

        assertThat(storage.amount(0)).isEqualTo(20L);
        assertThat(changes).hasValue(1);
    }

    @Test
    void extraction_uses_native_stack_chunks_without_losing_long_backing_amount() {
        BulkItemStorage storage = new BulkItemStorage(1_000L, () -> {});
        storage.forceInsert(new ItemStack(Items.IRON_INGOT, 1), 200L, false);

        ItemStack simulated = storage.extractItem(0, 64, true);
        assertThat(ItemStack.isSameItemSameComponents(simulated, new ItemStack(Items.IRON_INGOT))).isTrue();
        assertThat(simulated.getCount()).isEqualTo(64);
        assertThat(storage.amount(0)).isEqualTo(200L);
        ItemStack extracted = storage.extractItem(0, 64, false);
        assertThat(ItemStack.isSameItemSameComponents(extracted, new ItemStack(Items.IRON_INGOT))).isTrue();
        assertThat(extracted.getCount()).isEqualTo(64);

        assertThat(storage.amount(0)).isEqualTo(136L);
    }
}
