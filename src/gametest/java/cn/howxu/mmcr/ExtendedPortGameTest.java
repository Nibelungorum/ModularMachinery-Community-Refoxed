package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.tile.ExtendedCombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.List;
import java.util.Map;

/**
 * World-level coverage for extended combined ports.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ExtendedPortGameTest {

    public void itemPortsDropStoredItemsWhenRemoved(GameTestHelper helper) {
        List<BlockPos> positions = List.of(
                new BlockPos(0, 1, 0), new BlockPos(3, 1, 0), new BlockPos(0, 1, 3));
        List<String> ids = List.of(
                "extended_item_input_bus_basic", "combined_input_basic", "extended_combined_input_advanced");
        for (int index = 0; index < positions.size(); index++) {
            BlockPos position = positions.get(index);
            helper.setBlock(position, ModBlocks.BLOCKS.get(ids.get(index)).get().defaultBlockState());
            IOPortBlockEntity port = helper.getBlockEntity(position, IOPortBlockEntity.class);
            port.itemStorage().setContents(0, new net.minecraft.world.item.ItemStack(Items.IRON_INGOT), 3L);
        }

        positions.forEach(helper::destroyBlock);
        helper.runAtTickTime(1, () -> {
            for (BlockPos position : positions) {
                long dropped = droppedIron(helper, position);
                helper.assertTrue(dropped == 3L,
                        "Item port removal drops every stored item at " + position + " actual=" + dropped);
            }
            helper.succeed();
        });
    }

    public void largeItemPortDropsUseLegalBoundedStacks(GameTestHelper helper) {
        BlockPos position = new BlockPos(0, 1, 0);
        helper.setBlock(position, ModBlocks.BLOCKS.get("extended_item_input_bus_basic").get().defaultBlockState());
        IOPortBlockEntity port = helper.getBlockEntity(position, IOPortBlockEntity.class);
        port.itemStorage().setContents(0, new net.minecraft.world.item.ItemStack(Items.IRON_INGOT),
                (long) Integer.MAX_VALUE + 1L);
        port.itemStorage().setContents(1, new net.minecraft.world.item.ItemStack(Items.GOLD_INGOT), Long.MAX_VALUE);

        helper.destroyBlock(position);
        helper.runAtTickTime(1, () -> {
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(position)).inflate(1.25D));
            long ironDrops = drops.stream().filter(entity -> entity.getItem().is(Items.IRON_INGOT)).count();
            long goldDrops = drops.stream().filter(entity -> entity.getItem().is(Items.GOLD_INGOT)).count();
            helper.assertTrue(ironDrops <= 1024 && goldDrops <= 1024,
                    "Large item drop count is bounded per slot iron=" + ironDrops + " gold=" + goldDrops);
            helper.assertTrue(drops.stream().allMatch(entity ->
                            entity.getItem().getCount() <= entity.getItem().getMaxStackSize()),
                    "Large item drops never exceed the item stack limit");
            helper.assertTrue(port.itemStorage().amount(0) == 0L && port.itemStorage().resource(0) == null
                            && port.itemStorage().amount(1) == 0L && port.itemStorage().resource(1) == null,
                    "Large item storage is cleared after removal");
            helper.succeed();
        });
    }

    public void standaloneExtendedPortsExposeItemFluidAndEnergyCapabilities(GameTestHelper helper) {
        BlockPos itemPos = new BlockPos(0, 1, 0);
        BlockPos fluidPos = new BlockPos(1, 1, 0);
        BlockPos energyPos = new BlockPos(2, 1, 0);
        helper.setBlock(itemPos, ModBlocks.BLOCKS.get("extended_item_input_bus_basic").get().defaultBlockState());
        helper.setBlock(fluidPos, ModBlocks.BLOCKS.get("extended_fluid_input_hatch_basic").get().defaultBlockState());
        helper.setBlock(energyPos, ModBlocks.BLOCKS.get("extended_energy_input_hatch_reinforced").get().defaultBlockState());

        IItemHandler items = capability(helper, itemPos, ModCapabilities.ITEM_BLOCK);
        IFluidHandler fluids = capability(helper, fluidPos, ModCapabilities.FLUID_BLOCK);
        IEnergyStorage energy = capability(helper, energyPos, ModCapabilities.ENERGY_BLOCK);
        helper.assertTrue(items != null, "Standalone extended item capability is present");
        helper.assertTrue(fluids != null, "Standalone extended fluid capability is present");
        helper.assertTrue(energy != null, "Standalone extended energy capability is present");

        helper.assertTrue(items.insertItem(0, new net.minecraft.world.item.ItemStack(Items.IRON_INGOT,
                        Integer.MAX_VALUE), false).isEmpty(),
                "Standalone extended item capability accepts bounded native stacks");
        helper.assertTrue(fluids.fill(new FluidStack(Fluids.WATER, Integer.MAX_VALUE),
                        IFluidHandler.FluidAction.EXECUTE) == Integer.MAX_VALUE,
                "Standalone extended fluid capability accepts bounded native stacks");
        helper.assertTrue(energy.receiveEnergy(Integer.MAX_VALUE, false) == Integer.MAX_VALUE,
                "Standalone extended energy capability accepts bounded native amounts");
        helper.succeed();
    }

    public void extendedCombinedPortTransfersBeyondIntegerRange(GameTestHelper helper) {
        BlockPos portPos = new BlockPos(0, 1, 0);
        helper.setBlock(portPos, ModBlocks.BLOCKS.get("extended_combined_input_advanced").get().defaultBlockState());
        ExtendedCombinedPortBlockEntity port = helper.getBlockEntity(portPos, ExtendedCombinedPortBlockEntity.class);
        BlockPos worldPos = helper.absolutePos(portPos);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(worldPos);

        IItemHandler itemHandler = ModCapabilities.ITEM_BLOCK.getCapability(
                helper.getLevel(), worldPos, helper.getLevel().getBlockState(worldPos), blockEntity, Direction.EAST);
        IFluidHandler fluidHandler = ModCapabilities.FLUID_BLOCK.getCapability(
                helper.getLevel(), worldPos, helper.getLevel().getBlockState(worldPos), blockEntity, Direction.WEST);
        helper.assertTrue(itemHandler != null, "Extended combined item handler is exposed");
        helper.assertTrue(fluidHandler != null, "Extended combined fluid handler is exposed");

        itemHandler.insertItem(0, new net.minecraft.world.item.ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE), false);
        itemHandler.insertItem(0, new net.minecraft.world.item.ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE), false);
        fluidHandler.fill(new FluidStack(Fluids.WATER, Integer.MAX_VALUE), IFluidHandler.FluidAction.EXECUTE);
        fluidHandler.fill(new FluidStack(Fluids.WATER, Integer.MAX_VALUE), IFluidHandler.FluidAction.EXECUTE);

        helper.assertTrue(port.itemStorage().amount(0) > Integer.MAX_VALUE,
                "Extended item storage preserves cumulative amounts above int range");
        helper.assertTrue(port.fluidStorage().amount(0) > Integer.MAX_VALUE,
                "Extended fluid storage preserves cumulative amounts above int range");
        helper.succeed();
    }

    private static <T> T capability(GameTestHelper helper, BlockPos pos,
                                     BlockCapability<T, Direction> capability) {
        BlockPos worldPos = helper.absolutePos(pos);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(worldPos);
        return capability.getCapability(helper.getLevel(), worldPos, helper.getLevel().getBlockState(worldPos),
                blockEntity, Direction.UP);
    }

    private static long droppedIron(GameTestHelper helper, BlockPos position) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(helper.absolutePos(position)).inflate(1.25D)).stream()
                .filter(entity -> entity.getItem().is(Items.IRON_INGOT))
                .mapToLong(entity -> entity.getItem().getCount())
                .sum();
    }

    public void extendedCombinedPortPublishesFormedAppearance(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(0, 1, 0);
        BlockPos portPos = controllerPos.relative(Direction.EAST);
        var controllerBlock = ModBlocks.controllerFor(MMCR.id("test_cube")).get();
        var portBlock = ModBlocks.BLOCKS.get("extended_combined_input_advanced").get();
        helper.setBlock(controllerPos, controllerBlock.defaultBlockState().setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(portPos, portBlock.defaultBlockState());

        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        ExtendedCombinedPortBlockEntity port = helper.getBlockEntity(portPos, ExtendedCombinedPortBlockEntity.class);
        port.fluidStorage().setContents(0, new FluidStack(Fluids.WATER, 1), 1L);
        ResourceLocation texture = MMCR.id("block/extended_combined_test_casing");
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("extended_combined_appearance_test"),
                "extended combined appearance test",
                new BlockArray(Map.of(portPos.subtract(controllerPos), new BlockPredicate.OfBlock(portBlock))),
                MachineControllerSpec.defaultsFor(MMCR.id("test_cube")),
                new MachineAppearanceSpec(MMCR.id("basic_casing"), MMCR.id("block/basic_casing"), texture),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of());
        controller.setMachine(machine);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Extended combined port controller forms");
            helper.assertTrue(controller.runtimeSnapshot().linkedPortPositions().contains(port.getBlockPos()),
                    "Formed controller links the extended combined port");
            helper.assertTrue(port.appearanceBaseTexture().equals(texture),
                    "Extended combined port receives formed appearance texture");
            helper.assertTrue(port.getModelData().get(MachineModelDataKeys.PORT_BASE_TEXTURE).equals(texture),
                    "Extended combined port model data exposes formed appearance texture");
            helper.succeed();
        });
    }
}
