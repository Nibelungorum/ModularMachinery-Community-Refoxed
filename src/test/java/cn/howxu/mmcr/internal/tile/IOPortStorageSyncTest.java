package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies that only storage mutations emit port storage snapshot notifications.
 * @author howxu <dev@howxu.cn>
 */
class IOPortStorageSyncTest {
    private static final BlockPos POS = new BlockPos(1, 2, 3);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void storage_mutation_notifies_but_auto_io_and_direct_block_changes_do_not() {
        TrackingPort port = new TrackingPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.EXTENDED_ITEM_INPUT.id()).get().defaultBlockState());

        ((LongResourceStorage<ItemResource>) port.itemStorage()).setContents(0,
                ItemResource.of(Items.IRON_INGOT), 1L);
        assertThat(port.snapshotNotifications).isZero();
        port.flushStorageChanges();
        assertThat(port.snapshotNotifications).isEqualTo(1);

        port.setAutoIOEnabled(true);
        port.setChanged();
        port.flushStorageChanges();
        assertThat(port.snapshotNotifications).isEqualTo(1);
    }

    @Test
    void ordinary_item_storage_mutation_uses_the_same_notification_path() {
        TrackingItemPort port = new TrackingItemPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.ITEM_INPUT.id()).get().defaultBlockState());

        try (Transaction transaction = Transaction.openRoot()) {
            port.itemStorage().insert(
                    0,
                    ItemResource.of(new ItemStack(Items.IRON_INGOT)),
                    1L,
                    transaction
            );
            transaction.commit();
        }

        assertThat(port.snapshotNotifications).isZero();
        port.flushStorageChanges();
        assertThat(port.snapshotNotifications).isEqualTo(1);
    }

    @Test
    void committed_changes_save_immediately_and_coalesce_while_rollback_does_not_publish() {
        TrackingItemPort port = new TrackingItemPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.ITEM_INPUT.id()).get().defaultBlockState());
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        for (int commit = 0; commit < 2; commit++) {
            try (Transaction transaction = Transaction.openRoot()) {
                assertThat(port.itemStorage().insert(0, iron, 5L, transaction)).isEqualTo(5L);
                assertThat(port.storageRevision()).isEqualTo(commit);
                transaction.commit();
            }
        }
        assertThat(port.itemStorage().amount(0)).isEqualTo(10L);
        assertThat(port.storageRevision()).isEqualTo(2L);
        assertThat(port.saves).isEqualTo(2);
        try (Transaction transaction = Transaction.openRoot()) {
            port.itemStorage().insert(0, iron, 4L, transaction);
            port.itemStorage().extract(0, iron, 4L, transaction);
            transaction.commit();
        }
        assertThat(port.storageRevision()).isEqualTo(2L);
        assertThat(port.saves).isEqualTo(2);
        assertThat(port.snapshotNotifications).isZero();
        try (Transaction transaction = Transaction.openRoot()) {
            port.itemStorage().extract(0, iron, 4L, transaction);
        }
        assertThat(port.itemStorage().amount(0)).isEqualTo(10L);
        assertThat(port.storageRevision()).isEqualTo(2L);
        assertThat(port.saves).isEqualTo(2);
        port.flushStorageChanges();
        port.flushStorageChanges();
        assertThat(port.snapshotNotifications).isEqualTo(1);
    }

    @Test
    void viewerless_flush_captures_availability_once_and_never_encodes_a_payload() {
        ViewerlessPort port = new ViewerlessPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.EXTENDED_ITEM_INPUT.id()).get().defaultBlockState());
        port.setLevel(LevelStub.createWithBlockEntities(List.of(port)));
        ((LongResourceStorage<ItemResource>) port.itemStorage()).setContents(0, ItemResource.of(Items.IRON_INGOT), 10L);
        ((LongResourceStorage<ItemResource>) port.itemStorage()).setContents(0, ItemResource.of(Items.IRON_INGOT), 20L);
        port.captures = 0;

        port.flushStorageChanges();
        assertThat(port.captures).isEqualTo(1);
        port.flushStorageChanges();
        assertThat(port.captures).isEqualTo(1);
    }

    @Test
    void failed_direct_flush_preserves_pending_work() {
        TrackingEnergyPort port = new TrackingEnergyPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.ENERGY_INPUT.id()).get().defaultBlockState());
        port.getEnergyStorage().setAmount(10L);
        port.onSnapshot = () -> { throw new IllegalStateException("broken observer"); };
        assertThatThrownBy(() -> { port.flushStorageChanges(); }).isInstanceOf(IllegalStateException.class);
        port.flushStorageChanges();
        assertThat(port.snapshotNotifications).isEqualTo(2);
        port.flushStorageChanges();
        assertThat(port.snapshotNotifications).isEqualTo(2);
    }

    @Test
    void reentrant_commit_is_retained_for_the_next_flush() {
        TrackingEnergyPort port = new TrackingEnergyPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.ENERGY_INPUT.id()).get().defaultBlockState());
        port.getEnergyStorage().setAmount(10L);
        port.onSnapshot = () -> {
            port.getEnergyStorage().setAmount(20L);
            port.flushStorageChanges();
        };
        port.flushStorageChanges();
        assertThat(port.snapshotNotifications).isEqualTo(1);
        assertThat(port.storageRevision()).isEqualTo(2L);
        port.flushStorageChanges();
        assertThat(port.snapshotNotifications).isEqualTo(2);
        port.flushStorageChanges();
        assertThat(port.snapshotNotifications).isEqualTo(2);
    }

    @Test
    void level_flush_isolates_failed_ports_and_rechecks_reentrant_commits() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        TrackingEnergyPort first = new TrackingEnergyPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.ENERGY_INPUT.id()).get().defaultBlockState());
        TrackingEnergyPort second = new TrackingEnergyPort(POS.offset(2, 0, 0), first.getBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller,
                MachineRegistry.getMachine(MMCR.id("test_cube")), first, second);
        ServerLevel level = (ServerLevel) controller.getLevel();
        first.getEnergyStorage().setAmount(10L);
        second.getEnergyStorage().setAmount(10L);
        first.onSnapshot = () -> { throw new IllegalStateException("broken observer"); };
        second.onSnapshot = () -> second.getEnergyStorage().setAmount(20L);

        IOPortBlockEntity.flushStorageChanges(level);
        assertThat(first.snapshotNotifications).isEqualTo(1);
        assertThat(second.snapshotNotifications).isEqualTo(1);
        assertThat(second.storageRevision()).isEqualTo(2L);
        IOPortBlockEntity.flushStorageChanges(level);
        assertThat(first.snapshotNotifications).isEqualTo(2);
        assertThat(second.snapshotNotifications).isEqualTo(2);
        IOPortBlockEntity.flushStorageChanges(level);
        assertThat(first.snapshotNotifications).isEqualTo(2);
        assertThat(second.snapshotNotifications).isEqualTo(2);
    }

    @Test
    void repeated_commits_do_not_probe_the_dirty_set_again_until_detached() {
        var controller = TestBootstrap.newController();
        var port = new TrackingEnergyPort(POS, ModBlocks.BLOCKS.get(PortKinds.ENERGY_INPUT.id()).get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, MachineRegistry.getMachine(MMCR.id("test_cube")), port);
        ServerLevel level = (ServerLevel) controller.getLevel();
        port.energyStorage().setAmount(1L);
        int firstEnqueue = port.hashReads;
        for (int i = 2; i <= 17; i++) port.energyStorage().setAmount(i);
        assertThat(port.hashReads).isEqualTo(firstEnqueue);
        IOPortBlockEntity.flushStorageChanges(level);
        int detached = port.hashReads;
        port.energyStorage().setAmount(18L);
        assertThat(port.hashReads).isGreaterThan(detached);
        IOPortBlockEntity.flushStorageChanges(level);
        assertThat(port.snapshotNotifications).isEqualTo(2);
    }

    @Test
    void moving_a_queued_port_does_not_lose_the_destination_flush() {
        var first = TestBootstrap.newController();
        var second = TestBootstrap.newController();
        var port = new TrackingEnergyPort(POS, ModBlocks.BLOCKS.get(PortKinds.ENERGY_INPUT.id()).get().defaultBlockState());
        var machine = MachineRegistry.getMachine(MMCR.id("test_cube"));
        RuntimeTestFixtures.formStructureWithComponents(first, machine, port);
        port.energyStorage().setAmount(10L);
        RuntimeTestFixtures.formStructureWithComponents(second, machine, port);
        port.energyStorage().setAmount(20L);
        IOPortBlockEntity.flushStorageChanges((ServerLevel) first.getLevel());
        assertThat(port.snapshotNotifications).isZero();
        IOPortBlockEntity.flushStorageChanges((ServerLevel) second.getLevel());
        assertThat(port.snapshotNotifications).isEqualTo(1);
    }

    @Test
    void a_replaced_port_is_not_published_by_the_level_dirty_set() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        TrackingEnergyPort oldPort = new TrackingEnergyPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.ENERGY_INPUT.id()).get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller,
                MachineRegistry.getMachine(MMCR.id("test_cube")), oldPort);
        oldPort.getEnergyStorage().setAmount(10L);
        TrackingEnergyPort replacement = new TrackingEnergyPort(POS, oldPort.getBlockState());
        RuntimeTestFixtures.replaceBlockEntity(controller, replacement);
        replacement.getEnergyStorage().setAmount(20L);

        IOPortBlockEntity.flushStorageChanges((ServerLevel) controller.getLevel());
        assertThat(oldPort.snapshotNotifications).isZero();
        assertThat(replacement.snapshotNotifications).isEqualTo(1);
    }

    @Test
    void shared_controllers_see_live_revision_before_flush_and_receive_the_deferred_wake() {
        TrackingEnergyPort port = new TrackingEnergyPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.ENERGY_INPUT.id()).get().defaultBlockState());
        MachineControllerBlockEntity first = TestBootstrap.newController();
        MachineControllerBlockEntity second = new MachineControllerBlockEntity(POS.offset(5, 0, 0), first.getBlockState());
        var level = LevelStub.createWithBlockEntities(List.of(first, second, port));
        LevelStub.setGameTime(level, 1L);
        for (MachineControllerBlockEntity controller : List.of(first, second)) {
            controller.setLevel(level);
            port.linkControllerAppearance(controller.getBlockPos(), null);
            controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(
                    new MachineComponent(port.kind(), port.ioType()), port, POS,
                    POS.subtract(controller.getBlockPos()), List.of())));
            controller.currentRuntimeSnapshot();
        }
        port.setLevel(level);
        long firstRevision = first.componentRuntime().capabilityContentRevision();
        long secondRevision = second.componentRuntime().capabilityContentRevision();
        long firstVersion = first.componentRuntime().capabilityVersion();
        long secondVersion = second.componentRuntime().capabilityVersion();
        long firstWake = first.resourceAvailabilityEpoch();
        long secondWake = second.resourceAvailabilityEpoch();

        port.getEnergyStorage().setAmount(40L);
        assertThat(first.componentRuntime().capabilityContentRevision()).isEqualTo(firstRevision + 1L);
        assertThat(second.componentRuntime().capabilityContentRevision()).isEqualTo(secondRevision + 1L);
        for (MachineControllerBlockEntity controller : List.of(first, second)) {
            assertThat(controller.currentRuntimeSnapshot().capabilityPresentations().getFirst().amount()).isEqualTo(40L);
        }
        assertThat(first.resourceAvailabilityEpoch()).isEqualTo(firstWake);
        assertThat(second.resourceAvailabilityEpoch()).isEqualTo(secondWake);
        port.flushStorageChanges();
        assertThat(first.resourceAvailabilityEpoch()).isEqualTo(firstWake + 1L);
        assertThat(second.resourceAvailabilityEpoch()).isEqualTo(secondWake + 1L);
        assertThat(first.componentRuntime().capabilityVersion()).isEqualTo(firstVersion);
        assertThat(second.componentRuntime().capabilityVersion()).isEqualTo(secondVersion);
        assertThat(first.componentRuntime().capabilityContentRevision()).isEqualTo(firstRevision + 1L);
    }

    @Test
    void released_output_wakes_all_shared_controllers_and_failed_observers_are_retried() {
        TrackingPort port = new TrackingPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.EXTENDED_ITEM_OUTPUT.id()).get().defaultBlockState());
        BlockState controllerState = ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState();
        RecordingController first = new RecordingController(POS.offset(5, 0, 0), controllerState);
        RecordingController second = new RecordingController(POS.offset(7, 0, 0), controllerState);
        var level = LevelStub.createWithBlockEntities(List.of(first, second, port));
        first.setLevel(level);
        second.setLevel(level);
        port.setLevel(level);
        port.linkControllerAppearance(first.getBlockPos(), null);
        port.linkControllerAppearance(second.getBlockPos(), null);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        ((LongResourceStorage<ItemResource>) port.itemStorage()).setContents(0, iron, 8L);
        port.flushStorageChanges();
        assertThat(first.resources).isEmpty();
        assertThat(second.resources).isEmpty();
        long revision = port.storageRevision();
        try (Transaction transaction = Transaction.openRoot()) {
            port.itemStorage().extract(0, iron, 4L, transaction);
        }
        port.flushStorageChanges();
        assertThat(port.storageRevision()).isEqualTo(revision);
        assertThat(first.resources).isEmpty();
        try (Transaction transaction = Transaction.openRoot()) {
            port.itemStorage().extract(0, iron, 4L, transaction);
            transaction.commit();
        }
        assertThat(port.itemStorage().amount(0)).isEqualTo(4L);
        assertThat(first.resources).isEmpty();
        first.failNextNotification = true;

        assertThatThrownBy(() -> { port.flushStorageChanges(); }).isInstanceOf(IllegalStateException.class);
        assertThat(first.resources).containsExactly(iron);
        assertThat(second.resources).containsExactly(iron);
        port.flushStorageChanges();
        assertThat(first.resources).containsExactly(iron, iron);
        assertThat(second.resources).containsExactly(iron, iron);
        assertThat(first.reasons).containsOnly(ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY);
        assertThat(second.reasons).containsOnly(ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY);
        port.flushStorageChanges();
        assertThat(first.resources).hasSize(2);
        assertThat(second.resources).hasSize(2);
    }

    @Test
    void a_permanent_first_resource_failure_does_not_starve_later_resources_capabilities_payloads_or_ports() {
        TrackingCombinedPort port = new TrackingCombinedPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.COMBINED_INPUT.id()).get().defaultBlockState());
        TrackingEnergyPort otherPort = new TrackingEnergyPort(POS.offset(2, 0, 0),
                ModBlocks.BLOCKS.get(PortKinds.ENERGY_INPUT.id()).get().defaultBlockState());
        BlockState controllerState = ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState();
        RecordingController first = new RecordingController(POS.offset(5, 0, 0), controllerState);
        RecordingController second = new RecordingController(POS.offset(7, 0, 0), controllerState);
        RuntimeTestFixtures.formStructureWithComponents(TestBootstrap.newController(),
                MachineRegistry.getMachine(MMCR.id("test_cube")), first, second, port, otherPort);
        ServerLevel level = (ServerLevel) port.getLevel();
        port.linkControllerAppearance(first.getBlockPos(), null);
        port.linkControllerAppearance(second.getBlockPos(), null);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        ItemResource gold = ItemResource.of(Items.GOLD_INGOT);
        FluidResource water = FluidResource.of(Fluids.WATER);
        try (Transaction transaction = Transaction.openRoot()) {
            port.itemStorage().insert(0, iron, 1L, transaction);
            port.itemStorage().insert(1, gold, 1L, transaction);
            transaction.commit();
        }
        port.fluidStorage().setContents(0, water, 1L);
        first.failingResource = iron;
        second.failingResource = water;
        port.onSnapshot = () -> { throw new IllegalArgumentException("broken payload observer"); };

        for (int flush = 1; flush <= 2; flush++) {
            int suppressedFailures = flush == 1 ? 2 : 1;
            assertThatThrownBy(() -> { port.flushStorageChanges(); })
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(failure -> assertThat(failure.getSuppressed()).hasSize(suppressedFailures));
            assertThat(first.resources).hasSize(3 * flush).contains(gold, water);
            assertThat(second.resources).containsExactlyElementsOf(first.resources);
            assertThat(first.resources.subList(3 * (flush - 1), 3 * flush)).containsExactly(iron, gold, water);
            assertThat(first.deliveredResources.subList(2 * (flush - 1), 2 * flush)).containsExactly(gold, water);
            assertThat(second.deliveredResources.subList(2 * (flush - 1), 2 * flush)).containsExactly(iron, gold);
            assertThat(port.snapshotNotifications).isEqualTo(flush);
        }

        otherPort.getEnergyStorage().setAmount(10L);
        IOPortBlockEntity.flushStorageChanges(level);
        assertThat(port.snapshotNotifications).isEqualTo(3);
        assertThat(first.resources).hasSize(9);
        assertThat(second.resources).hasSize(9);
        assertThat(otherPort.snapshotNotifications).isEqualTo(1);

        first.failingResource = null;
        second.failingResource = null;
        IOPortBlockEntity.flushStorageChanges(level);
        assertThat(port.snapshotNotifications).isEqualTo(4);
        assertThat(first.resources).hasSize(12);
        assertThat(second.resources).hasSize(12);
        IOPortBlockEntity.flushStorageChanges(level);
        port.flushStorageChanges();
        assertThat(port.snapshotNotifications).isEqualTo(4);
        assertThat(otherPort.snapshotNotifications).isEqualTo(1);
    }

    @Test
    void a_commit_during_availability_notification_keeps_the_next_revision_pending() {
        TrackingCombinedPort port = new TrackingCombinedPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.COMBINED_INPUT.id()).get().defaultBlockState());
        RecordingController controller = new RecordingController(POS.offset(5, 0, 0),
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        var level = LevelStub.createWithBlockEntities(List.of(controller, port));
        controller.setLevel(level);
        port.setLevel(level);
        port.linkControllerAppearance(controller.getBlockPos(), null);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        FluidResource water = FluidResource.of(Fluids.WATER);
        try (Transaction transaction = Transaction.openRoot()) {
            port.itemStorage().insert(0, iron, 1L, transaction);
            transaction.commit();
        }
        port.fluidStorage().setContents(0, water, 1L);
        long revision = port.storageRevision();
        controller.onNotification = () -> {
            try (Transaction transaction = Transaction.openRoot()) {
                port.itemStorage().insert(0, iron, 1L, transaction);
                transaction.commit();
            }
            port.flushStorageChanges();
        };

        port.flushStorageChanges();
        assertThat(port.storageRevision()).isEqualTo(revision + 1L);
        assertThat(controller.resources).containsExactly(iron, water);
        assertThat(port.snapshotNotifications).isEqualTo(1);
        port.flushStorageChanges();
        assertThat(controller.resources).containsExactly(iron, water, iron);
        assertThat(port.snapshotNotifications).isEqualTo(2);
        port.flushStorageChanges();
        assertThat(port.snapshotNotifications).isEqualTo(2);
    }

    @Test
    void every_input_item_or_fluid_storage_host_notifies_recipe_inputs() {
        TrackingPort extendedItem = new TrackingPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.EXTENDED_ITEM_INPUT.id()).get().defaultBlockState());
        ((LongResourceStorage<ItemResource>) extendedItem.itemStorage()).setContents(0,
                ItemResource.of(Items.IRON_INGOT), 1L);

        TrackingFluidPort extendedFluid = new TrackingFluidPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.EXTENDED_FLUID_INPUT.id()).get().defaultBlockState());
        extendedFluid.fluidStorage().setContents(0, FluidResource.of(Fluids.WATER), 1L);

        TrackingCombinedPort combined = new TrackingCombinedPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.COMBINED_INPUT.id()).get().defaultBlockState());
        try (Transaction transaction = Transaction.openRoot()) {
            combined.itemStorage().insert(
                    0,
                    ItemResource.of(new ItemStack(Items.IRON_INGOT)),
                    1L,
                    transaction
            );
            transaction.commit();
        }
        combined.fluidStorage().setContents(0, FluidResource.of(Fluids.WATER), 1L);

        assertThat(extendedItem.recipeInputNotifications).isEqualTo(1);
        assertThat(extendedFluid.recipeInputNotifications).isEqualTo(1);
        assertThat(combined.recipeInputNotifications).isEqualTo(2);
    }

    @Test
    void energy_root_commit_refreshes_only_its_source_and_rollback_preserves_published_rows() {
        BlockPos controllerPos = POS.offset(5, 0, 0);
        TrackingPort itemPort = new TrackingPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.EXTENDED_ITEM_INPUT.id()).get().defaultBlockState());
        TrackingEnergyPort energyPort = new TrackingEnergyPort(POS.offset(2, 0, 0),
                ModBlocks.BLOCKS.get(PortKinds.ENERGY_INPUT.id()).get().defaultBlockState());
        ((LongResourceStorage<ItemResource>) itemPort.itemStorage()).setContents(0,
                ItemResource.of(Items.IRON_INGOT), 7L);
        MachineControllerBlockEntity controller = new MachineControllerBlockEntity(controllerPos,
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        var level = LevelStub.create(Map.of(
                controllerPos, controller.getBlockState().getBlock(),
                itemPort.getBlockPos(), itemPort.getBlockState().getBlock(),
                energyPort.getBlockPos(), energyPort.getBlockState().getBlock()),
                List.of(controller, itemPort, energyPort));
        controller.setLevel(level);
        long setupAvailabilityEpoch = controller.resourceAvailabilityEpoch();
        controller.setMachine(new DynamicMachine(MMCR.id("test_cube"), "runtime test", new BlockArray(Map.of())));
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(setupAvailabilityEpoch + 1L);
        itemPort.setLevel(level);
        energyPort.setLevel(level);
        for (IOPortBlockEntity port : List.of(itemPort, energyPort)) {
            port.linkControllerAppearance(controllerPos, null);
        }
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(new MachineComponent(itemPort.kind(), itemPort.ioType()), itemPort,
                        itemPort.getBlockPos(), itemPort.getBlockPos().subtract(controllerPos), List.of()),
                new ProcessingComponent(new MachineComponent(energyPort.kind(), energyPort.ioType()), energyPort,
                        energyPort.getBlockPos(), energyPort.getBlockPos().subtract(controllerPos), List.of())));
        var before = controller.currentRuntimeSnapshot();
        assertThat(before.capabilityPresentations()).hasSize(2);
        var itemRow = before.capabilityPresentations().getFirst();
        var energyRow = before.capabilityPresentations().get(1);
        assertThat(itemRow.typeId()).isEqualTo(BuiltinCapabilityDefinitions.ITEM_TYPE.id());
        assertThat(itemRow.slots().getFirst().resourceId()).isEqualTo(ItemResource.of(Items.IRON_INGOT).toString());
        assertThat(itemRow.slots().getFirst().amount()).isEqualTo(7L);
        assertThat(energyRow.typeId()).isEqualTo(BuiltinCapabilityDefinitions.ENERGY_TYPE.id());
        assertThat(energyRow.amount()).isZero();
        // setMachine notified the disconnected -> notRequired module transition in the setup tick.
        LevelStub.setGameTime(level, level.getGameTime() + 1L);
        long beforeEpoch = controller.componentRuntime().capabilityPresentationEpoch();
        long beforeAvailabilityEpoch = controller.resourceAvailabilityEpoch();

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(energyPort.getEnergyStorage().insert(40L, transaction)).isEqualTo(40L);
            assertThat(energyPort.snapshotNotifications).isZero();
            assertThat(controller.componentRuntime().capabilityPresentationEpoch()).isEqualTo(beforeEpoch);
            assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch);
            transaction.commit();
        }

        var committed = controller.currentRuntimeSnapshot();
        assertThat(energyPort.snapshotNotifications).isZero();
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch);
        assertThat(committed.capabilityPresentations().get(1).amount()).isEqualTo(40L);
        energyPort.flushStorageChanges();
        committed = controller.currentRuntimeSnapshot();
        assertThat(energyPort.snapshotNotifications).isEqualTo(1);
        assertThat(energyPort.getEnergyStorage().amount()).isEqualTo(40L);
        assertThat(controller.componentRuntime().capabilityPresentationEpoch()).isEqualTo(beforeEpoch + 2L);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch + 1L);
        assertThat(committed.capabilityPresentations().getFirst()).isSameAs(itemRow);
        assertThat(committed.capabilityPresentations().get(1)).isNotSameAs(energyRow);
        assertThat(committed.capabilityPresentations().get(1).amount()).isEqualTo(40L);
        assertThat(before.capabilityPresentations().get(1).amount()).isZero();
        assertThat(before.capabilityPresentations().getFirst().slots().getFirst().amount()).isEqualTo(7L);
        long committedEpoch = controller.componentRuntime().capabilityPresentationEpoch();

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(energyPort.getEnergyStorage().insert(40L, transaction)).isEqualTo(40L);
        }

        var rolledBack = controller.currentRuntimeSnapshot();
        assertThat(energyPort.getEnergyStorage().amount()).isEqualTo(40L);
        assertThat(energyPort.snapshotNotifications).isEqualTo(1);
        assertThat(controller.componentRuntime().capabilityPresentationEpoch()).isEqualTo(committedEpoch);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch + 1L);
        assertThat(rolledBack.capabilityPresentations()).isSameAs(committed.capabilityPresentations());
        assertThat(rolledBack.capabilityPresentations().getFirst()).isSameAs(itemRow);
        assertThat(rolledBack.capabilityPresentations().get(1)).isSameAs(committed.capabilityPresentations().get(1));
        assertThat(rolledBack.capabilityPresentations().get(1).amount()).isEqualTo(40L);
        assertThat(before.capabilityPresentations().get(1).amount()).isZero();

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(energyPort.getEnergyStorage().extract(15L, transaction)).isEqualTo(15L);
            assertThat(energyPort.snapshotNotifications).isEqualTo(1);
            assertThat(controller.componentRuntime().capabilityPresentationEpoch()).isEqualTo(committedEpoch);
            assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch + 1L);
            transaction.commit();
        }
        var consumed = controller.currentRuntimeSnapshot();
        energyPort.flushStorageChanges();
        assertThat(energyPort.getEnergyStorage().amount()).isEqualTo(25L);
        assertThat(energyPort.snapshotNotifications).isEqualTo(2);
        assertThat(controller.componentRuntime().capabilityPresentationEpoch()).isEqualTo(committedEpoch + 1L);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch + 1L);
        assertThat(consumed.capabilityPresentations().getFirst()).isSameAs(itemRow);
        assertThat(consumed.capabilityPresentations().get(1).amount()).isEqualTo(25L);
        assertThat(committed.capabilityPresentations().get(1).amount()).isEqualTo(40L);
        long consumedEpoch = controller.componentRuntime().capabilityPresentationEpoch();
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(energyPort.getEnergyStorage().extract(10L, transaction)).isEqualTo(10L);
        }
        assertThat(energyPort.getEnergyStorage().amount()).isEqualTo(25L);
        assertThat(energyPort.snapshotNotifications).isEqualTo(2);
        assertThat(controller.componentRuntime().capabilityPresentationEpoch()).isEqualTo(consumedEpoch);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch + 1L);
        assertThat(controller.currentRuntimeSnapshot().capabilityPresentations())
                .isSameAs(consumed.capabilityPresentations());

        controller.notifyCapabilityPresentationChanged(POS.offset(100, 0, 0));
        var unknownSource = controller.currentRuntimeSnapshot();
        assertThat(unknownSource.capabilityPresentations().getFirst()).isNotSameAs(itemRow).isEqualTo(itemRow);
        assertThat(unknownSource.capabilityPresentations().get(1))
                .isNotSameAs(consumed.capabilityPresentations().get(1))
                .isEqualTo(consumed.capabilityPresentations().get(1));
        controller.notifyCapabilityPresentationChanged();
        var fullRefresh = controller.currentRuntimeSnapshot();
        assertThat(fullRefresh.capabilityPresentations().getFirst())
                .isNotSameAs(unknownSource.capabilityPresentations().getFirst()).isEqualTo(itemRow);
        assertThat(fullRefresh.capabilityPresentations().get(1))
                .isNotSameAs(unknownSource.capabilityPresentations().get(1))
                .isEqualTo(consumed.capabilityPresentations().get(1));

        controller.notifyResourceAvailability(ResourceAvailabilityNotifier.Reason.ENERGY_AVAILABLE,
                BuiltinCapabilityDefinitions.ENERGY_TYPE);
        var legacyAvailability = controller.currentRuntimeSnapshot();
        assertThat(legacyAvailability.capabilityPresentations().getFirst())
                .isNotSameAs(fullRefresh.capabilityPresentations().getFirst()).isEqualTo(itemRow);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch + 1L);
        LevelStub.setGameTime(level, level.getGameTime() + 1L);
        controller.notifyResourceAvailability(ResourceAvailabilityNotifier.Reason.ENERGY_AVAILABLE,
                BuiltinCapabilityDefinitions.ENERGY_TYPE, energyPort.getBlockPos());
        var nextTickAvailability = controller.currentRuntimeSnapshot();
        assertThat(nextTickAvailability.capabilityPresentations().getFirst())
                .isSameAs(legacyAvailability.capabilityPresentations().getFirst());
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch + 2L);
        controller.onRecipeInputsChanged();
        assertThat(controller.currentRuntimeSnapshot().capabilityPresentations().getFirst())
                .isNotSameAs(nextTickAvailability.capabilityPresentations().getFirst()).isEqualTo(itemRow);
    }

    /** Tracks real energy root-commit storage notifications.
     * @author howxu <dev@howxu.cn>
     */
    private static final class TrackingEnergyPort extends EnergyInputHatchBlockEntity {
        private int hashReads;

        @Override public int hashCode() { hashReads++; return super.hashCode(); }

        private int snapshotNotifications;
        private Runnable onSnapshot;

        private TrackingEnergyPort(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        protected void sendStorageSnapshot() {
            snapshotNotifications++;
            Runnable callback = onSnapshot;
            onSnapshot = null;
            if (callback != null) callback.run();
        }
    }

    /** Uses the production viewer gate; payload encoding would request a second capability snapshot.
     * @author howxu <dev@howxu.cn>
     */
    private static final class ViewerlessPort extends ExtendedItemBusBlockEntity {
        private int captures;

        private ViewerlessPort(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            captures++;
            return super.capabilitySnapshot();
        }
    }

    /** Exercises real availability delivery with a failing shared observer.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingController extends MachineControllerBlockEntity {
        private final List<Object> resources = new ArrayList<>();
        private final List<Object> deliveredResources = new ArrayList<>();
        private final List<ResourceAvailabilityNotifier.Reason> reasons = new ArrayList<>();
        private boolean failNextNotification;
        private Object failingResource;
        private Runnable onNotification;

        private RecordingController(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        public void notifyResourceAvailability(ResourceAvailabilityNotifier.Reason reason, Object resource, BlockPos source) {
            resources.add(resource);
            reasons.add(reason);
            Runnable callback = onNotification;
            onNotification = null;
            if (callback != null) callback.run();
            if (failingResource != null && failingResource.equals(resource)) {
                throw new IllegalStateException("broken resource observer");
            }
            if (failNextNotification) {
                failNextNotification = false;
                throw new IllegalStateException("broken shared observer");
            }
            super.notifyResourceAvailability(reason, resource, source);
            deliveredResources.add(resource);
        }
    }

    private static final class TrackingPort extends ExtendedItemBusBlockEntity {
        private int snapshotNotifications;
        private int recipeInputNotifications;

        private TrackingPort(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        protected void sendStorageSnapshot() {
            snapshotNotifications++;
        }

        @Override
        protected void notifyControllerOfInputChange() {
            recipeInputNotifications++;
        }
    }

    private static final class TrackingFluidPort extends ExtendedFluidHatchBlockEntity {
        private int recipeInputNotifications;

        private TrackingFluidPort(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        protected void notifyControllerOfInputChange() {
            recipeInputNotifications++;
        }
    }

    private static final class TrackingCombinedPort extends CombinedPortBlockEntity {
        private int recipeInputNotifications;
        private int snapshotNotifications;
        private Runnable onSnapshot;

        private TrackingCombinedPort(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        protected void sendStorageSnapshot() {
            snapshotNotifications++;
            Runnable callback = onSnapshot;
            onSnapshot = null;
            if (callback != null) callback.run();
        }

        @Override
        protected void notifyControllerOfInputChange() {
            recipeInputNotifications++;
        }
    }

    private static final class TrackingItemPort extends ItemInputBusBlockEntity {
        private int snapshotNotifications;
        private int saves;

        private TrackingItemPort(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        protected void sendStorageSnapshot() {
            snapshotNotifications++;
        }

        @Override
        public void setChanged() {
            saves++;
            super.setChanged();
        }
    }
}
