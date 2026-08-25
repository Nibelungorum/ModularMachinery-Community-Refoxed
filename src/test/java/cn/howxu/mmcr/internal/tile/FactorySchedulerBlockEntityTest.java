package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.stream.Stream;
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

    private static FactorySchedulerBlockEntity createScheduler() {
        BlockEntity entity = ModBlockEntities.BES.get("factory_controller").get().create(
                BlockPos.ZERO,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        assertThat(entity).isInstanceOf(FactorySchedulerBlockEntity.class);
        return (FactorySchedulerBlockEntity) entity;
    }

    private static MachineControllerBlockEntity createController() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        Identifier machineId = MMCR.id("test_cube");
        DynamicMachine machine = new DynamicMachine(machineId, "Factory Capacity Notification Test",
                new BlockArray(Map.of()), MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(),
                List.of(), Map.of(), 1, false, true, 4);
        RuntimeTestFixtures.publishStructure(controller, machine, true, 1,
                net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.SOUTH);
        return controller;
    }

    @SuppressWarnings("unchecked")
    private static void addFactoryComponent(MachineControllerBlockEntity controller,
                                             FactorySchedulerBlockEntity scheduler) throws Exception {
        List<ProcessingComponent> components = new java.util.ArrayList<>(controller.componentRuntime().components());
        components.add(new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, List.of(), null));
        controller.componentRuntime().replaceComponents(components);
        RuntimeTestFixtures.publishStructure(controller, controller.structureSnapshot().configuredMachine(), true, 1,
                net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.SOUTH);
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }
}
