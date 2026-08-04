package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
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
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("iron_compressor")).get().defaultBlockState());
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
        helper.assertTrue(energy == 6800, "Energy consumed");
        helper.succeed();
    }
}
