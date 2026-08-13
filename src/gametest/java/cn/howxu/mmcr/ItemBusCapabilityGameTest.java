package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class ItemBusCapabilityGameTest {

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
            int inserted = 0;
            for (int slot = 0; slot < 4; slot++) {
                inserted += input.insert(slot, ItemResource.of(Items.IRON_INGOT), 1, tx);
            }
            int extracted = input.extract(0, ItemResource.of(Items.IRON_INGOT), 1, tx);
            helper.assertTrue(inserted == 4, "Input capability inserts");
            helper.assertTrue(extracted == 1, "Input capability extracts");
            tx.commit();
        }

        for (int slot = 0; slot < 4; slot++) {
            outputBus.getItemStackHandler(null).insertItem(slot, new ItemStack(Items.IRON_INGOT), false);
        }

        try (Transaction tx = Transaction.openRoot()) {
            int inserted = output.insert(0, ItemResource.of(Items.IRON_INGOT), 1, tx);
            int extracted = 0;
            for (int slot = 0; slot < 4; slot++) {
                extracted += output.extract(slot, ItemResource.of(Items.IRON_INGOT), 1, tx);
            }
            helper.assertTrue(inserted == 0, "Output capability rejects inserts");
            helper.assertTrue(extracted == 4, "Output capability extracts");
            tx.commit();
        }

        helper.succeed();
    }

    public static void itemBusCapabilityCacheFollowsAutoIOSideConfig(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());

        BlockPos inputWorldPos = helper.absolutePos(inputPos);
        ItemBusBlockEntity inputBus = helper.getBlockEntity(inputPos, ItemBusBlockEntity.class);
        BlockCapabilityCache<ResourceHandler<ItemResource>, Direction> cache = BlockCapabilityCache.create(
                ModCapabilities.ITEM_BLOCK, helper.getLevel(), inputWorldPos, Direction.UP);

        helper.assertTrue(cache.getCapability() != null, "Enabled side has cached item capability by default");
        inputBus.setAutoIOSide(Direction.UP, false);
        helper.assertTrue(cache.getCapability() == null, "Disabling side invalidates cache and removes item capability");
        inputBus.setAutoIOSide(Direction.UP, true);
        helper.assertTrue(cache.getCapability() != null, "Re-enabling side invalidates cache and exposes item capability");

        helper.succeed();
    }
}
