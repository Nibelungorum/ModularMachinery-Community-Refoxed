package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

@GameTestHolder(MMCR.MODID)
public class ItemBusCapabilityGameTest {

    @GameTest(template = "minecraft:empty")
    public static void itemBusAcceptsItems(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos outputPos = new BlockPos(0, 2, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());

        BlockPos inputWorldPos = helper.absolutePos(inputPos);
        BlockPos outputWorldPos = helper.absolutePos(outputPos);
        BlockEntity inputBe = helper.getLevel().getBlockEntity(inputWorldPos);
        BlockEntity outputBe = helper.getLevel().getBlockEntity(outputWorldPos);

        ItemBusBlockEntity inputBus = helper.getBlockEntity(inputPos, ItemBusBlockEntity.class);
        ItemBusBlockEntity outputBus = helper.getBlockEntity(outputPos, ItemBusBlockEntity.class);

        helper.assertTrue(inputBus.ioType() == IOType.INPUT, "Input bus is INPUT");
        helper.assertTrue(outputBus.ioType() == IOType.OUTPUT, "Output bus is OUTPUT");

        ResourceHandler<ItemResource> input = ModCapabilities.ITEM_BLOCK.getCapability(
                helper.getLevel(), inputWorldPos, helper.getLevel().getBlockState(inputWorldPos), inputBe, Direction.UP);
        ResourceHandler<ItemResource> output = ModCapabilities.ITEM_BLOCK.getCapability(
                helper.getLevel(), outputWorldPos, helper.getLevel().getBlockState(outputWorldPos), outputBe, Direction.UP);

        helper.assertTrue(input != null, "Input item capability is present");
        helper.assertTrue(output != null, "Output item capability is present");

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = input.insert(0, ItemResource.of(Items.IRON_INGOT), 4, tx);
            int extracted = input.extract(0, ItemResource.of(Items.IRON_INGOT), 1, tx);
            helper.assertTrue(inserted == 4, "Input capability inserts");
            helper.assertTrue(extracted == 1, "Input capability extracts");
            tx.commit();
        }

        outputBus.getItemStackHandler(null).insertItem(0, new ItemStack(Items.IRON_INGOT, 8), false);

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = output.insert(0, ItemResource.of(Items.IRON_INGOT), 1, tx);
            int extracted = output.extract(0, ItemResource.of(Items.IRON_INGOT), 4, tx);
            helper.assertTrue(inserted == 0, "Output capability rejects insertion");
            helper.assertTrue(extracted == 4, "Output capability extracts");
            tx.commit();
        }

        helper.succeed();
    }
}
