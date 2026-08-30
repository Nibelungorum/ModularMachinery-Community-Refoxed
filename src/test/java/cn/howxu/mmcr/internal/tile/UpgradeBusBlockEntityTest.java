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
            assertThat(create(size).itemStackHandler().getSlots()).isEqualTo(size.slots());
        }
    }

    @Test
    void persists_non_empty_slots_and_keeps_snapshot_stacks_isolated() {
        UpgradeBusBlockEntity source = create(UpgradeBusSize.ELITE);
        source.itemStackHandler().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        source.itemStackHandler().setStackInSlot(8, new ItemStack(Items.GOLD_INGOT, 7));

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        source.saveAdditional(output);

        UpgradeBusBlockEntity restored = create(UpgradeBusSize.ELITE);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()));

        assertThat(restored.itemStackHandler().getStackInSlot(0).getItem()).isEqualTo(Items.IRON_INGOT);
        assertThat(restored.itemStackHandler().getStackInSlot(0).getCount()).isEqualTo(3);
        assertThat(restored.itemStackHandler().getStackInSlot(8).getItem()).isEqualTo(Items.GOLD_INGOT);
        assertThat(restored.itemStackHandler().getStackInSlot(8).getCount()).isEqualTo(7);

        List<ItemStack> snapshot = restored.itemSnapshot();
        snapshot.get(0).setCount(1);
        assertThat(restored.itemStackHandler().getStackInSlot(0).getCount()).isEqualTo(3);
    }

    @Test
    void increments_version_for_real_insert_extract_and_replace_and_not_simulation() {
        UpgradeBusBlockEntity bus = create(UpgradeBusSize.NORMAL);
        AtomicInteger notifications = new AtomicInteger();
        bus.addControllerChangeListener(notifications::incrementAndGet);

        long initial = bus.contentsVersion();
        bus.itemStackHandler().insertItem(0, new ItemStack(Items.IRON_INGOT, 2), true);
        assertThat(bus.contentsVersion()).isEqualTo(initial);

        bus.itemStackHandler().insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false);
        long afterInsert = bus.contentsVersion();
        assertThat(afterInsert).isGreaterThan(initial);

        bus.itemStackHandler().extractItem(0, 1, false);
        long afterExtract = bus.contentsVersion();
        assertThat(afterExtract).isGreaterThan(afterInsert);

        bus.itemStackHandler().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 1));
        assertThat(bus.contentsVersion()).isGreaterThan(afterExtract);
        assertThat(notifications).hasValue(3);
    }

    private static UpgradeBusBlockEntity create(UpgradeBusSize size) {
        String id = "upgrade_bus_" + size.id();
        return new UpgradeBusBlockEntity(size, net.minecraft.core.BlockPos.ZERO,
                ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }
}
