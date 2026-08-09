package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FactorySchedulerBlockEntityTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(Items.IRON_INGOT);
    }

    @Test
    void emptySchedulerProvidesOneThread() {
        FactorySchedulerBlockEntity scheduler = createScheduler();

        assertThat(scheduler.threadCount()).isEqualTo(1);
        assertThat(scheduler.getItemStackHandler(null).getSlots()).isEqualTo(1);
    }

    @Test
    void slotOnlyAcceptsThreadDispersers() {
        FactorySchedulerBlockEntity scheduler = createScheduler();

        ItemStack rejected = scheduler.getItemStackHandler(null).insertItem(0, Items.IRON_INGOT.getDefaultInstance(), false);
        ItemStack accepted = scheduler.getItemStackHandler(null).insertItem(0,
                new ItemStack(ModItems.THREAD_DISPERSER.get(), 8), false);

        assertThat(rejected.isEmpty()).isFalse();
        assertThat(accepted.isEmpty()).isTrue();
        assertThat(scheduler.threadCount()).isEqualTo(9);
    }

    @Test
    void largeStoredStackSaturatesThreadCount() {
        FactorySchedulerBlockEntity scheduler = createScheduler();
        scheduler.getItemStackHandler(null).setStackInSlot(0,
                new ItemStack(ModItems.THREAD_DISPERSER.get(), Integer.MAX_VALUE));

        assertThat(scheduler.threadCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void inventoryRoundTripsThroughNbt() {
        FactorySchedulerBlockEntity scheduler = createScheduler();
        scheduler.getItemStackHandler(null).setStackInSlot(0, new ItemStack(ModItems.THREAD_DISPERSER.get(), 7));

        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        scheduler.saveAdditional(output);

        FactorySchedulerBlockEntity loaded = createScheduler();
        loaded.loadAdditional(TagValueInput.create(
                ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()),
                output.buildResult()));

        assertThat(loaded.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(7);
        assertThat(loaded.threadCount()).isEqualTo(8);
    }

    @Test
    void tickSyncsThreadLimitFromInventoryEveryFortyTicksAndNotifiesChanges() {
        FactorySchedulerBlockEntity scheduler = createScheduler();
        AtomicInteger syncs = new AtomicInteger();

        scheduler.getItemStackHandler(null).setStackInSlot(0, new ItemStack(ModItems.THREAD_DISPERSER.get(), 3));

        for (int i = 0; i < 39; i++) scheduler.tickScheduler(syncs::incrementAndGet);

        assertThat(syncs).hasValue(0);
        assertThat(scheduler.threadLimit()).isEqualTo(1);

        scheduler.tickScheduler(syncs::incrementAndGet);

        assertThat(scheduler.threadLimit()).isEqualTo(4);
        assertThat(syncs).hasValue(1);

        for (int i = 0; i < 40; i++) scheduler.tickScheduler(syncs::incrementAndGet);

        assertThat(syncs).hasValue(1);
    }

    private static FactorySchedulerBlockEntity createScheduler() {
        BlockEntity entity = ModBlockEntities.BES.get("factory_controller").get().create(
                BlockPos.ZERO,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        assertThat(entity).isInstanceOf(FactorySchedulerBlockEntity.class);
        return (FactorySchedulerBlockEntity) entity;
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }
}
