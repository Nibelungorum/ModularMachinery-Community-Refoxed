package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PortMenuDirectionGameTest {

    public void itemBusMenuAllowsContainerSlotTransfers(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(0, 2, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());

        Player player = null;
        Inventory inventory = new Inventory(null, null);
        ItemBusMenu inputMenu = new ItemBusMenu(1, inventory, helper.getBlockEntity(inputPos, ItemBusBlockEntity.class));
        ItemBusMenu outputMenu = new ItemBusMenu(2, inventory, helper.getBlockEntity(outputPos, ItemBusBlockEntity.class));

        Slot inputSlot = inputMenu.getSlot(0);
        helper.assertTrue(inputSlot.mayPlace(new ItemStack(Items.IRON_INGOT)), "Input slot accepts placement");

        helper.getBlockEntity(outputPos, ItemBusBlockEntity.class).getItemStackHandler(null)
                .setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
        Slot outputSlot = outputMenu.getSlot(0);
        helper.assertTrue(outputSlot.mayPlace(new ItemStack(Items.IRON_INGOT)), "Output slot accepts placement");
        helper.assertTrue(outputSlot.mayPickup(player), "Output slot accepts pickup");

        inventory.setItem(0, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(!inputMenu.quickMoveStack(player, 33).isEmpty(), "Player stack moves into input port");
        helper.assertTrue(inputSlot.mayPickup(player), "Input slot accepts pickup after it contains an item");
        helper.assertTrue(!inputMenu.quickMoveStack(player, 0).isEmpty(), "Input port stack moves to player");

        helper.assertTrue(!outputMenu.quickMoveStack(player, 0).isEmpty(), "Output port stack moves to player");
        inventory.setItem(1, new ItemStack(Items.COPPER_INGOT));
        helper.assertTrue(!outputMenu.quickMoveStack(player, 34).isEmpty(), "Player stack moves into output port");

        helper.succeed();
    }
}
