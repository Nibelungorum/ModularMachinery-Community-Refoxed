package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(MMCR.MODID)
public class ItemBusCapabilityGameTest {

    @GameTest(template = "minecraft:empty")
    public static void itemBusAcceptsItems(GameTestHelper helper) {
        BlockPos busPos = new BlockPos(0, 1, 0);
        helper.setBlock(busPos, ModBlocks.BLOCKS.get("io_port_item_basic").get().defaultBlockState());
        ItemBusBlockEntity be = helper.getBlockEntity(busPos, ItemBusBlockEntity.class);

        IItemHandler h = be.getItemHandler(null);
        h.insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);

        helper.assertTrue(h.getStackInSlot(0).getCount() == 4, "Inserted 4 ingots");
        helper.succeed();
    }
}
