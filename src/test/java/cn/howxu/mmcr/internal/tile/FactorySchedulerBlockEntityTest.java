package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class FactorySchedulerBlockEntityTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindItemComponents(Items.IRON_INGOT);
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
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
                HolderLookup.Provider.create(Stream.empty()));
        scheduler.saveAdditional(output);

        FactorySchedulerBlockEntity loaded = createScheduler();
        loaded.loadAdditional(TagValueInput.create(
                ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),
                output.buildResult()));

        assertThat(loaded.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(7);
        assertThat(loaded.threadCount()).isEqualTo(8);
    }

    @Test
    void controllerAggregatesAllFactoryComponentThreadCounts() throws Exception {
        MachineControllerBlockEntity controller = createController();
        FactorySchedulerBlockEntity first = createScheduler();
        FactorySchedulerBlockEntity second = createScheduler();
        first.getItemStackHandler(null).setStackInSlot(0,
                new ItemStack(ModItems.THREAD_DISPERSER.get(), 2));
        second.getItemStackHandler(null).setStackInSlot(0,
                new ItemStack(ModItems.THREAD_DISPERSER.get(), 4));
        addFactoryComponent(controller, first);
        addFactoryComponent(controller, second);

        assertThat(controller.factorySchedulerThreadCount()).isEqualTo(first.threadCount() + second.threadCount());
    }

    @Test
    void boundControllerIsNotifiedImmediatelyWhenInventoryChanges() throws Exception {
        MachineControllerBlockEntity controller = createController();
        FactorySchedulerBlockEntity scheduler = createScheduler();
        AtomicInteger invalidations = new AtomicInteger();
        controller.setFactoryCapacityInvalidationCallbackForTesting(invalidations::incrementAndGet);
        addFactoryComponent(controller, scheduler);
        scheduler.bindOwner(controller);

        scheduler.getItemStackHandler(null).insertItem(0,
                new ItemStack(ModItems.THREAD_DISPERSER.get(), 3), false);
        scheduler.getItemStackHandler(null).extractItem(0, 1, false);

        assertThat(invalidations).hasValue(2);
    }

    @Test
    void saveAndLoadPersistsOnlyFactoryCapacityInventory() throws Exception {
        MachineRecipe baseRecipe = recipe("scheduler_base_lock");
        MachineRecipe workerRecipe = recipe("scheduler_worker_lock");
        RecipeRegistry.register(baseRecipe);
        RecipeRegistry.register(workerRecipe);
        FactorySchedulerBlockEntity scheduler = createScheduler();
        FactoryRecipeScheduler internal = internalScheduler(scheduler);
        internal.allThreads().getFirst().setLockedRecipeId(baseRecipe.id());
        FactoryRecipeThread worker = FactoryRecipeThread.simple(null, new RecipeCraftingContextPool(), "factory-4");
        worker.setLockedRecipeId(workerRecipe.id());
        internal.addThreadForTesting(worker);

        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        scheduler.saveAdditional(output);
        FactorySchedulerBlockEntity loaded = createScheduler();
        loaded.loadAdditional(TagValueInput.create(
                ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),
                output.buildResult()));

        assertThat(internalScheduler(loaded).allThreads()).singleElement()
                .extracting(FactoryRecipeThread::lockedRecipeId)
                .isNull();
    }

    private static FactorySchedulerBlockEntity createScheduler() {
        BlockEntity entity = ModBlockEntities.BES.get("factory_controller").get().create(
                BlockPos.ZERO,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        assertThat(entity).isInstanceOf(FactorySchedulerBlockEntity.class);
        return (FactorySchedulerBlockEntity) entity;
    }

    private static MachineControllerBlockEntity createController() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlockEntity controller =
                (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
        Field components = MachineControllerBlockEntity.class.getDeclaredField("components");
        components.setAccessible(true);
        components.set(controller, new ArrayList<ProcessingComponent>());
        return controller;
    }

    @SuppressWarnings("unchecked")
    private static void addFactoryComponent(MachineControllerBlockEntity controller,
                                            FactorySchedulerBlockEntity scheduler) throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("components");
        field.setAccessible(true);
        ((List<ProcessingComponent>) field.get(controller)).add(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, List.of(), null));
    }

    private static FactoryRecipeScheduler internalScheduler(FactorySchedulerBlockEntity scheduler) throws Exception {
        Field field = FactorySchedulerBlockEntity.class.getDeclaredField("scheduler");
        field.setAccessible(true);
        return (FactoryRecipeScheduler) field.get(scheduler);
    }

    private static MachineRecipe recipe(String path) {
        return new MachineRecipe(MMCR.id(path), MMCR.id("scheduler_lock_machine"), 1, List.of(), List.of());
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }
}
