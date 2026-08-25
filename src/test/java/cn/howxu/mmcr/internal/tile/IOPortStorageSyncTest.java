package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.ItemResource;
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

        port.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));

        assertThat(port.snapshotNotifications).isEqualTo(1);
    }

    private static final class TrackingPort extends ExtendedItemBusBlockEntity {
        private int snapshotNotifications;

        private TrackingPort(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        protected void sendStorageSnapshot() {
            snapshotNotifications++;
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
