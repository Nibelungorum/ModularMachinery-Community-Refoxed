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
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

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
        inputBus.toggleAutoIOSide(Direction.EAST);
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
        outputHatch.getFluidHandler(Direction.EAST).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);

        outputHatch.toggleAutoIOEnabled();
        outputHatch.toggleAutoIOSide(Direction.EAST);
        helper.runAtTickTime(60, outputHatch::serverTick);
        helper.runAtTickTime(80, () -> {
            FluidStack exported = receiver.getFluidHandler(Direction.WEST).getFluidInTank(0);
            helper.assertTrue(exported.getFluid() == Fluids.WATER, "Fluid output hatch exports water east");
            helper.assertTrue(exported.getAmount() > 0, "Fluid output hatch moves water into east receiver");
            helper.assertTrue(outputHatch.getFluidHandler(Direction.EAST).getFluidInTank(0).getAmount() < 1000, "Fluid output hatch loses water to auto output");
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
        EnergyStorage outputStorage = outputHatch.getMutableEnergyStorage(Direction.EAST);
        outputStorage.receiveEnergy(700, false);

        outputHatch.toggleAutoIOEnabled();
        outputHatch.toggleAutoIOSide(Direction.EAST);
        helper.runAtTickTime(60, outputHatch::serverTick);
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(receiver.getEnergyStorage(Direction.WEST).getEnergyStored() > 0, "Energy output hatch exports FE east");
            helper.assertTrue(outputStorage.getEnergyStored() < 700, "Energy output hatch loses FE to auto output");
            helper.succeed();
        });
    }
}
