package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

        ((LongResourceStorage<ItemResource>) port.itemStorage()).setContents(0,
                ItemResource.of(net.minecraft.world.item.Items.IRON_INGOT), 1L);
        assertThat(port.snapshotNotifications).isEqualTo(1);

        port.setAutoIOEnabled(true);
        port.setChanged();
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

        assertThat(port.snapshotNotifications).isEqualTo(1);
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
