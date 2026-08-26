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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
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
            try (Transaction transaction = Transaction.openRoot()) {
                port.itemStorage().insert(0, ItemResource.of(Items.IRON_INGOT), 3L, transaction);
                transaction.commit();
            }
        }

        positions.forEach(helper::destroyBlock);
        helper.runAtTickTime(1, () -> {
            for (BlockPos position : positions) {
                helper.assertTrue(droppedIron(helper, position) == 3L,
                        "Item port removal drops every stored item at " + position);
            }
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

        ResourceHandler<ItemResource> items = capability(helper, itemPos, ModCapabilities.ITEM_BLOCK);
        ResourceHandler<FluidResource> fluids = capability(helper, fluidPos, ModCapabilities.FLUID_BLOCK);
        EnergyHandler energy = capability(helper, energyPos, ModCapabilities.ENERGY_BLOCK);
        helper.assertTrue(items != null, "Standalone extended item capability is present");
        helper.assertTrue(fluids != null, "Standalone extended fluid capability is present");
        helper.assertTrue(energy != null, "Standalone extended energy capability is present");

        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(items.insert(0, ItemResource.of(Items.IRON_INGOT), Integer.MAX_VALUE, transaction)
                            == Integer.MAX_VALUE,
                    "Standalone extended item capability accepts long-backed amounts");
            helper.assertTrue(fluids.insert(0, FluidResource.of(Fluids.WATER), Integer.MAX_VALUE, transaction)
                            == Integer.MAX_VALUE,
                    "Standalone extended fluid capability accepts long-backed amounts");
            helper.assertTrue(energy.insert(Integer.MAX_VALUE, transaction) == Integer.MAX_VALUE,
                    "Standalone extended energy capability accepts long-backed amounts");
            transaction.commit();
        }
        helper.succeed();
    }

    public void extendedCombinedPortTransfersBeyondIntegerRange(GameTestHelper helper) {
        BlockPos portPos = new BlockPos(0, 1, 0);
        helper.setBlock(portPos, ModBlocks.BLOCKS.get("extended_combined_input_advanced").get().defaultBlockState());
        ExtendedCombinedPortBlockEntity port = helper.getBlockEntity(portPos, ExtendedCombinedPortBlockEntity.class);
        BlockPos worldPos = helper.absolutePos(portPos);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(worldPos);

        ResourceHandler<ItemResource> itemHandler = ModCapabilities.ITEM_BLOCK.getCapability(
                helper.getLevel(), worldPos, helper.getLevel().getBlockState(worldPos), blockEntity, Direction.EAST);
        ResourceHandler<FluidResource> fluidHandler = ModCapabilities.FLUID_BLOCK.getCapability(
                helper.getLevel(), worldPos, helper.getLevel().getBlockState(worldPos), blockEntity, Direction.WEST);
        helper.assertTrue(itemHandler != null, "Extended combined item handler is exposed");
        helper.assertTrue(fluidHandler != null, "Extended combined fluid handler is exposed");

        try (Transaction transaction = Transaction.openRoot()) {
            itemHandler.insert(0, ItemResource.of(Items.IRON_INGOT), Integer.MAX_VALUE, transaction);
            itemHandler.insert(0, ItemResource.of(Items.IRON_INGOT), Integer.MAX_VALUE, transaction);
            fluidHandler.insert(0, FluidResource.of(Fluids.WATER), Integer.MAX_VALUE, transaction);
            fluidHandler.insert(0, FluidResource.of(Fluids.WATER), Integer.MAX_VALUE, transaction);
            transaction.commit();
        }

        helper.assertTrue(port.itemStorage().amount(0) > Integer.MAX_VALUE,
                "Extended item storage preserves cumulative amounts above int range");
        helper.assertTrue(port.fluidStorage().amount(0) > Integer.MAX_VALUE,
                "Extended fluid storage preserves cumulative amounts above int range");
        helper.succeed();
    }

    private static <T> T capability(GameTestHelper helper, BlockPos pos,
                                     net.neoforged.neoforge.capabilities.BlockCapability<T, Direction> capability) {
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
        try (Transaction transaction = Transaction.openRoot()) {
            port.fluidStorage().insert(0, FluidResource.of(Fluids.WATER), 1L, transaction);
            transaction.commit();
        }
        Identifier texture = MMCR.id("block/extended_combined_test_casing");
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
