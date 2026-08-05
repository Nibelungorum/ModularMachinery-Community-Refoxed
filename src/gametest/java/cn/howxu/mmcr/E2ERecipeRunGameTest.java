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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GameTestHolder(MMCR.MODID)
public class E2ERecipeRunGameTest {

    @GameTest(template = "minecraft:empty", timeoutTicks = 200)
    public void ironCompressorRuns(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("iron_compressor")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        BlockPos inputPos = new BlockPos(1, 2, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class).getItemHandler(null)
                .insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false);
        BlockPos outputPos = new BlockPos(1, 2, 2);
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());
        BlockPos energyPos = new BlockPos(2, 2, 1);
        helper.setBlock(energyPos, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());
        helper.getBlockEntity(energyPos, EnergyInputHatchBlockEntity.class).getEnergyStorage(null).receiveEnergy(10000, false);

        Map<BlockPos, BlockPredicate> pattern = new HashMap<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) pattern.put(new BlockPos(x, 0, z),
                    new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
        pattern.put(inputPos.subtract(controllerPos), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()));
        pattern.put(outputPos.subtract(controllerPos), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get()));
        pattern.put(energyPos.subtract(controllerPos), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("energy_input_hatch").get()));
        Identifier machineId = Identifier.fromNamespaceAndPath(MMCR.MODID, "iron_compressor");
        var machine = new DynamicMachine(machineId, "Iron Compressor", new BlockArray(pattern));
        MachineRegistry.register(machine);
        RecipeRegistry.register(new MachineRecipe(Identifier.fromNamespaceAndPath(MMCR.MODID, "iron_compressor_recipe"),
                machineId, 40,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2),
                        new MachineIngredient.EnergyIngredient(80)),
                List.of(new ItemStack(Items.IRON_NUGGET))));

        var controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(machine);
        controller.serverTick();
        helper.assertTrue(controller.isFormed(), "Structure formed");
        for (int tick = 0; tick < 40; tick++) controller.serverTick();
        ItemStack input = helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class).getItemHandler(null).getStackInSlot(0);
        ItemStack output = helper.getBlockEntity(outputPos, ItemOutputBusBlockEntity.class).getItemHandler(null).getStackInSlot(0);
        int energy = helper.getBlockEntity(energyPos, EnergyInputHatchBlockEntity.class).getEnergyStorage(null).getEnergyStored();
        helper.assertTrue(input.isEmpty(), "Input ingots consumed");
        helper.assertTrue(output.is(Items.IRON_NUGGET), "Output is iron nugget");
        helper.assertTrue(energy == 6800, "Energy consumed per tick");
        helper.succeed();
    }

    @GameTest(template = "minecraft:empty", timeoutTicks = 200)
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
        helper.getBlockEntity(energyPos, EnergyInputHatchBlockEntity.class).getEnergyStorage(null).receiveEnergy(10000, false);

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
        helper.assertTrue(energy == 9000, "Pattern energy hatch consumed energy outside legacy scan");
        helper.succeed();
    }
}
