package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

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

        var inputCapability = inputBus.capabilitySnapshot().capabilities().getFirst();
        var outputCapability = outputBus.capabilitySnapshot().capabilities().getFirst();
        helper.assertTrue(inputCapability.directions().supports(IOType.INPUT), "Input capability accepts INPUT");
        helper.assertTrue(outputCapability.directions().supports(IOType.OUTPUT), "Output capability accepts OUTPUT");

        IItemHandler input = Capabilities.ItemHandler.BLOCK.getCapability(
                helper.getLevel(), inputWorldPos, helper.getLevel().getBlockState(inputWorldPos), inputBe, Direction.UP);
        IItemHandler output = Capabilities.ItemHandler.BLOCK.getCapability(
                helper.getLevel(), outputWorldPos, helper.getLevel().getBlockState(outputWorldPos), outputBe, Direction.UP);

        helper.assertTrue(input != null, "Input item capability is present");
        helper.assertTrue(output != null, "Output item capability is present");

        int inserted = 0;
        for (int slot = 0; slot < 4; slot++) {
            inserted += input.insertItem(slot, new ItemStack(Items.IRON_INGOT), false).isEmpty() ? 1 : 0;
        }
        ItemStack extracted = input.extractItem(0, 1, false);
        helper.assertTrue(inserted == 4, "Input capability inserts");
        helper.assertTrue(extracted.isEmpty(), "Input capability rejects extraction");

        for (int slot = 0; slot < 4; slot++) {
            outputBus.itemStorage().setContents(slot, new ItemStack(Items.IRON_INGOT), 1L);
        }

        ItemStack insertionRemainder = output.insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        int extractedCount = 0;
        for (int slot = 0; slot < 4; slot++) {
            extractedCount += output.extractItem(slot, 1, false).getCount();
        }
        helper.assertTrue(insertionRemainder.getCount() == 1, "Output capability rejects inserts");
        helper.assertTrue(extractedCount == 4, "Output capability extracts");

        helper.succeed();
    }

    public static void itemBusDoesNotStackNonStackableItems(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        ItemBusBlockEntity bus = helper.getBlockEntity(pos, ItemBusBlockEntity.class);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Sharpness II"));

        ItemStack remainder = bus.itemStorage().insertItem(0, sword.copyWithCount(2), false);
        helper.assertTrue(remainder.getCount() == 1, "Non-stackable items are limited to one item per UI slot");

        helper.assertTrue(bus.itemStorage().amount(0) == 1L,
                "Non-stackable item storage does not exceed the item stack limit");
        helper.succeed();
    }

    public static void itemBusCapabilityCacheFollowsAutoIOSideConfig(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());

        BlockPos inputWorldPos = helper.absolutePos(inputPos);
        ItemBusBlockEntity inputBus = helper.getBlockEntity(inputPos, ItemBusBlockEntity.class);
        BlockCapabilityCache<IItemHandler, Direction> cache = BlockCapabilityCache.create(
                Capabilities.ItemHandler.BLOCK, helper.getLevel(), inputWorldPos, Direction.UP);

        helper.assertTrue(cache.getCapability() != null, "Enabled side has cached item capability by default");
        inputBus.setAutoIOSide(Direction.UP, false);
        helper.assertTrue(cache.getCapability() == null, "Disabling side invalidates cache and removes item capability");
        inputBus.setAutoIOSide(Direction.UP, true);
        helper.assertTrue(cache.getCapability() != null, "Re-enabling side invalidates cache and exposes item capability");

        helper.succeed();
    }

    public static void itemBusDropsStoredItemsWhenRemoved(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        ItemBusBlockEntity bus = helper.getBlockEntity(pos, ItemBusBlockEntity.class);
        bus.itemStorage().setContents(0, new ItemStack(Items.IRON_INGOT), 3L);

        helper.destroyBlock(pos);
        helper.runAfterDelay(1, () -> {
            long dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(helper.absolutePos(pos)).inflate(1))
                    .stream()
                    .filter(entity -> entity.getItem().is(Items.IRON_INGOT))
                    .mapToLong(entity -> entity.getItem().getCount())
                    .sum();
            helper.assertTrue(dropped == 3, "Removing an item bus drops every stored item");
            helper.succeed();
        });
    }

    public static void dynamicControllerDropsItself(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        var controller = ModBlocks.controllerFor(MMCR.id("test_cube")).get();
        helper.setBlock(pos, controller.defaultBlockState());

        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        helper.runAfterDelay(1, () -> {
            long dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(helper.absolutePos(pos)).inflate(1))
                    .stream()
                    .filter(entity -> entity.getItem().is(controller.asItem()))
                    .mapToLong(entity -> entity.getItem().getCount())
                    .sum();
            helper.assertTrue(dropped == 1, "Removing a dynamic controller drops itself without a loot table");
            helper.succeed();
        });
    }
}
