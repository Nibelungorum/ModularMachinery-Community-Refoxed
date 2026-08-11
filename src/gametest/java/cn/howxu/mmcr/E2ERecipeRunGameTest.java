package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class E2ERecipeRunGameTest {

    public void ironCompressorRuns(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("iron_compressor")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        BlockPos inputPos = new BlockPos(1, 2, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class).getItemHandler(null)
                .insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class).getItemHandler(null)
                .insertItem(1, new ItemStack(Items.IRON_INGOT), false);
        BlockPos outputPos = new BlockPos(1, 2, 2);
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());
        BlockPos energyPos = new BlockPos(2, 2, 1);
        helper.setBlock(energyPos, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());
        var energyInput = helper.getBlockEntity(energyPos, EnergyInputHatchBlockEntity.class).getMutableEnergyStorage(null);
        while (energyInput.receiveEnergy(10000, false) > 0) {}

        Identifier machineId = Identifier.fromNamespaceAndPath(MMCR.MODID, "iron_compressor");
        var machine = MachineRegistry.getMachine(machineId);
        RecipeRegistry.register(new MachineRecipe(Identifier.fromNamespaceAndPath(MMCR.MODID, "iron_compressor_recipe"),
                machineId, 40,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2),
                        new MachineIngredient.EnergyIngredient(80)),
                List.of(new ItemStack(Items.IRON_NUGGET)), List.of(), -100, 1));

        var controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(machine);
        for (int tick = 1; tick <= 80; tick++) {
            helper.runAtTickTime(tick, controller::serverTick);
        }
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(controller.isFormed(), "Structure formed");
            ItemStack input = helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class).getItemHandler(null).getStackInSlot(0);
            ItemStack input1 = helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class).getItemHandler(null).getStackInSlot(1);
            ItemStack output = helper.getBlockEntity(outputPos, ItemOutputBusBlockEntity.class).getItemHandler(null).getStackInSlot(0);
            int energy = helper.getBlockEntity(energyPos, EnergyInputHatchBlockEntity.class).getEnergyStorage(null).getEnergyStored();
            helper.assertTrue(input.isEmpty() && input1.isEmpty(), "Input ingots consumed");
            helper.assertTrue(output.is(Items.IRON_NUGGET), "Output is iron nugget");
            helper.assertTrue(energy == 4992, "Energy consumed per tick");
            helper.succeed();
        });
    }

    public void recipeUsesPortsFromMatchedPatternOutsideLegacyScan(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(2, 1, 2);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("wide_compressor")).get().defaultBlockState());

        BlockPos inputPos = controllerPos.offset(2, 0, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class).getItemHandler(null)
                .insertItem(0, new ItemStack(Items.IRON_INGOT), false);

        BlockPos outputPos = controllerPos.offset(0, 0, 2);
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());

        BlockPos energyPos = controllerPos.offset(-2, 0, 0);
        helper.setBlock(energyPos, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());
        var energyInput = helper.getBlockEntity(energyPos, EnergyInputHatchBlockEntity.class).getMutableEnergyStorage(null);
        while (energyInput.receiveEnergy(10000, false) > 0) {}

        Map<BlockPos, BlockPredicate> pattern = new HashMap<>();
        pattern.put(inputPos.subtract(controllerPos), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()));
        pattern.put(outputPos.subtract(controllerPos), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get()));
        pattern.put(energyPos.subtract(controllerPos), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("energy_input_hatch").get()));

        Identifier machineId = Identifier.fromNamespaceAndPath(MMCR.MODID, "wide_compressor");
        var machine = new DynamicMachine(machineId, "Wide Compressor", new BlockArray(pattern));
        MachineRegistry.register(machine);
        RecipeRegistry.register(new MachineRecipe(Identifier.fromNamespaceAndPath(MMCR.MODID, "wide_compressor_recipe"),
                machineId, 20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1),
                        new MachineIngredient.EnergyIngredient(50)),
                List.of(new ItemStack(Items.IRON_NUGGET))));

        var controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(machine);
        controller.serverTick();
        helper.assertTrue(controller.isFormed(), "Structure formed");
        for (int tick = 0; tick < 20; tick++) controller.serverTick();

        ItemStack input = helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class).getItemHandler(null).getStackInSlot(0);
        ItemStack output = helper.getBlockEntity(outputPos, ItemOutputBusBlockEntity.class).getItemHandler(null).getStackInSlot(0);
        int energy = helper.getBlockEntity(energyPos, EnergyInputHatchBlockEntity.class).getEnergyStorage(null).getEnergyStored();
        helper.assertTrue(input.isEmpty(), "Pattern input bus consumed ingot outside legacy scan");
        helper.assertTrue(output.is(Items.IRON_NUGGET), "Pattern output bus received nugget outside legacy scan");
        helper.assertTrue(energy == 7192, "Pattern energy hatch consumed energy outside legacy scan");
        helper.succeed();
    }
}
