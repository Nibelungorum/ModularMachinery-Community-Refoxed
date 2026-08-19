package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * @author howxu <dev@howxu.cn>
 */
public class AutoIOGameTest {

    public void itemInputAutoImports(GameTestHelper helper) {
        BlockPos inputPos = new BlockPos(0, 1, 0);
        BlockPos chestPos = inputPos.relative(Direction.EAST);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.setBlock(chestPos, Blocks.CHEST.defaultBlockState());

        ItemBusBlockEntity inputBus = helper.getBlockEntity(inputPos, ItemBusBlockEntity.class);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 3));

        inputBus.toggleAutoIOEnabled();
        inputBus.setAllAutoIOSides(false);
        inputBus.setAutoIOSide(Direction.EAST, true);
        helper.runAtTickTime(60, inputBus::serverTick);
        helper.runAtTickTime(80, () -> {
            ItemStack imported = inputBus.getItemStackHandler(Direction.EAST).getStackInSlot(0);
            helper.assertTrue(imported.is(Items.IRON_INGOT), "Input bus imports iron ingots from east chest");
            helper.assertTrue(imported.getCount() > 0, "Input bus receives items from east chest");
            helper.assertTrue(chest.getItem(0).getCount() < 3, "Source chest loses items to auto input");
            helper.succeed();
        });
    }

    public void fluidOutputAutoExports(GameTestHelper helper) {
        BlockPos outputPos = new BlockPos(0, 1, 0);
        BlockPos receiverPos = outputPos.relative(Direction.EAST);
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState());
        helper.setBlock(receiverPos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());

        FluidHatchBlockEntity outputHatch = helper.getBlockEntity(outputPos, FluidHatchBlockEntity.class);
        FluidHatchBlockEntity receiver = helper.getBlockEntity(receiverPos, FluidHatchBlockEntity.class);
        outputHatch.getMutableFluidStorage().forceInsert(new FluidStack(Fluids.WATER, 1000), false);

        outputHatch.toggleAutoIOEnabled();
        outputHatch.setAllAutoIOSides(false);
        outputHatch.setAutoIOSide(Direction.EAST, true);
        helper.runAtTickTime(60, outputHatch::serverTick);
        helper.runAtTickTime(80, () -> {
            FluidStack exported = receiver.getMutableFluidStorage().getFluidStack();
            helper.assertTrue(exported.getFluid() == Fluids.WATER, "Fluid output hatch exports water east");
            helper.assertTrue(exported.getAmount() > 0, "Fluid output hatch moves water into east receiver");
            helper.assertTrue(outputHatch.getMutableFluidStorage().getAmountAsLong() < 1000, "Fluid output hatch loses water to auto output");
            helper.succeed();
        });
    }

    public void energyOutputAutoExports(GameTestHelper helper) {
        BlockPos outputPos = new BlockPos(0, 1, 0);
        BlockPos receiverPos = outputPos.relative(Direction.EAST);
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("energy_output_hatch").get().defaultBlockState());
        helper.setBlock(receiverPos, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());

        EnergyHatchBlockEntity outputHatch = helper.getBlockEntity(outputPos, EnergyHatchBlockEntity.class);
        EnergyHatchBlockEntity receiver = helper.getBlockEntity(receiverPos, EnergyHatchBlockEntity.class);
        var outputStorage = outputHatch.getMutableEnergyStorage();
        outputStorage.forceInsert(700, false);

        outputHatch.toggleAutoIOEnabled();
        outputHatch.setAllAutoIOSides(false);
        outputHatch.setAutoIOSide(Direction.EAST, true);
        helper.runAtTickTime(60, outputHatch::serverTick);
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(receiver.getEnergyHandler(Direction.WEST).getAmountAsLong() > 0, "Energy output hatch exports FE east");
            helper.assertTrue(outputStorage.getAmountAsLong() < 700, "Energy output hatch loses FE to auto output");
            helper.succeed();
        });
    }

    public void itemInputEjectionStopsAfterFirstTarget(GameTestHelper helper) {
        ItemBusBlockEntity source = placeItemInputPort(helper, new BlockPos(0, 1, 0));
        ChestBlockEntity firstTarget = placeChest(helper, new BlockPos(1, 1, 0));
        ChestBlockEntity secondTarget = placeChest(helper, new BlockPos(-1, 1, 0));
        source.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 2));

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(source.ejectContents(), "Item input bus ejects its contents");
            int firstCount = firstTarget.getItem(0).getCount();
            int secondCount = secondTarget.getItem(0).getCount();
            helper.assertTrue((firstCount == 2 && secondCount == 0) || (firstCount == 0 && secondCount == 2),
                    "Exactly one adjacent item target receives all contents");
            helper.assertTrue(source.getItemStackHandler(null).getStackInSlot(0).isEmpty(), "Item input bus is empty after a complete first transfer");
            helper.succeed();
        });
    }

    public void itemInputEjectionContinuesAfterPartialTarget(GameTestHelper helper) {
        ItemBusBlockEntity source = placeItemInputPort(helper, new BlockPos(0, 1, 0));
        ChestBlockEntity firstTarget = placeChest(helper, new BlockPos(1, 1, 0));
        ChestBlockEntity secondTarget = placeChest(helper, new BlockPos(-1, 1, 0));
        source.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 2));
        fillChestExceptOne(firstTarget);
        fillChestExceptOne(secondTarget);

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(source.ejectContents(), "Item input bus ejects to partially available targets");
            helper.assertTrue(firstTarget.getItem(0).getCount() == 64,
                    "First item target receives its remaining capacity: " + firstTarget.getItem(0).getCount());
            helper.assertTrue(secondTarget.getItem(0).getCount() == 64,
                    "Second item target receives the remaining item: " + secondTarget.getItem(0).getCount());
            helper.assertTrue(source.getItemStackHandler(null).getStackInSlot(0).isEmpty(), "Item input bus is empty after both partial transfers");
            helper.succeed();
        });
    }

    public void itemInputEjectionPreservesRemainder(GameTestHelper helper) {
        ItemBusBlockEntity source = placeItemInputPort(helper, new BlockPos(0, 1, 0));
        ChestBlockEntity firstTarget = placeChest(helper, new BlockPos(1, 1, 0));
        ChestBlockEntity secondTarget = placeChest(helper, new BlockPos(-1, 1, 0));
        source.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));
        fillChestExceptOne(firstTarget);
        fillChestExceptOne(secondTarget);

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(source.ejectContents(), "Item input bus ejects until adjacent capacity is exhausted");
            helper.assertTrue(firstTarget.getItem(0).getCount() == 64,
                    "First item target is filled: " + firstTarget.getItem(0).getCount());
            helper.assertTrue(secondTarget.getItem(0).getCount() == 64,
                    "Second item target is filled: " + secondTarget.getItem(0).getCount());
            helper.assertTrue(source.getItemStackHandler(null).getStackInSlot(0).getCount() == 1, "Item input bus preserves the remaining item");
            helper.succeed();
        });
    }

    public void fluidInputEjectionStopsAfterFirstTarget(GameTestHelper helper) {
        FluidHatchBlockEntity source = placeFluidInputPort(helper, new BlockPos(0, 1, 0));
        FluidHatchBlockEntity firstTarget = placeFluidInputPort(helper, new BlockPos(1, 1, 0));
        FluidHatchBlockEntity secondTarget = placeFluidInputPort(helper, new BlockPos(-1, 1, 0));
        source.getMutableFluidStorage().forceInsert(new FluidStack(Fluids.WATER, 2), false);

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(source.ejectContents(), "Fluid input hatch ejects its contents");
            long firstAmount = firstTarget.getMutableFluidStorage().getAmountAsLong();
            long secondAmount = secondTarget.getMutableFluidStorage().getAmountAsLong();
            helper.assertTrue((firstAmount == 2 && secondAmount == 0) || (firstAmount == 0 && secondAmount == 2),
                    "Exactly one adjacent fluid target receives all contents");
            helper.assertTrue(source.getMutableFluidStorage().isEmpty(), "Fluid input hatch is empty after a complete first transfer");
            helper.succeed();
        });
    }

    public void fluidInputEjectionContinuesAfterPartialTarget(GameTestHelper helper) {
        FluidHatchBlockEntity source = placeFluidInputPort(helper, new BlockPos(0, 1, 0));
        FluidHatchBlockEntity firstTarget = placeFluidInputPort(helper, new BlockPos(1, 1, 0));
        FluidHatchBlockEntity secondTarget = placeFluidInputPort(helper, new BlockPos(-1, 1, 0));
        source.getMutableFluidStorage().forceInsert(new FluidStack(Fluids.WATER, 2), false);
        firstTarget.getMutableFluidStorage().forceInsert(new FluidStack(Fluids.WATER, 7999), false);
        secondTarget.getMutableFluidStorage().forceInsert(new FluidStack(Fluids.WATER, 7999), false);

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(source.ejectContents(), "Fluid input hatch ejects to partially available targets");
            helper.assertTrue(firstTarget.getMutableFluidStorage().getAmountAsLong() == 8000, "First fluid target receives its remaining capacity");
            helper.assertTrue(secondTarget.getMutableFluidStorage().getAmountAsLong() == 8000, "Second fluid target receives the remaining fluid");
            helper.assertTrue(source.getMutableFluidStorage().isEmpty(), "Fluid input hatch is empty after both partial transfers");
            helper.succeed();
        });
    }

    public void fluidInputEjectionPreservesRemainder(GameTestHelper helper) {
        FluidHatchBlockEntity source = placeFluidInputPort(helper, new BlockPos(0, 1, 0));
        FluidHatchBlockEntity firstTarget = placeFluidInputPort(helper, new BlockPos(1, 1, 0));
        FluidHatchBlockEntity secondTarget = placeFluidInputPort(helper, new BlockPos(-1, 1, 0));
        source.getMutableFluidStorage().forceInsert(new FluidStack(Fluids.WATER, 3), false);
        firstTarget.getMutableFluidStorage().forceInsert(new FluidStack(Fluids.WATER, 7999), false);
        secondTarget.getMutableFluidStorage().forceInsert(new FluidStack(Fluids.WATER, 7999), false);

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(source.ejectContents(), "Fluid input hatch ejects until adjacent capacity is exhausted");
            helper.assertTrue(firstTarget.getMutableFluidStorage().getAmountAsLong() == 8000, "First fluid target is filled");
            helper.assertTrue(secondTarget.getMutableFluidStorage().getAmountAsLong() == 8000, "Second fluid target is filled");
            helper.assertTrue(source.getMutableFluidStorage().getAmountAsLong() == 1, "Fluid input hatch preserves the remaining fluid");
            helper.succeed();
        });
    }

    public void energyInputEjectionStopsAfterFirstTarget(GameTestHelper helper) {
        EnergyHatchBlockEntity source = placeEnergyInputPort(helper, new BlockPos(0, 1, 0));
        EnergyHatchBlockEntity firstTarget = placeEnergyInputPort(helper, new BlockPos(1, 1, 0));
        EnergyHatchBlockEntity secondTarget = placeEnergyInputPort(helper, new BlockPos(-1, 1, 0));
        source.getMutableEnergyStorage().forceInsert(2, false);

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(source.ejectContents(), "Energy input hatch ejects its contents");
            long firstAmount = firstTarget.getMutableEnergyStorage().getAmountAsLong();
            long secondAmount = secondTarget.getMutableEnergyStorage().getAmountAsLong();
            helper.assertTrue((firstAmount == 2 && secondAmount == 0) || (firstAmount == 0 && secondAmount == 2),
                    "Exactly one adjacent energy target receives all contents");
            helper.assertTrue(source.getMutableEnergyStorage().getAmountAsLong() == 0, "Energy input hatch is empty after a complete first transfer");
            helper.succeed();
        });
    }

    public void energyInputEjectionContinuesAfterPartialTarget(GameTestHelper helper) {
        EnergyHatchBlockEntity source = placeEnergyInputPort(helper, new BlockPos(0, 1, 0));
        EnergyHatchBlockEntity firstTarget = placeEnergyInputPort(helper, new BlockPos(1, 1, 0));
        EnergyHatchBlockEntity secondTarget = placeEnergyInputPort(helper, new BlockPos(-1, 1, 0));
        source.getMutableEnergyStorage().forceInsert(2, false);
        firstTarget.getMutableEnergyStorage().forceInsert(999999, false);
        secondTarget.getMutableEnergyStorage().forceInsert(999999, false);

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(source.ejectContents(), "Energy input hatch ejects to partially available targets");
            helper.assertTrue(firstTarget.getMutableEnergyStorage().getAmountAsLong() == 1000000, "First energy target receives its remaining capacity");
            helper.assertTrue(secondTarget.getMutableEnergyStorage().getAmountAsLong() == 1000000, "Second energy target receives the remaining FE");
            helper.assertTrue(source.getMutableEnergyStorage().getAmountAsLong() == 0, "Energy input hatch is empty after both partial transfers");
            helper.succeed();
        });
    }

    public void energyInputEjectionPreservesRemainder(GameTestHelper helper) {
        EnergyHatchBlockEntity source = placeEnergyInputPort(helper, new BlockPos(0, 1, 0));
        EnergyHatchBlockEntity firstTarget = placeEnergyInputPort(helper, new BlockPos(1, 1, 0));
        EnergyHatchBlockEntity secondTarget = placeEnergyInputPort(helper, new BlockPos(-1, 1, 0));
        source.getMutableEnergyStorage().forceInsert(3, false);
        firstTarget.getMutableEnergyStorage().forceInsert(999999, false);
        secondTarget.getMutableEnergyStorage().forceInsert(999999, false);

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(source.ejectContents(), "Energy input hatch ejects until adjacent capacity is exhausted");
            helper.assertTrue(firstTarget.getMutableEnergyStorage().getAmountAsLong() == 1000000, "First energy target is filled");
            helper.assertTrue(secondTarget.getMutableEnergyStorage().getAmountAsLong() == 1000000, "Second energy target is filled");
            helper.assertTrue(source.getMutableEnergyStorage().getAmountAsLong() == 1, "Energy input hatch preserves the remaining FE");
            helper.succeed();
        });
    }

    private ItemBusBlockEntity placeItemInputPort(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        return helper.getBlockEntity(pos, ItemBusBlockEntity.class);
    }

    private FluidHatchBlockEntity placeFluidInputPort(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState());
        return helper.getBlockEntity(pos, FluidHatchBlockEntity.class);
    }

    private EnergyHatchBlockEntity placeEnergyInputPort(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());
        return helper.getBlockEntity(pos, EnergyHatchBlockEntity.class);
    }

    private ChestBlockEntity placeChest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST.defaultBlockState());
        return helper.getBlockEntity(pos, ChestBlockEntity.class);
    }

    private void fillChestExceptOne(ChestBlockEntity chest) {
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 63));
        for (int slot = 1; slot < chest.getContainerSize(); slot++) {
            chest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
    }
}
