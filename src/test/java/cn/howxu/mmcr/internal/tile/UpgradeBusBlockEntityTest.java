package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests standalone upgrade bus storage behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
class UpgradeBusBlockEntityTest {
    private static final HolderLookup.Provider EMPTY_LOOKUP = HolderLookup.Provider.create(Stream.empty());

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void each_tier_creates_its_fixed_storage_size() {
        for (UpgradeBusSize size : UpgradeBusSize.values()) {
            assertThat(create(size).itemStorage().size()).isEqualTo(size.slots());
        }
    }

    @Test
    void persists_non_empty_slots_and_keeps_snapshot_stacks_isolated() {
        UpgradeBusBlockEntity source = create(UpgradeBusSize.ELITE);
        insert(source, 0, new ItemStack(Items.IRON_INGOT, 3));
        insert(source, 8, new ItemStack(Items.GOLD_INGOT, 7));

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        source.saveAdditional(output);

        UpgradeBusBlockEntity restored = create(UpgradeBusSize.ELITE);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()));

        assertThat(restored.itemStorage().resource(0).toStack(1).getItem()).isEqualTo(Items.IRON_INGOT);
        assertThat(restored.itemStorage().amount(0)).isEqualTo(3L);
        assertThat(restored.itemStorage().resource(8).toStack(1).getItem()).isEqualTo(Items.GOLD_INGOT);
        assertThat(restored.itemStorage().amount(8)).isEqualTo(7L);

        List<ItemStack> snapshot = restored.itemSnapshot();
        snapshot.get(0).setCount(1);
        assertThat(restored.itemStorage().amount(0)).isEqualTo(3L);
    }

    @Test
    void increments_version_for_real_insert_extract_and_replace_and_not_simulation() {
        UpgradeBusBlockEntity bus = create(UpgradeBusSize.NORMAL);
        AtomicInteger notifications = new AtomicInteger();
        bus.addControllerChangeListener(notifications::incrementAndGet);

        long initial = bus.contentsVersion();
        try (Transaction transaction = Transaction.openRoot()) {
            bus.itemStorage().insert(0, ItemResource.of(new ItemStack(Items.IRON_INGOT, 2)), 2L, transaction);
        }
        assertThat(bus.contentsVersion()).isEqualTo(initial);

        insert(bus, 0, new ItemStack(Items.IRON_INGOT, 2));
        long afterInsert = bus.contentsVersion();
        assertThat(afterInsert).isGreaterThan(initial);

        try (Transaction transaction = Transaction.openRoot()) {
            ItemResource iron = ItemResource.of(new ItemStack(Items.IRON_INGOT, 1));
            bus.itemStorage().extract(0, iron, 1L, transaction);
            transaction.commit();
        }
        long afterExtract = bus.contentsVersion();
        assertThat(afterExtract).isGreaterThan(afterInsert);

        try (Transaction transaction = Transaction.openRoot()) {
            ItemResource iron = ItemResource.of(new ItemStack(Items.IRON_INGOT, 1));
            bus.itemStorage().extract(0, iron, bus.itemStorage().amount(0), transaction);
            bus.itemStorage().insert(0, ItemResource.of(new ItemStack(Items.GOLD_INGOT, 1)), 1L, transaction);
            transaction.commit();
        }
        assertThat(bus.contentsVersion()).isGreaterThan(afterExtract);
        assertThat(notifications).hasValue(3);
    }

    private static UpgradeBusBlockEntity create(UpgradeBusSize size) {
        String id = "upgrade_bus_" + size.id();
        return new UpgradeBusBlockEntity(size, net.minecraft.core.BlockPos.ZERO,
                ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }

    private static void insert(UpgradeBusBlockEntity bus, int slot, ItemStack stack) {
        try (Transaction transaction = Transaction.openRoot()) {
            bus.itemStorage().insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
        }
    }
}
