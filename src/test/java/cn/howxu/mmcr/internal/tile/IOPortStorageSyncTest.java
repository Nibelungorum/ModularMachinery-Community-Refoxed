package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

        ((LongItemStorage) port.itemHandler()).setContents(0, new ItemStack(Items.IRON_INGOT), 1L);
        assertThat(port.snapshotNotifications).isEqualTo(1);

        port.setAutoIOEnabled(true);
        port.setChanged();
        assertThat(port.snapshotNotifications).isEqualTo(1);
    }

    @Test
    void ordinary_item_storage_mutation_uses_the_same_notification_path() {
        TrackingItemPort port = new TrackingItemPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.ITEM_INPUT.id()).get().defaultBlockState());

        port.itemHandler().insertItem(0, new ItemStack(Items.IRON_INGOT), false);

        assertThat(port.snapshotNotifications).isEqualTo(1);
    }

    @Test
    void every_input_item_or_fluid_storage_host_notifies_recipe_inputs() {
        TrackingPort extendedItem = new TrackingPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.EXTENDED_ITEM_INPUT.id()).get().defaultBlockState());
        ((LongItemStorage) extendedItem.itemHandler()).setContents(0, new ItemStack(Items.IRON_INGOT), 1L);

        TrackingFluidPort extendedFluid = new TrackingFluidPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.EXTENDED_FLUID_INPUT.id()).get().defaultBlockState());
        extendedFluid.fluidHandler(Direction.NORTH).setContents(0, new FluidStack(Fluids.WATER, 1), 1L);

        TrackingCombinedPort combined = new TrackingCombinedPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.COMBINED_INPUT.id()).get().defaultBlockState());
        combined.itemHandler().insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        combined.fluidHandler(Direction.NORTH).setContents(0, new FluidStack(Fluids.WATER, 1), 1L);

        assertThat(extendedItem.recipeInputNotifications).isEqualTo(1);
        assertThat(extendedFluid.recipeInputNotifications).isEqualTo(1);
        assertThat(combined.recipeInputNotifications).isEqualTo(2);
    }

    @Test
    void native_energy_changes_refresh_only_their_source_and_simulation_preserves_published_rows() {
        BlockPos controllerPos = POS.offset(5, 0, 0);
        TrackingPort itemPort = new TrackingPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.EXTENDED_ITEM_INPUT.id()).get().defaultBlockState());
        TrackingEnergyPort energyPort = new TrackingEnergyPort(POS.offset(2, 0, 0),
                ModBlocks.BLOCKS.get(PortKinds.ENERGY_INPUT.id()).get().defaultBlockState());
        ((LongItemStorage) itemPort.itemHandler()).setContents(0,
                new ItemStack(Items.IRON_INGOT), 7L);
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
        assertThat(itemRow.slots().getFirst().resourceId()).isEqualTo(Items.IRON_INGOT.toString());
        assertThat(itemRow.slots().getFirst().amount()).isEqualTo(7L);
        assertThat(energyRow.typeId()).isEqualTo(BuiltinCapabilityDefinitions.ENERGY_TYPE.id());
        assertThat(energyRow.amount()).isZero();
        // setMachine notified the disconnected -> notRequired module transition in the setup tick.
        LevelStub.setGameTime(level, level.getGameTime() + 1L);
        long beforeEpoch = controller.componentRuntime().capabilityPresentationEpoch();
        long beforeAvailabilityEpoch = controller.resourceAvailabilityEpoch();

        assertThat(energyPort.energyStorage().insertLong(40L, true)).isEqualTo(40L);
        assertThat(energyPort.snapshotNotifications).isZero();
        assertThat(controller.componentRuntime().capabilityPresentationEpoch()).isEqualTo(beforeEpoch);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch);
        assertThat(energyPort.energyStorage().insertLong(40L, false)).isEqualTo(40L);

        var committed = controller.currentRuntimeSnapshot();
        assertThat(energyPort.snapshotNotifications).isEqualTo(1);
        assertThat(energyPort.energyStorage().getAmountAsLong()).isEqualTo(40L);
        assertThat(controller.componentRuntime().capabilityPresentationEpoch()).isEqualTo(beforeEpoch + 2L);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch + 1L);
        assertThat(committed.capabilityPresentations().getFirst()).isSameAs(itemRow);
        assertThat(committed.capabilityPresentations().get(1)).isNotSameAs(energyRow);
        assertThat(committed.capabilityPresentations().get(1).amount()).isEqualTo(40L);
        assertThat(before.capabilityPresentations().get(1).amount()).isZero();
        assertThat(before.capabilityPresentations().getFirst().slots().getFirst().amount()).isEqualTo(7L);
        long committedEpoch = controller.componentRuntime().capabilityPresentationEpoch();

        assertThat(energyPort.energyStorage().insertLong(40L, true)).isEqualTo(40L);

        var simulated = controller.currentRuntimeSnapshot();
        assertThat(energyPort.energyStorage().getAmountAsLong()).isEqualTo(40L);
        assertThat(energyPort.snapshotNotifications).isEqualTo(1);
        assertThat(controller.componentRuntime().capabilityPresentationEpoch()).isEqualTo(committedEpoch);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch + 1L);
        assertThat(simulated.capabilityPresentations()).isSameAs(committed.capabilityPresentations());
        assertThat(simulated.capabilityPresentations().getFirst()).isSameAs(itemRow);
        assertThat(simulated.capabilityPresentations().get(1)).isSameAs(committed.capabilityPresentations().get(1));
        assertThat(simulated.capabilityPresentations().get(1).amount()).isEqualTo(40L);
        assertThat(before.capabilityPresentations().get(1).amount()).isZero();

        assertThat(energyPort.energyStorage().extractLong(15L, true)).isEqualTo(15L);
        assertThat(energyPort.snapshotNotifications).isEqualTo(1);
        assertThat(controller.componentRuntime().capabilityPresentationEpoch()).isEqualTo(committedEpoch);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch + 1L);
        assertThat(energyPort.energyStorage().extractLong(15L, false)).isEqualTo(15L);
        var consumed = controller.currentRuntimeSnapshot();
        assertThat(energyPort.energyStorage().getAmountAsLong()).isEqualTo(25L);
        assertThat(energyPort.snapshotNotifications).isEqualTo(2);
        assertThat(controller.componentRuntime().capabilityPresentationEpoch()).isEqualTo(committedEpoch + 1L);
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeAvailabilityEpoch + 1L);
        assertThat(consumed.capabilityPresentations().getFirst()).isSameAs(itemRow);
        assertThat(consumed.capabilityPresentations().get(1).amount()).isEqualTo(25L);
        assertThat(committed.capabilityPresentations().get(1).amount()).isEqualTo(40L);
        long consumedEpoch = controller.componentRuntime().capabilityPresentationEpoch();
        assertThat(energyPort.energyStorage().extractLong(10L, true)).isEqualTo(10L);
        assertThat(energyPort.energyStorage().getAmountAsLong()).isEqualTo(25L);
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

    /** Tracks real native energy storage notifications.
     * @author howxu <dev@howxu.cn>
     */
    private static final class TrackingEnergyPort extends EnergyInputHatchBlockEntity {
        private int snapshotNotifications;

        private TrackingEnergyPort(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        protected void sendStorageSnapshot() {
            snapshotNotifications++;
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

        private TrackingCombinedPort(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        protected void notifyControllerOfInputChange() {
            recipeInputNotifications++;
        }
    }

    private static final class TrackingItemPort extends ItemInputBusBlockEntity {
        private int snapshotNotifications;

        private TrackingItemPort(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        protected void sendStorageSnapshot() {
            snapshotNotifications++;
        }
    }
}
