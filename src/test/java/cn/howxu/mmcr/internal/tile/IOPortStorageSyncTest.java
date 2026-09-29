package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
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

        ((LongItemStorage) port.itemStorage()).setContents(0, new ItemStack(Items.IRON_INGOT), 1L);
        assertThat(port.snapshotNotifications).isEqualTo(1);

        port.setAutoIOEnabled(true);
        port.setChanged();
        assertThat(port.snapshotNotifications).isEqualTo(1);
    }

    @Test
    void ordinary_item_storage_mutation_uses_the_same_notification_path() {
        TrackingItemPort port = new TrackingItemPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.ITEM_INPUT.id()).get().defaultBlockState());

        port.itemStorage().insertItem(0, new ItemStack(Items.IRON_INGOT), false);

        assertThat(port.snapshotNotifications).isEqualTo(1);
    }

    @Test
    void every_input_item_or_fluid_storage_host_notifies_recipe_inputs() {
        TrackingPort extendedItem = new TrackingPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.EXTENDED_ITEM_INPUT.id()).get().defaultBlockState());
        ((LongItemStorage) extendedItem.itemStorage()).setContents(0, new ItemStack(Items.IRON_INGOT), 1L);

        TrackingFluidPort extendedFluid = new TrackingFluidPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.EXTENDED_FLUID_INPUT.id()).get().defaultBlockState());
        extendedFluid.fluidStorage().setContents(0, new FluidStack(Fluids.WATER, 1), 1L);

        TrackingCombinedPort combined = new TrackingCombinedPort(POS,
                ModBlocks.BLOCKS.get(PortKinds.COMBINED_INPUT.id()).get().defaultBlockState());
        combined.itemStorage().insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        combined.fluidStorage().setContents(0, new FluidStack(Fluids.WATER, 1), 1L);

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
